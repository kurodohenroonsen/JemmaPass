/*
 * PillarMetadata.kt — JEMMA Pass · Plan B · L4 v2.5.3
 *
 * Central registry of the 18 IPS (International Patient Summary) pillars
 * supported by JEMMA Pass. Each pillar has a stable key (matching the
 * Plan A `jemmaPillarFieldMap` from `js/engine/jemma_pillar_field_map.js`),
 * an emoji, an FHIR R4 resource type, the IPS code systems used to
 * encode its values, and the ordered list of editable fields with their
 * cardinality + FHIR datatype.
 *
 * 7 pillars are marked `isActive=true` (patient, contacts, allergies,
 * medications, immunizations, procedures, devices) — these are the ones
 * the app lets you edit. The remaining 11 are read-only "info" pages that
 * surface their FHIR structure for transparency while they are being
 * ported to FHIR-native editing (feat/ips-18-pillars-cleanup).
 *
 * Why a Kotlin object (rather than parsing the JS file at runtime) :
 *   1. Type-safety : R.string refs caught at compile-time
 *   2. Zero runtime parsing cost
 *   3. Easy diff in code review when fields change
 *   4. KAPT/Hilt can inject directly without bootstrap
 *
 * To add a new pillar : append a Pillar(...) entry to ALL below, and
 * add the matching string resources to strings_jemma_pillars.xml in
 * all 3 locales.
 *
 * Sync invariant : if you add a field here, also add the matching
 *   pillar_field_<key>          (the label)
 *   pillar_field_<key>_desc     (the short description)
 * in the 3 locales of strings_jemma_pillars.xml.
 */
package be.heyman.android.jemmapassdemo.pillars

import androidx.annotation.StringRes
import be.heyman.android.jemmapassdemo.R

/**
 * One editable field inside a pillar. Mirrors a FHIR element with
 * cardinality + datatype.
 */
data class PillarField(
    val key: String,
    @StringRes val labelRes: Int,
    @StringRes val descRes: Int,
    val cardinality: String,
    val fhirType: String,
)

/**
 * One IPS pillar. Holds enough metadata to render the info-only
 * `fragment_pillar_stub` screen entirely.
 */
data class Pillar(
    val key: String,
    val emoji: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    @StringRes val ipsTypeRes: Int,
    val codeSystems: List<String>,
    val fields: List<PillarField>,
    val isActive: Boolean,
)

/**
 * Singleton registry — accessed from PillarStubFragment.bindFromKey().
 */
object PillarRegistry {

    val ALL: List<Pillar> = listOf(

        // ─── 1. patient (ACTIVE) ────────────────────────────────────────────
        Pillar(
            key = "patient",
            emoji = "📝",
            titleRes = R.string.pillar_patient_title,
            descRes = R.string.pillar_patient_desc,
            ipsTypeRes = R.string.pillar_patient_ips_type,
            codeSystems = listOf("HL7 Gender", "ISO 3166", "ISO 5218"),
            isActive = true,
            fields = listOf(
                PillarField("given_name",    R.string.pillar_field_given_name,    R.string.pillar_field_given_name_desc,    "1..1", "string"),
                PillarField("family_name",   R.string.pillar_field_family_name,   R.string.pillar_field_family_name_desc,   "1..1", "string"),
                PillarField("gender",        R.string.pillar_field_gender,        R.string.pillar_field_gender_desc,        "0..1", "code"),
                PillarField("birth_date",    R.string.pillar_field_birth_date,    R.string.pillar_field_birth_date_desc,    "0..1", "date"),
                PillarField("blood_type",    R.string.pillar_field_blood_type,    R.string.pillar_field_blood_type_desc,    "0..1", "CodeableConcept"),
                PillarField("phone",         R.string.pillar_field_phone,         R.string.pillar_field_phone_desc,         "0..*", "ContactPoint"),
                PillarField("email",         R.string.pillar_field_email,         R.string.pillar_field_email_desc,         "0..*", "ContactPoint"),
                PillarField("address_line1", R.string.pillar_field_address_line1, R.string.pillar_field_address_line1_desc, "0..1", "Address"),
                PillarField("city",          R.string.pillar_field_city,          R.string.pillar_field_city_desc,          "0..1", "Address.city"),
                PillarField("country",       R.string.pillar_field_country,       R.string.pillar_field_country_desc,       "0..1", "Address.country"),
            ),
        ),

        // ─── 2. contacts (DEMO STUB — était actif jusqu'au lot 14.5c11) ─────
        // 🆕 Lot 14.5c12 — Pour la démo du concours JEMMA, contacts est
        // passé en isActive=false : la fiche d'édition n'est pas exposée et
        // le tap depuis l'écran profil ouvre la fiche stub style Vaccinations
        // (PillarStubFragment) qui décrit la structure FHIR R4 Patient.contact
        // sans permettre l'édition. Réactiver en post-démo en remettant true.
        Pillar(
            key = "contacts",
            emoji = "👥",
            titleRes = R.string.pillar_contacts_title,
            descRes = R.string.pillar_contacts_desc,
            ipsTypeRes = R.string.pillar_contacts_ips_type,
            codeSystems = listOf("HL7 v3 RoleCode"),
            isActive = false,
            fields = listOf(
                PillarField("contact_name",  R.string.pillar_field_contact_name,  R.string.pillar_field_contact_name_desc,  "1..1", "HumanName"),
                PillarField("relationship",  R.string.pillar_field_relationship,  R.string.pillar_field_relationship_desc,  "0..1", "CodeableConcept"),
                PillarField("gender",        R.string.pillar_field_gender,        R.string.pillar_field_gender_desc,        "0..1", "code"),
                PillarField("phone_mobile",  R.string.pillar_field_phone_mobile,  R.string.pillar_field_phone_mobile_desc,  "0..1", "ContactPoint"),
                PillarField("phone_work",    R.string.pillar_field_phone_work,    R.string.pillar_field_phone_work_desc,    "0..1", "ContactPoint"),
            ),
        ),

        // ─── 3. allergies (ACTIVE) ──────────────────────────────────────────
        Pillar(
            key = "allergies",
            emoji = "⚠️",
            titleRes = R.string.pillar_allergies_title,
            descRes = R.string.pillar_allergies_desc,
            ipsTypeRes = R.string.pillar_allergies_ips_type,
            codeSystems = listOf("SNOMED CT", "RxNorm", "UNII"),
            isActive = true,
            fields = listOf(
                PillarField("substance",       R.string.pillar_field_substance,       R.string.pillar_field_substance_desc,       "1..1", "CodeableConcept"),
                PillarField("criticality",     R.string.pillar_field_criticality,     R.string.pillar_field_criticality_desc,     "0..1", "code"),
                PillarField("clinical_status", R.string.pillar_field_clinical_status, R.string.pillar_field_clinical_status_desc, "0..1", "CodeableConcept"),
                PillarField("manifestation",   R.string.pillar_field_manifestation,   R.string.pillar_field_manifestation_desc,   "0..*", "CodeableConcept"),
                PillarField("onsetDate",       R.string.pillar_field_onset_date,      R.string.pillar_field_onset_date_desc,      "0..1", "dateTime"),
            ),
        ),

        // ─── 4. medications (ACTIVE) ────────────────────────────────────────
        Pillar(
            key = "medications",
            emoji = "💊",
            titleRes = R.string.pillar_medications_title,
            descRes = R.string.pillar_medications_desc,
            ipsTypeRes = R.string.pillar_medications_ips_type,
            codeSystems = listOf("ATC", "RxNorm", "SNOMED CT"),
            isActive = true,
            fields = listOf(
                PillarField("medication", R.string.pillar_field_medication, R.string.pillar_field_medication_desc, "1..1", "CodeableConcept"),
                PillarField("dose_value", R.string.pillar_field_dose_value, R.string.pillar_field_dose_value_desc, "0..1", "decimal"),
                PillarField("dose_unit",  R.string.pillar_field_dose_unit,  R.string.pillar_field_dose_unit_desc,  "0..1", "code (UCUM)"),
                PillarField("timing",     R.string.pillar_field_timing,     R.string.pillar_field_timing_desc,     "0..1", "Timing"),
                PillarField("route",      R.string.pillar_field_route,      R.string.pillar_field_route_desc,      "0..1", "CodeableConcept"),
                PillarField("reason",     R.string.pillar_field_reason,     R.string.pillar_field_reason_desc,     "0..1", "CodeableConcept"),
            ),
        ),

        // ─── 5. conditions (passive) ────────────────────────────────────────
        Pillar(
            key = "conditions",
            emoji = "🩺",
            titleRes = R.string.pillar_conditions_title,
            descRes = R.string.pillar_conditions_desc,
            ipsTypeRes = R.string.pillar_conditions_ips_type,
            codeSystems = listOf("SNOMED CT", "ICD-10", "ICD-11"),
            isActive = false,
            fields = listOf(
                PillarField("condition",       R.string.pillar_field_condition,       R.string.pillar_field_condition_desc,       "1..1", "CodeableConcept"),
                PillarField("severity",        R.string.pillar_field_severity,        R.string.pillar_field_severity_desc,        "0..1", "CodeableConcept"),
                PillarField("clinical_status", R.string.pillar_field_clinical_status, R.string.pillar_field_clinical_status_desc, "0..1", "CodeableConcept"),
                PillarField("verif_status",    R.string.pillar_field_verif_status,    R.string.pillar_field_verif_status_desc,    "0..1", "CodeableConcept"),
                PillarField("onsetDate",       R.string.pillar_field_onset_date,      R.string.pillar_field_onset_date_desc,      "0..1", "dateTime"),
            ),
        ),

        // ─── 6. pastProblems (passive) ──────────────────────────────────────
        Pillar(
            key = "pastProblems",
            emoji = "📜",
            titleRes = R.string.pillar_past_problems_title,
            descRes = R.string.pillar_past_problems_desc,
            ipsTypeRes = R.string.pillar_past_problems_ips_type,
            codeSystems = listOf("SNOMED CT", "ICD-10"),
            isActive = false,
            fields = listOf(
                PillarField("condition",       R.string.pillar_field_condition,       R.string.pillar_field_condition_desc,       "1..1", "CodeableConcept"),
                PillarField("severity",        R.string.pillar_field_severity,        R.string.pillar_field_severity_desc,        "0..1", "CodeableConcept"),
                PillarField("clinical_status", R.string.pillar_field_clinical_status, R.string.pillar_field_clinical_status_desc, "0..1", "CodeableConcept"),
                PillarField("onsetDate",       R.string.pillar_field_onset_date,      R.string.pillar_field_onset_date_desc,      "0..1", "dateTime"),
            ),
        ),

        // ─── 7. immunizations (ACTIVE — FHIR-native, feat/ips-18-pillars-cleanup) ──
        Pillar(
            key = "immunizations",
            emoji = "💉",
            titleRes = R.string.pillar_immunizations_title,
            descRes = R.string.pillar_immunizations_desc,
            ipsTypeRes = R.string.pillar_immunizations_ips_type,
            codeSystems = listOf("SNOMED CT", "CVX"),
            isActive = true,
            fields = listOf(
                PillarField("vaccine",      R.string.pillar_field_vaccine,      R.string.pillar_field_vaccine_desc,      "1..1", "CodeableConcept"),
                PillarField("date",         R.string.pillar_field_date,         R.string.pillar_field_date_desc,         "1..1", "dateTime | string"),
                PillarField("lot_number",   R.string.pillar_field_lot_number,   R.string.pillar_field_lot_number_desc,   "0..1", "string"),
                PillarField("manufacturer", R.string.pillar_field_manufacturer, R.string.pillar_field_manufacturer_desc, "0..1", "Reference(Organization)"),
                PillarField("site",         R.string.pillar_field_site,         R.string.pillar_field_site_desc,         "0..1", "CodeableConcept"),
                PillarField("route",        R.string.pillar_field_route,        R.string.pillar_field_route_desc,        "0..1", "CodeableConcept"),
            ),
        ),

        // ─── 8. procedures (ACTIVE — FHIR-native, sprint 2) ────────────────
        Pillar(
            key = "procedures",
            emoji = "🏥",
            titleRes = R.string.pillar_procedures_title,
            descRes = R.string.pillar_procedures_desc,
            ipsTypeRes = R.string.pillar_procedures_ips_type,
            codeSystems = listOf("SNOMED CT"),
            isActive = true,
            fields = listOf(
                PillarField("procedure", R.string.pillar_field_procedure, R.string.pillar_field_procedure_desc, "1..1", "CodeableConcept"),
                PillarField("date",      R.string.pillar_field_date,      R.string.pillar_field_date_desc,      "1..1", "dateTime | string"),
                PillarField("status",    R.string.pillar_field_status,    R.string.pillar_field_status_desc,    "1..1", "code"),
                PillarField("body_site", R.string.pillar_field_body_site, R.string.pillar_field_body_site_desc, "0..1", "CodeableConcept"),
                PillarField("outcome",   R.string.pillar_field_outcome,   R.string.pillar_field_outcome_desc,   "0..1", "CodeableConcept"),
            ),
        ),

        // ─── 9. devices (ACTIVE — FHIR-native Device + DeviceUseStatement, sprint 2) ──
        Pillar(
            key = "devices",
            emoji = "📟",
            titleRes = R.string.pillar_devices_title,
            descRes = R.string.pillar_devices_desc,
            ipsTypeRes = R.string.pillar_devices_ips_type,
            codeSystems = listOf("SNOMED CT", "UDI (GS1 / HIBCC / ICCBBA)"),
            isActive = true,
            fields = listOf(
                PillarField("device",    R.string.pillar_field_device,    R.string.pillar_field_device_desc,    "1..1", "CodeableConcept"),
                PillarField("date",      R.string.pillar_field_date,      R.string.pillar_field_date_desc,      "0..1", "dateTime"),
                PillarField("status",    R.string.pillar_field_status,    R.string.pillar_field_status_desc,    "0..1", "code"),
                PillarField("body_site", R.string.pillar_field_body_site, R.string.pillar_field_body_site_desc, "0..1", "CodeableConcept"),
            ),
        ),

        // ─── 10. functional (passive) ───────────────────────────────────────
        Pillar(
            key = "functional",
            emoji = "♿",
            titleRes = R.string.pillar_functional_title,
            descRes = R.string.pillar_functional_desc,
            ipsTypeRes = R.string.pillar_functional_ips_type,
            codeSystems = listOf("SNOMED CT", "LOINC", "ICF"),
            isActive = false,
            fields = listOf(
                PillarField("impairment",      R.string.pillar_field_impairment,      R.string.pillar_field_impairment_desc,      "1..1", "CodeableConcept"),
                PillarField("severity",        R.string.pillar_field_severity,        R.string.pillar_field_severity_desc,        "0..1", "CodeableConcept"),
                PillarField("clinical_status", R.string.pillar_field_clinical_status, R.string.pillar_field_clinical_status_desc, "0..1", "CodeableConcept"),
            ),
        ),

        // ─── 11. pregnancy (passive) ────────────────────────────────────────
        Pillar(
            key = "pregnancy",
            emoji = "🤰",
            titleRes = R.string.pillar_pregnancy_title,
            descRes = R.string.pillar_pregnancy_desc,
            ipsTypeRes = R.string.pillar_pregnancy_ips_type,
            codeSystems = listOf("LOINC", "SNOMED CT"),
            isActive = false,
            fields = listOf(
                PillarField("status", R.string.pillar_field_status_preg, R.string.pillar_field_status_preg_desc, "1..1", "CodeableConcept"),
                PillarField("date",   R.string.pillar_field_date,        R.string.pillar_field_date_desc,        "0..1", "dateTime"),
            ),
        ),

        // ─── 12. results (passive) ──────────────────────────────────────────
        Pillar(
            key = "results",
            emoji = "🧪",
            titleRes = R.string.pillar_results_title,
            descRes = R.string.pillar_results_desc,
            ipsTypeRes = R.string.pillar_results_ips_type,
            codeSystems = listOf("LOINC", "UCUM", "SNOMED CT"),
            isActive = false,
            fields = listOf(
                PillarField("loinc",          R.string.pillar_field_loinc,          R.string.pillar_field_loinc_desc,          "1..1", "Coding (LOINC)"),
                PillarField("value",          R.string.pillar_field_value,          R.string.pillar_field_value_desc,          "0..1", "Quantity"),
                PillarField("unit",           R.string.pillar_field_unit,           R.string.pillar_field_unit_desc,           "0..1", "code (UCUM)"),
                PillarField("interpretation", R.string.pillar_field_interpretation, R.string.pillar_field_interpretation_desc, "0..1", "CodeableConcept"),
                PillarField("date",           R.string.pillar_field_date,           R.string.pillar_field_date_desc,           "0..1", "dateTime"),
                PillarField("status",         R.string.pillar_field_status,         R.string.pillar_field_status_desc,         "1..1", "code"),
            ),
        ),

        // ─── 13. advanceDirectives (passive) ────────────────────────────────
        Pillar(
            key = "advanceDirectives",
            emoji = "📜",
            titleRes = R.string.pillar_advance_title,
            descRes = R.string.pillar_advance_desc,
            ipsTypeRes = R.string.pillar_advance_ips_type,
            codeSystems = listOf("LOINC", "SNOMED CT"),
            isActive = false,
            fields = listOf(
                PillarField("category",   R.string.pillar_field_category,   R.string.pillar_field_category_desc,   "1..1", "CodeableConcept"),
                PillarField("status_adv", R.string.pillar_field_status_adv, R.string.pillar_field_status_adv_desc, "0..1", "code"),
                PillarField("cpr",        R.string.pillar_field_cpr,        R.string.pillar_field_cpr_desc,        "0..1", "boolean"),
                PillarField("comfort",    R.string.pillar_field_comfort,    R.string.pillar_field_comfort_desc,    "0..1", "boolean"),
                PillarField("nutrition",  R.string.pillar_field_nutrition,  R.string.pillar_field_nutrition_desc,  "0..1", "boolean"),
            ),
        ),

        // ─── 14. consents (passive) ─────────────────────────────────────────
        Pillar(
            key = "consents",
            emoji = "✍️",
            titleRes = R.string.pillar_consents_title,
            descRes = R.string.pillar_consents_desc,
            ipsTypeRes = R.string.pillar_consents_ips_type,
            codeSystems = listOf("LOINC", "SNOMED CT"),
            isActive = false,
            fields = listOf(
                PillarField("category", R.string.pillar_field_category, R.string.pillar_field_category_desc, "1..1", "CodeableConcept"),
                PillarField("intent",   R.string.pillar_field_intent,   R.string.pillar_field_intent_desc,   "1..1", "code"),
            ),
        ),

        // ─── 15. goals (passive) ────────────────────────────────────────────
        Pillar(
            key = "goals",
            emoji = "🎯",
            titleRes = R.string.pillar_goals_title,
            descRes = R.string.pillar_goals_desc,
            ipsTypeRes = R.string.pillar_goals_ips_type,
            codeSystems = listOf("SNOMED CT", "LOINC"),
            isActive = false,
            fields = listOf(
                PillarField("description", R.string.pillar_field_description, R.string.pillar_field_description_desc, "1..1", "string"),
                PillarField("status",      R.string.pillar_field_status,      R.string.pillar_field_status_desc,      "1..1", "code"),
                PillarField("priority",    R.string.pillar_field_priority,    R.string.pillar_field_priority_desc,    "0..1", "CodeableConcept"),
                PillarField("targetDate",  R.string.pillar_field_target_date, R.string.pillar_field_target_date_desc, "0..1", "date"),
            ),
        ),

        // ─── 16. encounters (passive) ───────────────────────────────────────
        Pillar(
            key = "encounters",
            emoji = "🚑",
            titleRes = R.string.pillar_encounters_title,
            descRes = R.string.pillar_encounters_desc,
            ipsTypeRes = R.string.pillar_encounters_ips_type,
            codeSystems = listOf("SNOMED CT", "ICD-10"),
            isActive = false,
            fields = listOf(
                PillarField("type",      R.string.pillar_field_type,       R.string.pillar_field_type_desc,       "1..1", "CodeableConcept"),
                PillarField("onsetDate", R.string.pillar_field_onset_date, R.string.pillar_field_onset_date_desc, "0..1", "dateTime"),
                PillarField("reason",    R.string.pillar_field_reason,     R.string.pillar_field_reason_desc,     "0..1", "CodeableConcept"),
            ),
        ),

        // ─── 17. occupational (passive) ─────────────────────────────────────
        Pillar(
            key = "occupational",
            emoji = "💼",
            titleRes = R.string.pillar_occupational_title,
            descRes = R.string.pillar_occupational_desc,
            ipsTypeRes = R.string.pillar_occupational_ips_type,
            codeSystems = listOf("ISCO-08", "NAICS"),
            isActive = false,
            fields = listOf(
                PillarField("employer",  R.string.pillar_field_employer,  R.string.pillar_field_employer_desc,  "0..1", "string"),
                PillarField("job_title", R.string.pillar_field_job_title, R.string.pillar_field_job_title_desc, "0..1", "string"),
            ),
        ),

        // ─── 18. providers (passive) ────────────────────────────────────────
        Pillar(
            key = "providers",
            emoji = "👨‍⚕️",
            titleRes = R.string.pillar_providers_title,
            descRes = R.string.pillar_providers_desc,
            ipsTypeRes = R.string.pillar_providers_ips_type,
            codeSystems = listOf("HL7 ProviderRole", "FHIR Practitioner"),
            isActive = false,
            fields = listOf(
                PillarField("practitioner", R.string.pillar_field_practitioner, R.string.pillar_field_practitioner_desc, "1..1", "Reference(Practitioner)"),
                PillarField("organization", R.string.pillar_field_organization, R.string.pillar_field_organization_desc, "0..1", "Reference(Organization)"),
                PillarField("phone",        R.string.pillar_field_phone,        R.string.pillar_field_phone_desc,        "0..1", "ContactPoint"),
            ),
        ),
    )

    /** Lookup by key. Returns null if no pillar has that key. */
    fun get(key: String): Pillar? = ALL.find { it.key == key }

    /** Convenience for the rich-card grid : the 4 active pillars. */
    val ACTIVE: List<Pillar> get() = ALL.filter { it.isActive }

    /** Convenience for the rich-card grid : the 14 passive pillars. */
    val PASSIVE: List<Pillar> get() = ALL.filter { !it.isActive }
}
