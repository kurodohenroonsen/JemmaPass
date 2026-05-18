package be.heyman.android.jemmapassdemo.mesh.codec

import java.security.MessageDigest

/**
 * 🆕 L44.16.86 — Profile fingerprint chunk encoder/decoder.
 *
 * ─── Wire format ─────────────────────────────────────────────────
 *
 *   F|<sid>|<sha256_hex_8>|<lastModifiedTs>|<chunkVersion>
 *
 * Example :
 *   F|HW,H|a3f9b2c1|1778255818|1     (24 bytes)
 *
 * Where :
 *   sid              4 chars         victim's stable identifier
 *   sha256_hex_8     8 hex chars     first 4 bytes of SHA-256(canonicalProfile)
 *   lastModifiedTs   10 chars        UNIX timestamp seconds
 *   chunkVersion     1+ chars        increments on profile change
 *
 * ─── Purpose ─────────────────────────────────────────────────────
 *
 * Let receivers detect they already have a peer's full profile cached,
 * and SKIP the V| rotation for that peer during a 60s window.
 *
 * Without this :
 *   - Pixel reconstructs Kurodo's full profile (5 V| chunks) on first
 *     contact (~30-90s rotation).
 *   - Samsung restarts its broadcast (e.g. after GPS refresh) → Pixel
 *     re-receives V|HW,H|1..5 again, even though nothing has changed.
 *   - Wasted airtime on both sides (~75 redundant chunks in a typical
 *     L44.16.84 dryrun).
 *
 * With this :
 *   - Samsung also broadcasts F|HW,H|<hash>|... every 10s.
 *   - Pixel computes localHash from its cached chunks, compares.
 *   - Match → CACHE HIT, mark Kurodo as "skip rotation 60s" in the
 *     RotationCache. Pixel still scans, but doesn't waste advertise
 *     slots on already-known V|HW,H|N chunks.
 *   - Mismatch → CACHE MISS, listen normally.
 *
 * ─── Why first 4 bytes (8 hex chars) of SHA-256, not full ───────
 *
 * Collision resistance with 32-bit hashes is 1 in 2^32 ≈ 4 billion.
 * For an emergency mesh with at most ~100 distinct profiles in a
 * disaster zone, expected collisions ≈ 0. Saving 56 bytes (full SHA
 * is 64 hex chars) on every F| broadcast is a much better trade-off
 * than collision avoidance for our use case.
 *
 * ─── Why broadcast every 10s, not 1.5s ──────────────────────────
 *
 * The fingerprint changes only when the profile changes (rare in a
 * disaster context — typically only when GPS refreshes the header).
 * Broadcasting every 10s means receivers detect cache state quickly
 * (max 10s latency on first encounter) without spending a lot of
 * airtime on this metadata. Final cost : ~7% of V| airtime budget.
 */
object FingerprintChunk {

    /** Number of bytes from SHA-256 we use (4 → 8 hex chars). */
    const val FINGERPRINT_BYTES = 4

    /** Cache hit window after detecting a F| match : skip V| rotation 60s. */
    const val CACHE_HIT_DURATION_MS = 60_000L

    /** Broadcast interval for F| chunks. */
    const val FINGERPRINT_BROADCAST_INTERVAL_MS = 10_000L

    /**
     * Decoded fingerprint chunk. All fields are non-null because a malformed
     * chunk returns null from `decode()` (silent drop).
     */
    data class Fingerprint(
        val sid: String,
        val sha256Hex8: String,
        val lastModifiedTs: Long,
        val chunkVersion: Int
    ) {
        /** Encode to wire-format string. Always ASCII, always ≤ 30 bytes. */
        fun encode(): String =
            "F|$sid|$sha256Hex8|$lastModifiedTs|$chunkVersion"
    }

    /**
     * Decode a wire-format chunk into a Fingerprint, or null on malformed.
     *
     * Tolerates extra fields beyond the expected 5 (forward-compat for
     * future extensions like signing).
     */
    fun decode(chunk: String): Fingerprint? {
        if (!chunk.startsWith("F|")) return null
        val parts = chunk.split("|")
        if (parts.size < 5) return null
        return try {
            Fingerprint(
                sid = parts[1],
                sha256Hex8 = parts[2],
                lastModifiedTs = parts[3].toLong(),
                chunkVersion = parts[4].toInt()
            )
        } catch (e: Exception) {
            null  // Malformed numeric field → silent drop
        }
    }

    /**
     * Compute the canonical fingerprint of a victim profile from its 5 V| chunks.
     *
     * The chunks must be ordered (V|...|1, V|...|2, ..., V|...|5) and concatenated
     * with a single newline separator. SHA-256 is computed and the first 4 bytes
     * are returned as an 8-char hex string.
     *
     * ── Determinism contract ──────────────────────────────────────
     * For the same input chunks, this MUST always return the same hash,
     * regardless of platform endianness, locale, or JVM version. SHA-256
     * via java.security.MessageDigest is guaranteed-stable across Android
     * versions per the AOSP cryptography contract.
     *
     * ── Why concatenate with newline ──────────────────────────────
     * Newline (0x0A) cannot appear inside our chunk format (UTF-8 with
     * pipe separators), so it's an unambiguous boundary. Joining with
     * "" or "|" would risk hash collisions with concatenated payloads.
     *
     * @param orderedVChunks the 5 V| chunks of a victim profile, in seq order
     * @return 8-character lowercase hex string
     * @throws IllegalArgumentException if size != 5
     */
    fun computeHash(orderedVChunks: List<String>): String {
        require(orderedVChunks.size == 5) {
            "Profile fingerprint requires exactly 5 V| chunks, got ${orderedVChunks.size}"
        }
        val canonical = orderedVChunks.joinToString("\n").toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical)
        return digest.take(FINGERPRINT_BYTES).joinToString("") {
            String.format("%02x", it.toInt() and 0xFF)
        }
    }

    /**
     * Build a Fingerprint object from raw inputs. Convenience constructor
     * for the broadcaster side.
     */
    fun build(
        sid: String,
        orderedVChunks: List<String>,
        lastModifiedTs: Long,
        chunkVersion: Int = 1
    ): Fingerprint = Fingerprint(
        sid = sid,
        sha256Hex8 = computeHash(orderedVChunks),
        lastModifiedTs = lastModifiedTs,
        chunkVersion = chunkVersion
    )
}
