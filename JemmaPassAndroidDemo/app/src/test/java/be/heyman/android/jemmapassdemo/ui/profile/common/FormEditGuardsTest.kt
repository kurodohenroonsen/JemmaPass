/*
 * FormEditGuardsTest.kt — UC-ALG-009 / UC-MED-007 / UC-PAT-007 (double tap on Save) and
 * UC-ALG-011 / UC-MED-010 / UC-PAT-005 / UC-PAT-006 / UC-HUM-002 (future and partial dates).
 * See qa/usecases/01-pillar-editing.md.
 */
package be.heyman.android.jemmapassdemo.ui.profile.common

import java.util.Date
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormEditGuardsTest {

    // ─── UC-ALG-009 / UC-MED-007 / UC-PAT-007 ─────────────────────────

    @Test
    fun `UC-PAT-007 second tap is refused while a save is in progress`() {
        val guard = SingleShotGuard()
        assertTrue(guard.tryAcquire())
        assertTrue(guard.isBusy)
        assertFalse(guard.tryAcquire())
        assertFalse(guard.tryAcquire())
    }

    @Test
    fun `UC-ALG-009 guard is usable again after a release (validation refused, alert cancelled, write failed)`() {
        val guard = SingleShotGuard()
        assertTrue(guard.tryAcquire())
        guard.release()
        assertFalse(guard.isBusy)
        assertTrue(guard.tryAcquire())
        assertFalse(guard.tryAcquire())
    }

    @Test
    fun `UC-MED-007 ten rapid taps give exactly one save`() {
        val guard = SingleShotGuard()
        var saves = 0
        repeat(10) { if (guard.tryAcquire()) saves++ }
        assertEquals(1, saves)
    }

    // ─── UC-PAT-006 / UC-ALG-011 / UC-MED-010 ─────────────────────────

    @Test
    fun `UC-PAT-006 a day after today is future, today and before are not`() {
        val today = "2026-10-02"
        assertTrue(IsoDateRules.isFuture("2026-10-03", today))
        assertTrue(IsoDateRules.isFuture("2027-01-01", today))
        assertFalse(IsoDateRules.isFuture("2026-10-02", today))
        assertFalse(IsoDateRules.isFuture("2026-10-01", today))
        assertFalse(IsoDateRules.isFuture("1946-02-05", today))
    }

    @Test
    fun `UC-ALG-011 partial dates are compared at their own precision`() {
        val today = "2026-10-02"
        assertFalse(IsoDateRules.isFuture("2026", today))
        assertFalse(IsoDateRules.isFuture("2026-10", today))
        assertFalse(IsoDateRules.isFuture("1985", today))
        assertTrue(IsoDateRules.isFuture("2027", today))
        assertTrue(IsoDateRules.isFuture("2026-11", today))
    }

    @Test
    fun `UC-MED-010 values that are not dates are never reported as future`() {
        val today = "2026-10-02"
        for (bad in listOf(null, "", "  ", "05/02/2099", "2099-13-01", "abcd", "20990101")) {
            assertFalse("'$bad'", IsoDateRules.isFuture(bad, today))
        }
    }

    @Test
    fun `UC-PAT-006 today is the local day, not the UTC day`() {
        // 2026-10-02T03:00:00Z : still the 1st in Los Angeles, already the 2nd in Tokyo.
        val instant = Date(1790910000000L)
        assertEquals("2026-10-02", IsoDateRules.todayLocalIso(instant, TimeZone.getTimeZone("UTC")))
        assertEquals("2026-10-01", IsoDateRules.todayLocalIso(instant, TimeZone.getTimeZone("America/Los_Angeles")))
        assertEquals("2026-10-02", IsoDateRules.todayLocalIso(instant, TimeZone.getTimeZone("Asia/Tokyo")))
    }

    // ─── UC-PAT-005 / UC-HUM-002 ──────────────────────────────────────

    @Test
    fun `UC-PAT-005 year only and year-month birth dates are accepted`() {
        val today = "2026-10-02"
        assertTrue(IsoDateRules.isAcceptableBirthDate("1946", today))
        assertTrue(IsoDateRules.isAcceptableBirthDate("1946-02", today))
        assertTrue(IsoDateRules.isAcceptableBirthDate("1946-02-05", today))
        assertTrue(IsoDateRules.isAcceptableBirthDate("2026-10-02", today))
    }

    @Test
    fun `UC-HUM-002 missing, malformed or future birth dates are refused`() {
        val today = "2026-10-02"
        for (bad in listOf(null, "", "46", "05/02/1946", "1946-2-5", "1946-13", "1946-00", "1946-02-32",
            "1946-02-00", " 1946", "2026-10-03", "2027")) {
            assertFalse("'$bad'", IsoDateRules.isAcceptableBirthDate(bad, today))
        }
    }
}
