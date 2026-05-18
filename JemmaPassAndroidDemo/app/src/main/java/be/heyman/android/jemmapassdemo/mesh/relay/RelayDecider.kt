package be.heyman.android.jemmapassdemo.mesh.relay

import be.heyman.android.jemmapassdemo.mesh.codec.ChunkType
import be.heyman.android.jemmapassdemo.mesh.codec.EventChunk
import be.heyman.android.jemmapassdemo.mesh.codec.RelayedChunkCodec
import be.heyman.android.jemmapassdemo.mesh.codec.TtlPolicy
import kotlin.random.Random

/**
 * 🆕 L44.16.75 — Core relay decision logic.
 *
 * For each incoming chunk, decides whether to :
 *   • DROP    — already seen, expired TTL, self-loop, or malformed
 *   • RELAY   — forward after TTL decrement and listen-first backoff
 *
 * ── Council Round 1 verdict (claudeai SNR-weighted listen-first) ──
 *
 * The decision sequence is :
 *   1. Parse chunk type. If unknown → DROP.
 *   2. If already-seen (Bloom dedup hit) → DROP.
 *   3. If author == mySid (self-loop guard) → DROP.
 *   4. Decrement TTL. If new TTL ≤ 0 → DROP.
 *   5. Compute listen-first backoff (50-200ms, optionally SNR-weighted).
 *   6. Caller listens during backoff; if echo heard, cancel relay.
 *   7. Otherwise, relay the (TTL-decremented) chunk.
 *
 * ── Self-loop guard (claudeai R1 blind spot finding) ────────────
 *
 * Critical : if Kurodo's phone is also broadcasting his own V| chunks,
 * Phone C might receive Kurodo's data via TWO paths — directly (rare,
 * he's trapped under a beam) or via Kamekichi's relay. The relay must
 * NOT echo back chunks that Kurodo himself originated, otherwise we'd
 * amplify the SOS broadcast and waste mesh bandwidth.
 *
 * The guard : if `parsedChunk.sourceSid == mySid`, drop. The original
 * broadcaster doesn't need its own data echoed back at it.
 *
 * ── Listen-first (Meshtastic / Trickle pattern) ─────────────────
 *
 * Instead of immediately rebroadcasting, wait a random 50-200ms backoff.
 * During that window, listen for the same chunk being broadcast by
 * another peer. If heard, cancel our own relay (the other peer already
 * did the work). This is THE technique for taming broadcast storms
 * without complex routing.
 *
 * Optional SNR-weighted backoff (claudeai R1) — strong signal nodes
 * get LARGER backoff (they're close to origin, less useful as relay);
 * weak signal nodes get SHORTER backoff (they're at the edge, more
 * useful as relay). For v1 we use flat random backoff; SNR weighting
 * can be added later if Nearby exposes RSSI cleanly.
 *
 * ── Usage ───────────────────────────────────────────────────────
 *
 *   val decider = RelayDecider(bloom, mySid = "abcd")
 *   val decision = decider.decide(incomingChunk)
 *   when (decision) {
 *       is RelayDecision.Drop -> log("Dropped: ${decision.reason}")
 *       is RelayDecision.Relay -> {
 *           delay(decision.backoffMs)
 *           if (!bloom.contains(chunk)) {  // re-check, peer may have echoed
 *               nearby.startAdvertising(decision.rewrittenChunk)
 *           }
 *       }
 *   }
 */
class RelayDecider(
    private val bloom: BloomDedup,
    private val mySid: String,
    /** Min listen-first backoff in ms. */
    private val backoffMinMs: Long = 50L,
    /** Max listen-first backoff in ms. */
    private val backoffMaxMs: Long = 200L,
    /** Injectable random for deterministic tests. */
    private val random: Random = Random.Default
) {

    /** Outcome of a relay decision. */
    sealed class RelayDecision {
        /** Drop the chunk. [reason] is for logging/diagnostics only. */
        data class Drop(val reason: String) : RelayDecision()

        /**
         * Relay the chunk. Caller should sleep for [backoffMs] then
         * check the bloom one more time before broadcasting [rewrittenChunk].
         */
        data class Relay(val rewrittenChunk: String, val backoffMs: Long) : RelayDecision()
    }

    /**
     * Decide what to do with an incoming chunk.
     *
     * @param incomingChunk raw wire-format chunk received from a peer
     * @return Drop or Relay decision
     */
    fun decide(incomingChunk: String): RelayDecision {
        val type = ChunkType.detectPrefix(incomingChunk)
            ?: return RelayDecision.Drop("unknown prefix")

        // 1. Self-loop guard — extract origin SID (this differs by chunk type)
        val origin = extractOriginSid(incomingChunk, type)
            ?: return RelayDecision.Drop("malformed: cannot extract origin")
        if (origin == mySid) {
            return RelayDecision.Drop("self-loop")
        }

        // 2. Bloom dedup — have we forwarded this exact chunk recently?
        val dedupKey = computeDedupKey(incomingChunk, type, origin)
        if (bloom.seenOrAdd(dedupKey)) {
            return RelayDecision.Drop("dedup hit")
        }

        // 3. TTL handling — decrement if relayable, drop if exhausted
        val rewritten: String = when (type) {
            ChunkType.VICTIM_DIRECT -> {
                // Direct V| chunk → wrap as VR| with initial TTL
                RelayedChunkCodec.wrapVictim(incomingChunk, TtlPolicy.TTL_VICTIM)
            }
            ChunkType.RESCUER_DIRECT -> {
                RelayedChunkCodec.wrapRescuer(incomingChunk, TtlPolicy.TTL_RESCUER)
            }
            ChunkType.VICTIM_RELAYED, ChunkType.RESCUER_RELAYED -> {
                val currentTtl = RelayedChunkCodec.extractTtl(incomingChunk)
                    ?: return RelayDecision.Drop("malformed: missing TTL")
                val newTtl = TtlPolicy.decrement(currentTtl)
                if (newTtl <= 0) return RelayDecision.Drop("TTL exhausted")
                RelayedChunkCodec.rewriteTtl(incomingChunk, newTtl)
                    ?: return RelayDecision.Drop("malformed: TTL rewrite failed")
            }
            ChunkType.EVENT -> {
                val event = EventChunk.parse(incomingChunk)
                    ?: return RelayDecision.Drop("malformed event")
                val newEvent = event.decrementTtl()
                    ?: return RelayDecision.Drop("event TTL exhausted")
                newEvent.encode()
            }
            // 🆕 L44.16.87b — Fingerprint chunks must never be relayed.
            //
            // A F| chunk is locally-meaningful only : it represents
            // "this is the SHA-256 of MY cached profile state". If
            // device A relays B's F| to device C, then C would receive
            // a hash that doesn't match anyone's actual local state
            // (it matches B's cache, not A's, not C's). At best : noise.
            // At worst : false cache hits causing missed profile updates.
            //
            // RelayDecider being defense-in-depth : even though
            // JemmaNearbySosService.handleMeshChunk intercepts F|
            // BEFORE us, we drop here too in case some future code path
            // bypasses the interceptor.
            ChunkType.FINGERPRINT -> return RelayDecision.Drop("fingerprint never relayed")
        }

        // 4. Listen-first backoff
        val backoff = random.nextLong(backoffMinMs, backoffMaxMs + 1)

        return RelayDecision.Relay(rewritten, backoff)
    }

    /**
     * Extract the origin SID from an incoming chunk. This is needed for
     * the self-loop guard. The position of the SID differs by chunk type.
     */
    private fun extractOriginSid(chunk: String, type: ChunkType): String? {
        val parts = chunk.split('|')
        return when (type) {
            ChunkType.VICTIM_DIRECT, ChunkType.RESCUER_DIRECT -> parts.getOrNull(1)
            ChunkType.VICTIM_RELAYED, ChunkType.RESCUER_RELAYED -> parts.getOrNull(1)
            ChunkType.EVENT -> parts.getOrNull(1)  // E|<source_sid>|...
            // 🆕 L44.16.87b — F|<sid>|<hash>|<ts>|<v>
            //
            // Fingerprint chunks carry the victim sid at index 1, just
            // like V|/VR|. We provide the right answer here for any
            // diagnostic path that calls extractOriginSid on a F|
            // chunk (even though decide() will Drop F| earlier).
            ChunkType.FINGERPRINT -> parts.getOrNull(1)
        }
    }

    /**
     * Compute the dedup key for a chunk. Per R1 cross-cutting note (kimi),
     * the key for events must be (source_sid, victim_sid, seq) to avoid
     * collisions on the per-source seq counter.
     *
     * For V/VR/S/SR chunks, the key includes (type, sid, chunk_index)
     * so different chunks of the same profile don't collide.
     */
    private fun computeDedupKey(chunk: String, type: ChunkType, origin: String): String {
        val parts = chunk.split('|')
        return when (type) {
            ChunkType.VICTIM_DIRECT -> "V|$origin|${parts.getOrNull(2) ?: "?"}"
            ChunkType.VICTIM_RELAYED -> "VR|$origin|${parts.getOrNull(2) ?: "?"}"
            ChunkType.RESCUER_DIRECT -> "S|$origin"
            ChunkType.RESCUER_RELAYED -> "SR|$origin"
            ChunkType.EVENT -> {
                // E|<source>|<victim>|<status>|<rescuer>|<ts>|<ttl>|<seq>
                // Key: (source, victim, seq) — claudeai R1 + kimi cross-cut note
                val victim = parts.getOrNull(2) ?: "?"
                val seq = parts.getOrNull(7) ?: "?"
                "E|$origin|$victim|$seq"
            }
            // 🆕 L44.16.87b — F|<sid>|<hash>|<ts>|<v>
            //
            // Dedup key includes the hash to distinguish two F| with
            // the same sid but different content (which happens whenever
            // the victim's profile changes and they emit a fresh F|).
            // Without the hash in the key, a peer's profile UPDATE would
            // be silently swallowed by Bloom dedup as if it were a
            // duplicate of the OLD fingerprint.
            //
            // Defense in depth : RelayDecider.decide() will Drop F|
            // before reaching this point, but we provide a sane key
            // here in case the dedup layer is ever called independently.
            ChunkType.FINGERPRINT -> {
                val hash = parts.getOrNull(2) ?: "?"
                "F|$origin|$hash"
            }
        }
    }
}
