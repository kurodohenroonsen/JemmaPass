package be.heyman.android.jemmapassdemo.ui.profiles.detail

import be.heyman.android.jemmapassdemo.kb.KbCheckStatus
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** UC-SAFE-UI — the detail screen never shows an unchecked profile like a clean one. */
class SafetyBannerDecisionTest {

    /** UC-SAFE-UI-01 — fully checked, no alert : nothing shown. */
    @Test
    fun `UC-SAFE-UI-01 clean profile shows no banner`() {
        val s = SafetyBannerDecision.decide(KbCheckStatus.CHECKED, 0, 0, 0)
        assertEquals(KbSafetyVerdict.CLEAN, s.verdict)
        assertFalse(s.showAlert)
        assertFalse(s.showNote)
    }

    /** UC-SAFE-UI-02 — KB unavailable, no alert : "not run" note, never hidden. */
    @Test
    fun `UC-SAFE-UI-02 kb unavailable shows not checked note`() {
        val s = SafetyBannerDecision.decide(KbCheckStatus.KB_UNAVAILABLE, 0, 0, 0)
        assertEquals(KbSafetyVerdict.NOT_CHECKED, s.verdict)
        assertEquals(SafetyAlertBanner.NONE, s.alert)
        assertEquals(SafetyCheckNote.NOT_CHECKED, s.note)
        assertTrue(s.showNote)
        assertEquals(0, s.unverifiedCount)
    }

    /** UC-SAFE-UI-03 — incomplete, no alert : note with the unverified count. */
    @Test
    fun `UC-SAFE-UI-03 incomplete shows note with count`() {
        val s = SafetyBannerDecision.decide(KbCheckStatus.INCOMPLETE, 0, 0, 2)
        assertEquals(KbSafetyVerdict.INCOMPLETE, s.verdict)
        assertFalse(s.showAlert)
        assertEquals(SafetyCheckNote.INCOMPLETE, s.note)
        assertEquals(2, s.unverifiedCount)
    }

    /** UC-SAFE-UI-04 — incomplete without a countable cause : note kept, count 0 (generic wording). */
    @Test
    fun `UC-SAFE-UI-04 incomplete with unknown count still shows note`() {
        val s = SafetyBannerDecision.decide(KbCheckStatus.INCOMPLETE, 0, 0, 0)
        assertTrue(s.showNote)
        assertEquals(0, s.unverifiedCount)
        assertEquals(0, SafetyBannerDecision.decide(KbCheckStatus.INCOMPLETE, 0, 0, -3).unverifiedCount)
    }

    /** UC-SAFE-UI-05 — alerts AND incomplete : both the alert banner and the note. */
    @Test
    fun `UC-SAFE-UI-05 alerts plus incomplete shows both`() {
        val s = SafetyBannerDecision.decide(KbCheckStatus.INCOMPLETE, 3, 1, 1)
        assertEquals(KbSafetyVerdict.ALERT, s.verdict)
        assertEquals(SafetyAlertBanner.MAJOR, s.alert)
        assertEquals(1, s.majorCount)
        assertEquals(SafetyCheckNote.INCOMPLETE, s.note)
        assertEquals(1, s.unverifiedCount)
    }

    /** UC-SAFE-UI-06 — alerts found by heuristics while the KB is down : alert + "not run" note. */
    @Test
    fun `UC-SAFE-UI-06 alerts plus kb unavailable shows both`() {
        val s = SafetyBannerDecision.decide(KbCheckStatus.KB_UNAVAILABLE, 1, 0, 0)
        assertEquals(KbSafetyVerdict.ALERT, s.verdict)
        assertEquals(SafetyAlertBanner.OTHER, s.alert)
        assertEquals(0, s.majorCount)
        assertEquals(SafetyCheckNote.NOT_CHECKED, s.note)
    }

    /** UC-SAFE-UI-07 — fully checked with alerts : alert banner only, as before. */
    @Test
    fun `UC-SAFE-UI-07 checked alerts show alert only`() {
        val major = SafetyBannerDecision.decide(KbCheckStatus.CHECKED, 2, 2, 5)
        assertEquals(SafetyAlertBanner.MAJOR, major.alert)
        assertEquals(2, major.majorCount)
        assertFalse(major.showNote)
        assertEquals(0, major.unverifiedCount)

        val other = SafetyBannerDecision.decide(KbCheckStatus.CHECKED, 1, 0, 0)
        assertEquals(SafetyAlertBanner.OTHER, other.alert)
        assertFalse(other.showNote)
    }

    /** UC-SAFE-UI-08 — a note is shown for every status but CHECKED, whatever the alert count. */
    @Test
    fun `UC-SAFE-UI-08 note never hidden unless fully checked`() {
        for (status in KbCheckStatus.values()) {
            for (alerts in 0..2) {
                val s = SafetyBannerDecision.decide(status, alerts, 0, 1)
                assertEquals("$status / $alerts", status != KbCheckStatus.CHECKED, s.showNote)
                if (alerts == 0 && status != KbCheckStatus.CHECKED) {
                    assertTrue(s.verdict == KbSafetyVerdict.INCOMPLETE || s.verdict == KbSafetyVerdict.NOT_CHECKED)
                }
            }
        }
    }

    /** UC-SAFE-UI-09 — medications without any code are the ones counted as unverified. */
    @Test
    fun `UC-SAFE-UI-09 counts medications without code`() {
        assertEquals(0, SafetyBannerDecision.countUnverified(emptyList()))
        assertEquals(2, SafetyBannerDecision.countUnverified(listOf(true, false, false)))
    }
}
