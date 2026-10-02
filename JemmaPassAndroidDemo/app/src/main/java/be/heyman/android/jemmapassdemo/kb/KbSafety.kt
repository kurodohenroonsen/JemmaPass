/*
 * KbSafety.kt — "did the safety check actually run ?" (UC-SAFE-KB-01..)
 *
 * A cross-check (drug × allergy, drug × drug, drug × disease) that returns an
 * empty hit list means two very different things :
 *
 *   • the KB was queried and holds no rule for the pair      → CLEAN
 *   • the KB is absent / not downloaded / failed to open or
 *     query, or an entry could not be resolved to a KB drug  → NOT VERIFIED
 *
 * The second case must never be shown or told as "nothing to report". The
 * types below carry that state next to the hit lists ; everything here is
 * pure Kotlin (no Android class) so it is unit-testable on the JVM.
 */
package be.heyman.android.jemmapassdemo.kb

/** Outcome of one pillar check, independent of the hits it found. Ordered from best to worst. */
enum class KbCheckStatus {
    /** Every pair was looked up in the KB (or there was nothing to look up). */
    CHECKED,

    /** The KB answered, but at least one entry could not be verified (unresolved drug, query error). */
    INCOMPLETE,

    /** The KB is absent, not downloaded yet, or could not be opened : nothing was looked up. */
    KB_UNAVAILABLE,
}

/** What the UI / the LLM is allowed to say about a cross-check. */
enum class KbSafetyVerdict {
    /** At least one collision was found (always wins, even on a partial check). */
    ALERT,

    /** Fully checked, no collision. The ONLY verdict that may be rendered as "nothing to report". */
    CLEAN,

    /** Partially checked, no collision found in the part that ran. */
    INCOMPLETE,

    /** Nothing was verified (KB unavailable, or the candidate drug is unknown to the KB). */
    NOT_CHECKED,
}

/** Hits of one pillar together with whether the pillar was really checked. */
data class PillarCheck<T>(
    val hits: List<T>,
    val status: KbCheckStatus,
    /**
     * Exact number of profile entries this pillar could not verify (UC-SAFE-UI-10..).
     * 0 = none, or not countable (the default for callers that only report a status).
     */
    val unverifiedItems: Int = 0,
)

/** Per-pillar check status of a cross-check result. Defaults to "all checked" for legacy constructors. */
data class KbCheckReport(
    val allergy: KbCheckStatus = KbCheckStatus.CHECKED,
    val ddi: KbCheckStatus = KbCheckStatus.CHECKED,
    val drugDisease: KbCheckStatus = KbCheckStatus.CHECKED,
    /**
     * Exact number of distinct profile entries that could not be verified against the KB
     * (UC-SAFE-UI-10..), as counted by whoever ran the checks. 0 = nothing unverified, or
     * not counted : the status fields above stay the authority on whether the checks ran,
     * this number only feeds the wording ("2 items could not be verified").
     */
    val unverifiedItems: Int = 0,
) {
    /** Worst of the three pillars. */
    val overall: KbCheckStatus
        get() = KbSafety.worst(allergy, ddi, drugDisease)

    val fullyChecked: Boolean
        get() = overall == KbCheckStatus.CHECKED

    /** False as soon as one pillar could not reach the KB. */
    val kbAvailable: Boolean
        get() = allergy != KbCheckStatus.KB_UNAVAILABLE &&
            ddi != KbCheckStatus.KB_UNAVAILABLE &&
            drugDisease != KbCheckStatus.KB_UNAVAILABLE

    companion object {
        fun all(status: KbCheckStatus) = KbCheckReport(status, status, status)
    }
}

object KbSafety {

    const val REASON_KB_UNAVAILABLE = "kb_unavailable"
    const val REASON_CANDIDATE_NOT_RESOLVED = "candidate_not_resolved"
    const val REASON_DRUG_NOT_RESOLVED = "drug_not_resolved"

    const val WARNING_KB_UNAVAILABLE =
        "NOT CHECKED: the medical knowledge base is not available on this device (not downloaded, " +
            "still loading, or failed to open), so no allergy / interaction / disease check was done. " +
            "Do not tell the user it is safe."
    const val WARNING_CANDIDATE_NOT_RESOLVED =
        "NOT CHECKED: this drug was not found in the knowledge base, so no allergy / interaction / " +
            "disease check was done. Do not tell the user it is safe."
    const val WARNING_INCOMPLETE =
        "INCOMPLETE CHECK: part of the profile could not be verified against the knowledge base " +
            "(see allergy_check / ddi_check / drug_disease_check). Report the hits found, but do not " +
            "tell the user the drug is safe."
    const val WARNING_DDI_NOT_CHECKED =
        "NOT CHECKED: the interaction lookup did not run (knowledge base unavailable or drug not " +
            "recognised). Do not tell the user the combination is safe."

    /**
     * Status of one pillar.
     *
     * @param kbAvailable      the KB could be opened for this check
     * @param itemsToCheck     number of profile entries (or pairs) the pillar had to verify
     * @param itemsUnverified  how many of them could not be verified (unresolved, query error)
     */
    fun pillarStatus(kbAvailable: Boolean, itemsToCheck: Int, itemsUnverified: Int = 0): KbCheckStatus = when {
        itemsToCheck <= 0 -> KbCheckStatus.CHECKED            // nothing to collide with
        !kbAvailable -> KbCheckStatus.KB_UNAVAILABLE
        itemsUnverified > 0 -> KbCheckStatus.INCOMPLETE
        else -> KbCheckStatus.CHECKED
    }

    /**
     * Exact unverified-entry count of a report built from several pillars checking the SAME
     * entries (e.g. the profile medications seen by the DDI and the drug×disease pillars) :
     * the largest pillar count, capped by the number of entries — summing would count one
     * medication twice. Negative inputs count as 0.
     */
    fun unverifiedItems(itemsTotal: Int, vararg perPillar: Int): Int =
        (perPillar.maxOrNull() ?: 0).coerceAtLeast(0).coerceAtMost(itemsTotal.coerceAtLeast(0))

    fun worst(vararg statuses: KbCheckStatus): KbCheckStatus =
        statuses.maxByOrNull { it.ordinal } ?: KbCheckStatus.CHECKED

    /** Verdict for a status + hit count. Never CLEAN unless the status is CHECKED. */
    fun verdict(status: KbCheckStatus, totalHits: Int): KbSafetyVerdict = when {
        totalHits > 0 -> KbSafetyVerdict.ALERT
        status == KbCheckStatus.CHECKED -> KbSafetyVerdict.CLEAN
        status == KbCheckStatus.INCOMPLETE -> KbSafetyVerdict.INCOMPLETE
        else -> KbSafetyVerdict.NOT_CHECKED
    }

    /** Lower = more severe. An unparsable severity is treated as worse than a documented "None". */
    fun ddiSeverityRank(severity: DDIResult.Severity): Int = when (severity) {
        DDIResult.Severity.MAJOR -> 0
        DDIResult.Severity.MODERATE -> 1
        DDIResult.Severity.MINOR -> 2
        DDIResult.Severity.UNKNOWN -> 3
        DDIResult.Severity.NONE -> 4
    }

    /**
     * When several KB rows describe the same drug pair, the most severe one wins
     * (first one on a tie), whatever the order the rows came back in.
     */
    fun <T> mostSevere(items: Iterable<T>, severityOf: (T) -> DDIResult.Severity): T? =
        items.minByOrNull { ddiSeverityRank(severityOf(it)) }

    /**
     * The safety fields of the LLM tool answer for a whole-profile cross-check.
     * `is_clean` is true only when the candidate was resolved AND every pillar ran.
     */
    fun crossCheckSafetyFields(r: CrossCheckResult): Map<String, Any> {
        val reason = when {
            !r.kbAvailable -> REASON_KB_UNAVAILABLE
            !r.candidateResolved -> REASON_CANDIDATE_NOT_RESOLVED
            else -> ""
        }
        val warning = when {
            !r.kbAvailable -> WARNING_KB_UNAVAILABLE
            !r.candidateResolved -> WARNING_CANDIDATE_NOT_RESOLVED
            !r.checks.fullyChecked -> WARNING_INCOMPLETE
            else -> ""
        }
        return mapOf(
            "ok" to (r.kbAvailable && r.candidateResolved),
            "reason" to reason,
            "is_clean" to r.isClean,
            "checked" to r.checked,
            "kb_available" to r.kbAvailable,
            "verdict" to r.verdict.name,
            "allergy_check" to r.checks.allergy.name,
            "ddi_check" to r.checks.ddi.name,
            "drug_disease_check" to r.checks.drugDisease.name,
            "warning" to warning,
        )
    }
}
