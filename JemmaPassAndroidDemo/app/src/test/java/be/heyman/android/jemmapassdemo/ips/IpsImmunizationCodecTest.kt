/*
 * IpsImmunizationCodecTest.kt
 *
 * JVM round-trip tests for the FHIR-native Immunizations pillar :
 *   domain ⇄ FHIR R4 Immunization (dev.ohs.fhir SDK) ⇄ JSON,
 *   plus the Bundle-level extraction used by ProfilesRepository.
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Composition
import dev.ohs.fhir.model.r4.Immunization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsImmunizationCodecTest {

    private val patientUrn = "urn:uuid:00000000-0000-0000-0000-000000000001"

    private val full = IpsImmunization(
        id = "im-test-1",
        code = "871876003",
        system = IpsCodeSystems.SNOMED,
        display = "Tetanus-diphtheria-pertussis (Tdap)",
        date = "2022-05-17",
        status = IpsImmunizationStatus.COMPLETED,
        doseNumber = 2,
        seriesDoses = 3,
        lotNumber = "AC52B213BC",
        manufacturer = "Sanofi Pasteur",
        performer = "Dr. Lambert, Couvin",
        note = "Booster before the pilgrimage",
    )

    private fun roundTrip(im: IpsImmunization): IpsImmunization {
        val resource = IpsFhirCodec.toFhir(im, patientUrn).build()
        val json = IpsFhirCodec.encode(resource)
        val parsed = IpsFhirCodec.json.decodeFromString(json)
        assertTrue("decoded resource must be an Immunization", parsed is Immunization)
        return IpsFhirCodec.fromFhir(parsed as Immunization)
    }

    @Test
    fun fullEntrySurvivesFhirRoundTrip() {
        assertEquals(full, roundTrip(full))
    }

    @Test
    fun fhirJsonCarriesTheIpsEssentials() {
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(full, patientUrn).build())
        assertTrue(json.contains("\"resourceType\": \"Immunization\""))
        assertTrue(json.contains("\"status\": \"completed\""))
        assertTrue(json.contains("\"occurrenceDateTime\": \"2022-05-17\""))
        assertTrue(json.contains("\"lotNumber\": \"AC52B213BC\""))
        assertTrue(json.contains(IpsFhirCodec.PROFILE_IMMUNIZATION_UV_IPS))
        assertTrue(json.contains(patientUrn))
        assertTrue(json.contains("\"doseNumberPositiveInt\": 2"))
        assertTrue(json.contains("\"seriesDosesPositiveInt\": 3"))
    }

    @Test
    fun unknownDateBecomesOccurrenceStringAndComesBackNull() {
        val im = full.copy(id = "im-nodate", date = null)
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(im, patientUrn).build())
        assertTrue(json.contains("\"occurrenceString\": \"${IpsFhirCodec.OCCURRENCE_UNKNOWN}\""))
        assertNull(roundTrip(im).date)
    }

    @Test
    fun partialDatesAreKeptAsIs() {
        assertEquals("2019", roundTrip(full.copy(date = "2019")).date)
        assertEquals("2019-11", roundTrip(full.copy(date = "2019-11")).date)
    }

    @Test
    fun invalidDateIsTreatedAsUnknownInsteadOfCrashing() {
        assertNull(roundTrip(full.copy(date = "last spring")).date)
    }

    @Test
    fun statusIsNormalized() {
        assertEquals(IpsImmunizationStatus.COMPLETED, roundTrip(full.copy(status = "bogus")).status)
        assertEquals(IpsImmunizationStatus.NOT_DONE, roundTrip(full.copy(status = "not-done")).status)
        assertEquals(IpsImmunizationStatus.ENTERED_IN_ERROR, roundTrip(full.copy(status = "entered-in-error")).status)
    }

    @Test
    fun freeTextVaccineWithoutCodeRoundTrips() {
        val im = IpsImmunization(id = "im-text", code = null, system = null, text = "Vaccin du village, 1985")
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(im, patientUrn).build())
        assertTrue(json.contains("\"text\": \"Vaccin du village, 1985\""))
        val back = roundTrip(im)
        assertNull(back.code)
        assertEquals("Vaccin du village, 1985", back.text)
        assertEquals("Vaccin du village, 1985", back.label())
    }

    @Test
    fun minimalEntryOmitsOptionalElements() {
        val im = IpsImmunization(id = "im-min", code = "836374004", display = "Hepatitis B vaccine", date = "2001-01-01")
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(im, patientUrn).build())
        assertTrue(!json.contains("lotNumber"))
        assertTrue(!json.contains("manufacturer"))
        assertTrue(!json.contains("protocolApplied"))
        assertTrue(!json.contains("\"note\""))
        assertEquals(im, roundTrip(im))
    }

    @Test
    fun stableUrnsAreDeterministic() {
        val a = IpsFhirCodec.immunizationUrn("demo_kurodo", "im-1")
        val b = IpsFhirCodec.immunizationUrn("demo_kurodo", "im-1")
        val c = IpsFhirCodec.immunizationUrn("demo_kurodo", "im-2")
        val d = IpsFhirCodec.immunizationUrn("demo_haru", "im-1")
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(a, d)
        assertTrue(a.startsWith("urn:uuid:"))
        assertEquals("urn:uuid:".length + 36, a.length)
    }

    @Test
    fun parseBundleRejectsGarbage() {
        assertNull(IpsFhirCodec.parseBundle(null))
        assertNull(IpsFhirCodec.parseBundle(""))
        assertNull(IpsFhirCodec.parseBundle("{ not json"))
        assertNull(IpsFhirCodec.parseBundle("{\"resourceType\":\"Patient\"}"))
    }

    // ── Bundle level, through the real builder ──────────────────────────

    private fun hydratedStub(sid: String): HydratedProfile = HydratedProfile(
        raw = JemmaProfileJ(j = "1.2", sid = sid, p = JPatient(gn = "Kurodo", fn = "Henro", gs = "M", bd = "1979-04-04")),
        uiLang = "en",
        allergies = emptyList(),
        medications = emptyList(),
        conditions = emptyList(),
        ddiAlerts = emptyList(),
        allergyAlerts = emptyList(),
        drugDiseaseAlerts = emptyList(),
        hydrationMs = 0L,
    )

    @Test
    fun bundleBuilderEmbedsNativeImmunizationsAndSection() {
        val native = IpsNativePillars(immunizations = listOf(full, full.copy(id = "im-test-2", code = "836374004", display = "Hepatitis B vaccine", date = "2001-01-01", doseNumber = null, seriesDoses = null)))
        val json = JemmaFhirBundleBuilder.build(hydratedStub("demo_test"), native)

        val bundle = IpsFhirCodec.parseBundle(json)
        assertNotNull("builder output must parse back as a Bundle", bundle)
        bundle!!

        val back = IpsFhirCodec.nativeOf(bundle)
        assertEquals(native.immunizations, back.immunizations)

        val composition = IpsFhirCodec.resourcesOf(bundle).filterIsInstance<Composition>().single()
        val section = composition.section.single { s ->
            s.code?.coding?.any { it.code?.value == IpsFhirCodec.LOINC_SECTION_IMMUNIZATIONS } == true
        }
        val fullUrls = bundle.entry.mapNotNull { it.fullUrl?.value }.toSet()
        val refs = section.entry.mapNotNull { it.reference?.value }
        assertEquals(2, refs.size)
        assertTrue("section entries must point at bundle fullUrls", fullUrls.containsAll(refs))
        assertEquals(setOf(IpsFhirCodec.immunizationUrn("demo_test", "im-test-1"), IpsFhirCodec.immunizationUrn("demo_test", "im-test-2")), refs.toSet())
    }

    @Test
    fun bundleWithoutNativePillarsHasNoImmunizationSection() {
        val json = JemmaFhirBundleBuilder.build(hydratedStub("demo_empty"))
        val bundle = IpsFhirCodec.parseBundle(json)!!
        assertTrue(IpsFhirCodec.nativeOf(bundle).isEmpty)
        val composition = IpsFhirCodec.resourcesOf(bundle).filterIsInstance<Composition>().single()
        assertTrue(composition.section.none { s -> s.code?.coding?.any { it.code?.value == IpsFhirCodec.LOINC_SECTION_IMMUNIZATIONS } == true })
    }

    @Test
    fun bundleUrnsAreStableAcrossRebuilds() {
        val native = IpsNativePillars(immunizations = listOf(full))
        val b1 = IpsFhirCodec.parseBundle(JemmaFhirBundleBuilder.build(hydratedStub("demo_stable"), native))!!
        val b2 = IpsFhirCodec.parseBundle(JemmaFhirBundleBuilder.build(hydratedStub("demo_stable"), native))!!
        val urls1 = b1.entry.mapNotNull { it.fullUrl?.value }
        val urls2 = b2.entry.mapNotNull { it.fullUrl?.value }
        assertEquals(urls1, urls2)
        assertEquals(urls1.size, urls1.toSet().size)
    }

    @Test
    fun bundleTypeIsDocumentWithCompositionFirst() {
        val bundle: Bundle = IpsFhirCodec.parseBundle(JemmaFhirBundleBuilder.build(hydratedStub("demo_doc")))!!
        assertEquals("document", bundle.type.value?.getCode())
        assertTrue(bundle.entry.first().resource is Composition)
    }
}
