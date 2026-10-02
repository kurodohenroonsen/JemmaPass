/*
 * RED TEST (wave 1) — SD-01, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * Two rescuers' phones that received the same triage events must show the same status,
 * whatever the order the mesh delivered them in.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.triage.SaltCode
import be.heyman.android.jemmapassdemo.triage.StatusEvent
import be.heyman.android.jemmapassdemo.triage.StatusResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class Sd01TriageOrderTest {

    /** What one phone shows for victim v1 after receiving [events] in that order. */
    private fun statusAfter(vararg events: StatusEvent): SaltCode {
        val phone = StatusResolver()
        for (e in events) phone.apply(e)
        return phone.get("v1")!!.status
    }

    private fun deceased(at: Long) =
        StatusEvent(victimSid = "v1", status = SaltCode.DCD, rescuerSid = "r2", timestampSec = at)

    private fun stabilized(at: Long) =
        StatusEvent(victimSid = "v1", status = SaltCode.STAB, rescuerSid = "r1", timestampSec = at)

    @Test
    fun `SD-01 UC-SOS-021 two phones that receive deceased and stabilized in a different order show the same status`() {
        for (delay in listOf(1L, 20L, 30L)) {
            val d = deceased(1_000)
            val s = stabilized(1_000 + delay)
            val phoneA = statusAfter(d, s)
            val phoneB = statusAfter(s, d)
            assertEquals(
                "Rescuer r2 marked the victim deceased at t=1000 and rescuer r1 marked her stabilized ${delay}s later " +
                    "(not an explicit cancel). The phone that got 'deceased' first shows $phoneA, the phone that got " +
                    "'stabilized' first shows $phoneB : some rescuers see a living victim as deceased and nobody goes back. " +
                    "Both phones hold the same two events, so StatusResolver must give the same status on both, " +
                    "whatever the order of arrival.",
                phoneA, phoneB,
            )
        }
    }

    @Test
    fun `SD-01 UC-SOS-020 deceased and stabilized created in the same second give the same status on every phone`() {
        val d = deceased(1_000)
        val s = stabilized(1_000)
        val phoneA = statusAfter(d, s)
        val phoneB = statusAfter(s, d)
        assertEquals(
            "Two rescuers acted in the same second : one phone shows $phoneA, the other $phoneB. " +
                "A tie between 'deceased' and a living status must be decided the same way on every phone " +
                "(the class comment promises a deterministic tie-break).",
            phoneA, phoneB,
        )
    }

    @Test
    fun `SD-01 UC-SOS-021 a third phone that receives a duplicate through a relay agrees with the two others`() {
        val d = deceased(1_000)
        val s = stabilized(1_020)
        val direct = statusAfter(d, s)
        val relayed = statusAfter(s, d, s, d)
        assertEquals(
            "A phone that received stabilized, deceased, then both again through a relay shows $relayed while a phone " +
                "that received deceased then stabilized shows $direct. Receiving an event twice or in another order " +
                "must not change the status.",
            direct, relayed,
        )
    }
}
