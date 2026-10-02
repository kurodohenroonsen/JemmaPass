/*
 * LiveScanVerdictBadge.kt — which status badge the live-scan panel may show under
 * the hit badges (UC-SAFE-UI-2x).
 *
 * Empty hit lists do not mean "safe" : the green badge is reachable ONLY when the
 * report verdict is CLEAN on a fully run check. Anything else is shown as an amber
 * "not verified" / "incomplete" badge.
 *
 * No Android dependency : covered by LiveScanVerdictBadgeTest.
 */
package be.heyman.android.jemmapassdemo.ui.radar

import be.heyman.android.jemmapassdemo.ai.livescan.CrossCheckReport
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict

object LiveScanVerdictBadge {

    enum class Kind {
        /** Green "no interactions detected". */
        SAFE,

        /** Amber : nothing was verified. */
        NOT_VERIFIED,

        /** Amber : only part of the check ran. */
        INCOMPLETE,
    }

    /**
     * Status badge of a report, null when the hit badges are the whole story
     * (hits found on a fully run check).
     */
    fun statusBadge(report: CrossCheckReport): Kind? {
        if (report.totalHits > 0) {
            return if (report.fullyChecked) null else Kind.INCOMPLETE
        }
        return when (report.verdict) {
            KbSafetyVerdict.CLEAN -> if (report.fullyChecked) Kind.SAFE else Kind.INCOMPLETE
            KbSafetyVerdict.INCOMPLETE -> Kind.INCOMPLETE
            // ALERT without any hit is not a coherent report : treat as not verified.
            KbSafetyVerdict.NOT_CHECKED,
            KbSafetyVerdict.ALERT -> Kind.NOT_VERIFIED
        }
    }

    /** True only for the green badge. */
    fun isGreen(kind: Kind?): Boolean = kind == Kind.SAFE
}
