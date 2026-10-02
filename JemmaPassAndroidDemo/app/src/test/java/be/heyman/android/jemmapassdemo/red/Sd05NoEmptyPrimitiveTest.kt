/*
 * RED TEST (wave 1) — SD-05, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed
 * (certainty "read + SDK").
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * The IPS document never holds an empty FHIR primitive ("code": "", "text": "") : a hospital
 * validator rejects the document, and an allergy coded "nothing" is worse than an allergy
 * described by its label only.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JContact
import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd05NoEmptyPrimitiveTest {

    private fun resources(profile: JemmaProfileJ, type: String): List<JSONObject> {
        val json = try {
            JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(profile))
        } catch (e: Throwable) {
            throw AssertionError("building the IPS document threw ${e.javaClass.simpleName} (${e.message}) ; a missing code or name must be left out, not written empty", e)
        }
        val entries = JSONObject(json).getJSONArray("entry")
        return (0 until entries.length())
            .map { entries.getJSONObject(it).getJSONObject("resource") }
            .filter { it.getString("resourceType") == type }
    }

    /** The codes of `code.coding[]` of a resource ; a coding without `code` gives null. */
    private fun codingCodes(codeableConcept: JSONObject): List<String?> {
        val codings = codeableConcept.optJSONArray("coding") ?: return emptyList()
        return (0 until codings.length()).map { i ->
            val coding = codings.getJSONObject(i)
            if (coding.has("code")) coding.getString("code") else null
        }
    }

    @Test
    fun `SD-05 UC-FHIR-011 an allergy without a code has no empty code in the document`() {
        val profile = JemmaProfileJ(
            j = "1.2", sid = "qa_empty", p = JPatient(gn = "Haru"),
            al = listOf(JAllergy(displayLabel = "Savon de grand-mère", s = "H")),
        )
        val allergy = resources(profile, "AllergyIntolerance").single().getJSONObject("code")
        assertEquals("the label of the allergy must stay readable", "Savon de grand-mère", allergy.optString("text"))
        for (code in codingCodes(allergy)) {
            assertFalse(
                "The allergy has no code, yet the document says \"code\": \"\" under the SNOMED system. " +
                    "An empty FHIR primitive is invalid (the HL7 validator rejects it). Without a code there must be " +
                    "no coding at all : only the text label.",
                code != null && code.isBlank(),
            )
        }
    }

    @Test
    fun `SD-05 UC-FHIR-014 a medication without a code has no empty code in the document`() {
        val profile = JemmaProfileJ(
            j = "1.2", sid = "qa_empty", p = JPatient(gn = "Haru"),
            md = listOf(JMedication(displayLabel = "Tisane du jardin")),
        )
        val medication = resources(profile, "Medication").single().getJSONObject("code")
        assertEquals("the label of the medication must stay readable", "Tisane du jardin", medication.optString("text"))
        for (code in codingCodes(medication)) {
            assertFalse(
                "The medication has no code, yet the document says \"code\": \"\". An empty FHIR primitive is " +
                    "invalid. Without a code there must be no coding at all : only the text label.",
                code != null && code.isBlank(),
            )
        }
    }

    @Test
    fun `SD-05 UC-QRF-008 an emergency contact with a phone only has no empty name in the document`() {
        val profile = JemmaProfileJ(
            j = "1.2", sid = "qa_empty",
            p = JPatient(gn = "Haru", ct = listOf(JContact(p = "+81 90 0000 0000"))),
        )
        val patient = resources(profile, "Patient").single()
        val contacts = patient.optJSONArray("contact")
        assertTrue("the emergency contact must stay in the document : its phone is the way to reach someone", contacts != null && contacts.length() == 1)
        val contact = contacts!!.getJSONObject(0)
        assertTrue("the phone of the contact must stay in the document", contact.toString().contains("+81 90 0000 0000"))
        val name = contact.optJSONObject("name")
        if (name != null && name.has("text")) {
            assertFalse(
                "The contact has no name, yet the document says \"name\": { \"text\": \"\" }. An empty FHIR primitive " +
                    "is invalid. Without a name the contact must have no name element.",
                name.getString("text").isBlank(),
            )
        }
    }
}
