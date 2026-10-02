/*
 * SafetyBannerUnverifiedCountTest.kt — UC-SAFE-UI-10..12 : the "N items could not be verified"
 * number is the exact count carried by the check report, not a guess rebuilt by the screen.
 */
package be.heyman.android.jemmapassdemo.ui.profiles.detail

import be.heyman.android.jemmapassdemo.kb.KbCheckReport
import be.heyman.android.jemmapassdemo.kb.KbCheckStatus
import be.heyman.android.jemmapassdemo.kb.KbSafety
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import be.heyman.android.jemmapassdemo.kb.PillarCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyBannerUnverifiedCountTest {

    @Test
    fun `UC-SAFE-UI-10 the report carries an exact count, defaulted to zero`() {
        assertEquals(0, KbCheckReport().unverifiedItems)
        assertEquals(0, KbCheckReport.all(KbCheckStatus.INCOMPLETE).unverifiedItems)
        assertEquals(0, PillarCheck(emptyList<String>(), KbCheckStatus.CHECKED).unverifiedItems)
        assertEquals(2, PillarCheck(emptyList<String>(), KbCheckStatus.INCOMPLETE, 2).unverifiedItems)
        // The count never changes whether the checks are considered run.
        val counted = KbCheckReport(ddi = KbCheckStatus.INCOMPLETE, unverifiedItems = 3)
        assertEquals(KbCheckStatus.INCOMPLETE, counted.overall)
        assertFalse(counted.fullyChecked)
        assertTrue(KbCheckReport(unverifiedItems = 3).fullyChecked)
        // Pillars looking at the same entries: the largest count, never more than the entries.
        assertEquals(2, KbSafety.unverifiedItems(5, 2, 1))
        assertEquals(5, KbSafety.unverifiedItems(5, 2, 12))
        assertEquals(0, KbSafety.unverifiedItems(5))
        assertEquals(0, KbSafety.unverifiedItems(-1, -4, 3))
    }

    @Test
    fun `UC-SAFE-UI-11 the banner shows the exact count of the report`() {
        val s = SafetyBannerDecision.decide(
            KbCheckReport(ddi = KbCheckStatus.INCOMPLETE, drugDisease = KbCheckStatus.INCOMPLETE, unverifiedItems = 3),
            totalAlerts = 0, majorAlerts = 0,
        )
        assertEquals(KbSafetyVerdict.INCOMPLETE, s.verdict)
        assertEquals(SafetyCheckNote.INCOMPLETE, s.note)
        assertEquals(3, s.unverifiedCount)
        // Same state as the four-argument decision fed with that number.
        assertEquals(SafetyBannerDecision.decide(KbCheckStatus.INCOMPLETE, 0, 0, 3), s)
    }

    @Test
    fun `UC-SAFE-UI-12 without a count the wording has no number and other notes ignore it`() {
        val generic = SafetyBannerDecision.decide(KbCheckReport(ddi = KbCheckStatus.INCOMPLETE), 0, 0)
        assertEquals(SafetyCheckNote.INCOMPLETE, generic.note)
        assertEquals(0, generic.unverifiedCount)

        val down = SafetyBannerDecision.decide(
            KbCheckReport(allergy = KbCheckStatus.KB_UNAVAILABLE, unverifiedItems = 4), 1, 1,
        )
        assertEquals(SafetyCheckNote.NOT_CHECKED, down.note)
        assertEquals(0, down.unverifiedCount)
        assertEquals(SafetyAlertBanner.MAJOR, down.alert)

        val clean = SafetyBannerDecision.decide(KbCheckReport(unverifiedItems = 4), 0, 0)
        assertEquals(KbSafetyVerdict.CLEAN, clean.verdict)
        assertFalse(clean.showNote)
        assertEquals(0, clean.unverifiedCount)
    }
}
