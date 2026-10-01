/*
 * IpsProblemCodecTest.kt — JVM round trips for the 🩺 Problem List pillar :
 * Condition (Condition-uv-ips, problem-list-item) ⇄ IpsProblem, `_j.cn` projection
 * (legacy JCondition shape kept for the cross-checks), Bundle wiring next to the
 * 📜 past problems, legacy Bundles and the FHIR-QR path without native pillars.
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.qr.JCondition
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

class IpsProblemCodecTest {

    private val patientUrn = "urn:uuid:00000000-0000-0000-0000-000000000001"

    private val heartFailure = IpsProblem(
        id = "cn-1", code = "84114007", system = IpsCodeSystems.SNOMED, display = "Heart failure",
        onset = "2020-11", clinicalStatus = IpsProblemStatus.ACTIVE,
        severity = IpsConditionSeverity.MODERATE, note = "NYHA II, on furosemide",
    )

    private fun roundTrip(p: IpsProblem): IpsProblem {
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(p, patientUrn).build())
        val parsed = IpsFhirCodec.json.decodeFromString(json)
        assertTrue(parsed is Condition)
        return IpsFhirCodec.problemFromFhir(parsed as Condition)
    }

    @Test
    fun statusNormalization() {
        assertEquals("active", IpsProblemStatus.normalize(null))
        assertEquals("active", IpsProblemStatus.normalize("A"))
        assertEquals("active", IpsProblemStatus.normalize("resolved"))
        assertEquals("relapse", IpsProblemStatus.normalize(" Relapse "))
        assertEquals("recurrence", IpsProblemStatus.normalize("recurrence"))
    }

    @Test
    fun fullRoundTrip() {
        assertEquals(heartFailure, roundTrip(heartFailure))
        val free = IpsProblem(id = "cn-2", text = "Lombalgie chronique", clinicalStatus = IpsProblemStatus.RELAPSE)
        assertEquals(free, roundTrip(free))
    }

    @Test
    fun jsonCarriesIpsEssentials() {
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(heartFailure, patientUrn).build())
        assertTrue(json.contains(IpsFhirCodec.PROFILE_CONDITION_UV_IPS))
        assertTrue(json.contains("\"code\": \"problem-list-item\""))
        assertTrue(json.contains("\"code\": \"active\""))
        assertTrue(json.contains("\"code\": \"84114007\""))
        assertTrue(json.contains("\"onsetDateTime\": \"2020-11\""))
        assertTrue(json.contains("\"code\": \"LA6751-7\""))
        assertFalse(json.contains("abatement"))
    }

    @Test
    fun projectionKeepsTheLegacyShape() {
        val c = heartFailure.toJCondition()
        assertEquals("84114007", c.c)
        assertEquals("Heart failure", c.displayLabel)
        assertEquals("active", c.st)
        assertEquals("moderate", c.s)
        assertEquals("NYHA II, on furosemide", c.d)
        assertEquals("2020-11", c.date)
        assertNull(c.codeSystem)
        val back = IpsProblem.fromJCondition(c, 0)
        assertEquals(heartFailure.copy(id = back.id), back)
        // legacy one-letter values from QR / mesh imports
        val legacy = IpsProblem.fromJCondition(JCondition(c = "38341003", st = "A", s = "H", displayLabel = "Hypertension"))
        assertEquals(IpsProblemStatus.ACTIVE, legacy.clinicalStatus)
        assertEquals(IpsConditionSeverity.SEVERE, legacy.severity)
        assertEquals(legacy.id, IpsProblem.fromJCondition(JCondition(c = "38341003", st = "A", s = "H", displayLabel = "Hypertension")).id)
    }

    private fun hydratedStub(sid: String, cn: List<JCondition> = emptyList()): HydratedProfile = HydratedProfile(
        raw = JemmaProfileJ(j = "1.2", sid = sid, p = JPatient(gn = "Haru", fn = "Tanaka", gs = "F", bd = "1946-02-08"), cn = cn),
        uiLang = "en", allergies = emptyList(), medications = emptyList(), conditions = emptyList(),
        ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
    )

    private fun sectionRefs(comp: Composition, loinc: String): Set<String> =
        comp.section.filter { s -> s.code?.coding?.any { it.code?.value == loinc } == true }
            .flatMap { s -> s.entry.mapNotNull { it.reference?.value } }.toSet()

    @Test
    fun problemsAndPastProblemsLiveInTheirOwnSections() {
        val ckd = IpsProblem(id = "cn-ckd", code = "433144002", display = "Chronic kidney disease stage 3")
        val tb = IpsPastProblem(id = "ph-tb", code = "56717001", display = "Tuberculosis", onset = "1962", abatement = "1963")
        val native = IpsNativePillars(problems = listOf(heartFailure, ckd), pastProblems = listOf(tb))
        val bundle = IpsFhirCodec.parseBundle(JemmaFhirBundleBuilder.build(hydratedStub("demo_test"), native))!!

        val back = IpsFhirCodec.nativeOf(bundle)
        assertEquals(native.problems, back.problems)
        assertEquals(native.pastProblems, back.pastProblems)

        val comp = IpsFhirCodec.resourcesOf(bundle).filterIsInstance<Composition>().single()
        assertEquals(setOf(IpsFhirCodec.problemUrn("demo_test", "cn-1"), IpsFhirCodec.problemUrn("demo_test", "cn-ckd")),
            sectionRefs(comp, IpsFhirCodec.LOINC_SECTION_PROBLEMS))
        assertEquals(setOf(IpsFhirCodec.pastProblemUrn("demo_test", "ph-tb")), sectionRefs(comp, IpsFhirCodec.LOINC_SECTION_PAST_ILLNESS))
        val problemSection = comp.section.first { s -> s.code?.coding?.any { it.code?.value == "11450-4" } == true }
        assertEquals("Problem list - Reported", problemSection.code?.coding?.single()?.display?.value)
        // 1 Composition + 1 Patient + 3 Condition
        assertEquals(5, bundle.entry.size)
    }

    @Test
    fun withoutNativePillarsTheBuilderFallsBackOnTheProjection() {
        // FHIR QR channel: JemmaFhirBundleBuilder.build(hydrated) without native pillars.
        val cn = listOf(heartFailure.toJCondition())
        val bundle = IpsFhirCodec.parseBundle(JemmaFhirBundleBuilder.build(hydratedStub("demo_qr", cn)))!!
        val problems = IpsFhirCodec.nativeOf(bundle).problems
        assertEquals(1, problems.size)
        assertEquals("84114007", problems.single().code)
        assertEquals("2020-11", problems.single().onset)
    }

    @Test
    fun legacyProblemListConditionsAreReadWithAStableId() {
        // Bundles written before sprint 5: Condition without id nor profile, listed in 11450-4.
        val legacy = """
            {"resourceType":"Bundle","type":"document","entry":[
              {"fullUrl":"urn:uuid:aaaaaaaa-0000-3000-8000-000000000001","resource":{"resourceType":"Composition","status":"final",
                "type":{"coding":[{"system":"http://loinc.org","code":"60591-5"}]},"date":"2026-01-01","author":[{"display":"x"}],"title":"IPS",
                "section":[{"title":"Problems","code":{"coding":[{"system":"http://loinc.org","code":"11450-4"}]},
                  "entry":[{"reference":"urn:uuid:aaaaaaaa-0000-3000-8000-000000000002"}]}]}},
              {"fullUrl":"urn:uuid:aaaaaaaa-0000-3000-8000-000000000002","resource":{"resourceType":"Condition",
                "clinicalStatus":{"coding":[{"system":"http://terminology.hl7.org/CodeSystem/condition-clinical","code":"active"}]},
                "code":{"coding":[{"system":"http://snomed.info/sct","code":"38341003","display":"Hypertension"}],"text":"Hypertension"},
                "subject":{"reference":"urn:uuid:p"}}}
            ]}
        """.trimIndent()
        val bundle = IpsFhirCodec.parseBundle(legacy)
        assertNotNull(bundle)
        val a = IpsFhirCodec.problemsOf(bundle!!)
        val b = IpsFhirCodec.problemsOf(IpsFhirCodec.parseBundle(legacy)!!)
        assertEquals(1, a.size)
        assertEquals("38341003", a.single().code)
        assertEquals(a.single().id, b.single().id)
        assertTrue(IpsFhirCodec.pastProblemsOf(bundle).isEmpty())
    }
}
