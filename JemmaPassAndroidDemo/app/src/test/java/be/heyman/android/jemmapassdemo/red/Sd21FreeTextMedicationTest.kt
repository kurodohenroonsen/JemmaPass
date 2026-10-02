/*
 * RED/GUARD TEST (wave 1) — SD-21, decision of the product owner on 2026-10-02 :
 * a remedy that has no code (home herbal tea, food supplement, a drug bought abroad) can be
 * recorded as free text, because the IPS allows a medication described by its text only.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * What a person needs : the remedy they wrote is in the document and on the emergency QR,
 * word for word, and never disguised as a coded drug.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.qr.JemmaTextPayloadBuilder
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import be.heyman.android.jemmapassdemo.ui.profile.medications.MedicationFormLogic
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd21FreeTextMedicationTest {

    private val label = "Tisane maison (thym, miel)"

    private val profile = JemmaProfileJ(
        j = "1.2", sid = "demo_freetext", p = JPatient(gn = "Haru", gs = "F"),
        md = listOf(
            JMedication(c = "C03CA01", displayLabel = "Furosemide 20mg"),
            JMedication(c = null, displayLabel = label, t = "1x/day"),
        ),
    )

    private fun bundle(): String = try {
        JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(profile))
    } catch (e: Throwable) {
        throw AssertionError("building the IPS document threw ${e.javaClass.simpleName} (${e.message}) for a free-text medication ; it must be exported with its text only", e)
    }

    private fun resources(type: String): List<JSONObject> {
        val entries = JSONObject(bundle()).getJSONArray("entry")
        return (0 until entries.length())
            .map { entries.getJSONObject(it).getJSONObject("resource") }
            .filter { it.getString("resourceType") == type }
    }

    @Test
    fun `SD-21 UC-MED-016 a free text remedy is in the document with its exact words`() {
        assertEquals("Both medications, coded and free text, must be exported.", 2, resources("MedicationStatement").size)
        assertTrue(
            "The words the person typed (\"$label\") must appear as text in the IPS document.",
            bundle().contains(label),
        )
    }

    @Test
    fun `SD-21 UC-MED-016 a free text remedy is never given a code`() {
        val freeText = resources("Medication").filter { it.toString().contains(label) }
        assertEquals("The free-text remedy must be one Medication described by code.text.", 1, freeText.size)
        val code = freeText.single().getJSONObject("code")
        assertEquals("code.text must hold the person's words.", label, code.optString("text"))
        val codings = code.optJSONArray("coding")
        assertTrue(
            "A remedy without a code must have no coding at all (no empty code, no invented code) : found $codings",
            codings == null || codings.length() == 0,
        )
    }

    @Test
    fun `SD-21 UC-QRT-004 a free text remedy is on the emergency text QR`() {
        val text = JemmaTextPayloadBuilder.build(ProfileFixtures.hydrated(profile), JemmaTextPayloadBuilder.Lang.EN)
        assertTrue("The emergency QR must list the free-text remedy : a rescuer needs to know everything the person takes.", text.contains(label))
        assertFalse("The emergency QR must not print the word null for a remedy without a code.", text.contains("null"))
    }

    @Test
    fun `SD-21 UC-MED-023 a stored free text remedy is reported as not verified`() {
        assertEquals(
            "A remedy without a code cannot be checked against the knowledge base : it must be named among the unverified items.",
            listOf(label),
            MedicationFormLogic.uncodedLabels(profile.md),
        )
    }
}
