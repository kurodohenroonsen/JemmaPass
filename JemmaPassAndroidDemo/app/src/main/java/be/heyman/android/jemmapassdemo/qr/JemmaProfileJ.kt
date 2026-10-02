/*
 * JemmaProfileJ.kt — v2.3.0a
 *
 * Data classes Kotlin du schema `_j 1.2` SHORT format émis par
 * jemma_profiles_manager.js (cf. buildMinimal_j() et _migrateLongToShort()).
 *
 * On lit ces objets depuis Moshi qui gère :
 *   • les missing fields via les defaults Kotlin (emptyList(), null, "")
 *     → équivalent du smartReHydrate côté JS
 *   • les empty arrays (les top-level arrays sont déjà initialisés à []
 *     dans le canonical buildMinimal_j, mais le JS les drop pour
 *     gagner des bytes — Moshi les remet à emptyList() automatiquement)
 *
 * Le mapping JSON→Kotlin utilise `@Json(name = "...")` parce que les
 * field names du JS sont des codes courts (`gn`, `fn`, `gs`, etc.) qu'on
 * ne veut pas garder en Kotlin pour la lisibilité du code consommateur.
 *
 * Référence canonique du schema (cf. buildMinimal_j) :
 *
 *   {
 *     _j: '1.2',
 *     p: { gn, fn, gs:'M'|'F'|'O'|'U', bd:'YYYY-MM-DD', nat, bt, ct:[] },
 *     al: [{ c, s:'H'|'L'|'U', st:'A'|'I'|'R', d, m, d_display }],
 *     md: [{ c, t, r:'O'|'I'|'T'|'S', v, u, rs, rc, d_display }],
 *     cn: [], ph: [], im: [], pr: [], dv: [], fs: [], pg: [], rs: [],
 *     ad: [], cs: [], gl: [], en: [], oc: [], pv: []
 *   }
 *
 * Note : `_j` est un keyword underscore en Moshi. On l'expose en Kotlin
 * sous `j` avec `@Json(name = "_j")`.
 */
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.R
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Profile complet `_j 1.2` SHORT format. Reflète ce que émet
 * jemma_profiles_manager.js#buildMinimal_j() côté HTML JEMMA.
 */
@JsonClass(generateAdapter = false)
data class JemmaProfileJ(
    /** Schema version — doit être "1.2". Renommé `j` car `_j` est cassé en Kotlin. */
    @Json(name = "_j") val j: String? = null,

    /** Patient demographics. Optional pour résilience aux QR malformés. */
    @Json(name = "p") val p: JPatient? = null,

    /** Allergies. */
    @Json(name = "al") val al: List<JAllergy> = emptyList(),

    /** Medications. */
    @Json(name = "md") val md: List<JMedication> = emptyList(),

    /** Conditions. */
    @Json(name = "cn") val cn: List<JCondition> = emptyList(),

    /** Past problems / history of past illness (`pastProblems` pillar). */
    @Json(name = "ph") val ph: List<JEntryGeneric> = emptyList(),

    /** Immunisations. */
    @Json(name = "im") val im: List<JEntryGeneric> = emptyList(),

    /** Procedures — FHIR-native since sprint 2 (projection of `Procedure` resources). */
    @Json(name = "pr") val pr: List<JEntryGeneric> = emptyList(),

    /** Devices (pacemaker, prothèses, etc.) — FHIR-native since sprint 2 (projection of `DeviceUseStatement`+`Device`). */
    @Json(name = "dv") val dv: List<JEntryGeneric> = emptyList(),

    /** Functional status. */
    @Json(name = "fs") val fs: List<JEntryGeneric> = emptyList(),

    /** Pregnancy / gynaecology specific. */
    @Json(name = "pg") val pg: List<JEntryGeneric> = emptyList(),

    /** Results (lab tests). */
    @Json(name = "rs") val rs: List<JEntryGeneric> = emptyList(),

    /** Advance directives. */
    @Json(name = "ad") val ad: List<JEntryGeneric> = emptyList(),

    /** Care services. */
    @Json(name = "cs") val cs: List<JEntryGeneric> = emptyList(),

    /** Goals. */
    @Json(name = "gl") val gl: List<JEntryGeneric> = emptyList(),

    /** Encounters. */
    @Json(name = "en") val en: List<JEntryGeneric> = emptyList(),

    /** Occupations. */
    @Json(name = "oc") val oc: List<JEntryGeneric> = emptyList(),

    /** Provenance / signature trail. */
    @Json(name = "pv") val pv: List<JEntryGeneric> = emptyList(),

    /**
     * Session id / unique ID stamp. Présent depuis L44.16.47, optionnel.
     * Permet à profiles_manager d'éviter les doubles imports.
     */
    @Json(name = "sid") val sid: String? = null,
)

/**
 * Patient demographics (`p` in `_j`).
 */
@JsonClass(generateAdapter = false)
data class JPatient(
    /** Given name (prénom). */
    @Json(name = "gn") val gn: String? = null,
    /** Family name (nom). */
    @Json(name = "fn") val fn: String? = null,
    /** Gender short : 'M' | 'F' | 'O' | 'U'. */
    @Json(name = "gs") val gs: String? = null,
    /** Birth date ISO YYYY-MM-DD. Approx (Day-1) si dérivé de l'âge. */
    @Json(name = "bd") val bd: String? = null,
    /** Nationality / country code. */
    @Json(name = "nat") val nat: String? = null,
    /** Blood type (A+, O-, etc.). */
    @Json(name = "bt") val bt: String? = null,
    // ─── 🆕 v2.6.0g — IPS Patient must-support fields ────────────────
    /**
     * Postal address (text libre multi-ligne).
     * Maps to FHIR Patient.address.text (IPS must-support 0..*).
     * Format libre — par exemple "Rue de la Paix 12 / 5660 Couvin / Belgique".
     */
    @Json(name = "adr") val adr: String? = null,
    /**
     * Patient's own phone (distinct des contacts d'urgence dans ct[]).
     * Maps to FHIR Patient.telecom[system=phone] (IPS must-support 0..*).
     */
    @Json(name = "tel") val tel: String? = null,
    /**
     * Patient's own email.
     * Maps to FHIR Patient.telecom[system=email] (IPS must-support 0..*).
     */
    @Json(name = "eml") val eml: String? = null,
    /**
     * National ID number (carte d'identité, sécurité sociale, etc.).
     * Maps to FHIR Patient.identifier (IPS must-support 0..*).
     * Format libre — pas de système typé dans cette v1 (futur : ajouter `idn_sys`).
     */
    @Json(name = "idn") val idn: String? = null,
    // ────────────────────────────────────────────────────────────────
    // 🆕 v2.6.0h — IPS-FULL structured lists (preferred over LEGACY single-field)
    /** FHIR Patient.identifier (structured list, ex: BE-NRN + Passport + EU-EHIC). */
    @Json(name = "ids") val ids: List<JIdentifier> = emptyList(),
    /** FHIR Patient.address (structured list, ex: home + work). */
    @Json(name = "adrs") val adrs: List<JAddress> = emptyList(),
    /** FHIR Patient.telecom (structured list, ex: mobile + work phone + home email). */
    @Json(name = "tels") val tels: List<JTelecom> = emptyList(),
    /** FHIR Patient.generalPractitioner (free text or KB doctor code). */
    @Json(name = "gp") val gp: String? = null,
    /** FHIR Patient.communication.language BCP-47 (e.g. "fr-BE", "ja-JP", "en-US"). */
    @Json(name = "lang") val lang: String? = null,
    // ────────────────────────────────────────────────────────────────
    /** Contacts (urgence + médecin traitant typiquement). */
    @Json(name = "ct") val ct: List<JContact> = emptyList(),
)

/**
 * Contact entry (`p.ct[]`).
 */
@JsonClass(generateAdapter = false)
data class JContact(
    /** Name. */
    @Json(name = "n") val n: String? = null,
    /** Relation (e.g. "spouse", "doctor"). */
    @Json(name = "r") val r: String? = null,
    /** Phone. */
    @Json(name = "p") val p: String? = null,
    /** Email. */
    @Json(name = "e") val e: String? = null,
    /** Address (libre, adresse postale). */
    @Json(name = "adr") val adr: String? = null,
)

// ──────────────────────────────────────────────────────────────────
// 🆕 v2.6.0h — IPS-FULL structured sub-models
// ──────────────────────────────────────────────────────────────────

/**
 * Patient.identifier — FHIR Identifier datatype.
 * System short codes (see IpsIdentifierSystemCatalog) :
 *   BE-NRN | JP-MyNumber | FR-NSS | EU-EHIC | PASSPORT | OTHER
 */
@JsonClass(generateAdapter = false)
data class JIdentifier(
    /** Identifier system short-code or URI. */
    @Json(name = "s") val system: String? = null,
    /** Identifier value (numéro). */
    @Json(name = "v") val value: String? = null,
)

/**
 * Patient.address — FHIR Address datatype.
 * `use` short codes (see IpsAddressUseCatalog) :
 *   home | work | temp | old | billing
 */
@JsonClass(generateAdapter = false)
data class JAddress(
    /** Address use : home | work | temp | old | billing. */
    @Json(name = "u") val use: String? = null,
    /** Street line (peut contenir plusieurs lignes séparées par \n). */
    @Json(name = "l") val line: String? = null,
    /** City. */
    @Json(name = "c") val city: String? = null,
    /** Postal code. */
    @Json(name = "z") val postalCode: String? = null,
    /** State / region (optionnel). */
    @Json(name = "st") val state: String? = null,
    /** Country ISO-3166-1 alpha-2. */
    @Json(name = "o") val country: String? = null,
)

/**
 * Patient.telecom — FHIR ContactPoint datatype.
 * `system` short codes (see IpsTelecomSystemCatalog) :
 *   phone | email | sms | fax | url | other
 * `use` short codes (see IpsTelecomUseCatalog) :
 *   home | work | temp | old | mobile
 */
@JsonClass(generateAdapter = false)
data class JTelecom(
    /** Telecom system : phone | email | sms | fax | url | other. */
    @Json(name = "s") val system: String? = null,
    /** Value (numéro / adresse). */
    @Json(name = "v") val value: String? = null,
    /** Telecom use : home | work | temp | old | mobile. */
    @Json(name = "u") val use: String? = null,
)

/**
 * AllergyIntolerance.reaction.* — FHIR Reaction backbone.
 * Manifestation code typically SNOMED via KB (KbConditionPicker).
 * Severity codes (see IpsReactionSeverityCatalog) : mild | moderate | severe.
 */
@JsonClass(generateAdapter = false)
data class JReaction(
    /** Manifestation code (SNOMED CT from KB). */
    @Json(name = "m") val manifestationCode: String? = null,
    /** Manifestation display denormalized (rendu rapide). */
    @Json(name = "md") val manifestationDisplay: String? = null,
    /** Manifestation code system URI. */
    @Json(name = "ms") val manifestationSystem: String? = null,
    /** Severity : mild | moderate | severe. */
    @Json(name = "sv") val severity: String? = null,
)

/**
 * Allergy entry (`al[]`).
 */
@JsonClass(generateAdapter = false)
data class JAllergy(
    /** Code (RxNorm / SNOMED CT / ATC). */
    @Json(name = "c") val c: String? = null,
    /** Criticality : 'H' (high) | 'L' (low) | 'U' (unknown). */
    @Json(name = "s") val s: String? = null,
    /** Status : 'A' (active) | 'I' (inactive) | 'R' (resolved). */
    @Json(name = "st") val st: String? = null,
    /** Details / notes. */
    @Json(name = "d") val d: String? = null,
    /** Mechanism (e.g. "IgE-mediated"). */
    @Json(name = "m") val m: String? = null,
    /** Denormalized display label, ajouté par le wizard JEMMA pour rendu rapide. */
    @Json(name = "d_display") val displayLabel: String? = null,
    // 🆕 v2.6.0h — IPS-FULL
    /** Code system URI (e.g. "http://snomed.info/sct"). */
    @Json(name = "cs") val codeSystem: String? = null,
    /** IPS type : "allergy" | "intolerance". */
    @Json(name = "tp") val type: String? = null,
    /** IPS category : "food" | "medication" | "environment" | "biologic". */
    @Json(name = "cat") val category: String? = null,
    /** Onset date ISO YYYY-MM-DD (when allergy was identified). */
    @Json(name = "on") val onset: String? = null,
    /** Reactions list (manifestations + severities). */
    @Json(name = "rxns") val reactions: List<JReaction> = emptyList(),
)

/**
 * Medication entry (`md[]`).
 */
@JsonClass(generateAdapter = false)
data class JMedication(
    /** Code (RxNorm). */
    @Json(name = "c") val c: String? = null,
    /** Timing / frequency (e.g. "1x/day", "BID"). */
    @Json(name = "t") val t: String? = null,
    /** Route : 'O' (oral) | 'I' (injection) | 'T' (topical) | 'S' (subcutaneous). */
    @Json(name = "r") val r: String? = null,
    /** Dose value. */
    @Json(name = "v") val v: String? = null,
    /** Dose unit (mg, mL). */
    @Json(name = "u") val u: String? = null,
    /** Reason source. */
    @Json(name = "rs") val rs: String? = null,
    /** Reason code. */
    @Json(name = "rc") val rc: String? = null,
    /** Denormalized display label. */
    @Json(name = "d_display") val displayLabel: String? = null,
    // 🆕 v2.6.0h — IPS-FULL
    /** Code system URI (e.g. "http://www.whocc.no/atc"). */
    @Json(name = "cs") val codeSystem: String? = null,
    /**
     * IPS status (FHIR medication-statement-status) :
     *   active | completed | entered-in-error | intended | stopped | on-hold |
     *   unknown | not-taken
     */
    @Json(name = "ms") val status: String? = null,
    /**
     * IPS effective[x] — when the medication is/was taken.
     * ISO YYYY-MM-DD ou YYYY-MM-DD/YYYY-MM-DD pour Period.
     */
    @Json(name = "eff") val effective: String? = null,
    /**
     * IPS effective absence reason — utilisé si effective est null mais
     * qu'on doit toujours respecter l'IPS 1..1.
     * Valeurs : "asked-unknown" | "temp-unknown" | "not-asked" | "not-applicable" |
     *           "masked" | "unsupported" | "as-text" | "error"
     */
    @Json(name = "effar") val effectiveAbsenceReason: String? = null,
)

/**
 * Condition entry (`cn[]`).
 */
@JsonClass(generateAdapter = false)
data class JCondition(
    @Json(name = "c") val c: String? = null,
    @Json(name = "s") val s: String? = null,
    @Json(name = "st") val st: String? = null,
    @Json(name = "d") val d: String? = null,
    @Json(name = "rs") val rs: String? = null,
    @Json(name = "rc") val rc: String? = null,
    @Json(name = "d_display") val displayLabel: String? = null,
    /** Onset date (YYYY, YYYY-MM, YYYY-MM-DD) — FHIR-native problem list, sprint 5. */
    @Json(name = "dt") val date: String? = null,
    /** Code system URI when not SNOMED CT. */
    @Json(name = "cs") val codeSystem: String? = null,
)

/**
 * Generic entry pour les pillars où le détail varie peu. On parse les
 * champs minimum (code + display) et on garde le reste en raw map au
 * cas où le wizard ait stamped des champs custom — ces pillars sont
 * peu peuplés en pratique côté JEMMA HTML 1.0.
 */
@JsonClass(generateAdapter = false)
data class JEntryGeneric(
    @Json(name = "c") val c: String? = null,
    @Json(name = "d") val d: String? = null,
    @Json(name = "d_display") val displayLabel: String? = null,
    // ── Optional compact fields (all default to null → omitted from JSON;
    //    older `_j 1.2` readers ignore unknown keys). Filled by the
    //    FHIR-native pillars' projections (Immunizations first).
    /** Occurrence / performed date: YYYY, YYYY-MM or YYYY-MM-DD. */
    @Json(name = "dt") val date: String? = null,
    /** Code system URI when it is not SNOMED CT (e.g. CVX, ATC). */
    @Json(name = "cs") val codeSystem: String? = null,
    /** FHIR status when it is not the pillar default (e.g. "not-done"). */
    @Json(name = "st") val status: String? = null,
    /** Dose number in a series (immunizations). */
    @Json(name = "dn") val doseNumber: Int? = null,
    // ── Results (Observation) projection — sprint 3.
    /** Measured value: number as typed ("5.4"), or the coded / free-text result label. */
    @Json(name = "v") val value: String? = null,
    /** UCUM unit code of a numeric value ("mmol/L", "%", "10*3/uL"). */
    @Json(name = "u") val unit: String? = null,
    /** v3 ObservationInterpretation code when abnormal or explicit (H, L, HH, LL, N, A, POS, NEG). */
    @Json(name = "ip") val interpretation: String? = null,
    /** Reference range as "low-high", "≥low" or "≤high" (same unit as `u`). */
    @Json(name = "rr") val referenceRange: String? = null,
    /** Code of a coded value (e.g. SNOMED blood group) when the result is not numeric. */
    @Json(name = "vc") val valueCode: String? = null,
    /** Code system URI of the coded value when it is not SNOMED CT (e.g. LOINC answer list). */
    @Json(name = "vcs") val valueCodeSystem: String? = null,
    /** Observation category when not the pillar default (results: "laboratory"). */
    @Json(name = "ct") val category: String? = null,
    // ── Past problems (Condition) projection — sprint 4.
    /** Abatement (resolution) date: YYYY, YYYY-MM or YYYY-MM-DD. */
    @Json(name = "ab") val abatement: String? = null,
    /** Severity as the IPS LOINC answer code (LA6752-5 mild, LA6751-7 moderate, LA6750-9 severe). */
    @Json(name = "sv") val severity: String? = null,
)

// ──────────────────────────────────────────────────────────────────────
// Convenience helpers — used by the import UI for "Importer Haru-san ?" dialog
// ──────────────────────────────────────────────────────────────────────

/**
 * Display name compact pour le dialog de confirmation. Préfère
 * `gn fn` (prénom nom), tombe sur `gn` ou "Profile inconnu".
 */
fun JemmaProfileJ.displayName(): String {
    val gn = p?.gn?.takeIf { it.isNotBlank() }
    val fn = p?.fn?.takeIf { it.isNotBlank() }
    return when {
        gn != null && fn != null -> "$gn $fn"
        gn != null -> gn
        fn != null -> fn
        else -> "Profile inconnu"
    }
}

/**
 * Summary ligne pour le dialog : "🩹 2 allergies · 💊 3 médicaments · 🩺 1 condition"
 */
fun JemmaProfileJ.summaryLine(): String {
    val parts = mutableListOf<String>()
    if (al.isNotEmpty()) parts += "🩹 ${al.size} allerg${if (al.size > 1) "ies" else "ie"}"
    if (md.isNotEmpty()) parts += "💊 ${md.size} médicament${if (md.size > 1) "s" else ""}"
    if (cn.isNotEmpty()) parts += "🩺 ${cn.size} condition${if (cn.size > 1) "s" else ""}"
    if (im.isNotEmpty()) parts += "💉 ${im.size} vaccin${if (im.size > 1) "s" else ""}"
    return parts.joinToString(" · ").ifEmpty { "Aucune donnée médicale" }
}

/**
 * Localized summary line for the confirmation dialogs.
 */
fun JemmaProfileJ.summaryLine(context: android.content.Context): String {
    val parts = mutableListOf<String>()
    if (al.isNotEmpty()) {
        parts += context.getString(R.string.profile_card_allergies_count, al.size)
    }
    if (md.isNotEmpty()) {
        parts += context.getString(R.string.profile_card_medications_count, md.size)
    }
    if (cn.isNotEmpty()) {
        parts += context.getString(R.string.profile_card_conditions_count, cn.size)
    }
    if (im.isNotEmpty()) {
        val locale = context.resources.configuration.locales[0]
        val lang = locale.language.lowercase()
        val format = when (lang) {
            "ja" -> "💉 %1\$d件のワクチン"
            "fr" -> "💉 %1\$d vaccins"
            else -> "💉 %1\$d vaccinations"
        }
        parts += String.format(format, im.size)
    }
    return parts.joinToString(" · ").ifEmpty {
        val locale = context.resources.configuration.locales[0]
        val lang = locale.language.lowercase()
        when (lang) {
            "ja" -> "医療データなし"
            "fr" -> "Aucune donnée médicale"
            else -> "No medical data"
        }
    }
}
