package be.heyman.android.jemmapassdemo.mesh.codec

/**
 * 🆕 L44.16.75 — Event chunk parser/encoder for `E|` triage events.
 *
 * ── Wire format ─────────────────────────────────────────────────
 *
 *   E|<source_sid>|<victim_sid>|<status>|<rescuer_sid>|<ts_unix>|<ttl>|<seq>
 *
 * Field semantics :
 *   source_sid   — phone that originally created the event (4 hex chars)
 *   victim_sid   — patient whose status changed (4 hex chars)
 *   status       — SALT 4-char code: WAIT, EVAL, STAB, HELP, EVAC, DCD
 *   rescuer_sid  — clinician who took the action (4 hex chars)
 *   ts_unix      — UNIX seconds (10 chars, good until 2286)
 *   ttl          — hops remaining (1 char, decimal)
 *   seq          — per-(source, victim) counter rolling 00-99 (2 chars)
 *
 * ── Example chunks (all under 131 bytes) ────────────────────────
 *
 *   E|7a3b|7a3b|STAB|kiko|1715000000|3|07     ← Kiko marks Haru STAB (37B)
 *   E|c1d8|c1d8|HELP|kame|1715000005|3|01     ← Kamekichi calls HELP for Kurodo (37B)
 *   E|c1d8|c1d8|DCD |kame|1715000300|1|02     ← DCD with low TTL (38B)
 *
 * ── Why no free-text note in v1 ─────────────────────────────────
 * Council R2 consensus : drop the optional note for v1. The 131-byte
 * budget is tight once we add per-source/victim/rescuer SIDs and
 * timestamps. Notes can be reintroduced in v2 if needed.
 *
 * ── seq semantics ───────────────────────────────────────────────
 * The seq is per-(source_sid, victim_sid) pair (kimi's R2 catch from
 * Round 1 cross-cutting note). This way, two events on two different
 * victims from the same source don't share a counter and confuse the
 * dedup layer. The dedup key becomes (source_sid, victim_sid, seq).
 */
data class EventChunk(
    val sourceSid: String,
    val victimSid: String,
    val status: String,        // SALT 4-char code
    val rescuerSid: String,
    val timestampSec: Long,
    val ttl: Int,
    val seq: Int
) {
    /**
     * Encode this event as a wire-format string. Caller is responsible
     * for verifying byte length via `MeshByteSafety.assertChunkFits`.
     */
    fun encode(): String =
        "E|$sourceSid|$victimSid|$status|$rescuerSid|$timestampSec|$ttl|$seq"

    /**
     * Return a copy of this event with TTL decremented by 1. Used during
     * relay forwarding. Returns null if the new TTL would be 0 (do not
     * forward).
     */
    fun decrementTtl(): EventChunk? {
        val newTtl = ttl - 1
        return if (newTtl <= 0) null else copy(ttl = newTtl)
    }

    /**
     * Dedup key — what BloomDedup hashes. Per Round 1 cross-cutting note
     * (kimi), the seq must be scoped to (source, victim) to avoid
     * collisions across unrelated events.
     */
    fun dedupKey(): String = "E|$sourceSid|$victimSid|$seq"

    companion object {
        /** Recognised SALT triage codes. */
        val SALT_CODES = setOf("WAIT", "EVAL", "STAB", "HELP", "EVAC", "DCD")

        /**
         * Parse a wire-format `E|...` string into an EventChunk. Returns
         * null on any malformed input — never throws. The caller MUST
         * handle null gracefully (the Bridgefy-Royal-Holloway lesson :
         * malformed chunks must not kill the network).
         */
        fun parse(chunk: String): EventChunk? {
            if (!chunk.startsWith("E|")) return null
            val parts = chunk.split('|')
            if (parts.size != 8) return null

            val status = parts[3].uppercase()
            if (status !in SALT_CODES) return null

            val ts = parts[5].toLongOrNull() ?: return null
            val ttl = parts[6].toIntOrNull() ?: return null
            val seq = parts[7].toIntOrNull() ?: return null

            // Sanity: hex SIDs are 4 chars, but we don't strictly enforce
            // here (the bloom dedup is keyed on the strings as-is).
            return EventChunk(
                sourceSid = parts[1],
                victimSid = parts[2],
                status = status,
                rescuerSid = parts[4],
                timestampSec = ts,
                ttl = ttl,
                seq = seq
            )
        }
    }
}
