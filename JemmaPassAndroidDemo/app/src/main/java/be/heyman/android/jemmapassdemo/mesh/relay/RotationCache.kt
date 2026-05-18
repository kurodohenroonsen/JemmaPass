package be.heyman.android.jemmapassdemo.mesh.relay

import be.heyman.android.jemmapassdemo.mesh.codec.ChunkType

/**
 * 🆕 L44.16.78 — Map-based rotation cache for outbound BLE chunks.
 *
 * ─── Why this exists ─────────────────────────────────────────────
 *
 * Before L44.16.78, the advertise rotation was a flat `List<String>`
 * with `if (size < 25) add()` as a cap. The 2-phone dry-run logs
 * (logs_fr_2b.txt + logs_fr_4b.txt at 06:56) revealed a critical
 * defect:
 *
 *   • Phone B (Kamekichi) advertised 4 E| chunks (HELP + STAB)
 *   • Phone A (Kurodo) received 0 E| chunks out of 45 received total
 *
 * The cause was structural:
 *   1. Cap=25 silently dropped new relay/event chunks once full
 *   2. Even when accepted, an E| chunk was 1 entry among 25 → only
 *      ~4% of advertise time slots
 *   3. After 1-2 cycles, the chunk was effectively buried behind the
 *      same V| / VR| chunks repeating in the loop
 *
 * ─── The new model: a filing cabinet, not a queue ─────────────────
 *
 * Each chunk has a fixed slot keyed by its identity. Receiving a
 * NEW VERSION of the same chunk (e.g. Haru's GPS moved) UPDATES the
 * existing slot rather than creating a new one. This means:
 *
 *   • No duplicates — same chunk = same key
 *   • Natural refresh — newer payload overwrites stale one
 *   • Unbounded but self-pruning — eviction by age, not by cap
 *   • Priority broadcasting — events boosted, cold relays demoted
 *
 * ─── Key format ──────────────────────────────────────────────────
 *
 *   V|<originSid>|<seq>      (own profile, multi-chunk)
 *   S|<originSid>             (own beacon, single-chunk)
 *   VR|<originSid>|<seq>     (relayed victim, multi-chunk)
 *   SR|<originSid>           (relayed beacon, single-chunk)
 *   E|<victimSid>             (triage event — note: NOT keyed by
 *                              rescuer/source, so LWW per-victim)
 *
 * Key examples:
 *   "V|H**-|1"     → V|H**-|1|5|Kurodo|M|47|B-|1|50.00027|4.44388|fr
 *   "V|H**-|2"     → V|H**-|2|5|A:735971005.91936005
 *   "S|;Ggu"       → S|;Ggu|Kamekichi|ja|50.00033|4.44389
 *   "VR|RegT|1"    → VR|RegT|1|5|2|Haru|F|70|A+|1|50.00029|4.44396|ja
 *   "E|H**-"       → E|;Ggu|H**-|HELP|;Ggu|1778216172|3|1
 *   "E|RegT"       → E|;Ggu|RegT|STAB|;Ggu|1778216027|3|1
 *
 * ─── TTL eviction policy ─────────────────────────────────────────
 *
 * Per Council Round 2 consensus (5/5 IA validation):
 *
 *   • Own V|/S|       : NEVER evicted (always re-broadcasted)
 *   • Relayed VR|/SR| : 60s since last refresh
 *   • Triage E|       : 180s since last refresh (Claude.ai win — late
 *                       arrivals like Pierre-Paul-Jacques must still
 *                       see HELP/STAB events that were tapped earlier)
 *
 * ─── Priority rotation ───────────────────────────────────────────
 *
 * The scheduler favours E| events 3× per cycle (spaced temporally,
 * not in adjacent slots) so that life-critical triage propagates
 * faster than profile chunks. See `selectNextChunk()` for the policy.
 *
 * ─── Concurrency ─────────────────────────────────────────────────
 *
 * Backed by `LinkedHashMap` with `synchronized` access. Iteration is
 * always over a snapshot to avoid ConcurrentModificationException
 * when handleMeshChunk fires from the discovery thread while the
 * rotation runnable iterates from the main thread.
 */
class RotationCache {

    /**
     * One slot in the rotation cabinet.
     *
     * @property chunk the encoded BLE wire chunk (≤131 bytes UTF-8)
     * @property type the chunk type (V/S/VR/SR/E)
     * @property updatedAtMs last time we touched this slot
     * @property broadcastCount how many times we have advertised this
     *           chunk since it entered the cache (used for fairness)
     * @property isOwn true for V|/S| we generate ourselves; never
     *           evicted by the age-based reaper
     */
    data class Entry(
        val chunk: String,
        val type: ChunkType,
        val updatedAtMs: Long,
        val broadcastCount: Long = 0L,
        val isOwn: Boolean = false
    )

    /**
     * Reasons a put might decide to no-op (rare; mostly for diagnostics).
     */
    enum class PutOutcome { Inserted, Updated, Skipped }

    private val store = LinkedHashMap<String, Entry>()

    /**
     * 🆕 L44.16.86 — Cache-hit window per peer SID.
     *
     * When a F| fingerprint chunk matches our local profile cache, we
     * mark the peer's SID here with an expiry timestamp. While the
     * marker is active, the rotation scheduler SKIPS the V|/VR| chunks
     * for that peer (treats them as already-known), freeing airtime
     * for events and other peers.
     *
     * This is the central anti-redundancy mechanism added in L44.16.86.
     * Without it, every restart of a peer's broadcast (e.g. after GPS
     * refresh) caused us to re-broadcast all 5 of their V| chunks even
     * though nothing had changed — wasting ~75 chunks per restart in
     * the L44.16.84 dryrun.
     *
     * Concurrency : guarded by class-level `@Synchronized` methods.
     */
    private val cacheHitUntilMs = HashMap<String, Long>()

    /**
     * Build the cache key for a given raw chunk.
     *
     * Returns null if the chunk is malformed or of unknown type.
     */
    fun keyFor(chunk: String): String? {
        val type = ChunkType.detectPrefix(chunk) ?: return null
        return when (type) {
            ChunkType.VICTIM_DIRECT -> {
                // V|<sid>|<seq>|<total>|<payload>
                val parts = chunk.split('|', limit = 4)
                if (parts.size < 3) return null
                "V|${parts[1]}|${parts[2]}"
            }
            ChunkType.RESCUER_DIRECT -> {
                // S|<sid>|<name>|<lang>|<lat>|<lon>
                val parts = chunk.split('|', limit = 3)
                if (parts.size < 2) return null
                "S|${parts[1]}"
            }
            ChunkType.VICTIM_RELAYED -> {
                // VR|<originSid>|<seq>|<total>|<ttl>|<payload>
                val parts = chunk.split('|', limit = 4)
                if (parts.size < 3) return null
                "VR|${parts[1]}|${parts[2]}"
            }
            ChunkType.RESCUER_RELAYED -> {
                // SR|<originSid>|<name>|<lang>|<lat>|<lon>|<ttl>
                val parts = chunk.split('|', limit = 3)
                if (parts.size < 2) return null
                "SR|${parts[1]}"
            }
            ChunkType.EVENT -> {
                // E|<rescuerSid>|<victimSid>|<saltCode>|<sourceSid>|<ts>|<ttl>|<seq>
                // Key is per VICTIM, not per rescuer (Council R2 LWW spec)
                val parts = chunk.split('|', limit = 4)
                if (parts.size < 3) return null
                "E|${parts[2]}"
            }
            ChunkType.FINGERPRINT -> {
                // 🆕 L44.16.86 — F|<sid>|<sha256_hex_8>|<lastModifiedTs>|<chunkVersion>
                // Key is per VICTIM SID. A new fingerprint for the same
                // victim REPLACES the old one (LWW), which is exactly
                // what we want : a profile update produces a new hash,
                // and the new F| chunk supersedes the old one.
                val parts = chunk.split('|', limit = 3)
                if (parts.size < 2) return null
                "F|${parts[1]}"
            }
        }
    }

    /**
     * Put or update a chunk. The newest payload wins for the same key
     * (LWW). Note: for E| events, the broadcast layer overwrite is
     * additionally moderated by `StatusResolver.merge()` BEFORE this
     * put is called — see RelayManager.publishEvent / handleMeshChunk.
     *
     * @return outcome (Inserted / Updated / Skipped)
     */
    @Synchronized
    fun put(
        chunk: String,
        nowMs: Long = System.currentTimeMillis(),
        isOwn: Boolean = false
    ): PutOutcome {
        val type = ChunkType.detectPrefix(chunk) ?: return PutOutcome.Skipped
        val key = keyFor(chunk) ?: return PutOutcome.Skipped
        val existing = store[key]

        // 🆕 L44.16.84 — Reset broadcastCount when the chunk PAYLOAD
        // differs from the existing one for the same key.
        //
        // ── Why this matters ──────────────────────────────────────
        // Empirical bug from dryrun 15:14 of 2026-05-08 on L44.83:
        //   1. Pixel publishEvent EVAL → key="E|RegT", broadcastCount=0
        //   2. Scheduler advertises EVAL 8 times → broadcastCount=8
        //   3. Pixel publishEvent DCD → key="E|RegT" (same key for
        //      same victim), but L44.82 logic kept broadcastCount=8
        //   4. New score = (8+1)/3 = 3.0 — much higher (worse) than
        //      the V|RegT|N chunks at score 0.5-1.5
        //   5. Scheduler never picks the new DCD chunk again. Samsung
        //      never receives it. Killer demo broken for 3-phone
        //      scenarios where PPJ needs to relay DCD.
        //
        // ── Why preserve count in the IDENTICAL-payload case ──────
        // For V|RegT|1 GPS-refresh : the chunk content barely changes
        // (lat 50.000440 → 50.000256, e.g. 5m drift), but the CHUNK
        // STRING has different bytes. We could conservatively reset
        // every time the bytes differ, but that would re-broadcast
        // V|RegT|1 storm every time GPS twitches even by 1m of
        // accuracy noise. So we use a smarter rule :
        //
        //   - If existing.chunk != newChunk → reset count (new info)
        //
        // For V| chunks : a GPS drift of 5m+ creates a different
        // string, which triggers reset. That's actually what we
        // want — the receiver should learn the new position quickly.
        //
        // For E| chunks (events) : status change EVAL→DCD creates a
        // different string → reset, scheduler picks the new chunk
        // immediately, Samsung receives DCD with TTL=3, can relay
        // to PPJ. ✅ Killer demo unblocked.
        //
        // ── Edge case : insertion-time burst ──────────────────────
        // If the same chunk is put() twice in a row (e.g. mesh
        // relay duplicate), we keep the broadcastCount as-is (no
        // reset, no inflation). Idempotent.
        val preservedCount = if (existing != null && existing.chunk != chunk) {
            // Different payload → new info, reset count for fair scheduling
            0L
        } else {
            existing?.broadcastCount ?: 0L
        }

        val newEntry = Entry(
            chunk = chunk,
            type = type,
            updatedAtMs = nowMs,
            broadcastCount = preservedCount,
            isOwn = isOwn || (existing?.isOwn == true)
        )
        store[key] = newEntry
        return if (existing == null) PutOutcome.Inserted else PutOutcome.Updated
    }

    /**
     * Mark an entry as own (a special case for self-V| / self-S| that
     * weren't initially flagged so). After this, the reaper will never
     * touch it.
     */
    @Synchronized
    fun markOwn(key: String) {
        val existing = store[key] ?: return
        store[key] = existing.copy(isOwn = true)
    }

    /**
     * Increment the broadcast count for an entry that was just sent
     * over the wire. Used by the rotation scheduler to know what's
     * been advertised recently and what hasn't.
     */
    @Synchronized
    fun recordBroadcast(key: String, nowMs: Long = System.currentTimeMillis()) {
        val existing = store[key] ?: return
        store[key] = existing.copy(broadcastCount = existing.broadcastCount + 1)
        // Note: we do NOT bump updatedAtMs on broadcast. updatedAtMs
        // tracks when we last RECEIVED or PUBLISHED the chunk, which
        // is the right signal for eviction. Broadcasting is a downstream
        // effect that shouldn't keep stale chunks alive.
    }

    /**
     * Snapshot all current entries for safe iteration. The returned
     * list is a copy, so callers can iterate even while concurrent
     * puts happen.
     */
    @Synchronized
    fun snapshot(): List<Pair<String, Entry>> {
        return store.entries.map { it.key to it.value }
    }

    /**
     * Snapshot just the keys (cheaper if caller only needs to know
     * what slots exist).
     */
    @Synchronized
    fun keys(): List<String> = store.keys.toList()

    /**
     * Number of slots currently held.
     */
    @Synchronized
    fun size(): Int = store.size

    /**
     * Lookup a single entry without copying the whole map.
     */
    @Synchronized
    fun get(key: String): Entry? = store[key]

    /**
     * Remove a specific entry.
     */
    @Synchronized
    fun remove(key: String): Entry? = store.remove(key)

    /**
     * Reset everything (used on full stopAdvertising / SOS cancellation).
     */
    @Synchronized
    fun clear() {
        store.clear()
    }

    /**
     * 🆕 L44.16.80 — Remove only the own entries (isOwn=true), keeping
     * the relayed VR|/SR|/E| chunks intact.
     *
     * This is needed when the advertise rotation is RESTARTED (e.g.
     * because GPS moved by 1m and the rescuer/victim chunk needs a
     * payload refresh) — we want to keep all the relays we have
     * accumulated rather than throwing them away every few seconds.
     *
     * Without this, a rescuer doing `startAdvertisingMixed` 5 times
     * in 90 seconds would lose all the VR|HW,H Kurodo chunks every
     * time — confirmed bug in dry-run logs L44.16.79 (Pixel rescuer
     * advertised 0 VR| despite receiving 5 from Samsung).
     *
     * @return list of (key, entry) that got removed
     */
    @Synchronized
    fun clearOwn(): List<Pair<String, Entry>> {
        val removed = mutableListOf<Pair<String, Entry>>()
        val it = store.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.value.isOwn) {
                removed.add(entry.key to entry.value)
                it.remove()
            }
        }
        return removed
    }

    /**
     * 🆕 L44.16.80 — Remove only the relayed entries (isOwn=false),
     * keeping the own V|/S| chunks intact.
     *
     * Symmetric counterpart of clearOwn. Less commonly needed but
     * useful when the user explicitly resets the relay state without
     * stopping their own broadcast.
     *
     * @return list of (key, entry) that got removed
     */
    @Synchronized
    fun clearRelayed(): List<Pair<String, Entry>> {
        val removed = mutableListOf<Pair<String, Entry>>()
        val it = store.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (!entry.value.isOwn) {
                removed.add(entry.key to entry.value)
                it.remove()
            }
        }
        return removed
    }

    /**
     * Reaper: remove entries older than the type-specific TTL.
     *
     * Per Council R2:
     *   • Own V|/S|       → never evicted
     *   • VR|/SR|         → 60s
     *   • E|              → 180s
     *
     * @return list of (key, entry) that got evicted, for logging
     */
    @Synchronized
    fun evictOld(nowMs: Long = System.currentTimeMillis()): List<Pair<String, Entry>> {
        val evicted = mutableListOf<Pair<String, Entry>>()
        val it = store.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.value.isOwn) continue   // never evict own chunks
            val ageMs = nowMs - entry.value.updatedAtMs
            val ttlMs = ttlForType(entry.value.type)
            if (ageMs > ttlMs) {
                evicted.add(entry.key to entry.value)
                it.remove()
            }
        }
        return evicted
    }

    /**
     * Pick the next chunk to advertise based on a WEIGHTED ROUND-ROBIN
     * policy.
     *
     * 🆕 L44.16.79 — replaces the previous tier-strict scheduler which
     * had a critical starvation bug : as long as `tierOwn` had any
     * chunk, the scheduler never fell through to `tierFreshRelay`,
     * meaning relayed VR|/SR| chunks were inserted into the cache but
     * NEVER advertised. Confirmed in dry-run logs L44.16.78 :
     *   • Phone A advertised 54 V| (own) + 37 E| (relayed) + 0 VR| ❌
     *   • Pierre-Paul-Jacques arriving 50m away never saw Haru's
     *     profile because Phone A was its only relay path.
     *
     * ─── Weight policy ───────────────────────────────────────────
     *
     *   tierEvents       weight 3   life-critical, broadcast 3× more
     *   tierOwn          weight 2   self-broadcast at full priority
     *   tierFreshRelay   weight 2   fresh relayed (<30s)
     *   tierColdRelay    weight 1   cold relayed (>30s) — half as often
     *
     * ─── Score formula ───────────────────────────────────────────
     *
     *   score = (broadcastCount + 1) / weight
     *
     * The scheduler picks the entry with the LOWEST score. This means :
     *   • A weight-3 entry with 5 broadcasts (score 6/3 = 2.0) tied
     *     with a weight-1 entry at 1 broadcast (score 2/1 = 2.0)
     *   • A weight-2 entry that has never been broadcast (score 1/2
     *     = 0.5) BEATS a weight-2 entry broadcast 5 times (score 3.0)
     *   • Events still naturally win when fresh, but they don't
     *     monopolize the cycle once their own broadcastCount climbs.
     *
     * Tie-breaks by oldest `updatedAtMs` (FIFO within same score).
     *
     * @param nowMs current epoch millis (for cold/fresh classification)
     * @return key of the next chunk to advertise, or null if cache empty
     */
    @Synchronized
    fun selectNextChunk(nowMs: Long = System.currentTimeMillis()): String? {
        if (store.isEmpty()) return null
        val freshThresholdMs = 30_000L

        var bestKey: String? = null
        var bestEntry: Entry? = null
        var bestScore = Double.MAX_VALUE
        var bestWeight = 0

        for ((key, entry) in store) {
            // 🆕 L44.16.86 — Skip V|/VR| chunks for peers in cache-hit window.
            //
            // If a peer broadcasts a F| chunk that matches our locally-cached
            // profile hash, we mark them as "cache-hit" for 60s, during which
            // we suppress their V|/VR| chunks from rotation scoring. This
            // saves the airtime that would otherwise be wasted re-broadcasting
            // chunks the receiver already has.
            //
            // E| events and S|/SR| beacons remain in rotation : events are
            // time-sensitive (status changes must propagate), and beacons
            // carry GPS that legitimately changes.
            if (entry.type == ChunkType.VICTIM_DIRECT || entry.type == ChunkType.VICTIM_RELAYED) {
                val sid = extractVictimSid(key)
                if (sid != null && isCacheHit(sid, nowMs)) {
                    continue  // Skip this chunk — receiver already has it
                }
            }

            val weight = weightFor(entry, nowMs, freshThresholdMs)
            // (broadcastCount + 1) so that a never-broadcast entry
            // gets score = 1/weight (small but non-zero), which is
            // still smaller than any entry that has broadcast.
            val score = (entry.broadcastCount + 1).toDouble() / weight.toDouble()
            val current = bestEntry
            val currentKey = bestKey
            val isBetter = when {
                current == null -> true
                score < bestScore -> true
                score > bestScore -> false
                // tie on score → prefer oldest updatedAtMs (FIFO)
                entry.updatedAtMs < current.updatedAtMs -> true
                entry.updatedAtMs > current.updatedAtMs -> false
                // tie on age → prefer higher-weight tier so events
                // beat profile-relays at literal-equal score
                weight > bestWeight -> true
                weight < bestWeight -> false
                // 🆕 L44.16.82 — Final deterministic tiebreak by KEY
                // lex order. Without this, the LinkedHashMap iteration
                // order (= insertion order) made the FIRST inserted
                // chunk win every tie, which permanently starved the
                // siblings of a multi-chunk own profile.
                //
                // Empirical bug: dryrun 11:56-11:59 of 2026-05-08 had
                // V|RegT|1, V|RegT|2..5 all inserted in the same
                // milliseconds with broadcastCount=0 after each
                // clearOwn() restart. V|RegT|1 won every tie → 14
                // broadcasts. V|RegT|2..5: 0 broadcasts each. Samsung
                // never received Haru's allergies/meds → killed the
                // Warfarin × Ibuprofen DDI demo.
                //
                // Why lex-order specifically (and not random or
                // round-robin): determinism. Two phones running the
                // same RotationCache implementation must select the
                // same chunk under identical conditions, otherwise we
                // create dedup mismatches that explode the BloomDedup
                // cache size on the receiver side. Lex by key is
                // both deterministic and naturally balanced (since
                // chunk keys end in seq numbers, lex order means
                // V|RegT|1 < V|RegT|2 < V|RegT|3 ... so seq=1 is
                // always picked first in a tie, but its broadcastCount
                // increments and the next tie picks seq=2 fairly).
                key < currentKey!! -> true
                else -> false
            }
            if (isBetter) {
                bestKey = key
                bestEntry = entry
                bestScore = score
                bestWeight = weight
            }
        }
        return bestKey
    }

    /**
     * Compute the rotation weight for an entry. Higher weight = more
     * frequent broadcasting (lower score in `selectNextChunk`).
     *
     *   tierEvents       weight 3
     *   tierOwn          weight 2
     *   tierFreshRelay   weight 2
     *   tierColdRelay    weight 1
     *   tierFingerprint  weight 1   🆕 L44.16.86 — broadcast sparsely (~10s)
     */
    private fun weightFor(
        entry: Entry,
        nowMs: Long,
        freshThresholdMs: Long
    ): Int = when {
        entry.type == ChunkType.EVENT -> 3
        // 🆕 L44.16.86 — Fingerprints are metadata, broadcast at lowest
        // priority. They serve as cache-skip hints, not core data flow.
        // weight=1 ensures F| occupies at most ~10% of advertise slots
        // even when the cache only has F| + 5 own V| chunks.
        entry.type == ChunkType.FINGERPRINT -> 1
        entry.isOwn -> 2
        (nowMs - entry.updatedAtMs) < freshThresholdMs -> 2
        else -> 1
    }

    /**
     * 🆕 L44.16.86 — Mark a peer's profile as cache-hit (already known).
     *
     * After this call, the rotation scheduler will SKIP V|/VR| chunks
     * for the given victimSid for the next `durationMs` (default 60s).
     *
     * E| triage events for this peer are NOT skipped (they are
     * time-sensitive : a HELP→DCD status change must always reach
     * receivers immediately).
     *
     * S|/SR| rescuer beacons for this peer are NOT skipped (they carry
     * GPS that legitimately changes; we want fresh location).
     *
     * Called by RelayManager.handleFingerprintChunk() when an incoming
     * F| chunk matches our locally-computed hash for the same sid.
     *
     * @param victimSid the peer whose profile chunks should be skipped
     * @param nowMs current time
     * @param durationMs how long to suppress V|/VR| broadcasts (default 60s)
     */
    @Synchronized
    fun markCacheHit(
        victimSid: String,
        nowMs: Long = System.currentTimeMillis(),
        durationMs: Long = 60_000L
    ) {
        cacheHitUntilMs[victimSid] = nowMs + durationMs
    }

    /**
     * 🆕 L44.16.86 — Check if a peer's profile is currently cache-hit.
     *
     * Returns true if an active cache-hit window exists AND has not
     * expired. Expired entries are silently cleaned on next call.
     */
    @Synchronized
    fun isCacheHit(victimSid: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val until = cacheHitUntilMs[victimSid] ?: return false
        if (nowMs >= until) {
            // Expired — clean up
            cacheHitUntilMs.remove(victimSid)
            return false
        }
        return true
    }

    /**
     * 🆕 L44.16.86 — Extract the victim sid from a chunk key, if applicable.
     *
     * Used internally by selectNextChunk to determine whether a chunk
     * belongs to a peer in cache-hit mode.
     *
     * Returns null for chunks where the concept of "victim sid" doesn't
     * apply (S|, SR| have rescuer sid, not victim sid).
     */
    private fun extractVictimSid(key: String): String? {
        // Keys: V|sid|seq, VR|sid|seq, E|sid (sid is always [1] after split)
        if (!key.startsWith("V|") && !key.startsWith("VR|")) return null
        val parts = key.split("|")
        return if (parts.size >= 2) parts[1] else null
    }

    /**
     * 🆕 L44.16.85 — Adaptive duty cycle decision for VICTIM_SOS role.
     *
     * Returns true if the rotation runnable should pause advertising for
     * a brief scan-only window. This is a half-duplex BLE radio constraint
     * workaround.
     *
     * ── Why this exists ────────────────────────────────────────────
     * Empirical bug from L44.16.84 dryrun (15:14 then 17:56 of 2026-05-08):
     * Samsung in pure VICTIM SOS mode broadcasts so aggressively (47 ad
     * V|HW,H + 16 ad E|RegT in 2min30) that its scanner never gets a
     * radio window. Result : capture rate Pixel→Samsung dropped from 65%
     * (L44.82, rescuer-only) to 15.6% (L44.83-84, victim-path unfiltered).
     *
     * Bluetooth Low Energy is structurally half-duplex on most Android
     * BLE controllers (Samsung Exynos 990 in particular). A device
     * cannot reliably advertise AND scan at the same time. By inserting
     * deliberate scan-only windows, we trade a small fraction of our
     * advertise duty (~30%) for a much larger improvement in our ability
     * to RECEIVE chunks from peers (capture rate target ≥40%).
     *
     * ── Why 70/30 rather than 50/50 ────────────────────────────────
     * The victim is the side that NEEDS to be seen (their profile is
     * what the rescuer wants). They should advertise more than scan.
     * 70% advertise / 30% scan-only over 10s windows means the victim
     * is advertising for 7 of every 10 seconds. Plenty of opportunities
     * for nearby rescuers to catch them, while the 3-second scan windows
     * let the victim catch incoming triage events (HELP/STAB/EVAC/DCD).
     *
     * ── Why only for VICTIM_SOS ────────────────────────────────────
     * Rescuers are typically scan-heavy by nature (their UI shows what
     * they discover). They don't need this pause; they're already
     * receiving plenty of chunks. The victim, in contrast, is "blasting"
     * (advertising on a tight 1.5s rotation) and needs help being a
     * good listener. Mixed role (rescuer that just scanned a QR badge)
     * is treated as victim-priority for this purpose.
     *
     * ── Algorithm ──────────────────────────────────────────────────
     * Slot the time line into 10-second windows. Within each window,
     * the first 7 seconds are ADVERTISE (slot 0..6), the last 3 are
     * SCAN_ONLY (slot 7..9). Cross-device alignment is automatic
     * because all devices use the same UNIX clock (modulo small skew).
     *
     * @param role the current advertise role ("victim", "rescuer", "mixed")
     * @param nowMs wall-clock time in ms
     * @return true if the caller should temporarily stop advertising
     */
    fun shouldPauseForScan(role: String, nowMs: Long): Boolean {
        // Only victim-mode (or mixed-with-victim) needs the pause.
        // Pure rescuer beacons are short and their device is naturally
        // scan-heavy, no need to throttle.
        if (role != "victim" && role != "mixed") return false
        val slot = (nowMs / 1_000L) % 10L
        return slot >= 7L  // Last 3 seconds of every 10s window
    }

    companion object {
        /**
         * Type-dependent TTL for cache eviction. Per Council R2 spec.
         */
        fun ttlForType(type: ChunkType): Long = when (type) {
            ChunkType.VICTIM_DIRECT, ChunkType.RESCUER_DIRECT -> {
                // Own chunks should never be evicted, but if isOwn was
                // not set for some reason, treat as relay TTL anyway.
                60_000L
            }
            ChunkType.VICTIM_RELAYED, ChunkType.RESCUER_RELAYED -> 60_000L
            ChunkType.EVENT -> 180_000L
            // 🆕 L44.16.86 — Fingerprints are metadata, refreshed every 10s
            // by their owner. If we haven't seen one in 30s, the peer is
            // probably gone or has changed profiles → evict.
            ChunkType.FINGERPRINT -> 30_000L
        }
    }
}
