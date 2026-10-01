/*
 * IpsPastProblemCodecTest.kt — JVM round trips for the 📜 Past Problems pillar :
 * Condition (Condition-uv-ips) ⇄ IpsPastProblem, `_j.ph` projection, Bundle wiring
 * (section 11348-0) and coexistence with the legacy problem-list Conditions (`cn`).
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import dev.ohs.fhir.model.r4.Composition
import dev.ohs.fhir.model.r4.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsPastProblemCodecTest {

    private val patientUrn = "urn:uuid:00000000-0000-0000-0000-000000000001"

    private val appendicitis = IpsPastProblem(
        id = "ph-1", code = "74400008", system = IpsCodeSystems.SNOMED, display = "Appendicitis",
        onset = "1995-07-10", abatement = "1995-07-12", clinicalStatus = IpsPastProblemStatus.RESOLVED,
        severity = IpsConditionSeverity.MODERATE, note = "Treated by appendectomy",
    )

    private fun roundTrip(pp: IpsPastProblem): IpsPastProblem {
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(pp, patientUrn).build())
        val parsed = IpsFhirCodec.json.decodeFromString(json)
        assertTrue(parsed is Condition)
        return IpsFhirCodec.fromFhir(parsed as Condition)
    }

    // ── Domain ───────────────────────────────────────────────────────────

    @Test
    fun statusNormalizationKeepsOnlyPastStatuses() {
        assertEquals("resolved", IpsPastProblemStatus.normalize(null))
        assertEquals("resolved", IpsPastProblemStatus.normalize("active"))
        assertEquals("resolved", IpsPastProblemStatus.normalize("recurrence"))
        assertEquals("inactive", IpsPastProblemStatus.normalize(" Inactive "))
        assertEquals("remission", IpsPastProblemStatus.normalize("remission"))
    }

    @Test
    fun severityUsesTheIpsLoincAnswers() {
        assertEquals("LA6752-5", IpsConditionSeverity.MILD)
        assertEquals("LA6751-7", IpsConditionSeverity.MODERATE)
        assertEquals("LA6750-9", IpsConditionSeverity.SEVERE)
        assertEquals("Moderate", IpsConditionSeverity.display(IpsConditionSeverity.MODERATE))
        assertNull(IpsConditionSeverity.normalize("LA0000-0"))
        assertEquals(IpsConditionSeverity.SEVERE, IpsConditionSeverity.normalize("LA6750-9"))
    }

    @Test
    fun abatementBeforeOnsetIsRejected() {
        assertTrue(IpsPastProblem.isChronologyValid("1995-07-10", "1995-07-12"))
        assertTrue(IpsPastProblem.isChronologyValid("1995", "1995-07-12"))
        assertTrue(IpsPastProblem.isChronologyValid(null, "1995"))
        assertTrue(IpsPastProblem.isChronologyValid("2001-03", "2001-03"))
        assertFalse(IpsPastProblem.isChronologyValid("2001-03-05", "2000-12-31"))
        assertFalse(IpsPastProblem.isChronologyValid("2001", "1999-06"))
    }

    // ── FHIR Condition ───────────────────────────────────────────────────

    @Test
    fun fullRoundTrip() {
        assertEquals(appendicitis, roundTrip(appendicitis))
    }

    @Test
    fun jsonCarriesIpsEssentials() {
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(appendicitis, patientUrn).build())
        assertTrue(json.contains("\"resourceType\": \"Condition\""))
        assertTrue(json.contains(IpsFhirCodec.PROFILE_CONDITION_UV_IPS))
        assertTrue(json.contains("http://terminology.hl7.org/CodeSystem/condition-clinical"))
        assertTrue(json.contains("\"code\": \"resolved\""))
        assertTrue(json.contains("\"code\": \"74400008\""))
        assertTrue(json.contains("\"onsetDateTime\": \"1995-07-10\""))
        assertTrue(json.contains("\"abatementDateTime\": \"1995-07-12\""))
        assertTrue(json.contains("\"code\": \"LA6751-7\""))
        assertTrue(json.contains("\"reference\": \"$patientUrn\""))
        // no problem-list category: membership comes from the 11348-0 section
        assertFalse(json.contains("problem-list-item"))
        assertFalse(json.contains("verificationStatus"))
    }

    @Test
    fun freeTextUndatedRemission() {
        val free = IpsPastProblem(id = "ph-2", text = "Hépatite virale (enfance)", clinicalStatus = IpsPastProblemStatus.REMISSION)
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(free, patientUrn).build())
        assertTrue(json.contains("\"text\": \"Hépatite virale (enfance)\""))
        assertFalse(json.contains("onsetDateTime"))
        assertFalse(json.contains("abatementDateTime"))
        assertFalse(json.contains("severity"))
        assertEquals(free, roundTrip(free))
    }

    @Test
    fun partialDatesSurvive() {
        val tb = appendicitis.copy(id = "ph-3", code = "56717001", display = "Tuberculosis", onset = "1962", abatement = "1963-04", severity = null, note = null)
        assertEquals(tb, roundTrip(tb))
    }

    // ── `_j.ph` projection ───────────────────────────────────────────────

    @Test
    fun projectionContract() {
        val e = appendicitis.toJEntry()
        assertEquals("74400008", e.c)
        assertEquals("Appendicitis", e.displayLabel)
        assertEquals("1995-07-10", e.date)
        assertEquals("1995-07-12", e.abatement)
        assertEquals("LA6751-7", e.severity)
        assertEquals("Treated by appendectomy", e.d)
        assertNull("SNOMED is the pillar default", e.codeSystem)
        assertNull("resolved is the pillar default", e.status)

        val back = IpsPastProblem.fromJEntry(e, 0)
        assertEquals(appendicitis.copy(id = back.id), back)

        val inactive = appendicitis.copy(clinicalStatus = IpsPastProblemStatus.INACTIVE).toJEntry()
        assertEquals("inactive", inactive.status)
    }

    @Test
    fun projectionIsDeterministic() {
        val e = appendicitis.toJEntry()
        assertEquals(IpsPastProblem.fromJEntry(e, 0).id, IpsPastProblem.fromJEntry(e, 0).id)
    }

    // ── Bundle wiring ────────────────────────────────────────────────────

    private fun hydratedStub(sid: String): HydratedProfile = HydratedProfile(
        raw = JemmaProfileJ(j = "1.2", sid = sid, p = JPatient(gn = "Haru", fn = "Tanaka", gs = "F", bd = "1946-02-08")),
        uiLang = "en", allergies = emptyList(), medications = emptyList(), conditions = emptyList(),
        ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
    )

    private fun sectionOf(comp: Composition, loinc: String): Composition.Section? =
        comp.section.firstOrNull { s -> s.code?.coding?.any { it.code?.value == loinc } == true }

    @Test
    fun bundleEmbedsPastProblemsWithTheirSection() {
        val mi = IpsPastProblem(id = "ph-mi", code = "22298006", display = "Myocardial infarction", onset = "2015-08-27", severity = IpsConditionSeverity.SEVERE)
        val native = IpsNativePillars(pastProblems = listOf(appendicitis, mi))
        val json = JemmaFhirBundleBuilder.build(hydratedStub("demo_test"), native)
        val bundle = IpsFhirCodec.parseBundle(json)
        assertNotNull(bundle); bundle!!

        assertEquals(native.pastProblems, IpsFhirCodec.nativeOf(bundle).pastProblems)

        val comp = IpsFhirCodec.resourcesOf(bundle).filterIsInstance<Composition>().single()
        val section = sectionOf(comp, IpsFhirCodec.LOINC_SECTION_PAST_ILLNESS)
        assertNotNull(section); section!!
        assertEquals("History of Past illness note", section.code?.coding?.single()?.display?.value)
        val refs = section.entry.mapNotNull { it.reference?.value }.toSet()
        assertEquals(setOf(IpsFhirCodec.pastProblemUrn("demo_test", "ph-1"), IpsFhirCodec.pastProblemUrn("demo_test", "ph-mi")), refs)
        assertTrue(bundle.entry.mapNotNull { it.fullUrl?.value }.toSet().containsAll(refs))
        // 1 Composition + 1 Patient + 2 Condition
        assertEquals(4, bundle.entry.size)
    }

    @Test
    fun noSectionWhenThePillarIsEmpty() {
        val json = JemmaFhirBundleBuilder.build(hydratedStub("demo_empty"), IpsNativePillars.EMPTY)
        val bundle = IpsFhirCodec.parseBundle(json)!!
        val comp = IpsFhirCodec.resourcesOf(bundle).filterIsInstance<Composition>().single()
        assertNull(sectionOf(comp, IpsFhirCodec.LOINC_SECTION_PAST_ILLNESS))
        assertTrue(IpsFhirCodec.nativeOf(bundle).pastProblems.isEmpty())
    }

    @Test
    fun activeProblemListConditionsAreNotPastProblems() {
        // A legacy `cn` Condition (problem list, clinicalStatus active, no IPS profile) must
        // not leak into the past-problems pillar, even without any 11348-0 section.
        val active = dev.ohs.fhir.model.r4.Condition.Builder(
            dev.ohs.fhir.model.r4.Reference.Builder().apply { reference = dev.ohs.fhir.model.r4.String.Builder().apply { value = patientUrn } },
        ).apply {
            clinicalStatus = dev.ohs.fhir.model.r4.CodeableConcept.Builder().apply {
                coding.add(dev.ohs.fhir.model.r4.Coding.Builder().apply {
                    system = dev.ohs.fhir.model.r4.Uri.Builder().apply { value = "http://terminology.hl7.org/CodeSystem/condition-clinical" }
                    code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = "active" }
                })
            }
        }.build()
        assertFalse(IpsFhirCodec.isPastProblemCondition(active, inSection = false))
        assertTrue(IpsFhirCodec.isPastProblemCondition(active, inSection = true))
        val past = IpsFhirCodec.toFhir(appendicitis, patientUrn).build()
        assertTrue(IpsFhirCodec.isPastProblemCondition(past, inSection = false))
    }

    @Test
    fun legacyJArrayRebuild() {
        val j = JemmaProfileJ(j = "1.2", sid = "x", ph = listOf(appendicitis.toJEntry()))
        val a = IpsNativePillars.fromJEntries(j.im, j.pr, j.dv, j.rs, j.ph)
        assertEquals(1, a.pastProblems.size)
        assertEquals("74400008", a.pastProblems.single().code)
        assertEquals(a, IpsNativePillars.fromJEntries(j.im, j.pr, j.dv, j.rs, j.ph))
    }
}
