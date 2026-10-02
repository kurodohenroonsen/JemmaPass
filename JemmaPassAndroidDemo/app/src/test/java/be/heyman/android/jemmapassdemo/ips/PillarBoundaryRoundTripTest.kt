/*
 * PillarBoundaryRoundTripTest.kt — round-trip integrity of the 11 pillars with boundary data.
 *
 *   native pillars → Bundle → IpsFhirCodec.nativeOf            (what an edit screen saves and reloads)
 *   `_j` → JSON → `_j` → Bundle → nativeOf → `_j` projection    (what a QR import stores and re-exports)
 *
 * Boundary data : very long text, emoji, Japanese, right-to-left text, quotes / line breaks,
 * dates at year / year-month / leap-day / future precision, unknown dates, duplicates,
 * zero and very large counts, codes unknown to every catalog, blank strings.
 *
 * Use cases (qa/usecases) : UC-VAC-015/016, UC-PRO-010/012/013, UC-DEV-012/014/015,
 * UC-RES-014/018/020, UC-ANT-010/012/013, UC-PRB-010/011, UC-FON-015/016, UC-GRO-010,
 * UC-PAT-010, UC-ALG-012, UC-MED-014, UC-HUM-016/017/018, UC-I18N-019, UC-FHIR-016,
 * UC-FHIR-018, UC-FHIR-019, UC-STO-005, UC-IMP-016.
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JAddress
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JContact
import be.heyman.android.jemmapassdemo.qr.JEntryGeneric
import be.heyman.android.jemmapassdemo.qr.JIdentifier
import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JReaction
import be.heyman.android.jemmapassdemo.qr.JTelecom
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PillarBoundaryRoundTripTest {

    // ── boundary strings ────────────────────────────────────────────────────
    private val longText = "Très longue note d'un aidant bavard, phrase après phrase. ".repeat(40).trim()   // > 2 000 chars
    private val emoji = "mal au ❤️ \"fort\" 🙂 👨‍👩‍👧 — 100 % sûr"
    private val japanese = "慢性腰痛・左膝 ロット番号 松山赤十字病院"
    private val rtl = "حساسية من البنسلين — محمد بن عبد الله"
    private val multiline = "ligne 1\nligne 2\tfin \\ / <b>tag</b> & 'apostrophe'"

    private val sid = "qa_boundary"

    private fun patient(bt: String? = null) = JPatient(gn = "Taro", fn = "Tanaka", gs = "M", bd = "1946-02-08", bt = bt)

    // ── boundary entries, one list per native pillar ────────────────────────

    private val immunizations = listOf(
        IpsImmunization(id = "im-b1", code = "871876003", display = japanese, date = "2022-05-17", doseNumber = 2, seriesDoses = 3,
            lotNumber = "ロット🙂-$emoji", manufacturer = rtl, performer = japanese, note = longText),
        IpsImmunization(id = "im-b2", code = null, text = rtl, date = null),
        IpsImmunization(id = "im-b3", code = "836374004", display = "Hepatitis B vaccine", date = "1985", doseNumber = 1),
        IpsImmunization(id = "im-b4", code = "836374004", display = "Hepatitis B vaccine", date = "2019-11", status = IpsImmunizationStatus.NOT_DONE),
        IpsImmunization(id = "im-b5", code = "836374004", display = "Hepatitis B vaccine", date = "2024-02-29",
            status = IpsImmunizationStatus.ENTERED_IN_ERROR, doseNumber = Int.MAX_VALUE, seriesDoses = Int.MAX_VALUE),
        IpsImmunization(id = "im-b6", code = "836374004", display = "Hepatitis B vaccine", date = "2099-12-31", note = multiline),
        // duplicate of b3 (same vaccine, same day) : a second resource, never merged
        IpsImmunization(id = "im-b7", code = "836374004", display = "Hepatitis B vaccine", date = "1985", doseNumber = 1),
        IpsImmunization(id = "im-b8", code = "ZZ-UNKNOWN-0001", system = "urn:oid:1.2.3.4.5", display = "Vaccine unknown to every catalog", date = "2001-01-01"),
        IpsImmunization(id = "im-b9", code = "836374004"),
    )

    private val procedures = listOf(
        IpsProcedure(id = "pr-b1", code = "80146002", display = "Appendectomy", date = "1995-07-12", bodySite = "左膝 🙂",
            outcome = emoji, performer = rtl, location = japanese, note = longText),
        IpsProcedure(id = "pr-b2", code = null, text = japanese, date = null, status = IpsProcedureStatus.IN_PROGRESS),
        IpsProcedure(id = "pr-b3", code = "11466000", display = "Cesarean section", date = "1975", status = IpsProcedureStatus.NOT_DONE),
        IpsProcedure(id = "pr-b4", code = "11466000", display = "Cesarean section", date = "1975-06", status = IpsProcedureStatus.STOPPED),
        IpsProcedure(id = "pr-b5", code = "11466000", display = "Cesarean section", date = "2024-02-29", status = IpsProcedureStatus.ENTERED_IN_ERROR),
        IpsProcedure(id = "pr-b6", code = "11466000", display = "Cesarean section", date = "2099-12-31", status = IpsProcedureStatus.UNKNOWN, note = multiline),
        IpsProcedure(id = "pr-b7", code = "80146002", display = "Appendectomy", date = "1995-07-12"),
        IpsProcedure(id = "pr-b8", code = "ZZ-UNKNOWN-0002", system = "urn:oid:1.2.3.4.5", display = "Procedure unknown to every catalog"),
    )

    private val devices = listOf(
        IpsDevice(id = "dv-b1", code = "14106009", display = "Cardiac pacemaker", udi = "(01)00643169007222(21)PJN1234567",
            manufacturer = japanese, model = "Azure XT DR MRI SureScan 🙂", serial = "S".repeat(200), date = "2021-03-15",
            bodySite = "左胸 🙂", note = longText),
        IpsDevice(id = "dv-b2", code = null, text = rtl, date = null),
        IpsDevice(id = "dv-b3", code = "6012004", display = "Hearing aid", date = "2019-06", status = IpsDeviceStatus.INACTIVE, bodySite = "Left ear"),
        // two identical devices (left and right hearing aid) are two entries
        IpsDevice(id = "dv-b4", code = "6012004", display = "Hearing aid", date = "2019-06", status = IpsDeviceStatus.INACTIVE, bodySite = "Left ear"),
        IpsDevice(id = "dv-b5", code = "6012004", display = "Hearing aid", date = "2019", status = IpsDeviceStatus.ENTERED_IN_ERROR),
        IpsDevice(id = "dv-b6", code = "ZZ-UNKNOWN-0003", system = "urn:oid:1.2.3.4.5", display = "Device unknown to every catalog", date = "2024-02-29", note = multiline),
        IpsDevice(id = "dv-b7", code = "14106009", display = "Cardiac pacemaker", text = "Label typed by the patient", date = "2099-12-31"),
    )

    private val results = listOf(
        IpsResult(id = "rs-b1", code = "2823-3", display = "Potassium", date = "2026-02-10", value = "5.9", unit = "mmol/L",
            interpretation = IpsResultInterpretation.CRITICAL_HIGH, refLow = "3.5", refHigh = "5.1", performer = japanese, note = longText),
        IpsResult(id = "rs-b2", code = "2345-7", display = "Glucose", date = "2026", value = "0", unit = "mg/dL", interpretation = IpsResultInterpretation.CRITICAL_LOW),
        IpsResult(id = "rs-b3", code = "11555-0", display = "Base excess", date = "2026-02", value = "-0.5", unit = "mmol/L",
            interpretation = IpsResultInterpretation.LOW, refLow = "-2", refHigh = "2"),
        IpsResult(id = "rs-b4", code = "777-3", display = "Platelets", date = "2024-02-29", value = "250000", unit = "/uL",
            interpretation = IpsResultInterpretation.NORMAL, refLow = "150000"),
        IpsResult(id = "rs-b5", code = "30341-2", display = "ESR", date = null, value = "0.001", unit = "mm/h",
            status = IpsResultStatus.PRELIMINARY, interpretation = IpsResultInterpretation.ABNORMAL, refHigh = "20"),
        IpsResult(id = "rs-b6", code = "5778-6", display = "Urine colour", date = "2099-12-31", valueCode = "371244009",
            valueCodeSystem = IpsCodeSystems.SNOMED, valueDisplay = "Yellow", status = IpsResultStatus.AMENDED, interpretation = IpsResultInterpretation.POSITIVE),
        IpsResult(id = "rs-b7", text = "Échographie abdominale 🙂", date = "2025-12-03", category = IpsResultCategory.IMAGING,
            valueText = japanese, status = IpsResultStatus.CORRECTED, interpretation = IpsResultInterpretation.NEGATIVE, note = multiline),
        IpsResult(id = "rs-b8", text = rtl, category = IpsResultCategory.PROCEDURE, valueText = emoji, status = IpsResultStatus.CANCELLED,
            interpretation = IpsResultInterpretation.HIGH),
        // duplicate of b1 (same test, same day, same value)
        IpsResult(id = "rs-b9", code = "2823-3", display = "Potassium", date = "2026-02-10", value = "5.9", unit = "mmol/L",
            interpretation = IpsResultInterpretation.CRITICAL_HIGH, refLow = "3.5", refHigh = "5.1"),
        IpsResult(id = "rs-b10", code = "ZZ-UNKNOWN-0004", system = "urn:oid:1.2.3.4.5", display = "Test unknown to every catalog",
            value = "9999999", status = IpsResultStatus.UNKNOWN),
        IpsResult(id = "rs-b11", code = "2823-3", display = "Potassium", value = "37.25", unit = "mmol/L", status = IpsResultStatus.ENTERED_IN_ERROR),
    )

    private val pastProblems = listOf(
        IpsPastProblem(id = "ph-b1", code = "22298006", display = "Myocardial infarction", onset = "2015-08-27", abatement = "2015-09",
            severity = IpsConditionSeverity.SEVERE, note = longText),
        IpsPastProblem(id = "ph-b2", code = null, text = "結核 🙂", onset = null, abatement = null, clinicalStatus = IpsPastProblemStatus.INACTIVE),
        IpsPastProblem(id = "ph-b3", code = "56717001", display = "Tuberculosis", onset = "1962", abatement = "1962", severity = IpsConditionSeverity.MILD),
        IpsPastProblem(id = "ph-b4", code = "56717001", display = "Tuberculosis", onset = "2010-05-01", abatement = "2010",
            clinicalStatus = IpsPastProblemStatus.REMISSION, severity = IpsConditionSeverity.MODERATE),
        IpsPastProblem(id = "ph-b5", code = "56717001", display = "Tuberculosis", onset = "2024-02-29", abatement = "2099-12-31", note = multiline),
        IpsPastProblem(id = "ph-b6", code = "56717001", display = "Tuberculosis", onset = "1962", abatement = "1962", severity = IpsConditionSeverity.MILD),
        IpsPastProblem(id = "ph-b7", text = rtl, abatement = "1999"),
    )

    private val problems = listOf(
        IpsProblem(id = "cn-b1", code = "84114007", display = "Heart failure", onset = "2020-11", severity = IpsConditionSeverity.MODERATE, note = longText),
        IpsProblem(id = "cn-b2", code = null, text = "慢性腰痛 🙂", clinicalStatus = IpsProblemStatus.RELAPSE),
        IpsProblem(id = "cn-b3", code = "433144002", display = "Chronic kidney disease stage 3", onset = "2022",
            clinicalStatus = IpsProblemStatus.RECURRENCE, severity = IpsConditionSeverity.SEVERE),
        IpsProblem(id = "cn-b4", code = "433144002", display = "Chronic kidney disease stage 3", onset = "2024-02-29", severity = IpsConditionSeverity.MILD),
        // same problem entered twice
        IpsProblem(id = "cn-b5", code = "84114007", display = "Heart failure", onset = "2020-11", severity = IpsConditionSeverity.MODERATE),
        IpsProblem(id = "cn-b6", text = rtl, onset = "2099-12-31", note = multiline),
        IpsProblem(id = "cn-b7", code = "ZZ-UNKNOWN-0005", system = "urn:oid:1.2.3.4.5", display = "Problem unknown to every catalog"),
    )

    private val functional = listOf(
        IpsFunctional(id = "fs-b1", code = "15188001", display = "Hearing loss", onset = "2019", note = longText),
        IpsFunctional(id = "fs-b2", text = "車椅子 🙂", onset = "2020-06", clinicalStatus = IpsFunctionalStatus.INACTIVE),
        IpsFunctional(id = "fs-b3", text = rtl, onset = "2024-02-29", clinicalStatus = IpsFunctionalStatus.RESOLVED, note = multiline),
        IpsFunctional(id = "fs-b4", code = "15188001", display = "Hearing loss", onset = "2019"),
        IpsFunctional(id = "fs-b5", text = emoji),
    )

    private val pregnancy = listOf(
        IpsPregnancyObs(id = "pg-b1", code = IpsPregnancyCodes.STATUS, valueCode = IpsPregnancyCodes.PREGNANT, date = "2026-09-20", note = japanese),
        IpsPregnancyObs(id = "pg-b2", code = IpsPregnancyCodes.STATUS, valueCode = IpsPregnancyCodes.NOT_PREGNANT, date = "2024-02-29"),
        IpsPregnancyObs(id = "pg-b3", code = IpsPregnancyCodes.STATUS, valueCode = IpsPregnancyCodes.UNKNOWN),
        IpsPregnancyObs(id = "pg-b4", code = "11778-8", valueDate = "2099-01-01", date = "2026-09-20"),
        IpsPregnancyObs(id = "pg-b5", code = "11779-6", valueDate = "2028-02-29", date = "2027"),
        IpsPregnancyObs(id = "pg-b6", code = "11780-4", valueDate = "2027-04", date = "2026-09"),
        IpsPregnancyObs(id = "pg-b7", code = "11640-0", count = 0, date = "2026-02-10"),
        IpsPregnancyObs(id = "pg-b8", code = "11636-8", count = 1),
        IpsPregnancyObs(id = "pg-b9", code = "11639-2", count = 99, note = longText),
        IpsPregnancyObs(id = "pg-b10", code = "11637-6", count = Int.MAX_VALUE),
        IpsPregnancyObs(id = "pg-b11", code = "11638-4", count = 2),
        IpsPregnancyObs(id = "pg-b12", code = "11612-9", count = 0),
        IpsPregnancyObs(id = "pg-b13", code = "11614-5", count = 3),
        IpsPregnancyObs(id = "pg-b14", code = "11613-7", count = 4),
        IpsPregnancyObs(id = "pg-b15", code = "33065-4", count = 5, date = "1990"),
    )

    private val boundary = IpsNativePillars(
        immunizations = immunizations, procedures = procedures, devices = devices, results = results,
        pastProblems = pastProblems, problems = problems, pregnancy = pregnancy, functional = functional,
    )

    private fun assertSamePillars(context: String, expected: IpsNativePillars, actual: IpsNativePillars) {
        assertEquals("$context 💉", expected.immunizations, actual.immunizations)
        assertEquals("$context 🏥", expected.procedures, actual.procedures)
        assertEquals("$context 📟", expected.devices, actual.devices)
        assertEquals("$context 🧪", expected.results, actual.results)
        assertEquals("$context 📜", expected.pastProblems, actual.pastProblems)
        assertEquals("$context 🩺", expected.problems, actual.problems)
        assertEquals("$context 🤰", expected.pregnancy, actual.pregnancy)
        assertEquals("$context ♿", expected.functional, actual.functional)
    }

    // ── native pillars → Bundle → native pillars ────────────────────────────

    @Test
    fun `UC-STO-005 boundary entries of the 8 native pillars survive the Bundle unchanged`() {
        val stored = ProfileFixtures.store(sid, JemmaProfileJ(j = "1.2", sid = sid, p = patient()), boundary)
        assertEquals("no blood group in the profile, nothing added", boundary, stored.native)

        val first = ProfileFixtures.parse(ProfileFixtures.bundleJson(stored))
        val back = IpsFhirCodec.nativeOf(first)
        assertSamePillars("first generation", boundary, back)

        // A second save from what was read gives the same document entries (no drift, no growth).
        val second = ProfileFixtures.parse(ProfileFixtures.bundleJson(ProfileFixtures.store(sid, stored.j, back)))
        assertSamePillars("second generation", boundary, IpsFhirCodec.nativeOf(second))
        assertEquals(first.entry.map { it.fullUrl?.value }, second.entry.map { it.fullUrl?.value })
    }

    @Test
    fun `UC-FHIR-018 with every pillar full each entry is listed by its own section and by no other`() {
        val bundle = ProfileFixtures.parse(JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(JemmaProfileJ(j = "1.2", sid = sid, p = patient())), boundary))
        val sections = linkedMapOf(
            IpsFhirCodec.LOINC_SECTION_IMMUNIZATIONS to immunizations.map { IpsFhirCodec.immunizationUrn(sid, it.id) },
            IpsFhirCodec.LOINC_SECTION_PROCEDURES to procedures.map { IpsFhirCodec.procedureUrn(sid, it.id) },
            IpsFhirCodec.LOINC_SECTION_DEVICES to devices.map { IpsFhirCodec.deviceUseStatementUrn(sid, it.id) },
            IpsFhirCodec.LOINC_SECTION_RESULTS to results.map { IpsFhirCodec.resultUrn(sid, it.id) },
            IpsFhirCodec.LOINC_SECTION_PAST_ILLNESS to pastProblems.map { IpsFhirCodec.pastProblemUrn(sid, it.id) },
            IpsFhirCodec.LOINC_SECTION_PROBLEMS to problems.map { IpsFhirCodec.problemUrn(sid, it.id) },
            IpsFhirCodec.LOINC_SECTION_PREGNANCY to pregnancy.map { IpsFhirCodec.pregnancyUrn(sid, it.id) },
            IpsFhirCodec.LOINC_SECTION_FUNCTIONAL to functional.map { IpsFhirCodec.functionalUrn(sid, it.id) },
        )
        val fullUrls = bundle.entry.mapNotNull { it.fullUrl?.value }
        assertEquals("duplicated content never shares a fullUrl", fullUrls.size, fullUrls.toSet().size)
        val seen = HashSet<String>()
        for ((loinc, urns) in sections) {
            assertEquals("section $loinc", urns, ProfileFixtures.sectionRefs(bundle, loinc))
            assertTrue("section $loinc resolves", fullUrls.containsAll(urns))
            for (u in urns) assertTrue("$u in one section only", seen.add(u))
        }
        // Composition + Patient + one resource per entry (two per device)
        val expectedEntries = 2 + immunizations.size + procedures.size + 2 * devices.size + results.size +
            pastProblems.size + problems.size + pregnancy.size + functional.size
        assertEquals(expectedEntries, bundle.entry.size)
    }

    // ── `_j` → JSON → `_j` → Bundle → native → `_j` ─────────────────────────

    @Test
    fun `UC-IMP-001 the projection of boundary entries re-exports identical after an import`() {
        val j = ProfileFixtures.store(sid, JemmaProfileJ(j = "1.2", sid = sid, p = patient()), boundary).j

        // hop 1 : the `_j` JSON (file, QR payload before compression)
        val reread = ProfileFixtures.adapter.fromJson(ProfileFixtures.adapter.toJson(j))!!
        assertEquals(j, reread)

        // hop 2 : an import rebuilds the native pillars from `_j` alone, stores a Bundle, reads it back
        val imported = IpsFhirCodec.nativeOf(ProfileFixtures.parse(ProfileFixtures.bundleJsonFromProjection(reread)))
        val again = ProfileFixtures.project(reread, sid, imported)
        assertEquals("💉", j.im, again.im)
        assertEquals("🏥", j.pr, again.pr)
        assertEquals("📟", j.dv, again.dv)
        assertEquals("🧪", j.rs, again.rs)
        assertEquals("📜", j.ph, again.ph)
        assertEquals("🩺", j.cn, again.cn)
        assertEquals("🤰", j.pg, again.pg)
        assertEquals("♿", j.fs, again.fs)

        // hop 3 : importing the same payload again is deterministic (same ids, UC-IMP-007)
        assertEquals(imported, IpsFhirCodec.nativeOf(ProfileFixtures.parse(ProfileFixtures.bundleJsonFromProjection(reread))))
        val ids = imported.immunizations.map { it.id } + imported.procedures.map { it.id } + imported.devices.map { it.id } +
            imported.results.map { it.id } + imported.pastProblems.map { it.id } + imported.problems.map { it.id } +
            imported.pregnancy.map { it.id } + imported.functional.map { it.id }
        assertEquals("duplicated entries keep distinct ids after an import", ids.size, ids.toSet().size)
    }

    @Test
    fun `UC-IMP-016 unknown codes and unknown statuses from a payload never crash and fall back to the pillar default`() {
        val odd = JEntryGeneric(c = "ZZ-999", displayLabel = "Unknown thing", date = "not a date", status = "xyz",
            codeSystem = "urn:oid:9.9.9", interpretation = "??", category = "nope", severity = "bad", doseNumber = 0)
        val j = JemmaProfileJ(
            j = "1.2", sid = sid, p = patient(),
            im = listOf(odd), pr = listOf(odd), dv = listOf(odd), rs = listOf(odd.copy(value = "abc", unit = "mg")),
            ph = listOf(odd), fs = listOf(odd), pg = listOf(odd, odd.copy(c = "11636-8", value = "many")),
            cn = listOf(be.heyman.android.jemmapassdemo.qr.JCondition(c = "ZZ-999", st = "xyz", s = "weird", displayLabel = "Unknown thing", date = "not a date")),
        )
        val back = IpsFhirCodec.nativeOf(ProfileFixtures.parse(ProfileFixtures.bundleJsonFromProjection(j)))
        // the entry is kept with its code and label ; only what could not be understood is defaulted
        assertEquals("ZZ-999", back.immunizations.single().code)
        assertEquals("Unknown thing", back.immunizations.single().display)
        assertEquals("urn:oid:9.9.9", back.immunizations.single().system)
        assertEquals(IpsImmunizationStatus.COMPLETED, back.immunizations.single().status)
        assertNull("an unreadable date is unknown, not another date", back.immunizations.single().date)
        assertNull("0 is not a dose number", back.immunizations.single().doseNumber)
        assertEquals(IpsProcedureStatus.COMPLETED, back.procedures.single().status)
        assertEquals(IpsDeviceStatus.ACTIVE, back.devices.single().status)
        val result = back.results.single()
        assertEquals(IpsResultStatus.FINAL, result.status)
        assertEquals(IpsResultCategory.LABORATORY, result.category)
        assertNull(result.interpretation)
        assertEquals("a non numeric value stays readable as text", "abc", result.valueText)
        assertEquals(IpsPastProblemStatus.RESOLVED, back.pastProblems.single().clinicalStatus)
        assertNull(back.pastProblems.single().severity)
        assertEquals(IpsFunctionalStatus.ACTIVE, back.functional.single().clinicalStatus)
        assertEquals(IpsProblemStatus.ACTIVE, back.problems.single().clinicalStatus)
        assertNull(back.problems.single().severity)
        // 🤰 a code outside the pregnancy value sets is not an obstetric observation (UC-GRO-019)
        assertEquals(listOf("11636-8"), back.pregnancy.map { it.code })
        assertNull(back.pregnancy.single().count)
    }

    // ── single-field boundaries ─────────────────────────────────────────────

    @Test
    fun `UC-HUM-018 blank strings are never stored as empty values`() {
        val blank = IpsNativePillars(
            immunizations = listOf(IpsImmunization(id = "im-e", code = "836374004", display = "", text = " ", date = "",
                lotNumber = "", manufacturer = "  ", performer = "", note = "\t")),
            procedures = listOf(IpsProcedure(id = "pr-e", code = "11466000", display = "", text = "", date = " ", bodySite = "",
                outcome = " ", performer = "", location = "", note = "")),
            devices = listOf(IpsDevice(id = "dv-e", code = "6012004", display = "", text = "", udi = "", manufacturer = " ", model = "",
                serial = "", date = "", bodySite = "", note = "")),
            results = listOf(IpsResult(id = "rs-e", code = "2823-3", display = "", text = "", date = "", value = "", unit = "",
                valueCode = "", valueDisplay = "", valueText = " ", interpretation = "", refLow = "", refHigh = " ", performer = "", note = "")),
            pastProblems = listOf(IpsPastProblem(id = "ph-e", code = "56717001", display = "", text = "", onset = "", abatement = " ", severity = "", note = "")),
            problems = listOf(IpsProblem(id = "cn-e", code = "84114007", display = "", text = "", onset = "", severity = "", note = " ")),
            functional = listOf(IpsFunctional(id = "fs-e", code = "15188001", display = "", text = "", onset = "", note = "")),
            pregnancy = listOf(IpsPregnancyObs(id = "pg-e", code = "11636-8", valueCode = "", valueDate = "", count = null, date = "", note = " ")),
        )
        val json = JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(JemmaProfileJ(j = "1.2", sid = sid, p = patient())), blank)
        assertFalse("no empty FHIR primitive in the document", json.contains(": \"\""))
        assertFalse(json.contains(": \" \""))

        val back = IpsFhirCodec.nativeOf(ProfileFixtures.parse(json))
        val im = back.immunizations.single()
        assertEquals("836374004", im.code)
        assertNull(im.text); assertNull(im.date); assertNull(im.lotNumber); assertNull(im.manufacturer); assertNull(im.performer); assertNull(im.note)
        val pr = back.procedures.single()
        assertNull(pr.date); assertNull(pr.bodySite); assertNull(pr.outcome); assertNull(pr.performer); assertNull(pr.location); assertNull(pr.note)
        val dv = back.devices.single()
        assertNull(dv.udi); assertNull(dv.manufacturer); assertNull(dv.model); assertNull(dv.serial); assertNull(dv.date); assertNull(dv.bodySite); assertNull(dv.note)
        val rs = back.results.single()
        assertNull(rs.date); assertNull(rs.value); assertNull(rs.unit); assertNull(rs.valueCode); assertNull(rs.valueText)
        assertNull(rs.interpretation); assertNull(rs.refLow); assertNull(rs.refHigh); assertNull(rs.performer); assertNull(rs.note)
        val ph = back.pastProblems.single()
        assertNull(ph.onset); assertNull(ph.abatement); assertNull(ph.severity); assertNull(ph.note)
        val cn = back.problems.single()
        assertNull(cn.onset); assertNull(cn.severity); assertNull(cn.note)
        val fs = back.functional.single()
        assertNull(fs.onset); assertNull(fs.note)
        val pg = back.pregnancy.single()
        assertNull(pg.count); assertNull(pg.date); assertNull(pg.note)

        // …and the `_j` projection carries no empty string either
        val j = ProfileFixtures.project(JemmaProfileJ(j = "1.2", sid = sid), sid, back)
        assertFalse(ProfileFixtures.adapter.toJson(j).contains("\"\""))
    }

    @Test
    fun `UC-FHIR-016 a date is exported at the precision it was given, never shifted`() {
        val dates = listOf("1946", "1975", "2019-06", "2024-02", "2024-02-29", "2000-02-29", "1900-01-01", "2099-12-31", "2026-01-01", "1999-12-31")
        val native = IpsNativePillars(
            immunizations = dates.mapIndexed { i, d -> IpsImmunization(id = "im-d$i", code = "836374004", display = "Hepatitis B vaccine", date = d) },
            procedures = dates.mapIndexed { i, d -> IpsProcedure(id = "pr-d$i", code = "11466000", display = "Cesarean section", date = d) },
            devices = dates.mapIndexed { i, d -> IpsDevice(id = "dv-d$i", code = "6012004", display = "Hearing aid", date = d) },
            results = dates.mapIndexed { i, d -> IpsResult(id = "rs-d$i", code = "2823-3", display = "Potassium", date = d, value = "4.1", unit = "mmol/L") },
            pastProblems = dates.mapIndexed { i, d -> IpsPastProblem(id = "ph-d$i", code = "56717001", display = "Tuberculosis", onset = d, abatement = d) },
            problems = dates.mapIndexed { i, d -> IpsProblem(id = "cn-d$i", code = "84114007", display = "Heart failure", onset = d) },
            functional = dates.mapIndexed { i, d -> IpsFunctional(id = "fs-d$i", code = "15188001", display = "Hearing loss", onset = d) },
            pregnancy = dates.mapIndexed { i, d -> IpsPregnancyObs(id = "pg-d$i", code = "11779-6", valueDate = d, date = d) },
        )
        val json = JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(JemmaProfileJ(j = "1.2", sid = sid, p = patient())), native)
        val back = IpsFhirCodec.nativeOf(ProfileFixtures.parse(json))
        assertEquals(dates, back.immunizations.map { it.date })
        assertEquals(dates, back.procedures.map { it.date })
        assertEquals(dates, back.devices.map { it.date })
        assertEquals(dates, back.results.map { it.date })
        assertEquals(dates, back.pastProblems.map { it.onset })
        assertEquals(dates, back.pastProblems.map { it.abatement })
        assertEquals(dates, back.problems.map { it.onset })
        assertEquals(dates, back.functional.map { it.onset })
        assertEquals(dates, back.pregnancy.map { it.date })
        assertEquals(dates, back.pregnancy.map { it.valueDate })
        // the raw document carries the same strings (no time, no zone appended)
        for (d in dates) assertTrue(d, json.contains("\"occurrenceDateTime\": \"$d\""))
    }

    @Test
    fun `UC-FHIR-015 a date that does not exist is exported as unknown, never as a neighbouring day`() {
        for (impossible in listOf("2023-02-29", "2023-13-01", "2023-00-10", "20230101", "05/02/1956", "hier", "2023-2-9")) {
            val native = IpsNativePillars(
                immunizations = listOf(IpsImmunization(id = "im-x", code = "836374004", display = "Hepatitis B vaccine", date = impossible)),
                problems = listOf(IpsProblem(id = "cn-x", code = "84114007", display = "Heart failure", onset = impossible)),
            )
            val back = IpsFhirCodec.nativeOf(ProfileFixtures.parse(
                JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(JemmaProfileJ(j = "1.2", sid = sid, p = patient())), native)))
            val imDate = back.immunizations.single().date
            val cnDate = back.problems.single().onset
            assertTrue("$impossible → $imDate", imDate == null || imDate == impossible)
            assertTrue("$impossible → $cnDate", cnDate == null || cnDate == impossible)
            // the entry itself is never lost
            assertEquals("836374004", back.immunizations.single().code)
            assertEquals("84114007", back.problems.single().code)
        }
    }

    @Test
    fun `UC-RES-014 zero, negative and comma decimals keep their exact value`() {
        fun valueAfterBundle(typed: String): String? {
            val native = IpsNativePillars(results = listOf(IpsResult(id = "rs-n", code = "2823-3", display = "Potassium", value = typed, unit = "mmol/L",
                refLow = typed, refHigh = typed)))
            val back = IpsFhirCodec.resultsOf(ProfileFixtures.parse(
                JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(JemmaProfileJ(j = "1.2", sid = sid, p = patient())), native))).single()
            assertEquals("low bound of $typed", back.value, back.refLow)
            assertEquals("high bound of $typed", back.value, back.refHigh)
            assertEquals("unit of $typed", "mmol/L", back.unit)
            return back.value
        }
        assertEquals("0", valueAfterBundle("0"))
        assertEquals("0", valueAfterBundle("0,0"))
        assertEquals("-0.5", valueAfterBundle("-0,5"))
        assertEquals("5.9", valueAfterBundle("5,9"))
        assertEquals("5.9", valueAfterBundle(" 5.90 "))
        assertEquals("120", valueAfterBundle("+120"))
        assertEquals("250000", valueAfterBundle("250000"))
        assertEquals("0.001", valueAfterBundle("0.001"))
        assertEquals("1234.5", valueAfterBundle("1234,5"))
    }

    @Test
    fun `UC-GRO-010 a count of zero is an observation and an empty count is none`() {
        val zero = IpsPregnancyObs(id = "pg-z", code = "11636-8", count = 0, date = "2026-02-10")
        val json = JemmaFhirBundleBuilder.build(
            ProfileFixtures.hydrated(JemmaProfileJ(j = "1.2", sid = sid, p = patient())),
            IpsNativePillars(pregnancy = listOf(zero)),
        )
        assertTrue(json.contains("\"valueInteger\": 0"))
        assertEquals(listOf(zero), IpsFhirCodec.pregnancyOf(ProfileFixtures.parse(json)))
        // projection : "0" is carried, and comes back as 0 (not as "no value")
        assertEquals("0", zero.toJEntry().value)
        assertEquals(0, IpsPregnancyObs.fromJEntry(zero.toJEntry())!!.count)
        assertNull(IpsPregnancyObs.fromJEntry(zero.toJEntry().copy(value = null))!!.count)
        assertNull(IpsPregnancyObs.fromJEntry(zero.toJEntry().copy(value = " "))!!.count)
    }

    @Test
    fun `UC-ANT-010 an end date is compared with the start date at their common precision`() {
        // same year, the end known to the year only : accepted
        assertTrue(IpsPastProblem.isChronologyValid("2010-05-01", "2010"))
        assertTrue(IpsPastProblem.isChronologyValid("2010", "2010-05-01"))
        assertTrue(IpsPastProblem.isChronologyValid("2010-05", "2010-05-01"))
        assertTrue(IpsPastProblem.isChronologyValid("2010-05-01", "2010-05-01"))
        // the end clearly before the start : refused, whatever the precision of each
        assertFalse(IpsPastProblem.isChronologyValid("2010-05-01", "2009"))
        assertFalse(IpsPastProblem.isChronologyValid("2010-05", "2010-04-30"))
        assertFalse(IpsPastProblem.isChronologyValid("2010", "2009-12-31"))
        assertFalse(IpsPastProblem.isChronologyValid("2024-03-01", "2024-02-29"))
        // an unknown date on either side cannot contradict the other
        assertTrue(IpsPastProblem.isChronologyValid(null, "2010"))
        assertTrue(IpsPastProblem.isChronologyValid("2010", null))
        assertTrue(IpsPastProblem.isChronologyValid("", " "))
    }

    // ── the three `_j`-authored pillars : patient, allergies, medications ───

    private val legacy = JemmaProfileJ(
        j = "1.2", sid = sid,
        p = JPatient(
            gn = "太郎", fn = "محمد بن عبد الله", gs = "O", bd = "1946-02-08", nat = "JP", bt = "AB-",
            adr = "Rue de la Paix 12\n5660 Couvin\nBelgique 🙂", tel = "+32 478 45 45 45", eml = "taro@example.org", idn = "BE-680412-123-45",
            ids = listOf(JIdentifier(system = "PASSPORT", value = "TK1234567"), JIdentifier(system = "BE-NRN", value = "46.02.08-123.45")),
            adrs = listOf(
                JAddress(use = "home", line = "青森市 1-2-3", city = "青森", postalCode = "030-0801", state = "青森県", country = "JP"),
                JAddress(use = "work", line = longText, city = "Couvin", postalCode = "5660", country = "BE"),
            ),
            tels = listOf(JTelecom(system = "phone", value = "+81 90 1234 5678", use = "mobile"), JTelecom(system = "email", value = "taro@example.jp", use = "home")),
            gp = "Dr 田中", lang = "ja-JP",
            ct = listOf(
                JContact(n = "美佐子 🙂", r = "SPS", p = "+32 478 00 00 00", e = "misako@example.org", adr = "Couvin"),
                JContact(n = rtl, r = "FRND", p = "+966 50 000 0000"),
            ),
        ),
        al = listOf(
            JAllergy(c = "91936005", s = "H", st = "A", d = longText, m = emoji, displayLabel = "ペニシリンアレルギー · Allergie à la pénicilline",
                codeSystem = IpsCodeSystems.SNOMED, type = "allergy", category = "medication", onset = "1985",
                reactions = listOf(
                    JReaction(manifestationCode = "39579001", manifestationDisplay = "Anaphylaxis", manifestationSystem = IpsCodeSystems.SNOMED, severity = "severe"),
                    JReaction(manifestationCode = "126485001", manifestationDisplay = "蕁麻疹", manifestationSystem = IpsCodeSystems.SNOMED, severity = "mild"),
                )),
            JAllergy(c = "91935009", s = "L", st = "I", displayLabel = rtl, category = "food", onset = "2024-02-29"),
            // the same allergy entered twice : two entries
            JAllergy(c = "91935009", s = "L", st = "I", displayLabel = rtl, category = "food", onset = "2024-02-29"),
            JAllergy(c = "ZZ-UNKNOWN-0006", s = "U", st = "R", displayLabel = "Allergy unknown to every catalog", type = "intolerance"),
        ),
        md = listOf(
            JMedication(c = "B01AA03", t = "朝1錠・夕1錠 🙂", r = "O", v = "2,5", u = "mg", rs = "Daily", rc = "I48",
                displayLabel = "Anticoagulant · ワーファリン", codeSystem = IpsCodeSystems.ATC, status = "active", effective = "2020"),
            JMedication(c = "R03AC02", t = longText, r = "H", v = "2", u = "puff", displayLabel = "Salbutamol", status = "on-hold", effective = "2024-02-29"),
            JMedication(c = "R03AC02", t = longText, r = "H", v = "2", u = "puff", displayLabel = "Salbutamol", status = "on-hold", effective = "2024-02-29"),
            JMedication(c = "ZZ-UNKNOWN-0007", displayLabel = rtl, status = "stopped", effectiveAbsenceReason = "asked-unknown"),
        ),
    )

    private fun resources(bundleJson: String): List<JSONObject> {
        val entries = JSONObject(bundleJson).getJSONArray("entry")
        return (0 until entries.length()).map { entries.getJSONObject(it).getJSONObject("resource") }
    }

    @Test
    fun `UC-PAT-010 identity, allergies and medications with non latin and very long text are read back identical from the _j file`() {
        val json = ProfileFixtures.adapter.toJson(legacy)
        assertEquals(legacy, ProfileFixtures.adapter.fromJson(json))
        // a second write of what was read is byte-identical (no drift between saves)
        assertEquals(json, ProfileFixtures.adapter.toJson(ProfileFixtures.adapter.fromJson(json)))
    }

    @Test
    fun `UC-I18N-019 the document carries the identity, every allergy and every medication as typed`() {
        val all = resources(JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(legacy)))
        val patient = all.single { it.getString("resourceType") == "Patient" }
        val name = patient.getJSONArray("name").getJSONObject(0)
        assertEquals("太郎", name.getJSONArray("given").getString(0))
        assertEquals("محمد بن عبد الله", name.getString("family"))
        assertEquals("other", patient.getString("gender"))
        assertEquals("1946-02-08", patient.getString("birthDate"))
        // legacy single address + the two structured ones, none dropped
        assertEquals(3, patient.getJSONArray("address").length())
        assertEquals("青森", patient.getJSONArray("address").getJSONObject(1).getString("city"))
        // legacy phone + e-mail + the two structured ones
        assertEquals(4, patient.getJSONArray("telecom").length())
        // both emergency contacts, names and phones as typed
        val contacts = patient.getJSONArray("contact")
        assertEquals(2, contacts.length())
        assertEquals("美佐子 🙂", contacts.getJSONObject(0).getJSONObject("name").getString("text"))
        assertEquals("+32 478 00 00 00", contacts.getJSONObject(0).getJSONArray("telecom").getJSONObject(0).getString("value"))
        assertEquals(rtl, contacts.getJSONObject(1).getJSONObject("name").getString("text"))

        // allergies : one resource per entry, duplicates included, order kept
        val allergies = all.filter { it.getString("resourceType") == "AllergyIntolerance" }
        assertEquals(legacy.al.map { it.c }, allergies.map { it.getJSONObject("code").getJSONArray("coding").getJSONObject(0).getString("code") })
        assertEquals(legacy.al.map { it.displayLabel }, allergies.map { it.getJSONObject("code").getString("text") })
        assertEquals(listOf("high", "low", "low", "unable-to-assess"), allergies.map { it.getString("criticality") })
        assertEquals(
            listOf("active", "inactive", "inactive", "resolved"),
            allergies.map { it.getJSONObject("clinicalStatus").getJSONArray("coding").getJSONObject(0).getString("code") },
        )

        // medications : one Medication + one MedicationStatement per entry, status as stored
        val medications = all.filter { it.getString("resourceType") == "Medication" }
        assertEquals(legacy.md.map { it.c }, medications.map { it.getJSONObject("code").getJSONArray("coding").getJSONObject(0).getString("code") })
        assertEquals(legacy.md.map { it.displayLabel }, medications.map { it.getJSONObject("code").getString("text") })
        val statements = all.filter { it.getString("resourceType") == "MedicationStatement" }
        assertEquals(listOf("active", "on-hold", "on-hold", "stopped"), statements.map { it.getString("status") })
        assertTrue(statements[0].getJSONArray("dosage").getJSONObject(0).getString("text").contains("朝1錠・夕1錠 🙂"))
        assertTrue(statements[1].getJSONArray("dosage").getJSONObject(0).getString("text").contains(longText))

        // one blood group Observation, the profile's (AB-)
        val bundle = ProfileFixtures.parse(JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(legacy)))
        assertEquals(listOf("278154007"), IpsFhirCodec.resultsOf(bundle).filter { it.code == IpsBloodGroup.LOINC_ABO_RH }.map { it.valueCode })
    }
}
