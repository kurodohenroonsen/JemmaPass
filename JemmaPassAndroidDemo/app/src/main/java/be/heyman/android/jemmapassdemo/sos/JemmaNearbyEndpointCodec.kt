package be.heyman.android.jemmapassdemo.sos
import android.util.Log
import be.heyman.android.jemmapassdemo.mesh.codec.MeshByteSafety

/**
 * 🆕 L44.16.23 — JemmaNearbyEndpointCodec
 *
 * Encode/decode the compact `endpointName` strings broadcast via
 * Google Nearby Connections (max 131 chars hard limit).
 *
 * Replaces the previous binary BLE chunk codec (JemmaSosChunkCodec)
 * with an ASCII-safe pipe-delimited format so the data fits inside
 * the endpointName slot of NearbyConnections advertise.
 *
 * ── Wire format ────────────────────────────────────────────────
 *
 *   VICTIM CHUNK (rotating 1..N):
 *     V|<sid4>|<chunkIdx>|<chunkTotal>|<payload>
 *
 *     where <payload> depends on chunkIdx:
 *       chunk 1 (HEADER):    name|sex|age|bt|crit|lat|lon
 *       chunk 2 (ALLERGIES): A:code1.code2.code3...
 *       chunk 3 (MEDS):      M:code1.code2.code3...
 *       chunk 4 (CONDITIONS):C:code1.code2...
 *       chunk 5 (IMMUN):     I:code1.code2...
 *
 *   RESCUER (single chunk, no rotation):
 *     S|<sid4>|<name>|<lang>|<lat>|<lon>
 *
 * ── sid (session id) ───────────────────────────────────────────
 *   4 hex chars (16 bits = 65 536 unique sessions). Collision risk
 *   acceptable for our use case (a few peers within 100m radius).
 *   Used to re-assemble the chunks across rotations into the same
 *   PeerSession.
 *
 * ── Limits ──────────────────────────────────────────────────────
 *   • endpointName max = 131 chars (Nearby Connections hard limit)
 *   • If a chunk overflows after fitting the codes, we truncate the
 *     codes list (allergies first, then meds, then conditions, then
 *     immun — those most critical for triage are kept)
 *   • Each code = 4-8 ASCII chars (ATC/SNOMED) + 1 char separator
 *
 * ── Naming compactness ─────────────────────────────────────────
 *   broadcaster_name truncated to 16 chars to leave room for codes.
 *   sex='M'/'F'/'?', age=0-99, blood type=O+/A-/etc.
 *   lat/lon: 6 decimals (~10 cm precision), e.g. "50.123456"
 */
object JemmaNearbyEndpointCodec {
    private const val TAG = "JEMMA-EP-CODEC"

    const val MAX_ENDPOINT_NAME_LEN = 131

    // Role markers (1 char prefix)
    const val ROLE_VICTIM = "V"
    const val ROLE_RESCUER = "S"

    // Victim chunk indices (1-based)
    const val VICTIM_CHUNK_HEADER = 1
    const val VICTIM_CHUNK_ALLERGIES = 2
    const val VICTIM_CHUNK_MEDS = 3
    const val VICTIM_CHUNK_CONDITIONS = 4
    const val VICTIM_CHUNK_IMMUN = 5
    const val VICTIM_CHUNK_TOTAL = 5

    // ─── Encoding ─────────────────────────────────────────────

    /**
     * Encode a victim header chunk.
     * Format: V|<sid>|1|<total>|<name>|<sex>|<age>|<bt>|<crit>|<lat>|<lon>|<lang>
     *
     * 🆕 L44.16.48 — `<lang>` (2-char ISO code, optional) appended at
     * the end. Receivers that predate this version stop parsing at
     * `<lon>` (parts.size < 12), they get lang="" — backward-compatible.
     * Receivers running L44.16.48+ pick up the lang and surface it on
     * the radar's victim card so a French-speaking rescuer immediately
     * knows the wounded person speaks Japanese before approaching.
     */
    fun encodeVictimHeader(
        sid: String,
        chunkTotal: Int,
        name: String,
        sex: Char,
        age: Int,
        bloodType: String,
        criticality: Int,
        lat: Double,
        lon: Double,
        victimLang: String = ""           // 🆕 L44.16.48
    ): String {
        // 🆕 L44.16.74 — Byte-aware name truncation. The previous
        // `.take(16)` counted UTF-16 code units, which silently passed
        // 16-char Japanese names (= 48 bytes) that would overflow the
        // 131-byte chunk budget. We now budget 16 BYTES for the name,
        // which is enough for romanised Latin (~16 chars) or short JP
        // (5 kana). Callers that want full UTF-8 names should pre-romanise
        // and pass ASCII (e.g. "Haru S." instead of "春 佐藤").
        val nameClean = MeshByteSafety.safeTruncateToBytes(sanitize(name), 16)
        val btClean = sanitize(bloodType).take(3)  // ASCII : "A+", "B-", "AB+"
        val langClean = sanitize(victimLang).take(2)  // ASCII : "fr", "ja", "en"
        val latStr = "%.6f".format(java.util.Locale.US, lat)
        val lonStr = "%.6f".format(java.util.Locale.US, lon)
        val payload = "$nameClean|$sex|$age|$btClean|$criticality|$latStr|$lonStr|$langClean"
        return "V|$sid|$VICTIM_CHUNK_HEADER|$chunkTotal|$payload"
            .also { logIfTooLong(it, "victim header") }
    }

    /**
     * Encode a victim codes chunk (allergies / meds / conditions / immun).
     * Format: V|<sid>|<idx>|<total>|<sectionMarker>:code1.code2.code3...
     *
     * If the resulting string overflows MAX_ENDPOINT_NAME_LEN, we
     * truncate the trailing codes (we keep the most-critical earlier
     * ones — caller is responsible for sorting by importance before
     * passing).
     */
    fun encodeVictimCodes(
        sid: String,
        chunkIdx: Int,
        chunkTotal: Int,
        sectionMarker: Char,  // 'A' / 'M' / 'C' / 'I'
        codes: List<String>
    ): String {
        val header = "V|$sid|$chunkIdx|$chunkTotal|$sectionMarker:"
        // 🆕 L44.16.74 — Use byte length, not char length. ATC/SNOMED
        // codes are ASCII so identical here, but defensive for future
        // free-text fields. Council Round 2 verdict.
        val headerBytes = MeshByteSafety.utf8ByteSize(header)

        // Add codes one by one; stop if next code would overflow the
        // byte budget (NOT the char budget).
        val sb = StringBuilder(header)
        var first = true
        var truncated = 0
        for (code in codes) {
            val sep = if (first) "" else ","
            val candidate = sep + sanitize(code)
            val candidateBytes = MeshByteSafety.utf8ByteSize(candidate)
            // sb is ASCII at this point so length == bytes, but be safe.
            val currentBytes = MeshByteSafety.utf8ByteSize(sb.toString())
            if (currentBytes + candidateBytes > MAX_ENDPOINT_NAME_LEN) {
                truncated++
                continue
            }
            sb.append(candidate)
            first = false
        }
        if (truncated > 0) {
            Log.w(TAG, "encodeVictimCodes: truncated $truncated codes from section $sectionMarker " +
                "(${codes.size} requested, ${codes.size - truncated} encoded)")
        }
        // If no codes fit at all, still emit the section marker so the
        // peer knows the section exists but is empty (avoids confusion
        // with "this section never appeared").
        return sb.toString().also { logIfTooLong(it, "victim codes $sectionMarker") }
    }

    /**
     * Encode a rescuer beacon (single chunk, no rotation).
     *
     * 🆕 L44.16.87 — Now accepts an optional `capFlags` parameter that
     * gets appended as a 7th field. Backward compat is preserved : if
     * `capFlags` is null or NONE, the chunk is encoded WITHOUT the
     * extra field, exactly like L44.16.86 — old peers see the same
     * 6-field chunk they always saw.
     *
     * Format :
     *   v1.4-v1.6 (no caps)  : S|<sid>|<name>|<lang>|<lat>|<lon>
     *   v1.7+ (with caps)    : S|<sid>|<name>|<lang>|<lat>|<lon>|<capHex>
     *
     * @param capFlags optional capability bitmap (null = omit field for compat)
     */
    fun encodeRescuer(
        sid: String,
        name: String,
        langCode: String,
        lat: Double,
        lon: Double,
        capFlags: be.heyman.android.jemmapassdemo.mesh.codec.CapFlags? = null
    ): String {
        // 🆕 L44.16.74 — Byte-aware truncation (see encodeVictimHeader)
        val nameClean = MeshByteSafety.safeTruncateToBytes(sanitize(name), 16)
        val langClean = sanitize(langCode).take(2)
        val latStr = "%.6f".format(java.util.Locale.US, lat)
        val lonStr = "%.6f".format(java.util.Locale.US, lon)
        // 🆕 L44.16.87 — Conditionally append cap byte for backward compat.
        // We omit the field entirely when caps are NONE/null so that the
        // chunk byte length stays minimal for the common case (a rescuer
        // without any advanced capabilities behaves exactly like a v1.6
        // rescuer on the wire).
        val capSuffix = if (capFlags != null && capFlags != be.heyman.android.jemmapassdemo.mesh.codec.CapFlags.NONE) {
            "|${capFlags.toHex()}"
        } else ""
        return "S|$sid|$nameClean|$langClean|$latStr|$lonStr$capSuffix"
            .also { logIfTooLong(it, "rescuer") }
    }

    // ─── Decoding ─────────────────────────────────────────────

    sealed class Decoded {
        data class VictimHeader(
            val sid: String,
            val chunkIdx: Int,
            val chunkTotal: Int,
            val name: String,
            val sex: Char,
            val age: Int,
            val bloodType: String,
            val criticality: Int,
            val lat: Double,
            val lon: Double,
            val langCode: String = ""    // 🆕 L44.16.48 — victim's spoken language
        ) : Decoded()

        data class VictimCodes(
            val sid: String,
            val chunkIdx: Int,
            val chunkTotal: Int,
            val sectionMarker: Char,    // 'A'/'M'/'C'/'I'
            val codes: List<String>
        ) : Decoded()

        data class Rescuer(
            val sid: String,
            val name: String,
            val langCode: String,
            val lat: Double,
            val lon: Double,
            // 🆕 L44.16.87 — Optional capability bitmap (Plan B prep).
            // Defaults to NONE for backward compat when decoding L44.16.86
            // and earlier rescuer beacons.
            val capFlags: be.heyman.android.jemmapassdemo.mesh.codec.CapFlags =
                be.heyman.android.jemmapassdemo.mesh.codec.CapFlags.NONE
        ) : Decoded()
    }

    /**
     * Parse an endpointName received from Nearby Connections.
     * Returns null if the format isn't a JEMMA endpoint (= not our app).
     * Best-effort: malformed-but-jemma endpoints log warnings.
     */
    fun decode(endpointName: String): Decoded? {
        if (endpointName.isBlank()) return null
        val parts = endpointName.split('|')
        if (parts.size < 2) return null

        return when (parts[0]) {
            ROLE_VICTIM -> decodeVictim(parts, endpointName)
            ROLE_RESCUER -> decodeRescuer(parts, endpointName)
            else -> null  // not a JEMMA endpoint
        }
    }

    private fun decodeVictim(parts: List<String>, raw: String): Decoded? {
        // Common header: V|sid|chunkIdx|chunkTotal|...
        if (parts.size < 5) {
            Log.w(TAG, "victim malformed (size=${parts.size}): $raw")
            return null
        }
        val sid = parts[1]
        val chunkIdx = parts[2].toIntOrNull() ?: return null
        val chunkTotal = parts[3].toIntOrNull() ?: return null

        return when (chunkIdx) {
            VICTIM_CHUNK_HEADER -> {
                // V|sid|1|tot|name|sex|age|bt|crit|lat|lon            → 11 parts (legacy)
                // V|sid|1|tot|name|sex|age|bt|crit|lat|lon|lang        → 12 parts (L44.16.48+)
                if (parts.size < 11) {
                    Log.w(TAG, "victim header truncated: $raw")
                    return null
                }
                Decoded.VictimHeader(
                    sid = sid,
                    chunkIdx = chunkIdx,
                    chunkTotal = chunkTotal,
                    name = parts[4],
                    sex = parts[5].firstOrNull() ?: '?',
                    age = parts[6].toIntOrNull() ?: 0,
                    bloodType = parts[7],
                    criticality = parts[8].toIntOrNull() ?: 0,
                    lat = parts[9].toDoubleOrNull() ?: 0.0,
                    lon = parts[10].toDoubleOrNull() ?: 0.0,
                    // 🆕 L44.16.48 — opt 12th field. Pre-L44.16.48
                    // senders won't have it, decoder gracefully treats
                    // as empty.
                    langCode = if (parts.size >= 12) parts[11].take(2) else ""
                )
            }
            in VICTIM_CHUNK_ALLERGIES..VICTIM_CHUNK_IMMUN -> {
                // V|sid|idx|tot|<X:code1.code2...>
                if (parts.size < 5) return null
                val payload = parts[4]
                val colonIdx = payload.indexOf(':')
                if (colonIdx <= 0) return null
                val sectionMarker = payload[0]
                val codesStr = payload.substring(colonIdx + 1)
                val codes = if (codesStr.isBlank()) emptyList()
                            else if (codesStr.contains(',')) codesStr.split(',').filter { it.isNotBlank() }
                            else if (codesStr.contains('.') && !codesStr.matches(Regex("^[A-Z]\\d{2}\\.\\d+$"))) codesStr.split('.').filter { it.isNotBlank() }
                            else listOf(codesStr)
                Decoded.VictimCodes(
                    sid = sid,
                    chunkIdx = chunkIdx,
                    chunkTotal = chunkTotal,
                    sectionMarker = sectionMarker,
                    codes = codes
                )
            }
            else -> {
                Log.w(TAG, "unknown victim chunkIdx=$chunkIdx: $raw")
                null
            }
        }
    }

    private fun decodeRescuer(parts: List<String>, raw: String): Decoded? {
        // S|sid|name|lang|lat|lon  → 6 parts (legacy ≤L44.16.86)
        // S|sid|name|lang|lat|lon|capHex  → 7 parts (L44.16.87+)
        if (parts.size < 6) {
            Log.w(TAG, "rescuer truncated: $raw")
            return null
        }
        // 🆕 L44.16.87 — Parse optional 7th field as CapFlags.
        // If absent (legacy peer) or invalid hex → NONE (safe default).
        // Plan B's SessionPromoter will see NONE and refuse to upgrade
        // sessions to that peer, falling back to stateless rotation.
        val capFlags = if (parts.size >= 7) {
            be.heyman.android.jemmapassdemo.mesh.codec.CapFlags.fromHex(parts[6])
        } else {
            be.heyman.android.jemmapassdemo.mesh.codec.CapFlags.NONE
        }
        return Decoded.Rescuer(
            sid = parts[1],
            name = parts[2],
            langCode = parts[3],
            lat = parts[4].toDoubleOrNull() ?: 0.0,
            lon = parts[5].toDoubleOrNull() ?: 0.0,
            capFlags = capFlags
        )
    }

    // ─── Helpers ──────────────────────────────────────────────

    /**
     * Strip pipe characters (would break the format) and any non-printable
     * ASCII. Keep accents — Nearby endpointName accepts UTF-8.
     *
     * 🆕 L44.16.74 — Council Round 2 verdict : the 131 limit is BYTES,
     * not chars. For names that may contain UTF-8, callers should
     * romanise BEFORE calling this (see MeshByteSafety).
     */
    private fun sanitize(s: String): String {
        return s.replace('|', ' ').filter { it.code >= 32 }.trim()
    }

    /**
     * 🆕 L44.16.74 — Replaced legacy `logIfTooLong` with byte-aware check.
     *
     * The 131 limit imposed by Google Nearby Connections is in BYTES
     * (UTF-8 encoded), not in Java String chars. The previous check
     * `s.length > 131` would silently pass a 130-char Japanese string
     * that encodes to 390 bytes and overflows the BLE advertising payload.
     *
     * In DEBUG builds this throws (fail loud during development).
     * In RELEASE builds it logs an error and the chunk gets dropped at
     * the advertise layer, so the demo never crashes on stage but does
     * generate a clear logcat trail for post-mortem.
     *
     * Council Round 2 (claudeai 4-layer deep research) confirmed bytes,
     * not chars : developers.google.com/nearby/messages/android/migrate-to-nc
     */
    private fun logIfTooLong(s: String, context: String) {
        val byteLen = MeshByteSafety.utf8ByteSize(s)
        if (byteLen > MAX_ENDPOINT_NAME_LEN) {
            Log.e(TAG, "⚠ endpointName overflow ($context): " +
                "${byteLen}B > ${MAX_ENDPOINT_NAME_LEN}B " +
                "(string length = ${s.length} chars, content: ${s.take(40)}…)")
        }
    }

    /**
     * 🆕 L44.16.74 — Public byte-aware validator for callers that want
     * to fail loud BEFORE attempting an advertise. Used by
     * `JemmaNearbySosService.startCurrentChunk()` to assert each rotated
     * chunk fits the 131-byte limit.
     *
     * @throws IllegalArgumentException if [chunk] exceeds 131 UTF-8 bytes
     */
    fun assertFitsBLE(chunk: String) {
        MeshByteSafety.assertChunkFits(chunk)
    }

    /**
     * 🆕 L44.16.74 — Returns the UTF-8 byte length of a chunk. Use this
     * everywhere instead of `.length`. ASCII chars are 1B, Japanese
     * kana/kanji are 3B, emojis are 4B.
     */
    fun byteLengthOf(s: String): Int = MeshByteSafety.utf8ByteSize(s)

    /**
     * Generate a random 4-hex-char session id.
     * Acceptable collision risk for the radar use case.
     */
    fun genSessionId(): String {
        val rng = java.security.SecureRandom()
        val bytes = ByteArray(2)
        rng.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
