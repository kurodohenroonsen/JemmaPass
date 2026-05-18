/*
 * PhoneNumberHelper.kt — JEMMA Pass · Plan B · v2.6.0 · L_BLINDAGE
 *
 * Helpers pour normaliser et valider les numéros de téléphone dans les
 * forms Patient et Contact. Pas une vraie librairie i18n (on n'embarque
 * pas libphonenumber pour économiser ~1 MB APK) — juste de l'heuristique
 * basée sur le ISO 3166 country code.
 *
 * Stratégie :
 *   1. Strip toutes les paren, espaces, tirets, points → conserve juste
 *      chiffres + un éventuel `+` initial
 *   2. Si commence par `+` → respecté, considéré E.164 OK
 *   3. Si commence par `00` → remplace par `+`
 *   4. Sinon : si country code fourni → préfixe le `+XX` après suppression
 *      du `0` leading qui est le national trunk prefix en Europe/Japon
 *
 * Limitations :
 *   - Le US `0`-leading n'est pas un trunk prefix → pour US faut pas
 *     stripper le `0`. On hardcode l'exception pour US/CA.
 *   - Numbers à 9 chiffres comme certaines admin DE pourraient être
 *     valides : on ne rejette JAMAIS, on WARN.
 *
 * Logging : tag JEMMA-PHONE-HELPER
 */
package be.heyman.android.jemmapassdemo.ui.profile.common

import android.util.Log

object PhoneNumberHelper {
    private const val TAG = "JEMMA-PHONE-HELPER"

    /** Mapping ISO 3166-1 alpha-2 → trunk prefix country code (E.164 calling code). */
    private val COUNTRY_PREFIX: Map<String, String> = mapOf(
        "BE" to "+32",
        "FR" to "+33",
        "DE" to "+49",
        "IT" to "+39",
        "NL" to "+31",
        "ES" to "+34",
        "PT" to "+351",
        "LU" to "+352",
        "CH" to "+41",
        "AT" to "+43",
        "DK" to "+45",
        "SE" to "+46",
        "NO" to "+47",
        "FI" to "+358",
        "GB" to "+44",
        "IE" to "+353",
        "GR" to "+30",
        "PL" to "+48",
        "CZ" to "+420",
        "JP" to "+81",
        "KR" to "+82",
        "CN" to "+86",
        "US" to "+1",
        "CA" to "+1",
        "AU" to "+61",
        "NZ" to "+64",
        "MX" to "+52",
        "BR" to "+55",
        "AR" to "+54",
    )

    /**
     * Strip whitespace, dashes, parens, dots.
     * Retains digits and a leading `+`.
     */
    fun strip(raw: String): String {
        val sb = StringBuilder()
        for ((i, c) in raw.toCharArray().withIndex()) {
            when {
                c == '+' && i == 0 -> sb.append(c)
                c.isDigit() -> sb.append(c)
                // else: skip
            }
        }
        return sb.toString()
    }

    /**
     * Tente de normaliser un numéro vers format E.164 (ex: "+32475123456").
     * Pas de garantie — c'est best-effort. Si on n'arrive pas à
     * normaliser : retourne le stripped original.
     *
     * @param raw le numéro tel que l'user a tapé
     * @param countryHint ISO 3166 alpha-2 hint pour deviner le prefix
     *                    (ex: depuis `JPatient.nat` ou `JAddress.country`)
     */
    fun normalize(raw: String, countryHint: String? = null): String {
        val stripped = strip(raw)
        if (stripped.isBlank()) return stripped

        // Already E.164
        if (stripped.startsWith("+")) {
            return stripped
        }
        // Trunk-international 00
        if (stripped.startsWith("00") && stripped.length > 4) {
            return "+" + stripped.substring(2)
        }
        // Use country hint
        val cc = countryHint?.uppercase()?.takeIf { it.length == 2 }
        val prefix = cc?.let { COUNTRY_PREFIX[it] } ?: return stripped

        // For US/CA, no trunk-prefix to strip
        val isNanp = cc == "US" || cc == "CA"
        val nationalPart = if (!isNanp && stripped.startsWith("0")) {
            stripped.drop(1)
        } else {
            stripped
        }
        val result = "$prefix$nationalPart"
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📞 normalize · raw='$raw' · cc=$cc · → $result")
        return result
    }

    /**
     * Retourne le prefix attendu pour une country code, ou null si pas
     * connu. Permet à l'UI de pré-remplir un hint.
     */
    fun expectedPrefix(countryCode: String?): String? {
        if (countryCode.isNullOrBlank()) return null
        return COUNTRY_PREFIX[countryCode.uppercase()]
    }

    /**
     * Validation soft : retourne null si OK, sinon un message d'erreur
     * localisable (via un resource ID). On utilise des string keys plutôt
     * que des R.string parce que ce helper est dans common/ — le caller
     * fait la traduction.
     */
    enum class ValidationCode {
        OK,
        TOO_SHORT,
        TOO_LONG,
        INVALID_CHARS,
        MISSING_PREFIX_WARNING,
    }

    fun validate(raw: String, hasCountryHint: Boolean): ValidationCode {
        val stripped = strip(raw)
        if (stripped.isBlank()) return ValidationCode.OK   // empty is allowed (caller checks if required)
        if (stripped.length < 7) return ValidationCode.TOO_SHORT
        if (stripped.length > 18) return ValidationCode.TOO_LONG
        // Check for forbidden chars in original
        for (c in raw) {
            if (!c.isDigit() && c != '+' && !c.isWhitespace() &&
                c != '-' && c != '.' && c != '(' && c != ')') {
                return ValidationCode.INVALID_CHARS
            }
        }
        if (!stripped.startsWith("+") && !stripped.startsWith("00") && !hasCountryHint) {
            return ValidationCode.MISSING_PREFIX_WARNING
        }
        return ValidationCode.OK
    }
}
