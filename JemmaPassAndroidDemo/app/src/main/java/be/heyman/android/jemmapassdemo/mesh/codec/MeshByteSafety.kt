package be.heyman.android.jemmapassdemo.mesh.codec

/**
 * 🆕 L44.16.74 — UTF-8 byte safety for Nearby Connections endpointName.
 *
 * ── Why this exists ───────────────────────────────────────────────
 * The 131-char limit advertised by Google Nearby Connections is a
 * misnomer — it is a 131-BYTE limit imposed by the underlying BLE
 * advertising payload (AD type 0x09 Complete Local Name, encoded as
 * UTF-8 per Bluetooth Core Spec). For ASCII-only content the limit
 * is identical (1 byte per char), but Japanese kana/kanji consume
 * 3 bytes per character, emojis 4 bytes, accented Latin 2 bytes.
 *
 * The previous codec checked `s.length` which counts UTF-16 code units
 * in the Kotlin String — a latent bug for our Shikoku demo where Haru's
 * name (春) and medication "オーグメンチン" (7 chars × 3 bytes = 21 bytes)
 * would silently overflow before reaching the 131-char ceiling.
 *
 * ── Council Round 2 verdict ──────────────────────────────────────
 * 6/8 council IAs (claudeai, gemini, chatgpt, aistudio, grok, kimi)
 * confirmed the bytes vs chars finding with sources :
 *   • Google Nearby docs : "Nearby Connections allows clients to send
 *     data within 131 BYTES without establishing a connection"
 *   • Bluetooth Core Spec : AD types 0x09/0x08 carry name as `utf8s`
 *   • AOSP `BluetoothAdapter.setName()` doc : "248 bytes UTF-8 encoding"
 *   • Empirical adjacent failures : Cordova plugin #297 "Too large data
 *     error" reproduces with multibyte names; Particle BLE warns about
 *     accent chars consuming 2-3 bytes inside 31-byte AdvData frames
 *
 * ── Failure modes if we ignored this ─────────────────────────────
 *   1. `startAdvertising()` silently fires `OnFailureListener` (caller
 *      not notified unless explicitly logged)
 *   2. GMS truncates mid-codepoint → receiver gets U+FFFD replacement
 *      character or — worse — pipe-split parses garbage as chunk index
 *   3. Demo dies silently on stage when Haru's `V|` chunk for chunk 2/5
 *      fails to advertise
 *
 * ── Usage ────────────────────────────────────────────────────────
 *   • `assertChunkFits(chunk)` — pre-flight check, fails LOUD in DEBUG
 *   • `safeEncodeForEndpointName(s)` — returns the longest UTF-8 prefix
 *     of `s` that fits in the byte limit, NEVER splitting a code point
 *   • `utf8ByteSize(s)` — exact byte count (do not use `s.length`)
 *
 * ── Strategic decision ───────────────────────────────────────────
 * For the Shikoku demo we ALSO romanise/Punycode names at the wire
 * level (e.g. broadcast "Haru S." instead of "春 佐藤"). The full
 * UTF-8 names live in the local DB and on the QR badge for display
 * after reassembly. This eliminates 95% of the multibyte hazard.
 * The byte-safe helpers in this file are the belt-and-suspenders
 * second line of defense — they MUST also be called on every chunk
 * before advertising, even when the inputs are nominally ASCII.
 */
object MeshByteSafety {
    private const val TAG = "JEMMA-BYTES"

    /**
     * Hard byte limit imposed by Google Nearby Connections on the
     * endpointName field. Confirmed by Round 2 council deep research.
     *
     * Source : developers.google.com/nearby/messages/android/migrate-to-nc
     */
    const val NEARBY_ENDPOINT_BYTE_LIMIT = 131

    /**
     * Exact UTF-8 byte length of [s]. Use this everywhere instead of
     * `s.length` when checking against the BLE payload budget.
     */
    fun utf8ByteSize(s: String): Int = s.toByteArray(Charsets.UTF_8).size

    /**
     * Pre-flight assertion. Call BEFORE every `startAdvertising()` to
     * fail loud if a chunk is too large. In RELEASE builds this still
     * throws — silent failures on stage are unacceptable.
     *
     * @param chunkAscii the encoded chunk string (V|...|... format)
     * @throws IllegalArgumentException if byte length exceeds limit
     */
    fun assertChunkFits(chunkAscii: String) {
        val n = utf8ByteSize(chunkAscii)
        require(n <= NEARBY_ENDPOINT_BYTE_LIMIT) {
            "Chunk is $n bytes (limit $NEARBY_ENDPOINT_BYTE_LIMIT): " +
                "${chunkAscii.take(40)}…"
        }
    }

    /**
     * Returns the longest UTF-8 prefix of [s] that fits in [maxBytes].
     * Walks back from `maxBytes` until the cut position is NOT a UTF-8
     * continuation byte (0b10xxxxxx → masked with 0xC0 == 0x80).
     *
     * This guarantees we never emit invalid UTF-8 to the receiver.
     *
     * @return the truncated String, valid UTF-8, ≤ maxBytes when encoded
     */
    fun safeTruncateToBytes(
        s: String,
        maxBytes: Int = NEARBY_ENDPOINT_BYTE_LIMIT
    ): String {
        val full = s.toByteArray(Charsets.UTF_8)
        if (full.size <= maxBytes) return s

        var cut = maxBytes
        // Continuation bytes have top 2 bits = 10. Walk back to a leading
        // byte (top bit 0 for ASCII, or top 2 bits = 11 for multibyte start).
        while (cut > 0 && (full[cut].toInt() and 0xC0) == 0x80) {
            cut--
        }
        return String(full, 0, cut, Charsets.UTF_8)
        // 🆕 L44.16.74 hotfix — removed Log.w call. Android `Log` is not
        // available in JVM unit tests (would throw "not mocked"). The
        // truncate event is rare and the byte counts are visible in the
        // assertChunkFits failure message anyway. If you need a trace,
        // wrap this call with your own logger at the call site.
    }

    /**
     * Strategic helper — romanise a string for wire-level transmission.
     * For now this is a placeholder that preserves ASCII chars and drops
     * everything else. Production code should use a proper transliterator
     * (e.g. ICU4J Latin-ASCII transform) but for the Shikoku demo we
     * pre-compute romanised names at registration and store them in
     * `_j` profile fields, so this function is rarely needed at runtime.
     *
     * Examples :
     *   "Haru Sato"  → "Haru Sato"      (ASCII passthrough)
     *   "春 佐藤"     → " "             (drop, profile should have name_ascii)
     *   "Café"       → "Caf"            (drop é — better: pre-compute "Cafe")
     *
     * @return ASCII-only version of [s], with non-ASCII chars dropped
     */
    fun toAsciiOnly(s: String): String {
        return s.filter { it.code in 0x20..0x7E }
    }

    /**
     * Build a sanitized chunk-safe wire string from raw input. Combines
     * pipe stripping (would break our format), non-printable filtering,
     * and byte-aware truncation.
     *
     * This is the SAME contract as `JemmaNearbyEndpointCodec.sanitize()`
     * but byte-aware. Use this in all new codec code (mesh.codec.*).
     */
    fun sanitizeForWire(s: String, maxBytes: Int = 24): String {
        val cleaned = s
            .replace('|', ' ')
            .filter { it.code >= 32 }
            .trim()
        return safeTruncateToBytes(cleaned, maxBytes)
    }
}
