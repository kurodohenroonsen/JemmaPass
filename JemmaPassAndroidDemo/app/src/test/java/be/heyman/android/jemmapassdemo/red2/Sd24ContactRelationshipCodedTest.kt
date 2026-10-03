/*
 * RED TEST (wave 2) — SD-24, found while auditing device cycle 25 (json/demo_kurodo.fhir.json) :
 *   "contact": [{ "relationship": [{ "text": "FRND" }], ... }]
 * The relation of an emergency contact is a code of the app's own catalogue (IpsRelationshipCatalog,
 * HL7 v3 RoleCode : FRND, SPS, CHILD, …), but the IPS document writes it as free text. A hospital
 * system reads the word "FRND" ; a person reads nothing useful.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * Expected : a catalogue code travels as a coding (system + code) with a readable text ;
 * a relation the person typed freely stays text only, word for word.
 */
package be.heyman.android.jemmapassdemo.red2

import be.heyman.android.jemmapassdemo.qr.JContact
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd24ContactRelationshipCodedTest {

    private fun relationship(relation: String): JSONObject {
        val profile = JemmaProfileJ(
            j = "1.2", sid = "qa_contact",
            p = JPatient(gn = "Kurodo", ct = listOf(JContact(n = "Kamekichi", r = relation, p = "+32 2 000 00 01"))),
        )
        val entries = JSONObject(JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(profile))).getJSONArray("entry")
        val patient = (0 until entries.length())
            .map { entries.getJSONObject(it).getJSONObject("resource") }
            .single { it.getString("resourceType") == "Patient" }
        return patient.getJSONArray("contact").getJSONObject(0).getJSONArray("relationship").getJSONObject(0)
    }

    @Test
    fun `SD-24 UC-PAT-016 a relation of the catalogue is exported as a code with its system`() {
        for (code in listOf("FRND", "SPS", "CHILD")) {
            val rel = relationship(code)
            val codings = rel.optJSONArray("coding")
            assertTrue(
                "The relation '$code' is a catalogue code but the document holds $rel : it must be a coding (system + code), not a bare word.",
                codings != null && codings.length() == 1,
            )
            val coding = codings!!.getJSONObject(0)
            assertEquals(code, coding.optString("code"))
            assertTrue("The coding of '$code' needs its code system URI.", coding.optString("system").startsWith("http"))
        }
    }

    @Test
    fun `SD-24 UC-PAT-016 the text of a coded relation is a word a person can read`() {
        val rel = relationship("FRND")
        assertNotEquals(
            "The text of the relation is the code itself : a caregiver reading the document sees 'FRND' instead of 'friend'.",
            "FRND", rel.optString("text"),
        )
        assertTrue("A coded relation must also carry a readable text.", rel.optString("text").isNotBlank())
    }

    @Test
    fun `SD-24 UC-PAT-017 a relation typed freely stays text only and word for word`() {
        // Guard : green today, must stay green.
        val rel = relationship("voisine du 3e étage")
        assertEquals("voisine du 3e étage", rel.optString("text"))
        val codings = rel.optJSONArray("coding")
        assertTrue("Free text must never be given a code.", codings == null || codings.length() == 0)
    }
}
