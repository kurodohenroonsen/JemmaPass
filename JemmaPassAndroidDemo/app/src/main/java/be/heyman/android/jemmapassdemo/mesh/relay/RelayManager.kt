package be.heyman.android.jemmapassdemo.mesh.relay

import be.heyman.android.jemmapassdemo.mesh.codec.ChunkType
import be.heyman.android.jemmapassdemo.mesh.codec.EventChunk
import be.heyman.android.jemmapassdemo.triage.SaltCode
import be.heyman.android.jemmapassdemo.triage.StatusEvent
import be.heyman.android.jemmapassdemo.triage.StatusResolver

/**
 * 🆕 L44.16.76 — Orchestrator that owns BloomDedup + RelayCache +
 * RelayDecider + StatusResolver, providing a thread-safe API for
 * `JemmaNearbySosService` to wire mesh logic into the rotation loop.
 *
 * ── Lifecycle ───────────────────────────────────────────────────
 *
 *   1. `JemmaNearbySosService` creates one `RelayManager` instance
 *      tied to the local SID (`mySid`).
 *   2. On every received chunk : `manager.onChunkReceived(rawChunk)`
 *      returns a `MeshAction` describing what the service should do
 *      (drop, ack only, relay, or relay+notify-listener).
 *   3. On every rotation tick : `manager.selectRelayChunks(quota)`
 *      returns the chunks worth re-broadcasting from the cache.
 *   4. The service is responsible for the actual `startAdvertising()`
 *      calls — RelayManager doesn't touch Nearby directly.
 *
 * ── Thread safety ───────────────────────────────────────────────
 *
 * All public methods are `@Synchronized`. The Nearby callbacks fire
 * on GMS threads while the rotation loop runs on Dispatchers.IO ;
 * locking via `synchronized` on the instance is the simplest correct
 * answer (kimi R1 threading note). For the demo's <50 events/min
 * throughput, lock contention is negligible.
 *
 * ── Connection to UI ────────────────────────────────────────────
 *
 * When an EVENT chunk arrives with a status change that wins the
 * StatusResolver vote, RelayManager fires `onTriageEvent(StatusEvent)`
 * via a listener. The radar bridge converts this to a JS callback
 * that updates the radar pin badge and the bottom sheet UI.
 */
class RelayManager(
    val mySid: String,
    private val bloom: BloomDedup = BloomDedup(),
    private val cache: RelayCache = RelayCache(),
    private val decider: RelayDecider = RelayDecider(bloom, mySid),
    private val resolver: StatusResolver = StatusResolver()
) {
    companion object {
        private const val TAG = "JEMMA-RELAY"

        /**
         * 🆕 L44.16.76 hotfix — Android `Log` is not available in JVM
         * unit tests (would throw "Method i in android.util.Log not
         * mocked"). This wrapper uses reflection so the same code works
         * in production (logs to logcat) AND in tests (silently noops).
         *
         * Same pattern as in `MeshByteSafety` — the lesson from L44.16.74.
         */
        private fun safeLog(level: String, msg: String) {
            try {
                val logClass = Class.forName("android.util.Log")
                val method = logClass.getMethod(level, String::class.java, String::class.java)
                method.invoke(null, TAG, msg)
            } catch (_: Throwable) {
                // JVM unit test environment — silently drop
            }
        }
    }

    /** Listener for triage events that have changed the radar state. */
    fun interface TriageEventListener {
        fun onTriageEvent(event: StatusEvent)
    }

    @Volatile private var triageListener: TriageEventListener? = null
    fun setTriageListener(l: TriageEventListener?) { triageListener = l }

    /**
     * Decision returned by `onChunkReceived`. Tells the service what
     * to do with the chunk we just received.
     */
    sealed class MeshAction {
        /** Drop silently (dedup hit, self-loop, malformed, expired). */
        object Drop : MeshAction()

        /**
         * Drop the chunk for relay purposes (TTL exhausted or self-loop)
         * but the caller may still want to USE the chunk locally (e.g.
         * to update its own peer list with profile data).
         */
        data class UseLocallyOnly(val reason: String) : MeshAction()

        /**
         * Queue the chunk for relay after [backoffMs]. The wire-format
         * string [rewrittenChunk] is what should be broadcast.
         * Caller schedules re-broadcast.
         */
        data class Relay(val rewrittenChunk: String, val backoffMs: Long) : MeshAction()
    }

    /**
     * Process an incoming chunk. Returns a MeshAction describing what
     * the service should do.
     *
     * Side effects (when applicable) :
     *   • Bloom dedup updated
     *   • RelayCache populated for future selectRelayChunks() calls
     *   • StatusResolver updated for E| events that win LWW
     *   • TriageEventListener notified for events that changed state
     */
    @Synchronized
    fun onChunkReceived(rawChunk: String): MeshAction {
        val type = ChunkType.detectPrefix(rawChunk) ?: return MeshAction.Drop

        // Process EVENT chunks first — they have triage side effects
        if (type == ChunkType.EVENT) {
            // 🆕 L44.16.93 — Trace EVERY event chunk that lands here, BEFORE
            // it is parsed/decided. If a STAB/DCD never logs at this entry
            // point during a dryrun, the loss happened upstream (Nearby
            // discovery dropped it / duty-cycle window / dedup at Nearby
            // layer). If it logs here but never reaches handleEventLocally,
            // the parser failed. If it reaches handleEventLocally but
            // doesn't change state, the resolver vetoed it.
            safeLog("i", "🩺 [RX-ENTRY] event chunk arrived: ${rawChunk.take(60)}")
            val event = EventChunk.parse(rawChunk)
            if (event != null) {
                handleEventLocally(event, hopCount = computeHopCount(event))
            } else {
                safeLog("w", "🩺 [RX-ENTRY] malformed E| chunk: ${rawChunk.take(40)}…")
            }
        }

        // Now ask the decider what to do at the relay level
        return when (val decision = decider.decide(rawChunk)) {
            is RelayDecider.RelayDecision.Drop -> {
                // For self-loop or TTL-exhausted, the chunk content may
                // still be useful locally (we got the profile/event data).
                if (decision.reason == "self-loop" ||
                    decision.reason.contains("TTL")
                ) {
                    MeshAction.UseLocallyOnly(decision.reason)
                } else {
                    MeshAction.Drop
                }
            }
            is RelayDecider.RelayDecision.Relay -> {
                // Cache the rewritten (TTL-decremented) chunk for rotation
                cache.put(buildCacheEntry(decision.rewrittenChunk, type), mySid)
                MeshAction.Relay(decision.rewrittenChunk, decision.backoffMs)
            }
        }
    }

    /**
     * Publish a NEW event from this device (e.g. user tapped STAB on
     * the radar UI). Encodes the event, broadcasts it via the listener,
     * and updates local resolver state.
     *
     * @return the wire-format chunk that should be added to the
     *         advertise rotation immediately.
     */
    @Synchronized
    fun publishEvent(
        victimSid: String,
        status: SaltCode,
        rescuerSid: String,
        timestampSec: Long = System.currentTimeMillis() / 1000,
        seq: Int = nextSeq(victimSid),
        isExplicitOverride: Boolean = false
    ): String {
        val event = StatusEvent(
            victimSid = victimSid,
            status = status,
            rescuerSid = rescuerSid,
            timestampSec = timestampSec,
            hopCount = 0,  // we are the origin
            isExplicitOverride = isExplicitOverride,
            seq = seq
        )

        val ttl = be.heyman.android.jemmapassdemo.mesh.codec.TtlPolicy.initial(
            be.heyman.android.jemmapassdemo.mesh.codec.ChunkType.EVENT,
            status.code
        )
        // 🆕 L44.16.83 — Use TtlPolicy.initial() instead of an inline
        // when{} so that the policy lives in ONE place (TtlPolicy.kt).
        // Audit dryrun 14h41 of 2026-05-08 caught the bug: L44.16.82
        // patched TtlPolicy.kt to bump DCD to TTL_EVENT_CRITICAL=3 but
        // RelayManager had a duplicated hardcoded `when {}` here that
        // ignored the policy → wire format kept TTL=1 → Samsung dropped
        // every relayed DCD with "use-locally-only event TTL exhausted".
        // Now there is only ONE definition of TTL per event status.

        val chunk = EventChunk(
            sourceSid = mySid,
            victimSid = victimSid,
            status = status.code,
            rescuerSid = rescuerSid,
            timestampSec = timestampSec,
            ttl = ttl,
            seq = seq
        ).encode()

        // Apply locally immediately (so UI updates before mesh round-trip)
        val applied = resolver.apply(event)
        triageListener?.onTriageEvent(applied)

        // Mark in bloom so we don't re-relay our own event when it echoes
        bloom.seenOrAdd("E|$mySid|$victimSid|$seq")

        // 🆕 L44.16.83 — Enriched log: explicit TTL + status + size, so
        // dryrun audits can verify TTL policy at a glance and Council
        // brainstorming has direct evidence of wire-level state.
        safeLog(
            "i",
            "publishEvent: status=${status.code} victim=${victimSid.take(6)} " +
                "rescuer=${rescuerSid.take(6)} ttl=$ttl seq=$seq " +
                "size=${chunk.toByteArray().size}B chunk=$chunk"
        )
        return chunk
    }

    /**
     * Select up to [quota] relay chunks for the next broadcast cycle,
     * highest-priority first.
     */
    @Synchronized
    fun selectRelayChunks(quota: Int): List<String> {
        return cache.selectForBroadcast(quota, mySid).map { it.chunk }
    }

    /** Periodic GC pass — call before each rotation. */
    @Synchronized
    fun reapStale(): Int {
        return cache.reapStale()
    }

    /** Get the current best-known status for a victim. */
    @Synchronized
    fun getStatusFor(victimSid: String): StatusEvent? = resolver.get(victimSid)

    /** Get all current statuses. */
    @Synchronized
    fun getAllStatuses(): Map<String, StatusEvent> = resolver.all()

    /** Clear all state. Used by "wipe" / debug menu. */
    @Synchronized
    fun clear() {
        bloom.clear()
        cache.clear()
        resolver.clear()
        seqCounters.clear()
    }

    // ─── private helpers ────────────────────────────────────────

    /**
     * Per-(source, victim) sequence counters. Per kimi's R1 cross-cut
     * note, the seq must be scoped to the (source, victim) pair so two
     * events on different victims from the same rescuer don't collide.
     */
    private val seqCounters = mutableMapOf<String, Int>()

    private fun nextSeq(victimSid: String): Int {
        val key = "$mySid|$victimSid"
        val next = (seqCounters[key] ?: 0) + 1
        // Roll over at 100 (2 wire chars)
        seqCounters[key] = next % 100
        return next % 100
    }

    private fun handleEventLocally(event: EventChunk, hopCount: Int) {
        val salt = SaltCode.parse(event.status) ?: run {
            safeLog("w", "🩺 [RX] event SALT code unknown: '${event.status}' victim=${event.victimSid.take(6)} ttl=${event.ttl} seq=${event.seq}")
            return
        }
        val statusEvent = StatusEvent(
            victimSid = event.victimSid,
            status = salt,
            rescuerSid = event.rescuerSid,
            timestampSec = event.timestampSec,
            hopCount = hopCount,
            seq = event.seq
        )

        val before = resolver.get(event.victimSid)
        val after = resolver.apply(statusEvent)

        // 🆕 L44.16.93 — Verbose RX trace, fired on EVERY event chunk we
        // process (not only on state-change). Lets the dryrun log show
        // when an event was *received* but ignored by the resolver — the
        // STAB/DCD-perdu hypothesis from the 2026-05-09 10:36 dryrun
        // needs this visibility to confirm whether incoming chunks reach
        // here at all (mesh problem) vs are silently dropped by the
        // shouldOverwrite() logic (resolver problem).
        val didChange = before != after
        safeLog("i",
            "🩺 [RX] handleEventLocally victim=${event.victimSid.take(6)} " +
            "status=${salt.code} hops=$hopCount ts=${event.timestampSec} " +
            "seq=${event.seq} ttl=${event.ttl} " +
            "before=${before?.status?.code ?: "—"}/${before?.seq ?: "—"} " +
            "after=${after.status.code}/${after.seq} " +
            "changed=$didChange")

        // Only notify listener if state actually changed
        if (didChange) {
            triageListener?.onTriageEvent(after)
            safeLog("i", "🩺 [RX]   ↳ triage applied: ${event.victimSid.take(6)} → ${salt.code} " +
                "(hops=$hopCount, ts=${event.timestampSec})")
        } else {
            // Helpful : explain *why* we kept the existing state.
            val reason = when {
                before == null -> "no-existing"
                before.status == salt && before.seq >= event.seq -> "older-or-equal-seq"
                before.timestampSec >= event.timestampSec -> "older-or-equal-ts"
                else -> "lost-LWW-tiebreak"
            }
            safeLog("i", "🩺 [RX]   ↳ no-op (reason=$reason)")
        }
    }

    private fun computeHopCount(event: EventChunk): Int {
        // 🆕 L44.16.93 — Use TtlPolicy.initial() instead of an inline
        // when{} so that the policy lives in ONE place. The previous
        // inline `when{ "WAIT","DCD" -> 1 }` was inconsistent with
        // TtlPolicy which says DCD → 3 (since L44.16.82). This caused
        // hopCount underflow for DCD events and confused dryrun audits.
        val initialTtl = be.heyman.android.jemmapassdemo.mesh.codec.TtlPolicy.initial(
            be.heyman.android.jemmapassdemo.mesh.codec.ChunkType.EVENT,
            event.status
        )
        return (initialTtl - event.ttl).coerceAtLeast(0)
    }

    private fun buildCacheEntry(chunk: String, type: ChunkType): RelayCache.Entry {
        val parts = chunk.split('|')
        val origin = parts.getOrNull(1) ?: "?"
        val saltCode = if (type == ChunkType.EVENT) parts.getOrNull(3) else null
        return RelayCache.Entry(
            chunk = chunk,
            type = type,
            sourceSid = origin,
            receivedAtMs = System.currentTimeMillis(),
            saltCode = saltCode
        )
    }
}
