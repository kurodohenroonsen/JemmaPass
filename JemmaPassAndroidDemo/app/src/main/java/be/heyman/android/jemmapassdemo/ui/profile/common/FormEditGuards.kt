/*
 * FormEditGuards.kt — pure helpers shared by the allergy, medication and identity forms.
 *
 *   - SingleShotGuard : reentrancy guard against a double tap on Save
 *                       (UC-ALG-009, UC-MED-007, UC-PAT-007, UC-A11Y-005).
 *   - IsoDateRules    : "not in the future" and partial-date rules
 *                       (UC-ALG-011, UC-MED-010, UC-PAT-005, UC-PAT-006, UC-HUM-002).
 *
 * No Android dependency : covered by JVM tests (FormEditGuardsTest).
 */
package be.heyman.android.jemmapassdemo.ui.profile.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A save is "acquired" by the first tap and must be released explicitly when the
 * form stays open (validation refused, alert cancelled, write failed). While it is
 * held, every other tap is refused. Main-thread only, like the views that use it.
 */
class SingleShotGuard {
    private var busy = false

    val isBusy: Boolean get() = busy

    /** @return true for the first caller only, until [release] is called. */
    fun tryAcquire(): Boolean {
        if (busy) return false
        busy = true
        return true
    }

    fun release() {
        busy = false
    }
}

object IsoDateRules {

    private val PARTIAL_OR_FULL = Regex("^(\\d{4})(?:-(\\d{2})(?:-(\\d{2}))?)?$")

    /** Today in the device time zone, as YYYY-MM-DD. */
    fun todayLocalIso(now: Date = Date(), zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone }.format(now)

    /** YYYY, YYYY-MM or YYYY-MM-DD with a plausible month and day. */
    fun isIsoDateOrPartial(iso: String?): Boolean {
        val m = PARTIAL_OR_FULL.matchEntire(iso ?: return false) ?: return false
        val month = m.groupValues[2]
        val day = m.groupValues[3]
        if (month.isNotEmpty() && month.toInt() !in 1..12) return false
        if (day.isNotEmpty() && day.toInt() !in 1..31) return false
        return true
    }

    /**
     * True when [iso] (YYYY, YYYY-MM or YYYY-MM-DD) is strictly after [todayIso]
     * (YYYY-MM-DD) at its own precision : the current year / month is not "future".
     * Anything that is not such a date returns false (nothing to compare).
     */
    fun isFuture(iso: String?, todayIso: String): Boolean {
        if (!isIsoDateOrPartial(iso)) return false
        val value = iso!!
        if (todayIso.length < value.length) return false
        return value > todayIso.substring(0, value.length)
    }

    /** A birth date the profile can store : full or partial, plausible, not in the future. */
    fun isAcceptableBirthDate(iso: String?, todayIso: String): Boolean =
        isIsoDateOrPartial(iso) && !isFuture(iso, todayIso)
}
