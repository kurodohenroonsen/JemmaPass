/*
 * MrzParser.kt — JEMMA Pass · Lot 14.5c35 · Patient Vision Pipeline
 *
 * BONUS gratuit du pipeline patient : si l'OCR retourne une zone MRZ
 * (Machine-Readable Zone) reconnaissable, on peut décoder
 * déterministiquement (sans appel Gemma) :
 *
 *   - family name + given names
 *   - date of birth (YYMMDD → ISO YYYY-MM-DD)
 *   - sex (M / F / <)
 *   - nationality (3-letter ICAO → 2-letter ISO 3166-1)
 *   - document number
 *
 * Ce n'est PAS le moteur principal. Le moteur principal est Gemma
 * multimodal (qui lit CNI, passeport, carte de visite, fiche
 * d'admission, ordonnance avec en-tête, etc.). Le MRZ est un fallback
 * de précision : quand il est disponible (CNI européenne moderne,
 * passeport), il fournit du ground-truth qu'on pré-applique dans le
 * draft AVANT d'appeler Gemma. Gemma complète ensuite les champs
 * absents du MRZ (adresse postale, email, blood type, etc.).
 *
 * Formats supportés :
 *   • TD1 (ID-1, CNI européennes) : 3 lignes × 30 caractères
 *   • TD2 (ID-2)                  : 2 lignes × 36 caractères
 *   • TD3 (passeports)            : 2 lignes × 44 caractères
 *
 * Si pas de MRZ détecté (vieille carte, carte de visite, ordonnance,
 * etc.) → on retourne null et Gemma fait tout depuis l'image.
 *
 * Log tag : JEMMA-MRZ.
 */
package be.heyman.android.jemmapassdemo.ai.patient

import android.util.Log

private const val TAG = "JEMMA-MRZ"

data class MrzResult(
    val documentType: String,             // "TD1" / "TD2" / "TD3"
    val issuingCountryIso2: String?,      // "BE", "FR", "JP", "DE", etc.
    val familyName: String?,              // "DUPONT"
    val givenNames: String?,              // "JEAN PIERRE MARIE"
    val birthDateIso: String?,            // "1985-03-15"
    val sex: String?,                     // "M" / "F" / "U"
    val nationalityIso2: String?,         // "BE", "FR", etc.
    val documentNumber: String?,
)

object MrzParser {

    fun tryParse(ocrText: String): MrzResult? {
        if (ocrText.isBlank()) return null

        val mrzLines = ocrText
            .lines()
            .map { line ->
                line.uppercase()
                    .replace('«', '<').replace('‹', '<').replace('〈', '<')
                    .replace(" ", "")
                    .replace("\t", "")
            }
            .filter { it.isNotBlank() && MRZ_LINE_RX.matches(it) }

        if (mrzLines.isEmpty()) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] no MRZ candidate lines in OCR")
            return null
        }

        // TD3 : passport (2 × 44)
        val td3 = mrzLines.filter { it.length == 44 }
        if (td3.size >= 2) {
            runCatching { parseTd3(td3[0], td3[1]) }
                .onSuccess { result ->
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ TD3 (passport) parsed · " +
                        "country=${result.issuingCountryIso2} · name=${result.familyName}, ${result.givenNames}")
                    return result
                }
                .onFailure { Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ TD3 parse failed: ${it.message}") }
        }

        // TD2 : ID-2 (2 × 36)
        val td2 = mrzLines.filter { it.length == 36 }
        if (td2.size >= 2) {
            runCatching { parseTd2(td2[0], td2[1]) }
                .onSuccess { result ->
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ TD2 (ID-2) parsed · " +
                        "country=${result.issuingCountryIso2} · name=${result.familyName}, ${result.givenNames}")
                    return result
                }
                .onFailure { Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ TD2 parse failed: ${it.message}") }
        }

        // TD1 : ID-1, CNI européennes (3 × 30)
        val td1 = mrzLines.filter { it.length == 30 }
        if (td1.size >= 3) {
            runCatching { parseTd1(td1[0], td1[1], td1[2]) }
                .onSuccess { result ->
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ TD1 (ID-1) parsed · " +
                        "country=${result.issuingCountryIso2} · name=${result.familyName}, ${result.givenNames}")
                    return result
                }
                .onFailure { Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ TD1 parse failed: ${it.message}") }
        }

        Log.d(TAG, "[t=${System.currentTimeMillis()}] no recognizable MRZ format " +
            "(TD1/TD2/TD3) — Gemma will do full extraction")
        return null
    }

    private fun parseTd3(line1: String, line2: String): MrzResult {
        require(line1.length == 44 && line2.length == 44) { "TD3 needs 2x44" }
        require(line1[0] == 'P') { "TD3 line1 must start with P (passport)" }

        val issuingIcao = line1.substring(2, 5).filter { it != '<' }
        val (family, given) = splitNameField(line1.substring(5, 44))

        val documentNumber = unwrapField(line2.substring(0, 9))
        val nationalityIcao = line2.substring(10, 13).filter { it != '<' }
        val birthYymmdd = line2.substring(13, 19)
        val sex = mapSex(line2[20])

        return MrzResult(
            documentType = "TD3",
            issuingCountryIso2 = icaoToIso2(issuingIcao),
            familyName = family,
            givenNames = given,
            birthDateIso = yymmddToIso(birthYymmdd),
            sex = sex,
            nationalityIso2 = icaoToIso2(nationalityIcao),
            documentNumber = documentNumber.takeIf { it.isNotBlank() },
        )
    }

    private fun parseTd2(line1: String, line2: String): MrzResult {
        require(line1.length == 36 && line2.length == 36) { "TD2 needs 2x36" }
        require(line1[0] == 'I' || line1[0] == 'A' || line1[0] == 'C') {
            "TD2 line1 must start with I/A/C"
        }

        val issuingIcao = line1.substring(2, 5).filter { it != '<' }
        val (family, given) = splitNameField(line1.substring(5, 36))

        val documentNumber = unwrapField(line2.substring(0, 9))
        val nationalityIcao = line2.substring(10, 13).filter { it != '<' }
        val birthYymmdd = line2.substring(13, 19)
        val sex = mapSex(line2[20])

        return MrzResult(
            documentType = "TD2",
            issuingCountryIso2 = icaoToIso2(issuingIcao),
            familyName = family,
            givenNames = given,
            birthDateIso = yymmddToIso(birthYymmdd),
            sex = sex,
            nationalityIso2 = icaoToIso2(nationalityIcao),
            documentNumber = documentNumber.takeIf { it.isNotBlank() },
        )
    }

    private fun parseTd1(line1: String, line2: String, line3: String): MrzResult {
        require(line1.length == 30 && line2.length == 30 && line3.length == 30) { "TD1 needs 3x30" }
        require(line1[0] == 'I' || line1[0] == 'A' || line1[0] == 'C') {
            "TD1 line1 must start with I/A/C"
        }

        val issuingIcao = line1.substring(2, 5).filter { it != '<' }
        val documentNumber = unwrapField(line1.substring(5, 14))

        val birthYymmdd = line2.substring(0, 6)
        val sex = mapSex(line2[7])
        val nationalityIcao = line2.substring(15, 18).filter { it != '<' }

        val (family, given) = splitNameField(line3)

        return MrzResult(
            documentType = "TD1",
            issuingCountryIso2 = icaoToIso2(issuingIcao),
            familyName = family,
            givenNames = given,
            birthDateIso = yymmddToIso(birthYymmdd),
            sex = sex,
            nationalityIso2 = icaoToIso2(nationalityIcao),
            documentNumber = documentNumber.takeIf { it.isNotBlank() },
        )
    }

    private fun splitNameField(field: String): Pair<String?, String?> {
        val parts = field.split("<<", limit = 2)
        val family = parts.getOrNull(0)
            ?.replace('<', ' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val given = parts.getOrNull(1)
            ?.replace('<', ' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return family to given
    }

    private fun unwrapField(field: String): String =
        field.trimEnd('<').replace('<', ' ').trim()

    private fun mapSex(c: Char): String? = when (c) {
        'M' -> "M"
        'F' -> "F"
        '<' -> "U"
        else -> null
    }

    private fun yymmddToIso(yymmdd: String): String? {
        if (yymmdd.length != 6 || !yymmdd.all { it.isDigit() }) return null
        val yy = yymmdd.substring(0, 2).toInt()
        val mm = yymmdd.substring(2, 4).toInt()
        val dd = yymmdd.substring(4, 6).toInt()
        if (mm !in 1..12 || dd !in 1..31) return null
        val cutoff = 31
        val year = if (yy > cutoff) 1900 + yy else 2000 + yy
        return "%04d-%02d-%02d".format(year, mm, dd)
    }

    private fun icaoToIso2(icao: String): String? = when (icao.uppercase()) {
        "BEL" -> "BE"
        "FRA" -> "FR"
        "DEU", "D<<" -> "DE"
        "NLD" -> "NL"
        "ITA" -> "IT"
        "ESP" -> "ES"
        "PRT" -> "PT"
        "GBR", "UK" -> "GB"
        "USA" -> "US"
        "CAN" -> "CA"
        "JPN" -> "JP"
        "CHN" -> "CN"
        "KOR" -> "KR"
        "AUT" -> "AT"
        "CHE" -> "CH"
        "LUX" -> "LU"
        "POL" -> "PL"
        "GRC" -> "GR"
        "IRL" -> "IE"
        "DNK" -> "DK"
        "SWE" -> "SE"
        "NOR" -> "NO"
        "FIN" -> "FI"
        "CZE" -> "CZ"
        "SVK" -> "SK"
        "HUN" -> "HU"
        "ROU" -> "RO"
        "BGR" -> "BG"
        "HRV" -> "HR"
        "SVN" -> "SI"
        "EST" -> "EE"
        "LVA" -> "LV"
        "LTU" -> "LT"
        "MAR" -> "MA"
        "TUN" -> "TN"
        "DZA" -> "DZ"
        "TUR" -> "TR"
        "BRA" -> "BR"
        "ARG" -> "AR"
        "MEX" -> "MX"
        else -> null
    }

    private val MRZ_LINE_RX = Regex("^[A-Z0-9<]+$")
}
