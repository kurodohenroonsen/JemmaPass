package be.heyman.android.jemmapassdemo.sos
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/**
 * 🆕 L44.16.12 — JEMMA SOS BLE Chunk Codec.
 *
 * Encodes/decodes the binary chunks broadcast over BLE 5.0 Extended
 * Advertising for the SOS Radar.
 *
 * Design philosophy: TRANSMIT CODES, NOT TEXT.
 *
 * Both broadcaster and receiver have `knowledge_full.db` (2.04 GB,
 * 21 languages). So when Kurodo broadcasts his profile, we don't send
 * "Penicillin" as 12 UTF-8 bytes — we send the SNOMED code as a short
 * ASCII string ("387207008" = 9 bytes). The receiver looks up the code
 * locally and resolves it to the LOCAL LANGUAGE label ("Penicilline"
 * for FR, "ペニシリン" for JA, "Penicillin" for EN). Universal,
 * compact, language-agnostic.
 *
 * ─── Wire format ─────────────────────────────────────────────────
 *
 * Each BLE advertise packet contains ONE chunk. Multiple chunks make
 * up one broadcast session, identified by `session_id` (UUID v4).
 *
 *   [byte 0     ] chunk_type     (see ChunkType enum)
 *   [byte 1     ] chunk_index    (0..N-1)
 *   [byte 2     ] chunk_total    (N)
 *   [bytes 3-18 ] session_id     (16 bytes, UUID v4)
 *   [bytes 19-N ] payload        (chunk_type-specific)
 *
 * BLE 5.0 Extended Advertising supports up to ~250 bytes payload.
 * Legacy BLE 4.x is limited to ~24 bytes. We target 5.0 (Pixel 9
 * Pro, S22+ all support it). Fallback to legacy = N times more
 * chunks but same protocol.
 *
 * ─── Chunk types ─────────────────────────────────────────────────
 *
 * 0x00 HEADER     [1 type, 1 idx, 1 total, 16 sid, 4 lat_e6, 4 lon_e6,
 *                  1 sex_age, 1 blood, 1 criticality, 1 flags,
 *                  4 ts_offset, 16 broadcaster_name_utf8]
 *
 *                  → tier-0 vital info, secouriste sees this in <200ms
 *
 * 0x01 ALLERGIES  [1 type, 1 idx, 1 total, 16 sid,
 *                  1 count, then per allergen:
 *                    [1 severity (HLU), 1 status (AIR), 1 system,
 *                     1 code_len, code_bytes (ASCII)]]
 *
 * 0x02 MEDS       [1 type, 1 idx, 1 total, 16 sid,
 *                  1 count, then per med:
 *                    [1 route (OITSE), 1 system,
 *                     1 code_len, code_bytes (ASCII)]]
 *
 * 0x03 CONDITIONS [1 type, 1 idx, 1 total, 16 sid,
 *                  1 count, then per condition:
 *                    [1 status (AIR), 1 system,
 *                     1 code_len, code_bytes (ASCII)]]
 *
 * 0x04 IMMUN      [1 type, 1 idx, 1 total, 16 sid,
 *                  1 count, then per vaccine:
 *                    [1 system,
 *                     1 code_len, code_bytes (ASCII)]]
 *
 * 0xFE TEXT_NOTE  [1 type, 1 idx, 1 total, 16 sid,
 *                  2 utf8_len, utf8_bytes]  (rarely used,
 *                                            for free-text emergency note)
 *
 * 0xFF SENTINEL   end-of-broadcast marker, payload empty
 *
 * ─── Coding systems byte ─────────────────────────────────────────
 * 0x01 = SNOMED-CT
 * 0x02 = RxNorm
 * 0x03 = ATC
 * 0x04 = ICD-10
 * 0x05 = CVX (vaccines)
 * 0x06 = LOINC
 * 0xFF = unknown / opaque
 *
 * ─── Severity / status / route enums (ASCII byte) ───────────────
 *  Allergy severity: 'H'/'L'/'U' (high/low/unknown)
 *  Allergy/condition status: 'A'/'I'/'R' (active/inactive/resolved)
 *  Medication route: 'O'/'I'/'T'/'S'/'E' (oral/IV/topical/subcutaneous/etc)
 *
 * ─── Criticality byte (radar color) ─────────────────────────────
 *  0x00 stable (green)
 *  0x01 urgent (orange)
 *  0x02 critical (red)
 *  0x03 DOA / DNR / cardiac arrest (black)
 *
 * ─── Flags bitmap (1 byte) ──────────────────────────────────────
 *  0x01 DNR (do not resuscitate)
 *  0x02 Anticoagulant therapy
 *  0x04 Insulin-dependent diabetic
 *  0x08 Pregnant
 *  0x10 Epileptic
 *  0x20 Pacemaker / cardiac device
 *  0x40 Reserved
 *  0x80 Reserved
 */
object JemmaSosChunkCodec {

    private const val TAG = "JEMMA-SOS-CODEC"

    // Chunk type constants
    const val CHUNK_HEADER:     Byte = 0x00
    const val CHUNK_ALLERGIES:  Byte = 0x01
    const val CHUNK_MEDS:       Byte = 0x02
    const val CHUNK_CONDITIONS: Byte = 0x03
    const val CHUNK_IMMUN:      Byte = 0x04
    // 🆕 L44.16.20 — Rescuer beacon. Same preamble as SOS chunks but
    // a different chunk_type byte. Carried in its OWN session (separate
    // sessionId from any victim broadcast) so receivers don't conflate.
    // Payload format: latE6(4) + lonE6(4) + lang(1) + name(16) = 25 bytes.
    const val CHUNK_RESCUER:    Byte = 0x10
    const val CHUNK_TEXT_NOTE:  Byte = 0xFE.toByte()
    const val CHUNK_SENTINEL:   Byte = 0xFF.toByte()

    // Coding systems
    const val SYS_SNOMED: Byte = 0x01
    const val SYS_RXNORM: Byte = 0x02
    const val SYS_ATC:    Byte = 0x03
    const val SYS_ICD10:  Byte = 0x04
    const val SYS_CVX:    Byte = 0x05
    const val SYS_LOINC:  Byte = 0x06
    const val SYS_UNKNOWN: Byte = 0xFF.toByte()

    // Criticality
    const val CRIT_STABLE:   Byte = 0x00
    const val CRIT_URGENT:   Byte = 0x01
    const val CRIT_CRITICAL: Byte = 0x02
    const val CRIT_DOA:      Byte = 0x03

    // Header preamble offsets (after chunk_type/idx/total/sid)
    private const val HEADER_PAYLOAD_SIZE = 4 + 4 + 1 + 1 + 1 + 1 + 4 + 16  // = 32
    private const val PREAMBLE_SIZE = 1 + 1 + 1 + 16  // chunk_type + idx + total + session_id = 19

    // 🆕 L44.16.20 — Rescuer payload: latE6(4) + lonE6(4) + lang(1) + name(16) = 25 bytes
    private const val RESCUER_PAYLOAD_SIZE = 4 + 4 + 1 + 16  // = 25

    // 🆕 L44.16.20 — Language byte values for rescuer beacons.
    // Receiver maps these back to TTS template strings.
    const val RESCUER_LANG_FR: Byte = 0
    const val RESCUER_LANG_EN: Byte = 1
    const val RESCUER_LANG_JA: Byte = 2

    /**
     * 🆕 L44.16.20 — Convert a 2-letter language code to the rescuer
     * beacon's 1-byte representation. Defaults to EN for unknown codes.
     */
    fun rescuerLangByte(langCode: String): Byte = when (langCode.lowercase().take(2)) {
        "fr" -> RESCUER_LANG_FR
        "ja", "jp" -> RESCUER_LANG_JA
        else -> RESCUER_LANG_EN
    }

    /**
     * 🆕 L44.16.20 — Convert the rescuer language byte back to its
     * 2-letter ISO code. Used by receivers to pick a TTS template.
     */
    fun rescuerLangCode(langByte: Byte): String = when (langByte) {
        RESCUER_LANG_FR -> "fr"
        RESCUER_LANG_JA -> "ja"
        else -> "en"
    }

    // Maximum byte budget per chunk on BLE 5.0 Extended Advertising,
    // taking into account our service UUID and overhead.
    // Conservative target: 200 bytes, well within the 250-byte
    // hard limit when broadcasting service data.
    const val MAX_CHUNK_BYTES = 200

    // ═══════════════════════════════════════════════════════════════
    //  ENCODE: _j 1.2 profile JSON → list of chunk byte arrays
    // ═══════════════════════════════════════════════════════════════

    data class BroadcastConfig(
        val sessionId: ByteArray,         // 16 bytes UUID v4
        val latE6: Int,                    // latitude × 1e6
        val lonE6: Int,                    // longitude × 1e6
        val criticality: Byte = CRIT_STABLE,
        val flags: Byte = 0,
        val tsOffsetSeconds: Int = 0,
        val broadcasterName: String = ""
    )

    /**
     * Encode a `_j 1.2` SHORT profile JSON into a list of chunk byte
     * arrays ready to be broadcast over BLE.
     *
     * The first chunk is always a HEADER (tier-0 vital info) so that
     * the receiver gets the most important data ASAP — even before
     * receiving all chunks, it can already triage and alert the
     * rescuer.
     *
     * @param jProfile  parsed JSON object of the _j 1.2 short profile
     * @param config    session-wide broadcast metadata (GPS, etc.)
     * @return ordered list of chunk byte arrays. Caller broadcasts
     *         them in rotation toutes les ~200ms.
     */
    fun encode(jProfile: JSONObject, config: BroadcastConfig): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()

        // ─── 1. Build payloads for each section ────────────────────
        val patient = jProfile.optJSONObject("p") ?: JSONObject()
        val allergies = jProfile.optJSONArray("al") ?: JSONArray()
        val meds = jProfile.optJSONArray("md") ?: JSONArray()
        val conditions = jProfile.optJSONArray("cn") ?: JSONArray()
        val immun = jProfile.optJSONArray("im") ?: JSONArray()

        // ─── 2. Pre-encode HEADER (without total fix-up) ───────────
        val headerPayload = buildHeaderPayload(patient, config)

        // ─── 3. Pre-encode CODE chunks (may produce >1 chunk per
        //        section if too many codes for a single advertise) ─
        val allergyChunks = encodeCodeSection(CHUNK_ALLERGIES, allergies, ::encodeAllergyEntry)
        val medChunks     = encodeCodeSection(CHUNK_MEDS,      meds,      ::encodeMedEntry)
        val condChunks    = encodeCodeSection(CHUNK_CONDITIONS, conditions, ::encodeConditionEntry)
        val immunChunks   = encodeCodeSection(CHUNK_IMMUN,     immun,     ::encodeImmunEntry)

        // ─── 4. Compute total chunk count (header + sections + sentinel)
        val total = 1 + allergyChunks.size + medChunks.size + condChunks.size + immunChunks.size + 1
        if (total > 255) {
            Log.w(TAG, "Profile too large: $total chunks > 255 limit. Truncating.")
            // Caller's problem if profile is that big, but we don't crash.
        }
        val totalByte = total.coerceIn(1, 255).toByte()

        // ─── 5. Wrap each payload with preamble (chunk_type, idx,
        //        total, session_id) and append to chunks list
        var idx = 0
        chunks.add(wrapWithPreamble(CHUNK_HEADER, idx++, totalByte, config.sessionId, headerPayload))

        for (payload in allergyChunks) {
            chunks.add(wrapWithPreamble(CHUNK_ALLERGIES, idx++, totalByte, config.sessionId, payload))
        }
        for (payload in medChunks) {
            chunks.add(wrapWithPreamble(CHUNK_MEDS, idx++, totalByte, config.sessionId, payload))
        }
        for (payload in condChunks) {
            chunks.add(wrapWithPreamble(CHUNK_CONDITIONS, idx++, totalByte, config.sessionId, payload))
        }
        for (payload in immunChunks) {
            chunks.add(wrapWithPreamble(CHUNK_IMMUN, idx++, totalByte, config.sessionId, payload))
        }

        chunks.add(wrapWithPreamble(CHUNK_SENTINEL, idx, totalByte, config.sessionId, ByteArray(0)))

        Log.i(TAG, "✓ encoded broadcast: ${chunks.size} chunks " +
            "(${chunks.sumOf { it.size }} total bytes), " +
            "al=${allergies.length()} md=${meds.length()} cn=${conditions.length()} im=${immun.length()}")

        return chunks
    }

    /**
     * 🆕 L44.16.20 — Encode a rescuer beacon broadcast.
     *
     * Unlike the full SOS encode() which produces multiple chunks
     * for patient/allergies/meds/etc, a rescuer beacon is a single
     * chunk (CHUNK_RESCUER) followed by a CHUNK_SENTINEL terminator.
     * Total: 2 chunks rotating at CHUNK_INTERVAL_MS = ~400ms cycle.
     *
     * Payload format (25 bytes):
     *   [0..3]   latE6 (BE int32, lat × 1e6)
     *   [4..7]   lonE6 (BE int32)
     *   [8]      lang byte (0=fr, 1=en, 2=ja)
     *   [9..24]  name UTF-8 padded with NUL to 16 bytes
     *
     * The session_id is fresh per call so receivers can track the
     * rescuer across multiple cycles and detect when they leave.
     *
     * @param rescuerName  rescuer's given name (max 16 UTF-8 bytes)
     * @param langByte     RESCUER_LANG_FR/EN/JA — TTS template selector
     * @param latE6        rescuer's current latitude × 1e6
     * @param lonE6        rescuer's current longitude × 1e6
     * @param sessionId    16-byte UUID for tracking; use newSessionId()
     */
    fun encodeRescuer(
        rescuerName: String,
        langByte: Byte,
        latE6: Int,
        lonE6: Int,
        sessionId: ByteArray
    ): List<ByteArray> {
        require(sessionId.size == 16) { "session_id must be exactly 16 bytes" }

        // Build the rescuer payload
        val nameBytes = rescuerName.toByteArray(Charsets.UTF_8)
        val nameField = ByteArray(16)
        System.arraycopy(nameBytes, 0, nameField, 0, minOf(nameBytes.size, 16))

        val payload = ByteBuffer.allocate(RESCUER_PAYLOAD_SIZE).order(ByteOrder.BIG_ENDIAN)
            .putInt(latE6)
            .putInt(lonE6)
            .put(langByte)
            .put(nameField)
            .array()

        // Total = 1 rescuer chunk + 1 sentinel = 2
        val total = 2.toByte()
        val chunks = mutableListOf<ByteArray>()
        chunks.add(wrapWithPreamble(CHUNK_RESCUER, 0, total, sessionId, payload))
        chunks.add(wrapWithPreamble(CHUNK_SENTINEL, 1, total, sessionId, ByteArray(0)))

        Log.i(TAG, "✓ encoded rescuer broadcast: 2 chunks, " +
            "name='$rescuerName' lang=${rescuerLangCode(langByte)} " +
            "lat=${latE6 / 1e6} lon=${lonE6 / 1e6}")
        return chunks
    }

    private fun wrapWithPreamble(
        chunkType: Byte,
        idx: Int,
        total: Byte,
        sessionId: ByteArray,
        payload: ByteArray
    ): ByteArray {
        require(sessionId.size == 16) { "session_id must be exactly 16 bytes" }
        val out = ByteArray(PREAMBLE_SIZE + payload.size)
        out[0] = chunkType
        out[1] = idx.coerceIn(0, 255).toByte()
        out[2] = total
        System.arraycopy(sessionId, 0, out, 3, 16)
        System.arraycopy(payload, 0, out, 19, payload.size)
        return out
    }

    private fun buildHeaderPayload(patient: JSONObject, config: BroadcastConfig): ByteArray {
        // Sex/age byte: high nibble = sex (M=1,F=2,O=3,U=0), low nibble = age decade (0..9 => 0..90+)
        val gs = patient.optString("gs", "U") ?: "U"
        val sexNibble = when (gs.uppercase().firstOrNull()) {
            'M' -> 1; 'F' -> 2; 'O' -> 3; else -> 0
        }
        val ageDecade = computeAgeDecade(patient.optString("bd", "") ?: "")
        val sexAgeByte = ((sexNibble shl 4) or (ageDecade and 0x0F)).toByte()

        // Blood type byte: 0=unknown, 1=O+, 2=O-, 3=A+, 4=A-, 5=B+, 6=B-, 7=AB+, 8=AB-
        val bloodByte = bloodTypeToByte(patient.optString("bt", "") ?: "")

        // Broadcaster name: trim or pad to 16 bytes UTF-8
        val nameBytes = config.broadcasterName.toByteArray(Charsets.UTF_8)
        val nameField = ByteArray(16)
        System.arraycopy(nameBytes, 0, nameField, 0, minOf(nameBytes.size, 16))

        val buf = ByteBuffer.allocate(HEADER_PAYLOAD_SIZE).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(config.latE6)
        buf.putInt(config.lonE6)
        buf.put(sexAgeByte)
        buf.put(bloodByte)
        buf.put(config.criticality)
        buf.put(config.flags)
        buf.putInt(config.tsOffsetSeconds)
        buf.put(nameField)
        return buf.array()
    }

    private fun encodeCodeSection(
        chunkType: Byte,
        items: JSONArray,
        entryEncoder: (JSONObject) -> ByteArray?
    ): List<ByteArray> {
        if (items.length() == 0) return emptyList()

        val chunks = mutableListOf<ByteArray>()
        // Available payload per chunk = MAX_CHUNK_BYTES - PREAMBLE_SIZE - 1 (count byte)
        val budget = MAX_CHUNK_BYTES - PREAMBLE_SIZE - 1

        var bufStream = mutableListOf<ByteArray>()
        var bufSize = 0
        var bufCount = 0

        fun flush() {
            if (bufCount == 0) return
            val payload = ByteArray(1 + bufSize)
            payload[0] = bufCount.coerceIn(0, 255).toByte()
            var off = 1
            for (entry in bufStream) {
                System.arraycopy(entry, 0, payload, off, entry.size)
                off += entry.size
            }
            chunks.add(payload)
            bufStream = mutableListOf()
            bufSize = 0
            bufCount = 0
        }

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val entry = entryEncoder(item) ?: continue
            // If adding this entry overflows the chunk budget, flush.
            if (bufSize + entry.size > budget && bufCount > 0) flush()
            // If a single entry is larger than budget alone, skip
            // (defensive: only happens for absurdly long codes).
            if (entry.size > budget) {
                Log.w(TAG, "skipping oversize entry (${entry.size} > $budget) in chunk type 0x${"%02x".format(chunkType.toInt() and 0xFF)}")
                continue
            }
            bufStream.add(entry)
            bufSize += entry.size
            bufCount++
        }
        flush()

        return chunks
    }

    private fun encodeAllergyEntry(item: JSONObject): ByteArray? {
        val code = (item.optString("c", "") ?: "").trim()
        if (code.isEmpty()) return null
        val s = (item.optString("s", "U") ?: "U").uppercase().firstOrNull() ?: 'U'
        val st = (item.optString("st", "A") ?: "A").uppercase().firstOrNull() ?: 'A'
        val sysByte = guessSystem(code)
        val codeBytes = code.toByteArray(Charsets.US_ASCII)
        if (codeBytes.size > 255) return null
        val out = ByteArray(4 + codeBytes.size)
        out[0] = s.code.toByte()
        out[1] = st.code.toByte()
        out[2] = sysByte
        out[3] = codeBytes.size.toByte()
        System.arraycopy(codeBytes, 0, out, 4, codeBytes.size)
        return out
    }

    private fun encodeMedEntry(item: JSONObject): ByteArray? {
        val code = (item.optString("c", "") ?: "").trim()
        if (code.isEmpty()) return null
        val r = (item.optString("r", "O") ?: "O").uppercase().firstOrNull() ?: 'O'
        val sysByte = guessSystem(code)
        val codeBytes = code.toByteArray(Charsets.US_ASCII)
        if (codeBytes.size > 255) return null
        val out = ByteArray(3 + codeBytes.size)
        out[0] = r.code.toByte()
        out[1] = sysByte
        out[2] = codeBytes.size.toByte()
        System.arraycopy(codeBytes, 0, out, 3, codeBytes.size)
        return out
    }

    private fun encodeConditionEntry(item: JSONObject): ByteArray? {
        val code = (item.optString("c", "") ?: "").trim()
        if (code.isEmpty()) return null
        val st = (item.optString("st", "A") ?: "A").uppercase().firstOrNull() ?: 'A'
        val sysByte = guessSystem(code)
        val codeBytes = code.toByteArray(Charsets.US_ASCII)
        if (codeBytes.size > 255) return null
        val out = ByteArray(3 + codeBytes.size)
        out[0] = st.code.toByte()
        out[1] = sysByte
        out[2] = codeBytes.size.toByte()
        System.arraycopy(codeBytes, 0, out, 3, codeBytes.size)
        return out
    }

    private fun encodeImmunEntry(item: JSONObject): ByteArray? {
        val code = (item.optString("c", "") ?: "").trim()
        if (code.isEmpty()) return null
        val sysByte = guessSystem(code)
        val codeBytes = code.toByteArray(Charsets.US_ASCII)
        if (codeBytes.size > 255) return null
        val out = ByteArray(2 + codeBytes.size)
        out[0] = sysByte
        out[1] = codeBytes.size.toByte()
        System.arraycopy(codeBytes, 0, out, 2, codeBytes.size)
        return out
    }

    /**
     * Heuristic to guess the coding system from the code string format.
     * Used when the _j profile doesn't carry an explicit system field.
     *
     *  - All digits → SNOMED-CT (most common in IPS allergies/conditions)
     *  - Letter+digits short (e.g. "B01AA03") → ATC code
     *  - "I50.9", "E11.9" with dot → ICD-10
     *  - Otherwise unknown
     */
    private fun guessSystem(code: String): Byte {
        return when {
            code.matches(Regex("^[0-9]+$")) -> SYS_SNOMED
            code.matches(Regex("^[A-Z][0-9]{2}[A-Z]{2}[0-9]{2}$")) -> SYS_ATC
            code.matches(Regex("^[A-Z][0-9]{1,2}\\.?[0-9A-Z]*$")) -> SYS_ICD10
            else -> SYS_UNKNOWN
        }
    }

    private fun bloodTypeToByte(bt: String): Byte = when (bt.uppercase().trim()) {
        "O+" -> 1; "O-" -> 2
        "A+" -> 3; "A-" -> 4
        "B+" -> 5; "B-" -> 6
        "AB+" -> 7; "AB-" -> 8
        else -> 0
    }

    private fun byteToBloodType(b: Byte): String = when (b.toInt() and 0xFF) {
        1 -> "O+"; 2 -> "O-"
        3 -> "A+"; 4 -> "A-"
        5 -> "B+"; 6 -> "B-"
        7 -> "AB+"; 8 -> "AB-"
        else -> ""
    }

    private fun computeAgeDecade(bd: String): Int {
        // Very rough: year-of-birth 4 chars at start of YYYY-MM-DD
        if (bd.length < 4) return 0
        val year = bd.substring(0, 4).toIntOrNull() ?: return 0
        val now = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val age = now - year
        return (age / 10).coerceIn(0, 9)
    }

    // ═══════════════════════════════════════════════════════════════
    //  DECODE: chunk byte array → ParsedChunk
    // ═══════════════════════════════════════════════════════════════

    sealed class ParsedChunk {
        abstract val sessionId: ByteArray
        abstract val chunkIndex: Int
        abstract val chunkTotal: Int

        data class Header(
            override val sessionId: ByteArray,
            override val chunkIndex: Int,
            override val chunkTotal: Int,
            val latE6: Int,
            val lonE6: Int,
            val sex: Char,           // 'M', 'F', 'O', 'U'
            val ageDecade: Int,      // 0..9
            val bloodType: String,   // "O+", "" if unknown
            val criticality: Byte,
            val flags: Byte,
            val tsOffsetSeconds: Int,
            val broadcasterName: String
        ) : ParsedChunk()

        data class Codes(
            override val sessionId: ByteArray,
            override val chunkIndex: Int,
            override val chunkTotal: Int,
            val sectionType: Byte,    // CHUNK_ALLERGIES / MEDS / CONDITIONS / IMMUN
            val entries: List<CodeEntry>
        ) : ParsedChunk()

        data class TextNote(
            override val sessionId: ByteArray,
            override val chunkIndex: Int,
            override val chunkTotal: Int,
            val note: String
        ) : ParsedChunk()

        data class Sentinel(
            override val sessionId: ByteArray,
            override val chunkIndex: Int,
            override val chunkTotal: Int
        ) : ParsedChunk()

        // 🆕 L44.16.20 — Rescuer beacon. Carries the rescuer's name,
        // language, and current GPS so the victim's phone can announce
        // "Kamekichi-san, 80 mètres先, こちらです" via TTS.
        data class Rescuer(
            override val sessionId: ByteArray,
            override val chunkIndex: Int,
            override val chunkTotal: Int,
            val latE6: Int,
            val lonE6: Int,
            val langByte: Byte,        // 0=fr, 1=en, 2=ja
            val rescuerName: String    // e.g. "Kamekichi"
        ) : ParsedChunk() {
            val langCode: String get() = rescuerLangCode(langByte)
        }
    }

    /** Generic code entry — fields not applicable to a section are blank. */
    data class CodeEntry(
        val code: String,
        val systemByte: Byte,
        val severity: Char = ' ',  // allergies only
        val status: Char = ' ',    // allergies + conditions
        val route: Char = ' '      // meds only
    ) {
        val systemName: String get() = when (systemByte) {
            SYS_SNOMED -> "SNOMED"
            SYS_RXNORM -> "RXNORM"
            SYS_ATC -> "ATC"
            SYS_ICD10 -> "ICD10"
            SYS_CVX -> "CVX"
            SYS_LOINC -> "LOINC"
            else -> "UNKNOWN"
        }
    }

    /**
     * Decode a raw chunk byte array (as received from BLE scan) into
     * a structured ParsedChunk, or null if the bytes are malformed.
     */
    fun decode(bytes: ByteArray): ParsedChunk? {
        if (bytes.size < PREAMBLE_SIZE) {
            Log.w(TAG, "chunk too short: ${bytes.size} < $PREAMBLE_SIZE")
            return null
        }
        val type = bytes[0]
        val idx = bytes[1].toInt() and 0xFF
        val total = bytes[2].toInt() and 0xFF
        val sid = ByteArray(16).also { System.arraycopy(bytes, 3, it, 0, 16) }
        val payload = ByteArray(bytes.size - PREAMBLE_SIZE).also {
            System.arraycopy(bytes, PREAMBLE_SIZE, it, 0, it.size)
        }

        return when (type) {
            CHUNK_HEADER -> decodeHeader(sid, idx, total, payload)
            CHUNK_ALLERGIES, CHUNK_MEDS, CHUNK_CONDITIONS, CHUNK_IMMUN ->
                decodeCodes(sid, idx, total, type, payload)
            // 🆕 L44.16.20 — Rescuer beacon
            CHUNK_RESCUER -> decodeRescuer(sid, idx, total, payload)
            CHUNK_TEXT_NOTE -> decodeTextNote(sid, idx, total, payload)
            CHUNK_SENTINEL -> ParsedChunk.Sentinel(sid, idx, total)
            else -> {
                Log.w(TAG, "unknown chunk type: 0x${"%02x".format(type.toInt() and 0xFF)}")
                null
            }
        }
    }

    private fun decodeHeader(sid: ByteArray, idx: Int, total: Int, payload: ByteArray): ParsedChunk.Header? {
        if (payload.size < HEADER_PAYLOAD_SIZE) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val latE6 = buf.int
        val lonE6 = buf.int
        val sexAgeByte = buf.get()
        val bloodByte = buf.get()
        val criticality = buf.get()
        val flags = buf.get()
        val tsOffset = buf.int
        val nameField = ByteArray(16).also { buf.get(it) }
        val name = nameField.toString(Charsets.UTF_8).trimEnd('\u0000')

        val sexNibble = (sexAgeByte.toInt() shr 4) and 0x0F
        val sex = when (sexNibble) { 1 -> 'M'; 2 -> 'F'; 3 -> 'O'; else -> 'U' }
        val ageDecade = sexAgeByte.toInt() and 0x0F

        return ParsedChunk.Header(
            sessionId = sid,
            chunkIndex = idx,
            chunkTotal = total,
            latE6 = latE6,
            lonE6 = lonE6,
            sex = sex,
            ageDecade = ageDecade,
            bloodType = byteToBloodType(bloodByte),
            criticality = criticality,
            flags = flags,
            tsOffsetSeconds = tsOffset,
            broadcasterName = name
        )
    }

    /**
     * 🆕 L44.16.20 — Decode a rescuer beacon payload.
     * Inverse of encodeRescuer. Returns null on malformed payload.
     */
    private fun decodeRescuer(sid: ByteArray, idx: Int, total: Int, payload: ByteArray): ParsedChunk.Rescuer? {
        if (payload.size < RESCUER_PAYLOAD_SIZE) {
            Log.w(TAG, "decodeRescuer: payload too small (${payload.size} < $RESCUER_PAYLOAD_SIZE)")
            return null
        }
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val latE6 = buf.int
        val lonE6 = buf.int
        val langByte = buf.get()
        val nameField = ByteArray(16).also { buf.get(it) }
        val name = nameField.toString(Charsets.UTF_8).trimEnd('\u0000')
        return ParsedChunk.Rescuer(
            sessionId = sid,
            chunkIndex = idx,
            chunkTotal = total,
            latE6 = latE6,
            lonE6 = lonE6,
            langByte = langByte,
            rescuerName = name
        )
    }

    private fun decodeCodes(
        sid: ByteArray,
        idx: Int,
        total: Int,
        sectionType: Byte,
        payload: ByteArray
    ): ParsedChunk.Codes? {
        if (payload.isEmpty()) return null
        val count = payload[0].toInt() and 0xFF
        val entries = mutableListOf<CodeEntry>()
        var off = 1
        var i = 0
        while (i < count && off < payload.size) {
            try {
                val entry = when (sectionType) {
                    CHUNK_ALLERGIES -> {
                        if (off + 4 > payload.size) break
                        val s = (payload[off].toInt() and 0xFF).toChar()
                        val st = (payload[off + 1].toInt() and 0xFF).toChar()
                        val sys = payload[off + 2]
                        val codeLen = payload[off + 3].toInt() and 0xFF
                        if (off + 4 + codeLen > payload.size) break
                        val code = String(payload, off + 4, codeLen, Charsets.US_ASCII)
                        off += 4 + codeLen
                        CodeEntry(code, sys, severity = s, status = st)
                    }
                    CHUNK_MEDS -> {
                        if (off + 3 > payload.size) break
                        val r = (payload[off].toInt() and 0xFF).toChar()
                        val sys = payload[off + 1]
                        val codeLen = payload[off + 2].toInt() and 0xFF
                        if (off + 3 + codeLen > payload.size) break
                        val code = String(payload, off + 3, codeLen, Charsets.US_ASCII)
                        off += 3 + codeLen
                        CodeEntry(code, sys, route = r)
                    }
                    CHUNK_CONDITIONS -> {
                        if (off + 3 > payload.size) break
                        val st = (payload[off].toInt() and 0xFF).toChar()
                        val sys = payload[off + 1]
                        val codeLen = payload[off + 2].toInt() and 0xFF
                        if (off + 3 + codeLen > payload.size) break
                        val code = String(payload, off + 3, codeLen, Charsets.US_ASCII)
                        off += 3 + codeLen
                        CodeEntry(code, sys, status = st)
                    }
                    CHUNK_IMMUN -> {
                        if (off + 2 > payload.size) break
                        val sys = payload[off]
                        val codeLen = payload[off + 1].toInt() and 0xFF
                        if (off + 2 + codeLen > payload.size) break
                        val code = String(payload, off + 2, codeLen, Charsets.US_ASCII)
                        off += 2 + codeLen
                        CodeEntry(code, sys)
                    }
                    else -> return null
                }
                entries.add(entry)
                i++
            } catch (e: Exception) {
                Log.w(TAG, "decodeCodes parse error at offset $off: ${e.message}")
                break
            }
        }
        return ParsedChunk.Codes(sid, idx, total, sectionType, entries)
    }

    private fun decodeTextNote(sid: ByteArray, idx: Int, total: Int, payload: ByteArray): ParsedChunk.TextNote? {
        if (payload.size < 2) return null
        val len = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        if (payload.size < 2 + len) return null
        val text = String(payload, 2, len, Charsets.UTF_8)
        return ParsedChunk.TextNote(sid, idx, total, text)
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helper: generate a fresh session_id (UUID v4 random bytes)
    // ═══════════════════════════════════════════════════════════════

    private val rng = SecureRandom()

    fun newSessionId(): ByteArray {
        val out = ByteArray(16)
        rng.nextBytes(out)
        // Set UUID v4 version + variant bits
        out[6] = ((out[6].toInt() and 0x0F) or 0x40).toByte()
        out[8] = ((out[8].toInt() and 0x3F) or 0x80).toByte()
        return out
    }

    fun sessionIdToHex(sid: ByteArray): String =
        sid.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
