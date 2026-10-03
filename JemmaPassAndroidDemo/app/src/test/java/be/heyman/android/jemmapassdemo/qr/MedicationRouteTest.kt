/*
 * MedicationRouteTest.kt — UC-MED-ROUTE-01..04 : an inhaled medication has its own route
 * value ("H"), is never stored or exported as "I" (injection), and entries already stored
 * as "I" keep meaning injection.
 */
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.kb.HydratedMedication
import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.kb.MedicationRoute
import be.heyman.android.jemmapassdemo.kb.mapKbRouteToShortCode
import be.heyman.android.jemmapassdemo.pillars.IpsRouteCatalog
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationRouteTest {

    private fun med(raw: JMedication): HydratedMedication = HydratedMedication(
        raw = raw, resolvedConcept = null, displayLocalized = raw.displayLabel ?: raw.c.orEmpty(),
        timing = raw.t, doseValue = raw.v, doseUnit = raw.u,
        route = MedicationRoute.UNKNOWN,
        atcCode = null, allAtcCodes = emptyList(), rxnormCui = null,
    )

    private fun hydrated(vararg meds: JMedication): HydratedProfile = HydratedProfile(
        raw = JemmaProfileJ(j = "1.2", sid = "demo_route", p = JPatient(gn = "Haru", gs = "F"), md = meds.toList()),
        uiLang = "en", allergies = emptyList(), medications = meds.map { med(it) }, conditions = emptyList(),
        ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
    )

    /** `Dosage.route` of each MedicationStatement, in `md[]` order. */
    private fun routes(vararg meds: JMedication): List<JSONObject?> {
        val entries = JSONObject(JemmaFhirBundleBuilder.build(hydrated(*meds))).getJSONArray("entry")
        return (0 until entries.length())
            .map { entries.getJSONObject(it).getJSONObject("resource") }
            .filter { it.getString("resourceType") == "MedicationStatement" }
            .map { it.getJSONArray("dosage").getJSONObject(0).optJSONObject("route") }
    }

    private fun code(route: JSONObject?): String? =
        route?.optJSONArray("coding")?.getJSONObject(0)?.getString("code")

    @Test
    fun `UC-MED-ROUTE-01 KB inhalation forms map to the inhaled route, not to injection`() {
        for (kb in listOf("inhal.aerosol", "inhal.powder", "inhal.solution", " Inhal.Aerosol ", "inhalation")) {
            assertEquals("kb='$kb'", "H", mapKbRouteToShortCode(kb))
        }
        assertEquals(IpsRouteCatalog.INHALED, mapKbRouteToShortCode("inhal.powder"))
        // The other mappings are unchanged.
        assertEquals("O", mapKbRouteToShortCode("oral"))
        assertEquals("O", mapKbRouteToShortCode("sublingual"))
        assertEquals("I", mapKbRouteToShortCode("parenteral"))
        assertEquals("I", mapKbRouteToShortCode("s.c. implant"))
        assertEquals("T", mapKbRouteToShortCode("transdermal"))
        assertEquals("T", mapKbRouteToShortCode("nasal"))
        assertNull(mapKbRouteToShortCode("ophthalmic"))
        assertNull(mapKbRouteToShortCode(""))
        assertNull(mapKbRouteToShortCode(null))
    }

    @Test
    fun `UC-MED-ROUTE-02 the catalog offers inhaled as a distinct value and I stays injection`() {
        val inhaled = IpsRouteCatalog.byShortCode("H")!!
        assertEquals("447694001", inhaled.snomedCode)
        assertEquals("Inhaled", inhaled.displayEn)
        // PROTOCOL 9.1 : the French and Japanese labels are interface texts, read from the string resources.
        val labels = be.heyman.android.jemmapassdemo.testsupport.ResXmlCodeLabels(
            be.heyman.android.jemmapassdemo.testsupport.ResXmlCodeLabels.locate()!!,
        )
        assertEquals("Inhalée", labels.getLabel(IpsRouteCatalog.CODE_SYSTEM, inhaled.snomedCode, "fr"))
        assertEquals("吸入", labels.getLabel(IpsRouteCatalog.CODE_SYSTEM, inhaled.snomedCode, "ja"))
        assertEquals("Inhaled", IpsRouteCatalog.getDisplay("h", "en-GB"))

        // No silent migration: a stored "I" still reads as an injection.
        val injection = IpsRouteCatalog.byShortCode("I")!!
        assertEquals("Injection", injection.displayEn)
        assertNotEquals(inhaled.snomedCode, injection.snomedCode)
        assertNotEquals(inhaled.emoji, injection.emoji)

        // One entry per short code; the four historical values are still there.
        val codes = IpsRouteCatalog.ALL.map { it.shortCode }
        assertEquals(codes.toSet().size, codes.size)
        assertTrue(codes.containsAll(listOf("O", "I", "T", "S", "H")))
    }

    @Test
    fun `UC-MED-ROUTE-03 an inhaler is exported as the respiratory route in the Bundle`() {
        val r = routes(
            JMedication(c = "R03AC02", displayLabel = "Salbutamol", r = mapKbRouteToShortCode("inhal.aerosol")),
            JMedication(c = "R03AC02", displayLabel = "Salbutamol", r = "h"),
            JMedication(c = "B01AB05", displayLabel = "Enoxaparin", r = "I"),
        )
        for (i in 0..1) {
            assertEquals("447694001", code(r[i]))
            assertEquals("Inhalation", r[i]!!.getString("text"))
            assertEquals("http://snomed.info/sct", r[i]!!.getJSONArray("coding").getJSONObject(0).getString("system"))
        }
        // "I" keeps meaning injection: text only, no inhalation code, no intravenous code.
        assertFalse(r[2]!!.has("coding"))
        assertEquals("Injection", r[2]!!.getString("text"))
        assertFalse(r.joinToString("\n") { it.toString() }.contains("47625008"))
    }

    @Test
    fun `UC-MED-ROUTE-04 every catalog route is handled by the Bundle and unknown ones fall back to text`() {
        val catalog = IpsRouteCatalog.ALL
        val r = routes(*catalog.map { JMedication(c = "R03AC02", r = it.shortCode) }.toTypedArray())
        catalog.forEachIndexed { i, entry ->
            // Coded routes agree with the catalog; "I" (unspecified injection) is text only.
            if (entry.shortCode == "I") assertNull(code(r[i])) else assertEquals(entry.shortCode, entry.snomedCode, code(r[i]))
        }
        // A value nobody knows: label and Bundle text are the stored value, no code invented.
        assertEquals("X", IpsRouteCatalog.getDisplay("X", "en"))
        assertNull(IpsRouteCatalog.byShortCode("X"))
        val unknown = routes(JMedication(c = "R03AC02", r = "X"), JMedication(c = "R03AC02", r = " "))
        assertNull(code(unknown[0]))
        assertEquals("X", unknown[0]!!.getString("text"))
        assertNull(unknown[1])
    }
}
