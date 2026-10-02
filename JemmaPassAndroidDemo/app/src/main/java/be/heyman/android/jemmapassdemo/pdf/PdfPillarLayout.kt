/*
 * PdfPillarLayout.kt — pure decisions for the printed Pocket Pass (no Android imports).
 *
 * The drawing code (qr/JemmaPdfExporter) asks this object in which order to print
 * the entries of a pillar, how many fit, and what to print for the rest. Rules:
 *  - UC-PDF-002 / UC-PDF-003 / UC-PDF-004: an entry is never dropped silently; when the
 *    rows are exhausted the last row is an explicit "+N" overflow indicator.
 *  - UC-PDF-002: allergies are printed highest criticality first, so the overflow
 *    never hides a high-criticality allergy behind a low one.
 *  - UC-PDF-005: an unknown criticality prints as "?", never as "L" (low).
 */
package be.heyman.android.jemmapassdemo.pdf

object PdfPillarLayout {

    const val LABEL_HIGH = "H"
    const val LABEL_LOW = "L"
    const val LABEL_UNKNOWN = "?"

    /**
     * Rows of 8 pt available in column 1 for allergies + conditions together,
     * between the patient block and the QR code of the clinical quadrant.
     */
    const val COLUMN1_ROWS = 13

    /** Rows of 8 pt available in column 2 (medications) above the QR code. */
    const val MEDICATION_ROWS = 18

    /** Rows kept for the conditions when the allergies would fill the whole column. */
    private const val CONDITION_RESERVED_ROWS = 2

    /** What to print for one pillar: the entries that fit, and how many do not. */
    data class Plan<T>(val shown: List<T>, val overflowCount: Int) {
        val hasOverflow: Boolean get() = overflowCount > 0
    }

    /** Rows given to each pillar of column 1. */
    data class ColumnRows(val allergyRows: Int, val conditionRows: Int)

    /**
     * Printed criticality letter from the enum name ("HIGH", "LOW", "UNABLE_TO_ASSESS")
     * or from the wire code ("H", "L", "U"). Anything that is not explicitly high or
     * low — null, blank, "U", an unexpected value — is unknown.
     */
    fun criticalityLabel(criticality: String?): String = when (criticality?.trim()?.uppercase()) {
        "HIGH", "H" -> LABEL_HIGH
        "LOW", "L" -> LABEL_LOW
        else -> LABEL_UNKNOWN
    }

    /** 0 = high, 1 = unknown (could be high), 2 = low. */
    fun criticalityRank(criticality: String?): Int = when (criticalityLabel(criticality)) {
        LABEL_HIGH -> 0
        LABEL_UNKNOWN -> 1
        else -> 2
    }

    /** Highest criticality first; the original order is kept inside one level. */
    fun <T> sortByCriticality(items: List<T>, criticalityOf: (T) -> String?): List<T> =
        items.sortedBy { criticalityRank(criticalityOf(it)) }

    /**
     * Fits [items] in [maxRows] rows. Everything is shown when it fits; otherwise the
     * last row is kept for the "+N" indicator, N being every entry not shown.
     */
    fun <T> plan(items: List<T>, maxRows: Int): Plan<T> {
        if (items.size <= maxRows) return Plan(items, 0)
        val shownCount = (maxRows - 1).coerceAtLeast(0)
        return Plan(items.take(shownCount), items.size - shownCount)
    }

    /** Allergies sorted by criticality, then fitted in [maxRows]. */
    fun <T> planAllergies(items: List<T>, maxRows: Int, criticalityOf: (T) -> String?): Plan<T> =
        plan(sortByCriticality(items, criticalityOf), maxRows)

    /**
     * Shares the rows of column 1. Allergies are served first; the conditions keep
     * up to two rows (one entry + the "+N" indicator) and take whatever the
     * allergies leave. An empty pillar needs one row for its "none" mention.
     */
    fun column1Rows(allergyCount: Int, conditionCount: Int, totalRows: Int = COLUMN1_ROWS): ColumnRows {
        val allergyNeed = allergyCount.coerceAtLeast(1)
        val conditionNeed = conditionCount.coerceAtLeast(1)
        val reserved = minOf(conditionNeed, CONDITION_RESERVED_ROWS)
        val allergyRows = minOf(allergyNeed, (totalRows - reserved).coerceAtLeast(1))
        val conditionRows = minOf(conditionNeed, (totalRows - allergyRows).coerceAtLeast(1))
        return ColumnRows(allergyRows, conditionRows)
    }

    /** Text of the overflow row, e.g. "+2 allergies". [noun] may be blank. */
    fun overflowText(overflowCount: Int, noun: String): String =
        if (noun.isBlank()) "+$overflowCount" else "+$overflowCount ${noun.trim()}"
}
