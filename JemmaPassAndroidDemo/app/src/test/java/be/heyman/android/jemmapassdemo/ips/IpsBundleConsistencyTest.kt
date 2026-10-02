/*
 * IpsBundleConsistencyTest.kt — one resource, one pillar:
 * UC-FHIR-027 (blood group 882-1 follows `p.bt`), UC-FHIR-025 (pregnancy vs results).
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Composition
import dev.ohs.fhir.model.r4.Observation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsBundleConsistencyTest {

    private val sid = "demo_bg"

    private fun hydrated(raw: JemmaProfileJ): HydratedProfile = HydratedProfile(
        raw = raw,
        uiLang = "en", allergies = emptyList(), medications = emptyList(), conditions = emptyList(),
        ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
    )

    private fun profile(bt: String?): JemmaProfileJ =
        JemmaProfileJ(j = "1.2", sid = sid, p = JPatient(gn = "Haru", gs = "F", bt = bt))

    private fun build(raw: JemmaProfileJ, native: IpsNativePillars = IpsNativePillars.EMPTY): Bundle =
        IpsFhirCodec.parseBundle(JemmaFhirBundleBuilder.build(hydrated(raw), native))!!

    private fun bloodGroups(bundle: Bundle): List<IpsResult> =
        IpsFhirCodec.resultsOf(bundle).filter { it.code == IpsBloodGroup.LOINC_ABO_RH }

    private fun sectionRefs(bundle: Bundle, loinc: String): List<String> =
        IpsFhirCodec.resourcesOf(bundle).filterIsInstance<Composition>().single().section
            .filter { s -> s.code?.coding?.any { it.code?.value == loinc } == true }
            .flatMap { s -> s.entry.mapNotNull { it.reference?.value } }

    private val potassium = IpsResult(id = "rs-1", code = "2823-3", display = "Potassium", value = "4.1", unit = "mmol/L")

    @Test
    fun `UC-FHIR-027 imported blood group copy follows the patient blood group`() {
        // Import: the derived Observation of the sender comes back through `_j.rs` with a
        // new id, so it no longer looks derived. The patient's group is then corrected to O-.
        val imported = IpsNativePillars.fromJEntries(
            im = emptyList(),
            rs = listOf(IpsBloodGroup.derivedResult("other_device", "A+")!!.toJEntry(), potassium.toJEntry()),
        )
        assertFalse(IpsBloodGroup.isDerived(imported.results.first()))

        val bundle = build(profile("O-"), imported)
        val groups = bloodGroups(bundle)
        assertEquals(1, groups.size)
        assertEquals("278148006", groups.single().valueCode)
        assertEquals(IpsBloodGroup.derivedId(sid), groups.single().id)
        // The other results are untouched and the section lists each entry once.
        assertEquals(2, IpsFhirCodec.resultsOf(bundle).size)
        val refs = sectionRefs(bundle, IpsFhirCodec.LOINC_SECTION_RESULTS)
        assertEquals(2, refs.size)
        assertEquals(refs.toSet().size, refs.size)
        assertFalse(IpsFhirCodec.encode(bundle).contains("278149003"))
    }

    @Test
    fun `UC-FHIR-027 exactly one blood group observation in every channel`() {
        // FHIR QR channel: no stored native pillars, `_j.rs` without blood group.
        val fromJ = bloodGroups(build(profile("AB+")))
        assertEquals(listOf("278151004"), fromJ.map { it.valueCode })

        // A matching result entered by hand is kept as is (date, id); no derived twin.
        val byHand = IpsResult(id = "rs-user", code = IpsBloodGroup.LOINC_ABO_RH, display = "ABO/Rh", date = "2015-09-01",
            valueCode = "278147001", valueDisplay = "Blood group O Rh(D) positive")
        val kept = bloodGroups(build(profile("O+"), IpsNativePillars(results = listOf(potassium, byHand))))
        assertEquals(listOf("rs-user"), kept.map { it.id })
        assertEquals("2015-09-01", kept.single().date)

        // Duplicates and a stale derived entry collapse to the one that matches `p.bt`.
        val stale = IpsBloodGroup.derivedResult(sid, "B+")!!
        val dup = bloodGroups(build(profile("O+"), IpsNativePillars(results = listOf(stale, byHand, byHand.copy(id = "rs-user-2")))))
        assertEquals(listOf("rs-user"), dup.map { it.id })

        // Already in sync (the repository's normal path): unchanged, still one.
        val synced = IpsNativePillars(results = IpsBloodGroup.sync(listOf(potassium), sid, "A-"))
        assertEquals(synced.results, IpsFhirCodec.resultsOf(build(profile("A-"), synced)))
    }

    @Test
    fun `UC-FHIR-027 no blood group in the profile means no derived observation`() {
        val stale = IpsBloodGroup.derivedResult(sid, "B+")!!
        assertTrue(bloodGroups(build(profile(null), IpsNativePillars(results = listOf(stale, potassium)))).isEmpty())
        // "0+" (zero) is not a blood group: nothing is invented.
        assertTrue(bloodGroups(build(profile("0+"), IpsNativePillars(results = listOf(potassium)))).isEmpty())
    }

    // ── Pregnancy vs results ─────────────────────────────────────────────

    private val status = IpsPregnancyObs(id = "pg-st", code = IpsPregnancyCodes.STATUS, valueCode = IpsPregnancyCodes.PREGNANT, date = "2026-09-20")
    private val live = IpsPregnancyObs(id = "pg-live", code = "11636-8", count = 2, date = "2026-09-20")

    @Test
    fun `UC-FHIR-025 pregnancy observations are read once after a round trip`() {
        val native = IpsNativePillars(pregnancy = listOf(status, live), results = listOf(potassium))
        val first = build(profile(null), native)
        val back = IpsFhirCodec.nativeOf(first)
        assertEquals(native.pregnancy, back.pregnancy)
        assertEquals(listOf("rs-1"), back.results.map { it.id })

        // Second generation: same counts, every pregnancy Observation listed in one section only.
        val second = build(profile(null), back)
        assertEquals(first.entry.size, second.entry.size)
        assertEquals(3, IpsFhirCodec.resourcesOf(second).filterIsInstance<Observation>().size)
        val pregnancyRefs = sectionRefs(second, IpsFhirCodec.LOINC_SECTION_PREGNANCY)
        val resultRefs = sectionRefs(second, IpsFhirCodec.LOINC_SECTION_RESULTS)
        assertEquals(2, pregnancyRefs.size)
        assertEquals(1, resultRefs.size)
        assertTrue(pregnancyRefs.intersect(resultRefs.toSet()).isEmpty())
    }

    @Test
    fun `UC-FHIR-025 a pregnancy observation with a laboratory category is not also a result`() {
        // An external system may categorise the pregnancy status as "laboratory".
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(potassium.copy(id = "pg-ext", code = IpsPregnancyCodes.STATUS, display = "Pregnancy status",
            value = null, unit = null, valueCode = IpsPregnancyCodes.PREGNANT, valueCodeSystem = IpsCodeSystems.LOINC), "urn:uuid:p").build())
            .replace(IpsFhirCodec.PROFILE_OBSERVATION_RESULTS_LABORATORY_UV_IPS, IpsFhirCodec.PROFILE_PREGNANCY_STATUS_UV_IPS)
        val external = IpsFhirCodec.json.decodeFromString(json) as Observation
        assertTrue(IpsFhirCodec.isPregnancyObservation(external))
        assertFalse(IpsFhirCodec.isResultsObservation(external))

        // The same code under a results profile (entered in the Results pillar) stays a result only.
        val asResult = IpsFhirCodec.toFhir(potassium.copy(id = "rs-pg", code = "11636-8", display = "Births live", value = "2", unit = null), "urn:uuid:p").build()
        assertTrue(IpsFhirCodec.isResultsObservation(asResult))
        assertFalse(IpsFhirCodec.isPregnancyObservation(asResult))

        val bundle = build(profile(null), IpsNativePillars(results = listOf(potassium.copy(id = "rs-pg", code = "11636-8", display = "Births live", value = "2", unit = null))))
        val back = IpsFhirCodec.nativeOf(bundle)
        assertEquals(1, back.results.size)
        assertTrue(back.pregnancy.isEmpty())
    }
}
