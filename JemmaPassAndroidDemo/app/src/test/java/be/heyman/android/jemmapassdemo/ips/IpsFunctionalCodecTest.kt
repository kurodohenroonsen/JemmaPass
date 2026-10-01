/*
 * IpsFunctionalCodecTest.kt — ♿ Functional status (sprint 7): Condition in section 47420-5
 * ⇄ IpsFunctional, `_j.fs` projection, and strict separation from 🩺 problems / 📜 past illnesses
 * (same FHIR resource type, three different sections).
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsFunctionalCodecTest {

    private val patientUrn = "urn:uuid:00000000-0000-0000-0000-000000000001"
    private val hearing = IpsFunctional(id = "fs-1", code = "15188001", display = "Hearing loss", onset = "2019", note = "Bilateral hearing aids")
    private val cane = IpsFunctional(id = "fs-2", text = "Walks with a cane outdoors", onset = "2021")

    private fun roundTrip(f: IpsFunctional): IpsFunctional {
        val parsed = IpsFhirCodec.json.decodeFromString(IpsFhirCodec.encode(IpsFhirCodec.toFhir(f, patientUrn).build()))
        return IpsFhirCodec.functionalFromFhir(parsed as Condition)
    }

    @Test
    fun roundTripsAndStatuses() {
        assertEquals(hearing, roundTrip(hearing))
        assertEquals(cane, roundTrip(cane))
        val resolved = cane.copy(id = "fs-3", clinicalStatus = IpsFunctionalStatus.RESOLVED)
        assertEquals(resolved, roundTrip(resolved))
        assertEquals("active", IpsFunctionalStatus.normalize("relapse"))
        assertEquals("inactive", IpsFunctionalStatus.normalize(" Inactive "))
    }

    @Test
    fun projection() {
        val j = hearing.toJEntry()
        assertEquals("15188001", j.c)
        assertEquals("2019", j.date)
        assertNull("active is the pillar default", j.status)
        assertEquals("inactive", hearing.copy(clinicalStatus = "inactive").toJEntry().status)
        val back = IpsFunctional.fromJEntry(j, 0)
        assertEquals(hearing.copy(id = back.id), back)
        val free = IpsFunctional.fromJEntry(cane.toJEntry(), 1)
        assertNull(free.code)
        assertEquals("Walks with a cane outdoors", free.text)
    }

    @Test
    fun threeConditionPillarsNeverMix() {
        val hydrated = HydratedProfile(
            raw = JemmaProfileJ(j = "1.2", sid = "demo_fs", p = JPatient(gn = "Haru", gs = "F")),
            uiLang = "en", allergies = emptyList(), medications = emptyList(), conditions = emptyList(),
            ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
        )
        val problem = IpsProblem(id = "cn-1", code = "84114007", display = "Heart failure")
        val past = IpsPastProblem(id = "ph-1", code = "56717001", display = "Tuberculosis", onset = "1962", abatement = "1963")
        // an active functional entry looks like a problem, a resolved one like a past illness:
        val resolvedFs = cane.copy(id = "fs-res", clinicalStatus = IpsFunctionalStatus.RESOLVED)
        val native = IpsNativePillars(problems = listOf(problem), pastProblems = listOf(past), functional = listOf(hearing, resolvedFs))
        val bundle = IpsFhirCodec.parseBundle(JemmaFhirBundleBuilder.build(hydrated, native))!!
        val back = IpsFhirCodec.nativeOf(bundle)
        assertEquals(native.functional, back.functional)
        assertEquals(native.problems, back.problems)
        assertEquals(native.pastProblems, back.pastProblems)

        val comp = IpsFhirCodec.resourcesOf(bundle).filterIsInstance<Composition>().single()
        fun refs(loinc: String) = comp.section.filter { s -> s.code?.coding?.any { it.code?.value == loinc } == true }
            .flatMap { s -> s.entry.mapNotNull { it.reference?.value } }.toSet()
        assertEquals(setOf(IpsFhirCodec.functionalUrn("demo_fs", "fs-1"), IpsFhirCodec.functionalUrn("demo_fs", "fs-res")),
            refs(IpsFhirCodec.LOINC_SECTION_FUNCTIONAL))
        assertTrue(refs(IpsFhirCodec.LOINC_SECTION_PROBLEMS).intersect(refs(IpsFhirCodec.LOINC_SECTION_FUNCTIONAL)).isEmpty())
        assertFalse(refs(IpsFhirCodec.LOINC_SECTION_PAST_ILLNESS).contains(IpsFhirCodec.functionalUrn("demo_fs", "fs-res")))
    }

    @Test
    fun functionalStatusNeverFeedsTheDrugDiseaseProjection() {
        // `_j.cn` (read by the drug × disease cross-check) must only hold the problem list.
        val j = JemmaProfileJ(j = "1.2", sid = "x", fs = listOf(hearing.toJEntry()))
        val native = IpsNativePillars.fromJEntries(j.im, j.pr, j.dv, j.rs, j.ph, j.cn, j.pg, j.fs)
        assertEquals(1, native.functional.size)
        assertTrue(native.problems.isEmpty())
    }
}
