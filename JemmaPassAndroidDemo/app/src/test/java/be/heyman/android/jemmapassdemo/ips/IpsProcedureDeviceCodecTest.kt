/*
 * IpsProcedureDeviceCodecTest.kt — JVM round trips for the sprint-2 pillars :
 * Procedure ⇄ IpsProcedure, DeviceUseStatement+Device ⇄ IpsDevice, Bundle wiring.
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import dev.ohs.fhir.model.r4.Composition
import dev.ohs.fhir.model.r4.Device
import dev.ohs.fhir.model.r4.DeviceUseStatement
import dev.ohs.fhir.model.r4.Procedure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsProcedureDeviceCodecTest {

    private val patientUrn = "urn:uuid:00000000-0000-0000-0000-000000000001"

    private val appendectomy = IpsProcedure(
        id = "pr-1", code = "80146002", system = IpsCodeSystems.SNOMED, display = "Appendectomy",
        date = "1995-07-12", status = IpsProcedureStatus.COMPLETED, bodySite = "Abdomen",
        outcome = "Uneventful recovery", performer = "Dr. Dupont", location = "CHU Namur", note = "Laparoscopic",
    )

    private val pacemaker = IpsDevice(
        id = "dv-1", code = "14106009", system = IpsCodeSystems.SNOMED, display = "Cardiac pacemaker",
        udi = "(01)00889842001234(11)210315(21)SN12345", manufacturer = "Medtronic", model = "Azure XT DR MRI",
        serial = "SN12345", date = "2021-03-15", status = IpsDeviceStatus.ACTIVE, bodySite = "Left pectoral", note = "Dual chamber",
    )

    private fun roundTripProcedure(pr: IpsProcedure): IpsProcedure {
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(pr, patientUrn).build())
        val parsed = IpsFhirCodec.json.decodeFromString(json)
        assertTrue(parsed is Procedure)
        return IpsFhirCodec.fromFhir(parsed as Procedure)
    }

    private fun roundTripDevice(dv: IpsDevice): IpsDevice {
        val deviceUrn = "urn:uuid:00000000-0000-0000-0000-0000000000dd"
        val device = IpsFhirCodec.json.decodeFromString(IpsFhirCodec.encode(IpsFhirCodec.toFhirDevice(dv, patientUrn).build())) as Device
        val st = IpsFhirCodec.json.decodeFromString(IpsFhirCodec.encode(IpsFhirCodec.toFhirUseStatement(dv, patientUrn, deviceUrn).build())) as DeviceUseStatement
        return IpsFhirCodec.fromFhir(st, device)
    }

    // ── Procedure ────────────────────────────────────────────────────────

    @Test
    fun procedureFullRoundTrip() {
        assertEquals(appendectomy, roundTripProcedure(appendectomy))
    }

    @Test
    fun procedureJsonCarriesIpsEssentials() {
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(appendectomy, patientUrn).build())
        assertTrue(json.contains("\"resourceType\": \"Procedure\""))
        assertTrue(json.contains("\"status\": \"completed\""))
        assertTrue(json.contains("\"performedDateTime\": \"1995-07-12\""))
        assertTrue(json.contains(IpsFhirCodec.PROFILE_PROCEDURE_UV_IPS))
        assertTrue(json.contains("\"code\": \"80146002\""))
        assertTrue(json.contains(patientUrn))
    }

    @Test
    fun procedureUnknownDateAndFreeTextAndStatuses() {
        val free = IpsProcedure(id = "pr-t", code = null, system = null, text = "Opération du genou, années 80", status = "in-progress")
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(free, patientUrn).build())
        assertTrue(json.contains("\"performedString\": \"unknown\""))
        val back = roundTripProcedure(free)
        assertNull(back.code)
        assertNull(back.date)
        assertEquals("Opération du genou, années 80", back.text)
        assertEquals(IpsProcedureStatus.IN_PROGRESS, back.status)
        assertEquals(IpsProcedureStatus.COMPLETED, roundTripProcedure(appendectomy.copy(status = "bogus")).status)
        assertEquals(IpsProcedureStatus.NOT_DONE, roundTripProcedure(appendectomy.copy(status = "not-done")).status)
        assertEquals("2010", roundTripProcedure(appendectomy.copy(date = "2010")).date)
    }

    @Test
    fun procedureProjectionContract() {
        val e = appendectomy.toJEntry()
        assertEquals("80146002", e.c)
        assertEquals("Appendectomy", e.displayLabel)
        assertEquals("1995-07-12", e.date)
        assertEquals("Laparoscopic", e.d)
        assertNull(e.codeSystem)
        assertNull(e.status)
        val back = IpsProcedure.fromJEntry(e)
        assertEquals(appendectomy.code, back.code)
        assertEquals(appendectomy.display, back.display)
        assertEquals(appendectomy.date, back.date)
        assertEquals(appendectomy.note, back.note)
        assertNull(back.performer)
        assertEquals("not-done", appendectomy.copy(status = "not-done").toJEntry().status)
    }

    // ── Device ───────────────────────────────────────────────────────────

    @Test
    fun deviceFullRoundTrip() {
        assertEquals(pacemaker, roundTripDevice(pacemaker))
    }

    @Test
    fun deviceJsonCarriesIpsEssentials() {
        val device = IpsFhirCodec.encode(IpsFhirCodec.toFhirDevice(pacemaker, patientUrn).build())
        assertTrue(device.contains("\"resourceType\": \"Device\""))
        assertTrue(device.contains(IpsFhirCodec.PROFILE_DEVICE_UV_IPS))
        assertTrue(device.contains("\"deviceIdentifier\": \"(01)00889842001234(11)210315(21)SN12345\""))
        assertTrue(device.contains("\"manufacturer\": \"Medtronic\""))
        assertTrue(device.contains("\"status\": \"active\""))
        val st = IpsFhirCodec.encode(IpsFhirCodec.toFhirUseStatement(pacemaker, patientUrn, "urn:uuid:dev").build())
        assertTrue(st.contains("\"resourceType\": \"DeviceUseStatement\""))
        assertTrue(st.contains(IpsFhirCodec.PROFILE_DEVICE_USE_STATEMENT_UV_IPS))
        assertTrue(st.contains("\"timingDateTime\": \"2021-03-15\""))
        assertTrue(st.contains("\"reference\": \"urn:uuid:dev\""))
    }

    @Test
    fun deviceStatusesAndFreeText() {
        val inactive = roundTripDevice(pacemaker.copy(status = IpsDeviceStatus.INACTIVE))
        assertEquals(IpsDeviceStatus.INACTIVE, inactive.status)
        assertEquals(IpsDeviceStatus.ENTERED_IN_ERROR, roundTripDevice(pacemaker.copy(status = "entered-in-error")).status)
        assertEquals(IpsDeviceStatus.ACTIVE, roundTripDevice(pacemaker.copy(status = "weird")).status)

        val free = IpsDevice(id = "dv-t", code = null, system = null, text = "Appareil auditif gauche", date = null)
        val back = roundTripDevice(free)
        assertNull(back.code)
        assertNull(back.date)
        assertEquals("Appareil auditif gauche", back.text)
        assertEquals("Appareil auditif gauche", back.label())
    }

    @Test
    fun deviceProjectionContract() {
        val e = pacemaker.toJEntry()
        assertEquals("14106009", e.c)
        assertEquals("2021-03-15", e.date)
        assertNull("active is the default → omitted", e.status)
        assertEquals("inactive", pacemaker.copy(status = IpsDeviceStatus.INACTIVE).toJEntry().status)
        val back = IpsDevice.fromJEntry(e)
        assertEquals(pacemaker.code, back.code)
        assertEquals(IpsDeviceStatus.ACTIVE, back.status)
        assertNull("UDI is not carried by the QR", back.udi)
    }

    // ── Bundle wiring ────────────────────────────────────────────────────

    private fun hydratedStub(sid: String): HydratedProfile = HydratedProfile(
        raw = JemmaProfileJ(j = "1.2", sid = sid, p = JPatient(gn = "Haru", fn = "Tanaka", gs = "F", bd = "1946-02-08")),
        uiLang = "en", allergies = emptyList(), medications = emptyList(), conditions = emptyList(),
        ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
    )

    private fun sectionRefs(comp: Composition, loinc: String): List<String> =
        comp.section.filter { s -> s.code?.coding?.any { it.code?.value == loinc } == true }
            .flatMap { s -> s.entry.mapNotNull { it.reference?.value } }

    @Test
    fun bundleEmbedsProceduresAndDevicesWithSections() {
        val native = IpsNativePillars(
            procedures = listOf(appendectomy, appendectomy.copy(id = "pr-2", code = "73761001", display = "Colonoscopy", date = "2019-05-03")),
            devices = listOf(pacemaker),
        )
        val json = JemmaFhirBundleBuilder.build(hydratedStub("demo_test"), native)
        val bundle = IpsFhirCodec.parseBundle(json)
        assertNotNull(bundle); bundle!!

        val back = IpsFhirCodec.nativeOf(bundle)
        assertEquals(native.procedures, back.procedures)
        assertEquals(native.devices, back.devices)
        assertTrue(back.immunizations.isEmpty())

        val comp = IpsFhirCodec.resourcesOf(bundle).filterIsInstance<Composition>().single()
        val fullUrls = bundle.entry.mapNotNull { it.fullUrl?.value }.toSet()
        val prRefs = sectionRefs(comp, IpsFhirCodec.LOINC_SECTION_PROCEDURES)
        val dvRefs = sectionRefs(comp, IpsFhirCodec.LOINC_SECTION_DEVICES)
        assertEquals(setOf(IpsFhirCodec.procedureUrn("demo_test", "pr-1"), IpsFhirCodec.procedureUrn("demo_test", "pr-2")), prRefs.toSet())
        assertEquals(listOf(IpsFhirCodec.deviceUseStatementUrn("demo_test", "dv-1")), dvRefs)
        assertTrue(fullUrls.containsAll(prRefs + dvRefs))
        assertTrue("the Device resource itself is in the bundle", fullUrls.contains(IpsFhirCodec.deviceUrn("demo_test", "dv-1")))
        assertTrue(sectionRefs(comp, IpsFhirCodec.LOINC_SECTION_IMMUNIZATIONS).isEmpty())
        // 1 Composition + 1 Patient + 2 Procedure + 1 Device + 1 DeviceUseStatement
        assertEquals(6, bundle.entry.size)
    }

    @Test
    fun legacyJArraysRebuildDeterministically() {
        val j = JemmaProfileJ(j = "1.2", sid = "x", pr = listOf(appendectomy.toJEntry()), dv = listOf(pacemaker.toJEntry()))
        val a = IpsNativePillars.fromJEntries(j.im, j.pr, j.dv)
        val b = IpsNativePillars.fromJEntries(j.im, j.pr, j.dv)
        assertEquals(a, b)
        assertEquals(1, a.procedures.size)
        assertEquals(1, a.devices.size)
        assertEquals("80146002", a.procedures.single().code)
        assertEquals("14106009", a.devices.single().code)
    }
}
