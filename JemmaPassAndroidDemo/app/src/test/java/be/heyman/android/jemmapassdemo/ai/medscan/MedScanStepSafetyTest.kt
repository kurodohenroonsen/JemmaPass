/*
 * MedScanStepSafetyTest.kt — UC-SAFE-SCAN-3x : the CROSS_CHECK step of the med-scan
 * timeline is OK only when the checkInteractions tool really ran during the scan and
 * answered CLEAN or ALERT. A skipped tool, NOT_CHECKED or INCOMPLETE is NOT VERIFIED,
 * and the agent's prose is not relayed.
 */
package be.heyman.android.jemmapassdemo.ai.medscan

import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedScanStepSafetyTest {

    private fun assertNotVerified(d: CrossCheckStepDecision) {
        assertNotEquals(StepLifecycle.OK, d.lifecycle)
        assertFalse(d.agentTextTrusted)
        assertFalse(d.verified)
        assertTrue(d.detail, d.detail.contains("NOT VERIFIED"))
    }

    // ── UC-SAFE-SCAN-30 : the model never called the tool → not verified ────

    @Test
    fun `UC-SAFE-SCAN-30 no tool call is not verified`() {
        val d = MedScanStepSafety.decide(invocationsBefore = 0, invocationsAfter = 0, lastVerdict = null)
        assertEquals(CrossCheckOutcome.NOT_RUN, d.outcome)
        assertEquals(StepLifecycle.FAIL, d.lifecycle)
        assertNotVerified(d)
    }

    @Test
    fun `UC-SAFE-SCAN-30 stale CLEAN verdict without a call during this scan is not verified`() {
        val d = MedScanStepSafety.decide(
            invocationsBefore = 2, invocationsAfter = 2, lastVerdict = KbSafetyVerdict.CLEAN,
        )
        assertEquals(CrossCheckOutcome.NOT_RUN, d.outcome)
        assertNotVerified(d)
    }

    // ── UC-SAFE-SCAN-31 : tool call + CLEAN → ok ────────────────────────────

    @Test
    fun `UC-SAFE-SCAN-31 tool call and CLEAN is ok`() {
        val d = MedScanStepSafety.decide(0, 1, KbSafetyVerdict.CLEAN)
        assertEquals(CrossCheckOutcome.VERIFIED_CLEAN, d.outcome)
        assertEquals(StepLifecycle.OK, d.lifecycle)
        assertTrue(d.agentTextTrusted)
        assertFalse(d.detail.contains("NOT VERIFIED"))
    }

    // ── UC-SAFE-SCAN-32 : NOT_CHECKED ───────────────────────────────────────

    @Test
    fun `UC-SAFE-SCAN-32 tool call and NOT_CHECKED is not verified`() {
        val d = MedScanStepSafety.decide(0, 1, KbSafetyVerdict.NOT_CHECKED)
        assertEquals(CrossCheckOutcome.NOT_CHECKED, d.outcome)
        assertEquals(StepLifecycle.FAIL, d.lifecycle)
        assertNotVerified(d)
    }

    @Test
    fun `UC-SAFE-SCAN-32 tool call with no verdict is not verified`() {
        val d = MedScanStepSafety.decide(0, 1, null)
        assertEquals(CrossCheckOutcome.NOT_CHECKED, d.outcome)
        assertNotVerified(d)
    }

    // ── UC-SAFE-SCAN-33 : INCOMPLETE ────────────────────────────────────────

    @Test
    fun `UC-SAFE-SCAN-33 tool call and INCOMPLETE is not verified`() {
        val d = MedScanStepSafety.decide(0, 1, KbSafetyVerdict.INCOMPLETE)
        assertEquals(CrossCheckOutcome.INCOMPLETE, d.outcome)
        assertEquals(StepLifecycle.WARN, d.lifecycle)
        assertNotVerified(d)
    }

    // ── UC-SAFE-SCAN-34 : ALERT ─────────────────────────────────────────────

    @Test
    fun `UC-SAFE-SCAN-34 tool call and ALERT is a completed check whose text is relayed`() {
        val d = MedScanStepSafety.decide(0, 2, KbSafetyVerdict.ALERT)
        assertEquals(CrossCheckOutcome.VERIFIED_ALERT, d.outcome)
        assertEquals(StepLifecycle.OK, d.lifecycle)
        assertTrue(d.agentTextTrusted)
        assertNotEquals(MedScanStepSafety.DETAIL_CLEAN, d.detail)
    }

    // ── UC-SAFE-SCAN-35 : only CLEAN / ALERT after a real call are OK ───────

    @Test
    fun `UC-SAFE-SCAN-35 step is OK only for a real call answering CLEAN or ALERT`() {
        val verdicts: List<KbSafetyVerdict?> = KbSafetyVerdict.values().toList() + null
        for (called in listOf(false, true)) {
            for (v in verdicts) {
                val d = MedScanStepSafety.decide(0, if (called) 1 else 0, v)
                val expectedOk = called && (v == KbSafetyVerdict.CLEAN || v == KbSafetyVerdict.ALERT)
                assertEquals("called=$called verdict=$v", expectedOk, d.lifecycle == StepLifecycle.OK)
                assertEquals("called=$called verdict=$v", expectedOk, d.agentTextTrusted)
            }
        }
    }

    // ── UC-SAFE-SCAN-36 : the replacement notice never says "safe" ──────────

    @Test
    fun `UC-SAFE-SCAN-36 english notice says not verified and to ask a pharmacist or a doctor`() {
        for (o in listOf(CrossCheckOutcome.NOT_RUN, CrossCheckOutcome.NOT_CHECKED, CrossCheckOutcome.INCOMPLETE)) {
            for (lang in listOf("en", "en-US", "xx", "")) {
                val t = MedScanStepSafety.notVerifiedNotice(lang, o).lowercase()
                assertTrue(t, t.contains("not verified"))
                assertTrue(t, t.contains("could not be"))
                assertTrue(t, t.contains("pharmacist"))
                assertTrue(t, t.contains("doctor"))
                assertFalse(t, t.contains("safe to"))
                assertFalse(t, t.contains("is safe"))
            }
        }
    }

    @Test
    fun `UC-SAFE-SCAN-36 notice is localized and never blank`() {
        val fr = MedScanStepSafety.notVerifiedNotice("fr-BE", CrossCheckOutcome.NOT_RUN)
        assertTrue(fr, fr.contains("pharmacien"))
        assertTrue(fr, fr.contains("médecin"))
        for (lang in listOf("fr", "nl", "de", "es", "ja", "ko")) {
            for (o in CrossCheckOutcome.values()) {
                assertTrue(MedScanStepSafety.notVerifiedNotice(lang, o).isNotBlank())
            }
        }
        assertNotEquals(
            MedScanStepSafety.notVerifiedNotice("en", CrossCheckOutcome.INCOMPLETE),
            MedScanStepSafety.notVerifiedNotice("en", CrossCheckOutcome.NOT_CHECKED),
        )
    }
}
