/*
 * RED TEST (wave 1) — SD-03, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed
 * (certainty "read + SDK" : the failure depends on dev.ohs.fhir rejecting unknown codes, as the
 * rest of the code already assumes).
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * An imported profile with one unreadable identity value (birth date not ISO, unknown address
 * use, unknown telecom system or use) still gets its IPS document : the unreadable value is
 * left out, the document is built.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.qr.JAddress
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JTelecom
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd03UnexpectedIdentityValuesTest {

    private fun assertDocumentBuilt(what: String, patient: JPatient) {
        val profile = JemmaProfileJ(j = "1.2", sid = "qa_imp", p = patient)
        val json = try {
            JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(profile))
        } catch (e: Throwable) {
            throw AssertionError(
                "$what : building the IPS document threw ${e.javaClass.simpleName} (${e.message}). " +
                    "The profile is then saved without its document and the FHIR QR screen crashes. " +
                    "An unreadable value must be left out and the document still built.",
                e,
            )
        }
        assertNotNull("$what : the builder output is not a readable FHIR Bundle", IpsFhirCodec.parseBundle(json))
        assertTrue("$what : the patient's name is no longer in the document", json.contains("\"Haru\""))
    }

    @Test
    fun `SD-03 UC-IMP-013 a birth date that is not ISO still gives a document`() {
        for (birthDate in listOf("05/02/1956", "1956-2-5", "5 février 1956")) {
            assertDocumentBuilt("birth date '$birthDate'", JPatient(gn = "Haru", bd = birthDate))
        }
    }

    @Test
    fun `SD-03 UC-IMP-013 an unknown address use still gives a document`() {
        assertDocumentBuilt(
            "address use 'vacation'",
            JPatient(gn = "Haru", adrs = listOf(JAddress(use = "vacation", city = "Aomori"))),
        )
    }

    @Test
    fun `SD-03 UC-QRF-013 an unknown telecom system still gives a document`() {
        assertDocumentBuilt(
            "telecom system 'whatsapp'",
            JPatient(gn = "Haru", tels = listOf(JTelecom(system = "whatsapp", value = "+81 90 0000 0000"))),
        )
    }

    @Test
    fun `SD-03 UC-QRF-013 an unknown telecom use still gives a document`() {
        assertDocumentBuilt(
            "telecom use 'cell'",
            JPatient(gn = "Haru", tels = listOf(JTelecom(system = "phone", value = "+81 90 0000 0000", use = "cell"))),
        )
    }
}
