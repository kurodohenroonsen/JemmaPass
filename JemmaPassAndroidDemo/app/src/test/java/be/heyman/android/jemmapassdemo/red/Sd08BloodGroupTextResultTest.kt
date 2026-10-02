/*
 * RED TEST (wave 1) — SD-08, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * A dated blood-group result that agrees with the profile's blood group, typed as text
 * ("O+", no SNOMED code), is kept by the repository rule (IpsBloodGroup.reconcile) : the IPS
 * document must keep it too, with its date and laboratory.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.ips.IpsBloodGroup
import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.ips.IpsNativePillars
import be.heyman.android.jemmapassdemo.ips.IpsResult
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class Sd08BloodGroupTextResultTest {

    @Test
    fun `SD-08 UC-RES-005 a matching blood group result typed as text keeps its date and laboratory in the document`() {
        val byHand = IpsResult(
            id = "rs-user", code = IpsBloodGroup.LOINC_ABO_RH, display = "ABO/Rh",
            date = "2015-09-01", valueText = "O+", performer = "Laboratoire",
        )
        // What ProfilesRepository stores (and projects into `_j.rs`) : holds today.
        val stored = IpsBloodGroup.sync(listOf(byHand), "qa_bg", "O+")
        assertEquals("the repository rule keeps the dated result that agrees with the profile", listOf(byHand), stored)

        val profile = JemmaProfileJ(j = "1.2", sid = "qa_bg", p = JPatient(gn = "Haru", bt = "O+"))
        val json = JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(profile), IpsNativePillars(results = stored))
        val bundle = ProfileFixtures.parse(json)
        val bloodGroups = IpsFhirCodec.resultsOf(bundle).filter { IpsBloodGroup.isBloodGroup(it) }

        assertEquals("the document holds exactly one blood-group result", 1, bloodGroups.size)
        val kept = bloodGroups.single()
        assertEquals("the blood group of the document is the profile's", "O+", IpsBloodGroup.labelOf(kept))
        assertEquals(
            "The repository kept the laboratory result of 2015-09-01 stating O+ (it agrees with the profile), but the " +
                "document builder replaced it with the undated mirror of the profile : the two files of the same save " +
                "disagree, and at the next read the dated proof of the blood group is gone without any message. " +
                "The builder must keep a blood-group result that the repository rule keeps.",
            "2015-09-01", kept.date,
        )
        assertEquals("the laboratory of the kept result must be in the document", "Laboratoire", kept.performer)
    }
}
