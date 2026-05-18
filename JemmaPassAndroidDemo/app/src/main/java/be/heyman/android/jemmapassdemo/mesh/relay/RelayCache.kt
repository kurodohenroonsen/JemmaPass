package be.heyman.android.jemmapassdemo.mesh.relay

import be.heyman.android.jemmapassdemo.mesh.codec.ChunkType

/**
 * 🆕 L44.16.75 — In-memory relay cache with priority-weighted eviction.
 *
 * ── Council Round 1 verdict (consensus across kimi/grok/deepseek) ──
 *
 * The relay phone needs a bounded buffer of chunks worth re-broadcasting.
 * The buffer must be :
 *   • IN-MEMORY ONLY (privacy + simplicity, no SQLite persistence)
 *   • LRU-bounded at 20 entries (35-second cycle ceiling)
 *   • Priority-weighted (drop low-importance chunks first)
 *
 * ── Eviction priority ───────────────────────────────────────────
 *
 *   priority(chunk) =
 *     +100 if source_sid == my_sid                  (own data)
 *     + 80 if EVENT and age_sec < 30                (recent events)
 *     + 50 if PROFILE_INCOMPLETE                    (missing chunks)
 *     + 30 if victim criticality >= 2               (critical)
 *     + 10 if completed profile age_sec < 120       (refresh)
 *     -  2 × relay_count                            (decay)
 *     - 10 × age_minutes                            (time decay)
 *
 * The cap is 20 entries. When inserting an entry that would exceed
 * the cap, the lowest-priority existing entry is evicted.
 *
 * ── Hard floor ──────────────────────────────────────────────────
 * NEVER evict an entry with status HELP or EVAC regardless of
 * staleness — these are the entries that justify the app's existence
 * (claudeai R1 design).
 *
 * ── Concurrency ─────────────────────────────────────────────────
 * This class is NOT thread-safe. Callers should wrap in a `Mutex` or
 * `synchronized` block. The Nearby callbacks fire on GMS threads, the
 * rotation loop runs on Dispatchers.IO, so synchronization is the
 * caller's responsibility (kimi R1 threading note).
 */
class RelayCache(
    val maxEntries: Int = 20,
    /** Injectable clock for testing. */
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * One entry in the relay cache. We keep the raw wire chunk so we
     * can re-broadcast verbatim (after TTL decrement) without re-encoding.
     */
    data class Entry(
        val chunk: String,                  // raw wire format chunk
        val type: ChunkType,
        val sourceSid: String,              // origin (NOT relay-source)
        /** Receive timestamp (ms since epoch). */
        val receivedAtMs: Long,
        /** Number of times we've heard this same chunk relayed by peers. */
        var relayCount: Int = 0,
        /** Optional victim criticality (1-3) for V|/VR| chunks. */
        var criticality: Int? = null,
        /** True if reassembly of the V|/VR| profile is incomplete. */
        var isIncomplete: Boolean = false,
        /** Optional SALT code for E| events. */
        var saltCode: String? = null
    ) {
        /** Compute the priority score for this entry, given current time. */
        fun priority(nowMs: Long, mySid: String): Int {
            var score = 0
            val ageSec = (nowMs - receivedAtMs) / 1000

            if (sourceSid == mySid) score += 100  // own data is sacred

            if (type == ChunkType.EVENT && ageSec < 30) score += 80
            if (isIncomplete) score += 50
            if ((criticality ?: 0) >= 2) score += 30
            if (type == ChunkType.VICTIM_DIRECT && ageSec < 120) score += 10
            if (type == ChunkType.VICTIM_RELAYED && ageSec < 120) score += 10

            score -= 2 * relayCount
            score -= 10 * (ageSec / 60).toInt()  // 10 points per minute aged

            // Hard floor: never evict critical SOS events
            if (saltCode == "HELP" || saltCode == "EVAC") {
                score = score.coerceAtLeast(60)
            }

            return score
        }
    }

    private val entries = LinkedHashMap<String, Entry>()  // key = raw chunk

    /**
     * Insert an entry into the cache, evicting the lowest-priority
     * entry if we'd exceed [maxEntries].
     *
     * @return true if the entry was newly inserted, false if it
     *         already existed (caller may want to bump relayCount)
     */
    fun put(entry: Entry, mySid: String): Boolean {
        val existing = entries[entry.chunk]
        if (existing != null) {
            existing.relayCount++
            return false
        }

        if (entries.size >= maxEntries) {
            evictLowestPriority(mySid)
        }
        entries[entry.chunk] = entry
        return true
    }

    /**
     * Remove an entry by raw chunk (e.g. after successful broadcast +
     * TTL=0 reached). Returns true if removed.
     */
    fun remove(chunk: String): Boolean = entries.remove(chunk) != null

    /** Number of entries currently held. */
    fun size(): Int = entries.size

    /** All current entries, ordered by priority DESC (highest first). */
    fun byPriority(mySid: String): List<Entry> {
        val now = clock()
        return entries.values.sortedByDescending { it.priority(now, mySid) }
    }

    /**
     * Select up to [quota] chunks to broadcast in the next rotation,
     * highest-priority first.
     */
    fun selectForBroadcast(quota: Int, mySid: String): List<Entry> {
        return byPriority(mySid).take(quota)
    }

    /**
     * Periodic GC pass — drop entries older than [maxAgeMs] regardless
     * of priority. Call this before every rotation.
     *
     * @return count of evicted entries
     */
    fun reapStale(maxAgeMs: Long = 5 * 60 * 1000L): Int {
        val now = clock()
        val before = entries.size
        entries.values.removeAll { (now - it.receivedAtMs) > maxAgeMs }
        return before - entries.size
    }

    fun clear() {
        entries.clear()
    }

    private fun evictLowestPriority(mySid: String) {
        val now = clock()
        val victim = entries.values.minByOrNull { it.priority(now, mySid) }
            ?: return
        entries.remove(victim.chunk)
    }
}
