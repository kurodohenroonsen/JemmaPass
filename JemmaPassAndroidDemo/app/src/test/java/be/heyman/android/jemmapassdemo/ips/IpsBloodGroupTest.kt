/*
 * IpsBloodGroupTest.kt — `p.bt` → Observation 882-1 (device QA cycle 6, HL7 validator).
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsBloodGroupTest {

    @Test
    fun bloodTypeLabelsNormaliseAndMapToTheKbConfirmedSnomedCodes() {
        assertEquals("O+", IpsBloodGroup.normalize("o +"))
        assertEquals("AB-", IpsBloodGroup.normalize("AB Rh-"))
        assertEquals("A+", IpsBloodGroup.normalize("A positive"))
        assertEquals("B-", IpsBloodGroup.normalize("B neg"))
        assertNull(IpsBloodGroup.normalize("unknown"))
        assertNull(IpsBloodGroup.normalize(""))
        assertNull(IpsBloodGroup.normalize(null))
        assertEquals("278147001", IpsBloodGroup.snomedCode("O+"))
        assertEquals("278149003", IpsBloodGroup.snomedCode("A+"))
        assertEquals("278150003", IpsBloodGroup.snomedCode("B+"))
        assertEquals("278151004", IpsBloodGroup.snomedCode("AB+"))
        assertEquals("278148006", IpsBloodGroup.snomedCode("O-"))
        assertEquals("Blood group O Rh(D) positive", IpsBloodGroup.snomedDisplay("O+"))
    }

    @Test
    fun syncKeepsExactlyOneBloodGroupResult() {
        val lab = IpsResult(id = "rs-1", code = "2823-3", display = "Potassium", value = "4.1", unit = "mmol/L")
        val once = IpsBloodGroup.sync(listOf(lab), "demo_haru", "O+")
        assertEquals(2, once.size)
        assertTrue(IpsBloodGroup.isDerived(once.first()))
        assertEquals("278147001", once.first().valueCode)
        assertEquals("rs-blood-group-demo-haru", once.first().id)

        // Idempotent, and the blood type change is picked up.
        val again = IpsBloodGroup.sync(once, "demo_haru", "A-")
        assertEquals(2, again.size)
        assertEquals("278152006", again.first().valueCode)

        // Unknown blood type → the derived entry disappears, the labs stay.
        assertEquals(listOf(lab), IpsBloodGroup.sync(again, "demo_haru", null))

        // A user-authored 882-1 result wins over the derived one.
        val userBg = IpsResult(id = "rs-user", code = IpsBloodGroup.LOINC_ABO_RH, display = "ABO/Rh", valueCode = "278148006", valueDisplay = "O-")
        val withUser = IpsBloodGroup.sync(listOf(userBg, lab), "demo_haru", "O+")
        assertEquals(listOf(userBg, lab), withUser)
    }

    private fun hydrated(sid: String, bt: String?, fn: String?): HydratedProfile = HydratedProfile(
        raw = JemmaProfileJ(j = "1.2", sid = sid, p = JPatient(gn = "Kamekichi", fn = fn, gs = "M", bd = "1950-01-01", bt = bt)),
        uiLang = "en", allergies = emptyList(), medications = emptyList(), conditions = emptyList(),
        ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
    )

    @Test
    fun bundleHasAnIdentifierNoEmptyNamePartsAndNoHomeMadeExtension() {
        val native = IpsNativePillars(results = IpsBloodGroup.sync(emptyList(), "demo_kamekichi", "B+"))
        val json = JemmaFhirBundleBuilder.build(hydrated("demo_kamekichi", "B+", fn = ""), native)
        val bundle = JSONObject(json)
        // bdl-9
        val identifier = bundle.getJSONObject("identifier")
        assertEquals("urn:ietf:rfc:3986", identifier.getString("system"))
        assertEquals(IpsFhirCodec.stableUrn("demo_kamekichi|Bundle"), identifier.getString("value"))
        // Patient
        val patient = bundle.getJSONArray("entry").getJSONObject(1).getJSONObject("resource")
        assertEquals("Patient", patient.getString("resourceType"))
        val name = patient.getJSONArray("name").getJSONObject(0)
        assertFalse("blank family must be omitted", name.has("family"))
        assertEquals("Kamekichi", name.getJSONArray("given").getString(0))
        assertFalse("no home-made blood-type extension", patient.has("extension"))
        assertFalse(json.contains("jemmapass.net/fhir/StructureDefinition/blood-type"))
        // The blood type is an Observation 882-1 with the SNOMED value instead
        val parsed = IpsFhirCodec.parseBundle(json)!!
        val results = IpsFhirCodec.resultsOf(parsed)
        assertEquals(1, results.size)
        assertEquals(IpsBloodGroup.LOINC_ABO_RH, results[0].code)
        assertEquals("278150003", results[0].valueCode)
        assertFalse("UDI issuer no longer emitted", json.contains("NamingSystem/gs1"))
    }
}
