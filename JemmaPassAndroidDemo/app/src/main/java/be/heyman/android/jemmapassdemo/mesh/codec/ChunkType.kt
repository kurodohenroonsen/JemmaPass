package be.heyman.android.jemmapassdemo.mesh.codec

/**
 * 🆕 L44.16.75 — Wire-format chunk type discriminator.
 *
 * The JEMMA mesh protocol uses 5 chunk types after the L44.16.75
 * Council Round 2 consensus on Option C (`VR|`/`SR|` for relayed
 * profiles, NOT inline TTL on `V|`/`S|`).
 *
 * ── Type-tag layout (first 1-2 chars of every chunk) ────────────
 *
 *   V   victim DIRECT (5-chunk rotation, no relay)
 *       Format: V|<sid4>|<chunkIdx>|<chunkTotal>|<payload>
 *       Existed before L44.16.75 — backward compatible.
 *
 *   VR  victim RELAYED (multi-hop forwarded by another phone)
 *       Format: VR|<sid4>|<chunkIdx>|<chunkTotal>|<ttl>|<payload>
 *       NEW in L44.16.75. Old phones drop unknown prefix → safe.
 *
 *   S   rescuer DIRECT (single chunk beacon)
 *       Format: S|<sid4>|<name_ascii>|<lang>|<lat>|<lon>
 *       Existed before — backward compatible.
 *
 *   SR  rescuer RELAYED (forwarded)
 *       Format: SR|<sid4>|<name_ascii>|<lang>|<lat>|<lon>|<ttl>
 *       NEW in L44.16.75.
 *
 *   E   triage event (status change, HELP signal, etc.)
 *       Format: E|<source>|<victim>|<status>|<rescuer>|<ts>|<ttl>|<seq>
 *       NEW in L44.16.75. status = SALT 4-char code.
 *
 * ── Why discriminated union (Option C) ──────────────────────────
 * Council R2 verdict (7/8 IAs, claudeai conceded gracefully) :
 * Option C preserves backward compat — old phones see V|/S|/VR|/SR|/E|,
 * recognise V|/S| and silently drop the rest. We can deploy
 * incrementally without a flag day. RFC 9171 BPv7 made the same call
 * (clean version bump beats optional trailers).
 *
 * ── Backward-compat contract ────────────────────────────────────
 * Old parsers (≤L44.16.73) only switch on `V|` and `S|`. They MUST
 * silently drop any chunk starting with an unknown leading-letter
 * sequence. The new parser (this file) checks the first 1-2 chars
 * via `detectPrefix()` BEFORE further parsing.
 */
enum class ChunkType(val prefix: String, val isRelayed: Boolean) {
    /** Direct victim profile chunk (no relay, no TTL). */
    VICTIM_DIRECT("V", isRelayed = false),

    /** Multi-hop relayed victim profile chunk (carries TTL). */
    VICTIM_RELAYED("VR", isRelayed = true),

    /** Direct rescuer beacon (no relay, no TTL). */
    RESCUER_DIRECT("S", isRelayed = false),

    /** Multi-hop relayed rescuer beacon (carries TTL). */
    RESCUER_RELAYED("SR", isRelayed = true),

    /** Triage event (status change, HELP, STAB, EVAC, DCD, etc). */
    EVENT("E", isRelayed = false),  // events have inline TTL but aren't "relayed" types

    /**
     * 🆕 L44.16.86 — Profile fingerprint advertisement.
     *
     * Format: F|<sid>|<sha256_hex_8>|<lastModifiedTs>|<chunkVersion>
     * Example: F|HW,H|a3f9b2c1|1778255818|1   (24 bytes)
     *
     * Purpose : let receivers detect that they already have a peer's
     * full profile cached, and skip the V| rotation for that peer
     * during a 60s window. Saves ~75 broadcasts of redundant V|
     * chunks per cache hit, freeing radio time for events and other
     * peers.
     *
     * Broadcast frequency : every 10s (vs 1.5s for V|), so the F|
     * airtime cost is ~7% of the V| airtime cost.
     */
    FINGERPRINT("F", isRelayed = false);

    companion object {
        /**
         * Detect the chunk type from the first few bytes of a wire chunk.
         * Returns null if no recognised prefix matches.
         *
         * Order of checks matters : `VR` and `SR` MUST be checked BEFORE
         * `V` and `S` because they share leading characters.
         *
         * @param chunk the raw endpointName string from Nearby
         * @return matching ChunkType or null if unknown / malformed
         */
        fun detectPrefix(chunk: String): ChunkType? {
            if (chunk.length < 2) return null

            // Two-char prefixes first (VR|, SR|)
            if (chunk.length >= 3 && chunk[2] == '|') {
                when (chunk.substring(0, 2)) {
                    "VR" -> return VICTIM_RELAYED
                    "SR" -> return RESCUER_RELAYED
                }
            }

            // Single-char prefixes (V|, S|, E|, F|)
            if (chunk[1] == '|') {
                return when (chunk[0]) {
                    'V' -> VICTIM_DIRECT
                    'S' -> RESCUER_DIRECT
                    'E' -> EVENT
                    'F' -> FINGERPRINT  // 🆕 L44.16.86
                    else -> null
                }
            }

            return null
        }
    }
}
