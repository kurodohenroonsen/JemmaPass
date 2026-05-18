/*
 * PatientAgentTools.kt — JEMMA Pass · Lot 14.5c35 · Patient Vision Pipeline
 *
 * @Tools function-callable methods used by Gemma 4 when extracting
 * Patient demographics from a photo of an ID card, passport, business
 * card, hospital admission slip, prescription header, etc.
 *
 * Pattern : reuse Lot 14.5c34's allergies @Tools approach. Each setter
 * normalises and stores into an in-memory draft. saveAllergyDraft's
 * counterpart here is finalizePatientDraft() — a no-op tool whose only
 * purpose is to let Gemma signal "done" so the host fragment can move
 * to the next pipeline step.
 *
 * IPS Patient must-support fields covered :
 *   - Patient.name.given        → setGivenName
 *   - Patient.name.family       → setFamilyName
 *   - Patient.gender            → setGender (M|F|O|U)
 *   - Patient.birthDate         → setBirthDate (ISO YYYY-MM-DD)
 *   - Patient.address.country   → setNationality (ISO 3166-1 alpha-2)
 *   - Patient.address           → setAddress (multi-part)
 *   - Patient.telecom[phone]    → setPhone
 *   - Patient.telecom[email]    → setEmail
 *   - Patient.identifier        → setIdentifier (free-form, system optional)
 *   - Patient.extension(bloodType) → setBloodType (JEMMA extension)
 *
 * Fields NOT covered here (Patient pipeline scope) :
 *   - Allergies, medications, conditions → separate pillar agents
 *   - generalPractitioner → covered by Providers pillar
 *
 * Log tag : JEMMA-PATIENT-AGENT.
 */
package be.heyman.android.jemmapassdemo.ai.patient

import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "JEMMA-PATIENT-AGENT"

/**
 * In-memory draft accumulated by Gemma's @Tool calls.
 *
 * Note: this is intentionally flat — no nested address/telecom lists.
 * The bridge layer (AssistantPatientPipelineFragment) re-assembles it
 * into a proper JPatient with JAddress / JTelecom / JIdentifier
 * structures before navigating to the form.
 */
data class PatientDraft(
    val givenName: String? = null,
    val familyName: String? = null,
    val birthDate: String? = null,          // ISO YYYY-MM-DD
    val gender: String? = null,              // M | F | O | U
    val nationality: String? = null,         // ISO 3166-1 alpha-2
    val bloodType: String? = null,           // A+, A-, B+, ..., O-, AB+, ...
    // Address (single, structured)
    val addressLine: String? = null,
    val addressCity: String? = null,
    val addressPostalCode: String? = null,
    val addressCountry: String? = null,      // ISO 3166-1 alpha-2
    // Telecom
    val phone: String? = null,
    val email: String? = null,
    // Identifier
    val identifierValue: String? = null,
    val identifierSystem: String? = null,    // BE-NRN | PASSPORT | OTHER
) {
    val filledCount: Int
        get() = listOfNotNull(
            givenName, familyName, birthDate, gender, nationality, bloodType,
            addressLine, addressCity, addressPostalCode, addressCountry,
            phone, email, identifierValue,
        ).size

    val isMinimallyComplete: Boolean
        get() = !familyName.isNullOrBlank() || !givenName.isNullOrBlank()
}

/**
 * @Tools surface for the patient extraction agent. Created scoped to a
 * single pipeline run by AssistantPatientPipelineFragment — NOT a
 * Singleton because each run is one-shot.
 */
@Singleton
class PatientAgentTools @Inject constructor() : ToolSet {

    private val draftRef = AtomicReference(PatientDraft())

    fun snapshot(): PatientDraft = draftRef.get()

    fun reset() {
        draftRef.set(PatientDraft())
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔄 PatientAgentTools reset")
    }

    /** Pre-seed the draft from a deterministic source (e.g. MRZ parse). */
    fun preSeed(seed: PatientDraft) {
        draftRef.set(seed)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🌱 pre-seeded · " +
            "given=${seed.givenName} · family=${seed.familyName} · " +
            "birth=${seed.birthDate} · sex=${seed.gender} · nat=${seed.nationality}")
    }

    // ──────────────────────────────────────────────────────────────────
    //  IPS Patient must-support tools
    // ──────────────────────────────────────────────────────────────────

    @Tool(description = "Set the patient's given name (first name / 名). Capitalised properly, e.g. 'Marie' not 'MARIE' (unless the document is fully in caps and you cannot tell). Multiple given names separated by spaces : 'Jean Pierre'.")
    fun setGivenName(
        @ToolParam(description = "Patient's given name(s), space-separated") name: String,
    ): Map<String, Any> {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛠 @Tool ENTRY setGivenName · raw='$name'")
        val clean = name.trim().titlecaseSmart()
        if (clean.isBlank()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ❌ setGivenName · rejected · empty")
            return mapOf("ok" to false, "error" to "empty_name")
        }
        draftRef.updateAndGet { it.copy(givenName = clean) }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 👤 setGivenName · ACCEPTED · '$clean'")
        return mapOf("ok" to true, "givenName" to clean)
    }

    @Tool(description = "Set the patient's family name (last name / surname / 姓). Capitalised properly. If document shows 'DUPONT', store 'Dupont' unless the user prefers all-caps.")
    fun setFamilyName(
        @ToolParam(description = "Patient's family name") name: String,
    ): Map<String, Any> {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛠 @Tool ENTRY setFamilyName · raw='$name'")
        val clean = name.trim().titlecaseSmart()
        if (clean.isBlank()) {
            return mapOf("ok" to false, "error" to "empty_name")
        }
        draftRef.updateAndGet { it.copy(familyName = clean) }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 👤 setFamilyName · ACCEPTED · '$clean'")
        return mapOf("ok" to true, "familyName" to clean)
    }

    @Tool(description = "Set the patient's birth date. Format: YYYY-MM-DD")
    fun setBirthDate(
        @ToolParam(description = "Birth date as YYYY-MM-DD") isoDate: String,
    ): Map<String, Any> {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛠 @Tool ENTRY setBirthDate · raw='$isoDate'")
        val normalized = normalizeBirthDate(isoDate)
        if (normalized == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ❌ setBirthDate · rejected · raw='$isoDate' · could not parse to YYYY-MM-DD")
            return mapOf("ok" to false, "error" to "invalid_date", "expected_format" to "YYYY-MM-DD")
        }
        draftRef.updateAndGet { it.copy(birthDate = normalized) }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📅 setBirthDate · ACCEPTED · '$normalized' (from raw='$isoDate')")
        return mapOf("ok" to true, "birthDate" to normalized)
    }

    @Tool(description = "Set the patient's administrative gender. MUST be one of these single-letter codes: 'M' (male / homme / 男), 'F' (female / femme / 女), 'O' (other / non-binary), 'U' (unknown / not stated). NEVER pass 'Male' or 'Female' — only the letter.")
    fun setGender(
        @ToolParam(description = "One of: M, F, O, U") gender: String,
    ): Map<String, Any> {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛠 @Tool ENTRY setGender · raw='$gender'")
        val raw = gender.trim().uppercase()
        val mapped = when (raw) {
            "M", "MALE", "HOMME", "MASCULIN", "男", "男性" -> "M"
            "F", "FEMALE", "FEMME", "FÉMININ", "FEMININ", "女", "女性" -> "F"
            "O", "OTHER", "AUTRE", "NB", "X" -> "O"
            "U", "UNKNOWN", "INCONNU", "<", "" -> "U"
            else -> raw
        }
        if (mapped !in setOf("M", "F", "O", "U")) {
            return mapOf("ok" to false, "error" to "invalid_gender", "allowed" to listOf("M", "F", "O", "U"))
        }
        draftRef.updateAndGet { it.copy(gender = mapped) }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ⚧ setGender · ACCEPTED · '$mapped' (from raw='$gender')")
        return mapOf("ok" to true, "gender" to mapped)
    }

    @Tool(description = "Set the patient's nationality as an ISO 3166-1 alpha-2 country code (2 uppercase letters). 'BE' for Belgium, 'FR' for France, 'JP' for Japan, 'DE' for Germany, 'NL' for Netherlands. NEVER pass the full country name — only the 2-letter code.")
    fun setNationality(
        @ToolParam(description = "Two-letter ISO 3166-1 alpha-2 code, e.g. 'BE'") iso2: String,
    ): Map<String, Any> {
        val clean = iso2.trim().uppercase()
        if (!ISO2_RX.matches(clean)) {
            return mapOf("ok" to false, "error" to "invalid_iso2", "expected_format" to "2 uppercase letters")
        }
        draftRef.updateAndGet { it.copy(nationality = clean) }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🌍 setNationality · ACCEPTED · '$clean'")
        return mapOf("ok" to true, "nationality" to clean)
    }

    @Tool(description = "Set the patient's blood type. MUST be one of: 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-', 'Unknown'. Use a real '+' or '-' character (not 'plus' / 'positive'). Only call this tool if the blood type is explicitly written on the document.")
    fun setBloodType(
        @ToolParam(description = "Blood type with ABO group + Rh sign, e.g. 'A+'") bloodType: String,
    ): Map<String, Any> {
        val clean = bloodType.trim()
            .replace("positive", "+", ignoreCase = true)
            .replace("negative", "-", ignoreCase = true)
            .replace("plus", "+", ignoreCase = true)
            .replace("minus", "-", ignoreCase = true)
            .replace(" ", "")
            .uppercase()
        if (clean !in VALID_BLOOD_TYPES) {
            return mapOf("ok" to false, "error" to "invalid_blood_type", "allowed" to VALID_BLOOD_TYPES.toList())
        }
        draftRef.updateAndGet { it.copy(bloodType = clean) }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩸 setBloodType · ACCEPTED · '$clean'")
        return mapOf("ok" to true, "bloodType" to clean)
    }

    @Tool(description = "Set the patient's postal address. Pass each component as a separate parameter when possible. If you only have a single free-form line, put it all in addressLine and leave the rest blank. Country MUST be an ISO 3166-1 alpha-2 code if provided.")
    fun setAddress(
        @ToolParam(description = "Street + number, e.g. 'Rue de la Paix 12'") addressLine: String,
        @ToolParam(description = "City name, e.g. 'Bruxelles'") city: String,
        @ToolParam(description = "Postal/ZIP code, e.g. '1000'") postalCode: String,
        @ToolParam(description = "Country ISO 3166-1 alpha-2, e.g. 'BE'") country: String,
    ): Map<String, Any> {
        val cLine = addressLine.trim().takeIf { it.isNotBlank() }
        val cCity = city.trim().takeIf { it.isNotBlank() }
        val cPost = postalCode.trim().takeIf { it.isNotBlank() }
        val cCountry = country.trim().uppercase()
            .takeIf { ISO2_RX.matches(it) }
        if (cLine == null && cCity == null && cPost == null) {
            return mapOf("ok" to false, "error" to "empty_address")
        }
        draftRef.updateAndGet {
            it.copy(
                addressLine = cLine ?: it.addressLine,
                addressCity = cCity ?: it.addressCity,
                addressPostalCode = cPost ?: it.addressPostalCode,
                addressCountry = cCountry ?: it.addressCountry,
            )
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏠 setAddress · ACCEPTED · " +
            "line='$cLine' city='$cCity' postal='$cPost' country='$cCountry'")
        return mapOf("ok" to true, "addressLine" to (cLine ?: ""))
    }

    @Tool(description = "Set the patient's phone number. Keep it as-printed (e.g. '+32 471 12 34 56' or '0471 12 34 56') — do not reformat. If multiple phones, pick the most prominent one (usually mobile).")
    fun setPhone(
        @ToolParam(description = "Phone number as printed") phone: String,
    ): Map<String, Any> {
        val clean = phone.trim()
        if (clean.isBlank()) return mapOf("ok" to false, "error" to "empty_phone")
        draftRef.updateAndGet { it.copy(phone = clean) }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📞 setPhone · ACCEPTED · '$clean'")
        return mapOf("ok" to true, "phone" to clean)
    }

    @Tool(description = "Set the patient's email address. Lowercased, no spaces. Must contain an '@' to be accepted.")
    fun setEmail(
        @ToolParam(description = "Email address") email: String,
    ): Map<String, Any> {
        val clean = email.trim().lowercase().replace(" ", "")
        if (!clean.contains('@') || clean.length < 5) {
            return mapOf("ok" to false, "error" to "invalid_email")
        }
        draftRef.updateAndGet { it.copy(email = clean) }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📧 setEmail · ACCEPTED · '$clean'")
        return mapOf("ok" to true, "email" to clean)
    }

    @Tool(description = "Set the patient's identifier — typically the national ID number, passport number, or social security number. Keep it as-printed. Use system='BE-NRN' for Belgian national register number, 'PASSPORT' for passport, 'JP-MyNumber' for Japanese My Number, 'FR-NSS' for French social security, or 'OTHER' if unsure.")
    fun setIdentifier(
        @ToolParam(description = "Identifier value as printed (e.g. '12.34.56-789.01')") value: String,
        @ToolParam(description = "System short-code: BE-NRN | PASSPORT | JP-MyNumber | FR-NSS | OTHER") system: String,
    ): Map<String, Any> {
        val cVal = value.trim()
        if (cVal.isBlank()) return mapOf("ok" to false, "error" to "empty_identifier")
        val cSys = system.trim().uppercase().let {
            when (it) {
                "BE-NRN", "PASSPORT", "JP-MYNUMBER", "FR-NSS", "EU-EHIC", "OTHER" -> it.replace("MYNUMBER", "MyNumber")
                else -> "OTHER"
            }
        }
        draftRef.updateAndGet { it.copy(identifierValue = cVal, identifierSystem = cSys) }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🆔 setIdentifier · ACCEPTED · '$cVal' (system=$cSys)")
        return mapOf("ok" to true, "identifierValue" to cVal, "identifierSystem" to cSys)
    }

    @Tool(description = "Signal that all extractable patient fields have been captured. Call this ONCE at the end of your extraction. Returns the full draft summary. Do not call it before you have at least set the family name or given name.")
    fun finalizePatientDraft(): Map<String, Any> {
        val draft = draftRef.get()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛠 @Tool ENTRY finalizePatientDraft · " +
            "filled=${draft.filledCount}")
        if (!draft.isMinimallyComplete) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ finalize called but no name set")
            return mapOf(
                "ok" to false,
                "error" to "no_name_set",
                "hint" to "Call setGivenName or setFamilyName before finalize.",
            )
        }
        return mapOf(
            "ok" to true,
            "filled_fields" to draft.filledCount,
            "summary" to mapOf(
                "givenName" to (draft.givenName ?: ""),
                "familyName" to (draft.familyName ?: ""),
                "birthDate" to (draft.birthDate ?: ""),
                "gender" to (draft.gender ?: ""),
                "nationality" to (draft.nationality ?: ""),
            ),
        )
    }

    // ──────────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────────

    private fun String.titlecaseSmart(): String =
        split(" ", "-").joinToString(" ") { part ->
            if (part.isEmpty()) part
            else part.substring(0, 1).uppercase() +
                part.substring(1).lowercase()
        }

    /**
     * 🆕 Lot 14.5c39 — Tolerant birth-date parser.
     *
     * Gemma is told to provide YYYY-MM-DD, but in practice it sometimes
     * outputs '1979-04' (drops the day), '04/04/1979' (FR slashes),
     * '04 AVR 1979' (FR month abbrev), '4 April 1979', '04.04.1979'
     * (BE dotted), etc. Instead of rejecting those, we normalize.
     *
     * Returns the canonical 'YYYY-MM-DD' or null if we honestly can't
     * reconstruct a full date (e.g. only year, or only year+month with
     * no day visible anywhere).
     */
    private fun normalizeBirthDate(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null

        // Case 1 : already canonical YYYY-MM-DD
        ISO_DATE_RX.matchEntire(s)?.let {
            return if (isPlausibleDate(s)) s else null
        }

        // Case 2 : YYYY-MM-D (single-digit day) → pad
        Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").matchEntire(s)?.let { m ->
            val (y, mo, d) = m.destructured
            val iso = "${y}-${mo.padStart(2, '0')}-${d.padStart(2, '0')}"
            return if (isPlausibleDate(iso)) iso else null
        }

        // Case 3 : DD/MM/YYYY or DD-MM-YYYY or DD.MM.YYYY (slashes/dashes/dots)
        Regex("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})$").matchEntire(s)?.let { m ->
            val (d, mo, y) = m.destructured
            val iso = "${y}-${mo.padStart(2, '0')}-${d.padStart(2, '0')}"
            return if (isPlausibleDate(iso)) iso else null
        }

        // Case 4 : DD/MM/YY (two-digit year) — adult assumption
        Regex("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2})$").matchEntire(s)?.let { m ->
            val (d, mo, y) = m.destructured
            val yi = y.toInt()
            val fullYear = if (yi > 31) 1900 + yi else 2000 + yi
            val iso = "${fullYear}-${mo.padStart(2, '0')}-${d.padStart(2, '0')}"
            return if (isPlausibleDate(iso)) iso else null
        }

        // Case 5 : 'DD MMM YYYY' or 'DD MMMM YYYY' (FR/EN month name or abbrev)
        Regex("^(\\d{1,2})\\s+([A-Za-zéûô]{3,12})\\.?\\s+(\\d{4})$").matchEntire(s)?.let { m ->
            val (d, monthName, y) = m.destructured
            val mo = MONTH_NAMES[monthName.lowercase().trimEnd('.')] ?: return@let null
            val iso = "${y}-${mo.toString().padStart(2, '0')}-${d.padStart(2, '0')}"
            return if (isPlausibleDate(iso)) iso else null
        }

        // Case 6 : 'MMMM DD, YYYY' (English natural)
        Regex("^([A-Za-zéûô]{3,12})\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})$").matchEntire(s)?.let { m ->
            val (monthName, d, y) = m.destructured
            val mo = MONTH_NAMES[monthName.lowercase().trimEnd('.')] ?: return@let null
            val iso = "${y}-${mo.toString().padStart(2, '0')}-${d.padStart(2, '0')}"
            return if (isPlausibleDate(iso)) iso else null
        }

        // Case 7 : YYYYMMDD (no separator)
        Regex("^(\\d{4})(\\d{2})(\\d{2})$").matchEntire(s)?.let { m ->
            val (y, mo, d) = m.destructured
            val iso = "${y}-${mo}-${d}"
            return if (isPlausibleDate(iso)) iso else null
        }

        // Case 8 : YYYY-MM (no day) → reject. We refuse to invent a day.
        // Returning null lets the user fill manually in the form.
        return null
    }

    private fun isPlausibleDate(iso: String): Boolean {
        // Basic sanity : 1900..currentYear, month 1..12, day 1..31.
        val parts = iso.split("-")
        if (parts.size != 3) return false
        val y = parts[0].toIntOrNull() ?: return false
        val mo = parts[1].toIntOrNull() ?: return false
        val d = parts[2].toIntOrNull() ?: return false
        if (y !in 1900..2100) return false
        if (mo !in 1..12) return false
        if (d !in 1..31) return false
        return true
    }

    companion object {
        private val ISO_DATE_RX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        private val ISO2_RX = Regex("^[A-Z]{2}$")

        /** 🆕 c39 — French + English month names + 3-letter abbrevs (FR Belgian
         *  CNI uses 'JAN FEV MAR AVR MAI JUN JUL AOU SEP OCT NOV DEC'). */
        private val MONTH_NAMES: Map<String, Int> = mapOf(
            // English full + abbrev
            "january" to 1, "jan" to 1,
            "february" to 2, "feb" to 2,
            "march" to 3, "mar" to 3,
            "april" to 4, "apr" to 4,
            "may" to 5,
            "june" to 6, "jun" to 6,
            "july" to 7, "jul" to 7,
            "august" to 8, "aug" to 8,
            "september" to 9, "sep" to 9, "sept" to 9,
            "october" to 10, "oct" to 10,
            "november" to 11, "nov" to 11,
            "december" to 12, "dec" to 12,
            // French full + abbrev (incl. CNI belge AVR/AOU/FEV)
            "janvier" to 1, "janv" to 1,
            "février" to 2, "fevrier" to 2, "fev" to 2, "févr" to 2,
            "mars" to 3,
            "avril" to 4, "avr" to 4,
            "mai" to 5,
            "juin" to 6,
            "juillet" to 7, "juil" to 7,
            "août" to 8, "aout" to 8, "aou" to 8,
            "septembre" to 9,
            "octobre" to 10,
            "novembre" to 11,
            "décembre" to 12, "decembre" to 12,
        )

        private val VALID_BLOOD_TYPES = setOf(
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "UNKNOWN",
        )
    }
}
