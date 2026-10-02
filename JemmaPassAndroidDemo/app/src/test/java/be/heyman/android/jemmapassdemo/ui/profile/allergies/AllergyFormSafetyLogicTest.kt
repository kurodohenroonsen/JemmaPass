/*
 * AllergyFormSafetyLogicTest.kt — UC-SAFE-UI-2x : the allergy form saves without a
 * dialog only when the check "new allergy × stored medications" is CLEAN.
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies

import be.heyman.android.jemmapassdemo.kb.AllergyCriticality
import be.heyman.android.jemmapassdemo.kb.KbCheckStatus
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import be.heyman.android.jemmapassdemo.ui.profile.allergies.AllergyFormSafetyLogic.GapMessage
import be.heyman.android.jemmapassdemo.ui.profile.common.NewAllergyConflict
import be.heyman.android.jemmapassdemo.ui.profile.common.NewAllergyConflicts
import be.heyman.android.jemmapassdemo.ui.profile.common.SingleShotGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllergyFormSafetyLogicTest {

    private val hit = NewAllergyConflict(
        existingMedDisplay = "Augmentin",
        existingMedAtc = "J01CR02",
        existingMedIndex = 0,
        matchedOn = "class:J01C",
        criticality = AllergyCriticality.HIGH,
    )

    private fun conflicts(
        status: KbCheckStatus,
        hits: List<NewAllergyConflict> = emptyList(),
        unchecked: List<String> = emptyList(),
    ) = NewAllergyConflicts(hits = hits, durationMs = 1L, uncheckedMeds = unchecked, status = status)

    private fun gaps(result: NewAllergyConflicts?, meds: Int = 2, profile: Boolean = true) =
        AllergyFormSafetyLogic.safetyCheckGaps(profile, meds, result)

    // ── UC-SAFE-UI-24 : CLEAN is the only silent save ───────────────────────
    @Test
    fun `UC-SAFE-UI-24 clean check saves without dialog`() {
        val result = conflicts(KbCheckStatus.CHECKED)
        assertEquals(KbSafetyVerdict.CLEAN, result.verdict)
        val g = gaps(result)
        assertTrue(AllergyFormSafetyLogic.maySaveSilently(g))
        assertNull(AllergyFormSafetyLogic.gapMessage(g))
    }

    @Test
    fun `UC-SAFE-UI-24 no stored medication means nothing to check`() {
        val g = gaps(result = null, meds = 0)
        assertTrue(AllergyFormSafetyLogic.maySaveSilently(g))
    }

    // ── UC-SAFE-UI-25 : NOT_CHECKED → dialog "could not run" ────────────────
    @Test
    fun `UC-SAFE-UI-25 knowledge base unavailable is not a silent save`() {
        val result = conflicts(KbCheckStatus.KB_UNAVAILABLE)
        assertEquals(KbSafetyVerdict.NOT_CHECKED, result.verdict)
        val g = gaps(result)
        assertTrue(g.checkNotRun)
        assertFalse(AllergyFormSafetyLogic.maySaveSilently(g))
        assertEquals(GapMessage.NOT_RUN, AllergyFormSafetyLogic.gapMessage(g))
    }

    @Test
    fun `UC-SAFE-UI-25 result built without a status is not verified`() {
        val g = gaps(NewAllergyConflicts(hits = emptyList(), durationMs = 0L))
        assertTrue(g.checkNotRun)
    }

    @Test
    fun `UC-SAFE-UI-25 null result while medications are stored is not verified`() {
        val g = gaps(result = null, meds = 3)
        assertTrue(g.checkNotRun)
        assertFalse(AllergyFormSafetyLogic.maySaveSilently(g))
    }

    @Test
    fun `UC-SAFE-UI-25 missing profile snapshot is not verified`() {
        val g = gaps(result = null, meds = 0, profile = false)
        assertTrue(g.checkNotRun)
        assertEquals(GapMessage.NOT_RUN, AllergyFormSafetyLogic.gapMessage(g))
    }

    // ── UC-SAFE-UI-26 : INCOMPLETE → dialog listing what was skipped ────────
    @Test
    fun `UC-SAFE-UI-26 incomplete check lists the unchecked medications`() {
        val result = conflicts(KbCheckStatus.INCOMPLETE, unchecked = listOf("Tisane maison", "?"))
        assertEquals(KbSafetyVerdict.INCOMPLETE, result.verdict)
        val g = gaps(result)
        assertEquals(listOf("Tisane maison", "?"), g.uncheckedMeds)
        assertFalse(g.checkIncomplete)
        assertFalse(AllergyFormSafetyLogic.maySaveSilently(g))
        assertEquals(GapMessage.UNCHECKED_MEDS, AllergyFormSafetyLogic.gapMessage(g))
    }

    @Test
    fun `UC-SAFE-UI-26 incomplete check without label still shows a dialog`() {
        val g = gaps(conflicts(KbCheckStatus.INCOMPLETE))
        assertTrue(g.checkIncomplete)
        assertFalse(AllergyFormSafetyLogic.maySaveSilently(g))
        assertEquals(GapMessage.INCOMPLETE, AllergyFormSafetyLogic.gapMessage(g))
    }

    // ── UC-SAFE-UI-27 : ALERT — hits first, gaps still reported ─────────────
    @Test
    fun `UC-SAFE-UI-27 hits on a full check leave no gap`() {
        val result = conflicts(KbCheckStatus.CHECKED, hits = listOf(hit))
        assertEquals(KbSafetyVerdict.ALERT, result.verdict)
        assertFalse(gaps(result).any)
    }

    @Test
    fun `UC-SAFE-UI-27 hits on a partial check still report the gap`() {
        val partial = gaps(conflicts(KbCheckStatus.INCOMPLETE, hits = listOf(hit), unchecked = listOf("Sirop")))
        assertEquals(listOf("Sirop"), partial.uncheckedMeds)
        val noKb = gaps(conflicts(KbCheckStatus.KB_UNAVAILABLE, hits = listOf(hit)))
        assertTrue(noKb.checkNotRun)
    }

    // ── UC-SAFE-UI-28 : pick-time log / state ───────────────────────────────
    @Test
    fun `UC-SAFE-UI-28 pick is clean only for a clean verdict`() {
        assertTrue(AllergyFormSafetyLogic.isCleanAtPick(null))
        assertTrue(AllergyFormSafetyLogic.isCleanAtPick(conflicts(KbCheckStatus.CHECKED)))
        assertFalse(AllergyFormSafetyLogic.isCleanAtPick(conflicts(KbCheckStatus.INCOMPLETE)))
        assertFalse(AllergyFormSafetyLogic.isCleanAtPick(conflicts(KbCheckStatus.KB_UNAVAILABLE)))
        assertFalse(AllergyFormSafetyLogic.isCleanAtPick(conflicts(KbCheckStatus.CHECKED, hits = listOf(hit))))
    }

    // ── UC-SAFE-UI-29 : single-shot guard across the dialog ─────────────────
    @Test
    fun `UC-SAFE-UI-29 cancelling the dialog releases the save guard`() {
        val guard = SingleShotGuard()
        assertTrue(guard.tryAcquire())
        // Dialog open : a second tap on Save is ignored.
        assertFalse(guard.tryAcquire())
        val g = gaps(conflicts(KbCheckStatus.KB_UNAVAILABLE))
        assertFalse(AllergyFormSafetyLogic.maySaveSilently(g))
        // "Review" / back : the form releases the guard, Save works again.
        guard.release()
        assertTrue(guard.tryAcquire())
    }
}
