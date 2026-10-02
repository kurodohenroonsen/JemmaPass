/*
 * SafetyBannerDecision.kt — which safety banner(s) the profile detail screen shows (UC-SAFE-UI-01..)
 *
 * "No alert" is only good news when the cross-checks really ran. This decides,
 * from the check status and the alert counts, both the red alert banner and the
 * amber "checks not run / incomplete" note. Pure Kotlin (no Android class) so
 * it is unit-testable on the JVM.
 */
package be.heyman.android.jemmapassdemo.ui.profiles.detail

import be.heyman.android.jemmapassdemo.kb.KbCheckReport
import be.heyman.android.jemmapassdemo.kb.KbCheckStatus
import be.heyman.android.jemmapassdemo.kb.KbSafety
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict

/** Red alert banner state. */
enum class SafetyAlertBanner { NONE, OTHER, MAJOR }

/** Amber "was it really checked ?" note state. */
enum class SafetyCheckNote {
    /** Every check ran : no note. */
    NONE,

    /** Some entries could not be verified. */
    INCOMPLETE,

    /** The knowledge base was unavailable : nothing (or not everything) was looked up. */
    NOT_CHECKED,
}

data class SafetyBannerState(
    val verdict: KbSafetyVerdict,
    val alert: SafetyAlertBanner,
    /** Number of major alerts, meaningful when [alert] is MAJOR. */
    val majorCount: Int,
    val note: SafetyCheckNote,
    /**
     * Number of entries that could not be verified, meaningful when [note] is
     * INCOMPLETE. 0 = incomplete for a reason that cannot be counted (query
     * error) : the UI then shows a wording without a number.
     */
    val unverifiedCount: Int,
) {
    val showAlert: Boolean get() = alert != SafetyAlertBanner.NONE
    val showNote: Boolean get() = note != SafetyCheckNote.NONE
}

object SafetyBannerDecision {

    /**
     * @param overall          worst status of the three cross-checks
     * @param totalAlerts      all alerts found (DDI + allergy + drug×disease)
     * @param majorAlerts      how many of them are major
     * @param unverifiedItems  profile entries known to be unverifiable (e.g. free-text medications)
     */
    fun decide(
        overall: KbCheckStatus,
        totalAlerts: Int,
        majorAlerts: Int,
        unverifiedItems: Int,
    ): SafetyBannerState {
        val total = totalAlerts.coerceAtLeast(0)
        val major = majorAlerts.coerceAtLeast(0)
        val alert = when {
            major > 0 -> SafetyAlertBanner.MAJOR
            total > 0 -> SafetyAlertBanner.OTHER
            else -> SafetyAlertBanner.NONE
        }
        // The note depends on the check status only : alerts never hide it.
        val note = when (overall) {
            KbCheckStatus.CHECKED -> SafetyCheckNote.NONE
            KbCheckStatus.INCOMPLETE -> SafetyCheckNote.INCOMPLETE
            KbCheckStatus.KB_UNAVAILABLE -> SafetyCheckNote.NOT_CHECKED
        }
        return SafetyBannerState(
            verdict = KbSafety.verdict(overall, maxOf(total, major)),
            alert = alert,
            majorCount = if (alert == SafetyAlertBanner.MAJOR) major else 0,
            note = note,
            unverifiedCount = if (note == SafetyCheckNote.INCOMPLETE) unverifiedItems.coerceAtLeast(0) else 0,
        )
    }

    /**
     * UC-SAFE-UI-10.. — same decision from the check report itself : the number shown is the
     * exact [KbCheckReport.unverifiedItems] counted by the checks, not a guess rebuilt by the
     * screen. A report without a count (0) gives the wording without a number.
     */
    fun decide(checks: KbCheckReport, totalAlerts: Int, majorAlerts: Int): SafetyBannerState =
        decide(checks.overall, totalAlerts, majorAlerts, checks.unverifiedItems)

    /**
     * Medications that cannot be looked up in the KB : no ATC code at all
     * (typically a free-text entry). One flag per medication, true = has a code.
     * Approximation kept for callers without a report : prefer [decide] with the
     * [KbCheckReport], whose count also covers query errors and unusable conditions.
     */
    fun countUnverified(hasAnyCode: List<Boolean>): Int = hasAnyCode.count { !it }
}
