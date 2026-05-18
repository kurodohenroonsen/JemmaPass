package be.heyman.android.jemmapassdemo.mesh.codec

/**
 * 🆕 L44.16.75 — Parser and encoder for `VR|`/`SR|` relayed chunks.
 *
 * ── Wire format ─────────────────────────────────────────────────
 *
 *   VR|<sid4>|<chunkIdx>|<chunkTotal>|<ttl>|<payload>
 *   SR|<sid4>|<name_ascii>|<lang>|<lat>|<lon>|<ttl>
 *
 * The TTL is positioned to allow a relay to rewrite it via fixed-width
 * substring replace without re-parsing the entire chunk :
 *   • For VR, TTL is field [4] (index 4 after split on `|`)
 *   • For SR, TTL is the LAST field
 *
 * ── Council Round 2 ─────────────────────────────────────────────
 * Option C locked. Old phones see VR|/SR|, don't recognise prefix,
 * silently drop. New phones parse and apply TTL decrement on relay.
 */
object RelayedChunkCodec {

    /** Build a `VR|` chunk from a direct `V|` chunk + initial TTL. */
    fun wrapVictim(directChunk: String, ttl: Int): String {
        require(directChunk.startsWith("V|")) {
            "wrapVictim expects a V| chunk, got: ${directChunk.take(20)}…"
        }
        // V|<sid>|<idx>|<total>|<payload>
        //   becomes
        // VR|<sid>|<idx>|<total>|<ttl>|<payload>
        val parts = directChunk.split('|', limit = 5)
        if (parts.size < 5) {
            // V| with no payload — degenerate, just append marker
            return "VR|${directChunk.substring(2)}|$ttl|"
        }
        // parts = ["V", sid, idx, total, payload]
        return "VR|${parts[1]}|${parts[2]}|${parts[3]}|$ttl|${parts[4]}"
    }

    /** Build an `SR|` chunk from a direct `S|` chunk + initial TTL. */
    fun wrapRescuer(directChunk: String, ttl: Int): String {
        require(directChunk.startsWith("S|")) {
            "wrapRescuer expects an S| chunk, got: ${directChunk.take(20)}…"
        }
        // S|<sid>|<name>|<lang>|<lat>|<lon>
        //   becomes
        // SR|<sid>|<name>|<lang>|<lat>|<lon>|<ttl>
        return "SR" + directChunk.substring(1) + "|$ttl"
    }

    /**
     * Extract the TTL from a `VR|` or `SR|` chunk. Returns null if the
     * chunk is not a relayed type or the TTL is malformed.
     */
    fun extractTtl(chunk: String): Int? {
        val type = ChunkType.detectPrefix(chunk) ?: return null
        if (!type.isRelayed) return null

        val parts = chunk.split('|')
        return when (type) {
            ChunkType.VICTIM_RELAYED -> parts.getOrNull(4)?.toIntOrNull()
            ChunkType.RESCUER_RELAYED -> parts.lastOrNull()?.toIntOrNull()
            else -> null
        }
    }

    /**
     * Rewrite the TTL in-place in a relayed chunk. Used by `RelayDecider`
     * to decrement TTL before forwarding.
     *
     * @return the chunk with TTL replaced, or null if the chunk is not relayable
     */
    fun rewriteTtl(chunk: String, newTtl: Int): String? {
        val type = ChunkType.detectPrefix(chunk) ?: return null
        if (!type.isRelayed) return null

        val parts = chunk.split('|').toMutableList()
        when (type) {
            ChunkType.VICTIM_RELAYED -> {
                if (parts.size < 5) return null
                parts[4] = newTtl.toString()
            }
            ChunkType.RESCUER_RELAYED -> {
                if (parts.size < 7) return null  // SR|sid|name|lang|lat|lon|ttl = 7 parts
                parts[parts.size - 1] = newTtl.toString()
            }
            else -> return null
        }
        return parts.joinToString("|")
    }

    /**
     * Strip the relayed wrapper to get back the equivalent direct chunk.
     * Useful for the receiver to merge relayed and direct data into a
     * single PeerInfo regardless of how it arrived.
     *
     * @return the direct V|/S| chunk, or null if input was already direct
     */
    fun unwrapToDirect(chunk: String): String? {
        val type = ChunkType.detectPrefix(chunk) ?: return null
        val parts = chunk.split('|')
        return when (type) {
            ChunkType.VICTIM_RELAYED -> {
                // VR|sid|idx|total|ttl|payload  →  V|sid|idx|total|payload
                if (parts.size < 6) return null
                "V|${parts[1]}|${parts[2]}|${parts[3]}|" +
                    parts.subList(5, parts.size).joinToString("|")
            }
            ChunkType.RESCUER_RELAYED -> {
                // SR|sid|name|lang|lat|lon|ttl  →  S|sid|name|lang|lat|lon
                if (parts.size < 7) return null
                "S|${parts.subList(1, parts.size - 1).joinToString("|")}"
            }
            else -> null
        }
    }
}
