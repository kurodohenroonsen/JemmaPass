/*
 * LiveScanVerdictBadgeTest.kt — UC-SAFE-UI-2x : the live-scan panel shows the green
 * "safe" badge only for a CLEAN, fully run check.
 */
package be.heyman.android.jemmapassdemo.ui.radar

import be.heyman.android.jemmapassdemo.ai.livescan.CrossCheckReport
import be.heyman.android.jemmapassdemo.ai.livescan.DdiHit
import be.heyman.android.jemmapassdemo.ai.livescan.Severity
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import be.heyman.android.jemmapassdemo.ui.radar.LiveScanVerdictBadge.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveScanVerdictBadgeTest {

    private val ddi = DdiHit(
        withDrugAtc = "B01AA03",
        withDrugName = "Warfarin",
        severity = Severity.MAJOR,
        mechanism = null,
        description = null,
    )

    private fun report(
        verdict: KbSafetyVerdict,
        fullyChecked: Boolean,
        ddiHits: List<DdiHit> = emptyList(),
        overall: Severity = Severity.NONE,
    ) = CrossCheckReport(
        drugAtc = "M01AE01",
        drugName = "Ibuprofen",
        overall = overall,
        allergyHits = emptyList(),
        ddiHits = ddiHits,
        conditionHits = emptyList(),
        verdict = verdict,
        fullyChecked = fullyChecked,
    )

    // ── UC-SAFE-UI-20 : CLEAN + fully checked → green ───────────────────────
    @Test
    fun `UC-SAFE-UI-20 clean fully checked report gets the green badge`() {
        val kind = LiveScanVerdictBadge.statusBadge(report(KbSafetyVerdict.CLEAN, fullyChecked = true))
        assertEquals(Kind.SAFE, kind)
        assertTrue(LiveScanVerdictBadge.isGreen(kind))
    }

    // ── UC-SAFE-UI-21 : no hit but not checked → amber "not verified" ───────
    @Test
    fun `UC-SAFE-UI-21 not checked report without hit is never green`() {
        val kind = LiveScanVerdictBadge.statusBadge(report(KbSafetyVerdict.NOT_CHECKED, fullyChecked = false))
        assertEquals(Kind.NOT_VERIFIED, kind)
        assertFalse(LiveScanVerdictBadge.isGreen(kind))
    }

    @Test
    fun `UC-SAFE-UI-21 report built without a verdict defaults to not verified`() {
        val legacy = CrossCheckReport(
            drugAtc = "M01AE01",
            drugName = "Ibuprofen",
            overall = Severity.NONE,
            allergyHits = emptyList(),
            ddiHits = emptyList(),
            conditionHits = emptyList(),
        )
        assertEquals(Kind.NOT_VERIFIED, LiveScanVerdictBadge.statusBadge(legacy))
    }

    @Test
    fun `UC-SAFE-UI-21 alert verdict without hit is treated as not verified`() {
        assertEquals(
            Kind.NOT_VERIFIED,
            LiveScanVerdictBadge.statusBadge(report(KbSafetyVerdict.ALERT, fullyChecked = true)),
        )
    }

    // ── UC-SAFE-UI-22 : partial check → amber "incomplete" ──────────────────
    @Test
    fun `UC-SAFE-UI-22 incomplete report without hit gets the incomplete badge`() {
        val kind = LiveScanVerdictBadge.statusBadge(report(KbSafetyVerdict.INCOMPLETE, fullyChecked = false))
        assertEquals(Kind.INCOMPLETE, kind)
        assertFalse(LiveScanVerdictBadge.isGreen(kind))
    }

    @Test
    fun `UC-SAFE-UI-22 clean verdict on a check that did not fully run is not green`() {
        assertEquals(
            Kind.INCOMPLETE,
            LiveScanVerdictBadge.statusBadge(report(KbSafetyVerdict.CLEAN, fullyChecked = false)),
        )
    }

    // ── UC-SAFE-UI-23 : hits ────────────────────────────────────────────────
    @Test
    fun `UC-SAFE-UI-23 hits on a full check need no status badge`() {
        val r = report(KbSafetyVerdict.ALERT, fullyChecked = true, ddiHits = listOf(ddi), overall = Severity.MAJOR)
        assertNull(LiveScanVerdictBadge.statusBadge(r))
    }

    @Test
    fun `UC-SAFE-UI-23 hits on a partial check also get the incomplete badge`() {
        val r = report(KbSafetyVerdict.ALERT, fullyChecked = false, ddiHits = listOf(ddi), overall = Severity.MAJOR)
        assertEquals(Kind.INCOMPLETE, LiveScanVerdictBadge.statusBadge(r))
    }

    @Test
    fun `UC-SAFE-UI-23 no verdict is ever green except clean and fully checked`() {
        for (verdict in KbSafetyVerdict.values()) {
            for (full in listOf(true, false)) {
                val kind = LiveScanVerdictBadge.statusBadge(report(verdict, full))
                val expectedGreen = verdict == KbSafetyVerdict.CLEAN && full
                assertEquals("$verdict / fullyChecked=$full", expectedGreen, LiveScanVerdictBadge.isGreen(kind))
            }
        }
    }
}
