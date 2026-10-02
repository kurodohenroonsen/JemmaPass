/*
 * RED TEST (wave 1) — SD-13, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * A problem imported as resolved / inactive / in remission must not come back as an ACTIVE
 * problem : a healed infarction shown as ongoing, and false drug × disease alerts.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.ips.IpsNativePillars
import be.heyman.android.jemmapassdemo.ips.IpsProblemStatus
import be.heyman.android.jemmapassdemo.qr.JCondition
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd13ResolvedProblemImportTest {

    /** What ProfilesRepository rebuilds from the `cn` array of a scanned payload. */
    private fun imported(status: String): IpsNativePillars = IpsNativePillars.fromJEntries(
        im = emptyList(),
        cn = listOf(JCondition(c = "22298006", st = status, displayLabel = "Myocardial infarction")),
    )

    @Test
    fun `SD-13 UC-IMP-017 a problem imported as resolved does not become an active problem`() {
        for (status in listOf("resolved", "inactive", "remission", "Resolved")) {
            val native = imported(status)
            assertTrue(
                "A myocardial infarction imported with the status '$status' is stored as an ACTIVE problem : it is shown " +
                    "as ongoing and enters the drug × disease check. A problem that is not current must go to the past " +
                    "illnesses, or keep a status that is not active.",
                native.problems.none { it.clinicalStatus == IpsProblemStatus.ACTIVE },
            )
        }
    }

    @Test
    fun `SD-13 UC-PRB-017 a problem imported as resolved is not lost either`() {
        for (status in listOf("resolved", "inactive", "remission")) {
            val native = imported(status)
            val stillActive = native.problems.any { it.clinicalStatus == IpsProblemStatus.ACTIVE }
            val kept = native.problems.any { it.code == "22298006" } || native.pastProblems.any { it.code == "22298006" }
            assertTrue(
                "A myocardial infarction imported with the status '$status' must be kept as a past illness or as a " +
                    "non-active problem (today : kept, but as an ACTIVE problem). Dropping it is not a fix.",
                kept && !stillActive,
            )
        }
    }
}
