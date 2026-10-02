/*
 * FhirMedicationBundleTest.kt — medications in the FHIR IPS Bundle:
 * UC-FHIR-008 (status), UC-FHIR-024 (decimal comma), UC-FHIR-026 (route).
 */
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.kb.HydratedMedication
import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.kb.MedicationRoute
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FhirMedicationBundleTest {

    private fun med(raw: JMedication): HydratedMedication = HydratedMedication(
        raw = raw, resolvedConcept = null, displayLocalized = raw.displayLabel ?: raw.c.orEmpty(),
        timing = raw.t, doseValue = raw.v, doseUnit = raw.u,
        // What the hydrator derives from `r` today; the builder must not depend on it.
        route = MedicationRoute.UNKNOWN,
        atcCode = null, allAtcCodes = emptyList(), rxnormCui = null,
    )

    private fun hydrated(vararg meds: JMedication): HydratedProfile = HydratedProfile(
        raw = JemmaProfileJ(j = "1.2", sid = "demo_med", p = JPatient(gn = "Haru", gs = "F"), md = meds.toList()),
        uiLang = "en", allergies = emptyList(), medications = meds.map { med(it) }, conditions = emptyList(),
        ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
    )

    /** The MedicationStatement resources of the Bundle, in `md[]` order. */
    private fun statements(vararg meds: JMedication): List<JSONObject> {
        val entries = JSONObject(JemmaFhirBundleBuilder.build(hydrated(*meds))).getJSONArray("entry")
        return (0 until entries.length())
            .map { entries.getJSONObject(it).getJSONObject("resource") }
            .filter { it.getString("resourceType") == "MedicationStatement" }
    }

    private fun dosage(statement: JSONObject): JSONObject = statement.getJSONArray("dosage").getJSONObject(0)

    private fun doseQuantity(statement: JSONObject): JSONObject? =
        dosage(statement).optJSONArray("doseAndRate")?.getJSONObject(0)?.getJSONObject("doseQuantity")

    @Test
    fun `UC-FHIR-008 medication status follows the profile entry`() {
        val codes = listOf("active", "completed", "entered-in-error", "intended", "stopped", "on-hold", "unknown", "not-taken")
        val meds = codes.map { JMedication(c = "C09AA05", displayLabel = "Ramipril", status = it) }
        assertEquals(codes, statements(*meds.toTypedArray()).map { it.getString("status") })

        val edge = statements(
            JMedication(c = "C09AA05", status = null),
            JMedication(c = "C09AA05", status = " Stopped "),
            JMedication(c = "C09AA05", status = "on_hold"),
            JMedication(c = "C09AA05", status = "paused-by-me"),
        ).map { it.getString("status") }
        // No status = the form's default; a value outside the value set is never exported as active.
        assertEquals(listOf("active", "stopped", "on-hold", "unknown"), edge)
    }

    @Test
    fun `UC-FHIR-024 decimal comma dose is exported as a quantity`() {
        val st = statements(
            JMedication(c = "B01AA03", v = "0,5", u = "mg"),
            JMedication(c = "B01AA03", v = "2,5", u = "mL"),
            JMedication(c = "B01AA03", v = "0,5 mg"),
            JMedication(c = "B01AA03", v = "1.25", u = "mg"),
            JMedication(c = "B01AA03", v = "1", u = "tab"),
        )
        val q0 = doseQuantity(st[0])!!
        assertEquals(0.5, q0.getDouble("value"), 0.0)
        assertEquals("mg", q0.getString("unit"))
        assertEquals("http://unitsofmeasure.org", q0.getString("system"))
        assertEquals("mg", q0.getString("code"))
        assertEquals(2.5, doseQuantity(st[1])!!.getDouble("value"), 0.0)
        // Unit typed in the value field
        val q2 = doseQuantity(st[2])!!
        assertEquals(0.5, q2.getDouble("value"), 0.0)
        assertEquals("mg", q2.getString("code"))
        assertEquals(1.25, doseQuantity(st[3])!!.getDouble("value"), 0.0)
        // Count units stay outside UCUM
        val q4 = doseQuantity(st[4])!!
        assertEquals(1.0, q4.getDouble("value"), 0.0)
        assertFalse(q4.has("system"))
        // The text keeps what the user typed
        assertTrue(dosage(st[0]).getString("text").contains("0,5"))
    }

    @Test
    fun `UC-FHIR-024 unreadable or ambiguous dose stays text only`() {
        val st = statements(
            JMedication(c = "B01AA03", v = "1/2", u = "tab"),
            JMedication(c = "B01AA03", v = "1,000", u = "mg"),
            JMedication(c = "B01AA03"),
        )
        st.forEach { assertNull(doseQuantity(it)) }
        assertTrue(dosage(st[0]).getString("text").contains("1/2"))
        assertEquals("0.5" to "mg", JemmaFhirBundleBuilder.parseDose(" 0,5 ", " mg "))
        assertEquals("0.500" to "", JemmaFhirBundleBuilder.parseDose("0,500", null))
        assertNull(JemmaFhirBundleBuilder.parseDose("12,500", "mg"))
        assertNull(JemmaFhirBundleBuilder.parseDose("-1", "mg"))
    }

    @Test
    fun `UC-FHIR-026 route is coded only when it is known`() {
        val st = statements(
            JMedication(c = "R03AC02", r = "O"),
            JMedication(c = "R03AC02", r = "T"),
            JMedication(c = "R03AC02", r = "S"),
            JMedication(c = "R03AC02", r = "inhaled"),
            JMedication(c = "R03AC02", r = "I"),
            JMedication(c = "R03AC02", r = "rectal"),
            JMedication(c = "R03AC02", r = null),
        )
        fun route(i: Int): JSONObject? = dosage(st[i]).optJSONObject("route")
        fun code(i: Int): String = route(i)!!.getJSONArray("coding").getJSONObject(0).getString("code")
        fun system(i: Int): String = route(i)!!.getJSONArray("coding").getJSONObject(0).getString("system")

        assertEquals("26643006", code(0))
        assertEquals("6064005", code(1))
        assertEquals("34206005", code(2))
        (0..3).forEach { assertEquals("http://snomed.info/sct", system(it)) }
        // Inhaled is the respiratory route, never an injection / intravenous code.
        assertEquals("447694001", code(3))
        assertEquals("Inhalation", route(3)!!.getString("text"))
        // "I" covers every injection (and, today, inhalers): text only, no code asserted.
        assertFalse(route(4)!!.has("coding"))
        assertEquals("Injection", route(4)!!.getString("text"))
        // Unknown route: the text as stored, no code.
        assertFalse(route(5)!!.has("coding"))
        assertEquals("rectal", route(5)!!.getString("text"))
        // No route: nothing emitted (no "UNKNOWN" placeholder).
        assertNull(route(6))
        val json = st.joinToString("\n") { it.toString() }
        assertFalse(json.contains("47625008"))
        assertFalse(json.contains("UNKNOWN"))
    }
}
