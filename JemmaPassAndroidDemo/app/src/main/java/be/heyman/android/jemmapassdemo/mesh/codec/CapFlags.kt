package be.heyman.android.jemmapassdemo.mesh.codec

/**
 * 🆕 L44.16.87 — Capability flags for the rescuer beacon (`S|`) chunk.
 *
 * ─── Why this exists ─────────────────────────────────────────────
 *
 * Plan B (v1.5) introduces opportunistic Wi-Fi Direct sessions for
 * heavy payloads (photos, audio, FHIR bundles, junction snapshots).
 * Before opening a session to a peer, the requester must know what
 * the peer is willing/able to serve. CapFlags is that signal.
 *
 * We add it to the `S|` chunk **NOW** (Plan A, sprint A3) so that
 * by the time Plan B's SessionPromoter is implemented, all v1.5+
 * peers are already advertising their capabilities. No flag day.
 *
 * ─── Wire format ─────────────────────────────────────────────────
 *
 * 1-2 hex chars at the end of the rescuer beacon :
 *
 *   v1.4 and earlier :  S|9k$W|Kamekichi|ja|50.000321|4.443986
 *   v1.5+            :  S|9k$W|Kamekichi|ja|50.000321|4.443986|0F
 *                                                            ^^
 *                                                          CapFlags
 *
 * ─── Bitmap ──────────────────────────────────────────────────────
 *
 *   bit 0 (0x01)  H — has wound photo available for transfer
 *   bit 1 (0x02)  A — has TTS audio (multi-language) available
 *   bit 2 (0x04)  F — has full FHIR R4 bundle available
 *   bit 3 (0x08)  J — willing to serve junction snapshot
 *   bits 4-7      reserved for future capabilities
 *
 * ─── Backward compat contract ────────────────────────────────────
 *
 *   - L44.16.86 and earlier peers parse `S|sid|name|lang|lat|lon`
 *     into a Rescuer (5 fields after the prefix). The optional
 *     `|<capByte>` is silently dropped at split('|') boundary.
 *
 *   - L44.16.87+ peers parse the optional 6th field as CapFlags.
 *     If absent, default to NONE (no advanced capabilities).
 *     Old peers transparently look like "no caps" peers, which is
 *     the safe default — Plan B will simply not initiate sessions
 *     to them, falling back to stateless rotation.
 *
 *   - Hex format (1-2 chars) is chosen over decimal so we get a
 *     clear case-insensitive parse and always-bounded width even
 *     if we add bits 4-7 later.
 *
 * ─── Thread safety ───────────────────────────────────────────────
 *
 * CapFlags is an immutable value type. Fully thread-safe by
 * construction.
 *
 * @property byte the raw bitmap byte (0..255)
 */
data class CapFlags(val byte: Byte) {

    /** Bit 0 : has wound photo available for transfer. */
    val hasPhoto: Boolean get() = byte.toInt() and 0x01 != 0

    /** Bit 1 : has TTS audio (multi-language) available. */
    val hasAudio: Boolean get() = byte.toInt() and 0x02 != 0

    /** Bit 2 : has full FHIR R4 bundle available. */
    val hasFhirBundle: Boolean get() = byte.toInt() and 0x04 != 0

    /** Bit 3 : willing to serve junction snapshot (Plan B trigger). */
    val servesJunction: Boolean get() = byte.toInt() and 0x08 != 0

    /**
     * Encode to a 1-2 char uppercase hex string.
     *
     *   CapFlags(0x00) → "00"
     *   CapFlags(0x08) → "08"
     *   CapFlags(0x0F) → "0F"
     *   CapFlags(0xFF.toByte()) → "FF"
     *
     * Always 2 chars to avoid ambiguity in parsing back.
     */
    fun toHex(): String = String.format("%02X", byte.toInt() and 0xFF)

    /**
     * Returns true if the peer advertises the capability for the given
     * payload kind. Used by Plan B's SessionPromoter to gate session
     * upgrade requests.
     *
     * @param payloadKindBit one of the 0x01..0x08 capability bits
     */
    fun supports(payloadKindBit: Int): Boolean =
        (byte.toInt() and payloadKindBit) != 0

    companion object {
        /** No advanced capabilities — default for L44.16.86 and earlier peers. */
        val NONE = CapFlags(0)

        /** All currently-defined capabilities enabled (0x0F). */
        val ALL_KNOWN = CapFlags(0x0F.toByte())

        /** Bit constants — kept here for explicit reference in callers. */
        const val BIT_PHOTO        = 0x01
        const val BIT_AUDIO        = 0x02
        const val BIT_FHIR_BUNDLE  = 0x04
        const val BIT_JUNCTION     = 0x08

        /**
         * Parse a hex string from the wire format. Tolerant of :
         *   - 1 or 2 hex chars (case-insensitive)
         *   - leading/trailing whitespace (defensive)
         *   - empty input → NONE
         *   - invalid chars → NONE (silent fallback for forward compat)
         *
         * Examples :
         *   "00" → CapFlags(0)
         *   "08" → CapFlags(0x08)
         *   "0F" → CapFlags(0x0F)
         *   "FF" → CapFlags(-1)  (Byte signed, but bits intact)
         *   "x"  → NONE
         *   ""   → NONE
         */
        fun fromHex(hex: String): CapFlags {
            val trimmed = hex.trim()
            if (trimmed.isEmpty()) return NONE
            return try {
                val intVal = trimmed.toInt(16)
                CapFlags((intVal and 0xFF).toByte())
            } catch (e: Exception) {
                NONE
            }
        }

        /**
         * Build a CapFlags from individual booleans. Convenience for
         * constructing flags at runtime based on app state.
         */
        fun of(
            hasPhoto: Boolean = false,
            hasAudio: Boolean = false,
            hasFhirBundle: Boolean = false,
            servesJunction: Boolean = false
        ): CapFlags {
            var bits = 0
            if (hasPhoto)       bits = bits or BIT_PHOTO
            if (hasAudio)       bits = bits or BIT_AUDIO
            if (hasFhirBundle)  bits = bits or BIT_FHIR_BUNDLE
            if (servesJunction) bits = bits or BIT_JUNCTION
            return CapFlags(bits.toByte())
        }
    }
}
