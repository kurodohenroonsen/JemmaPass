/*
 * IpsPregnancyCodecTest.kt — 🤰 History of Pregnancy (sprint 6 foundation):
 * status / EDD / outcome Observations ⇄ IpsPregnancyObs, `_j.pg` projection,
 * Bundle wiring (section 10162-6), and no leak into the 🧪 results pillar.
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import dev.ohs.fhir.model.r4.Composition
import dev.ohs.fhir.model.r4.Observation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsPregnancyCodecTest {

    private val patientUrn = "urn:uuid:00000000-0000-0000-0000-000000000001"

    private val status = IpsPregnancyObs(id = "pg-st", code = IpsPregnancyCodes.STATUS, valueCode = IpsPregnancyCodes.PREGNANT, date = "2026-09-20", note = "First trimester")
    private val edd = IpsPregnancyObs(id = "pg-edd", code = "11779-6", valueDate = "2027-04-02", date = "2026-09-20")
    private val live = IpsPregnancyObs(id = "pg-live", code = "11636-8", count = 2, date = "2026-09-20")

    private fun roundTrip(o: IpsPregnancyObs): IpsPregnancyObs {
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(o, patientUrn).build())
        val parsed = IpsFhirCodec.json.decodeFromString(json)
        assertTrue(parsed is Observation)
        return IpsFhirCodec.pregnancyFromFhir(parsed as Observation)!!
    }

    @Test
    fun kindsAndLabels() {
        assertEquals(IpsPregnancyKind.STATUS, status.kind)
        assertEquals(IpsPregnancyKind.EDD, edd.kind)
        assertEquals(IpsPregnancyKind.OUTCOME, live.kind)
        assertEquals("Pregnant", status.valueLabel())
        assertEquals("2027-04-02", edd.valueLabel())
        assertEquals("2", live.valueLabel())
        assertTrue(IpsPregnancyCodes.isPregnancyCode("33065-4"))
        assertFalse(IpsPregnancyCodes.isPregnancyCode("2823-3"))
    }

    @Test
    fun roundTrips() {
        assertEquals(status, roundTrip(status))
        assertEquals(edd, roundTrip(edd))
        assertEquals(live, roundTrip(live))
        val undated = live.copy(id = "pg-x", date = null, count = 0)
        assertEquals(undated, roundTrip(undated))
    }

    @Test
    fun jsonCarriesTheIpsProfilesAndValueTypes() {
        val st = IpsFhirCodec.encode(IpsFhirCodec.toFhir(status, patientUrn).build())
        assertTrue(st.contains(IpsFhirCodec.PROFILE_PREGNANCY_STATUS_UV_IPS))
        assertTrue(st.contains("\"code\": \"82810-3\""))
        assertTrue(st.contains("\"code\": \"LA15173-0\""))
        assertTrue(st.contains("\"effectiveDateTime\": \"2026-09-20\""))
        val e = IpsFhirCodec.encode(IpsFhirCodec.toFhir(edd, patientUrn).build())
        assertTrue(e.contains(IpsFhirCodec.PROFILE_PREGNANCY_EDD_UV_IPS))
        assertTrue(e.contains("\"valueDateTime\": \"2027-04-02\""))
        val o = IpsFhirCodec.encode(IpsFhirCodec.toFhir(live, patientUrn).build())
        assertTrue(o.contains(IpsFhirCodec.PROFILE_PREGNANCY_OUTCOME_UV_IPS))
        assertTrue(o.contains("\"valueInteger\": 2"))
    }

    @Test
    fun projection() {
        val j = status.toJEntry()
        assertEquals("82810-3", j.c)
        assertEquals("LA15173-0", j.valueCode)
        assertNull(j.value)
        assertEquals("2027-04-02", edd.toJEntry().value)
        assertEquals("2", live.toJEntry().value)
        val back = IpsPregnancyObs.fromJEntry(live.toJEntry(), 0)!!
        assertEquals(live.copy(id = back.id), back)
        assertNull("non-pregnancy codes are ignored", IpsPregnancyObs.fromJEntry(live.toJEntry().copy(c = "2823-3")))
    }

    @Test
    fun bundleSectionAndNoLeakIntoResults() {
        val hydrated = HydratedProfile(
            raw = JemmaProfileJ(j = "1.2", sid = "demo_pg", p = JPatient(gn = "Haru", gs = "F")),
            uiLang = "en", allergies = emptyList(), medications = emptyList(), conditions = emptyList(),
            ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
        )
        val native = IpsNativePillars(pregnancy = listOf(status, edd, live))
        val bundle = IpsFhirCodec.parseBundle(JemmaFhirBundleBuilder.build(hydrated, native))!!
        val back = IpsFhirCodec.nativeOf(bundle)
        assertEquals(native.pregnancy, back.pregnancy)
        assertTrue(back.results.isEmpty())
        val comp = IpsFhirCodec.resourcesOf(bundle).filterIsInstance<Composition>().single()
        val sec = comp.section.single { s -> s.code?.coding?.any { it.code?.value == IpsFhirCodec.LOINC_SECTION_PREGNANCY } == true }
        assertEquals(3, sec.entry.size)
        assertEquals(setOf("pg-st", "pg-edd", "pg-live").map { IpsFhirCodec.pregnancyUrn("demo_pg", it) }.toSet(),
            sec.entry.mapNotNull { it.reference?.value }.toSet())
    }
}
