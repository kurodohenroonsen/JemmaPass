/*
 * PdfPillarLayoutTest.kt — UC-PDF-002 / UC-PDF-003 / UC-PDF-004 / UC-PDF-005:
 * ordering, overflow and criticality labels of the printed Pocket Pass.
 */
package be.heyman.android.jemmapassdemo.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPillarLayoutTest {

    private data class Allergy(val name: String, val criticality: String?)

    private val fiveWithHighLast = listOf(
        Allergy("pollen", "LOW"),
        Allergy("latex", "LOW"),
        Allergy("kiwi", "UNABLE_TO_ASSESS"),
        Allergy("dust", "LOW"),
        Allergy("penicillin", "HIGH"),
    )

    /** UC-PDF-002 — 5 allergies, the high-criticality one entered last, 3 rows. */
    @Test
    fun ucPdf002_highCriticalityAllergyIsListedFirstAndTheRestIsCounted() {
        val plan = PdfPillarLayout.planAllergies(fiveWithHighLast, 3) { it.criticality }

        assertEquals(listOf("penicillin", "kiwi"), plan.shown.map { it.name })
        assertEquals(3, plan.overflowCount)
        assertTrue(plan.hasOverflow)
        assertEquals(fiveWithHighLast.size, plan.shown.size + plan.overflowCount)
    }

    /** UC-PDF-002 — when the rows are enough, all 5 are printed, high first, no indicator. */
    @Test
    fun ucPdf002_allAllergiesArePrintedWhenTheyFit() {
        val plan = PdfPillarLayout.planAllergies(fiveWithHighLast, 5) { it.criticality }

        assertEquals(listOf("penicillin", "kiwi", "pollen", "latex", "dust"), plan.shown.map { it.name })
        assertEquals(0, plan.overflowCount)
        assertFalse(plan.hasOverflow)
    }

    /** UC-PDF-002 — the real column budget prints the 5 allergies of the use case. */
    @Test
    fun ucPdf002_columnBudgetShowsFiveAllergiesAndFiveConditions() {
        val rows = PdfPillarLayout.column1Rows(allergyCount = 5, conditionCount = 5)

        assertEquals(5, rows.allergyRows)
        assertEquals(5, rows.conditionRows)
    }

    /** UC-PDF-002 / UC-PDF-003 — allergies are served first, the column is never overrun. */
    @Test
    fun ucPdf003_allergiesAreServedFirstAndConditionsKeepTheirOverflowRow() {
        val rows = PdfPillarLayout.column1Rows(allergyCount = 30, conditionCount = 30)

        assertEquals(PdfPillarLayout.COLUMN1_ROWS - 2, rows.allergyRows)
        assertEquals(2, rows.conditionRows)

        val conditions = PdfPillarLayout.plan((1..30).toList(), rows.conditionRows)
        assertEquals(listOf(1), conditions.shown)
        assertEquals(29, conditions.overflowCount)
    }

    /** UC-PDF-003 — an empty pillar still gets the row of its "none" mention. */
    @Test
    fun ucPdf003_emptyPillarsKeepOneRowEach() {
        val rows = PdfPillarLayout.column1Rows(allergyCount = 0, conditionCount = 0)

        assertEquals(1, rows.allergyRows)
        assertEquals(1, rows.conditionRows)
    }

    /** UC-PDF-004 — 20 medications: the last row is the indicator, nothing is lost. */
    @Test
    fun ucPdf004_medicationOverflowCountsEveryEntryNotShown() {
        val plan = PdfPillarLayout.plan((1..20).toList(), PdfPillarLayout.MEDICATION_ROWS)

        assertEquals(PdfPillarLayout.MEDICATION_ROWS - 1, plan.shown.size)
        assertEquals(20 - (PdfPillarLayout.MEDICATION_ROWS - 1), plan.overflowCount)
        assertEquals("+3 medications", PdfPillarLayout.overflowText(plan.overflowCount, "medications"))
        assertEquals("+3", PdfPillarLayout.overflowText(3, " "))
    }

    /** UC-PDF-005 — unknown criticality prints "?", never "L". */
    @Test
    fun ucPdf005_unknownCriticalityIsNeverPrintedAsLow() {
        for (unknown in listOf("UNABLE_TO_ASSESS", "U", null, "", "  ", "moderate")) {
            assertEquals("?", PdfPillarLayout.criticalityLabel(unknown))
        }
        assertEquals("H", PdfPillarLayout.criticalityLabel("HIGH"))
        assertEquals("H", PdfPillarLayout.criticalityLabel("h"))
        assertEquals("L", PdfPillarLayout.criticalityLabel("LOW"))
        assertEquals("L", PdfPillarLayout.criticalityLabel("L"))
    }

    /** UC-PDF-005 — unknown sorts above low: it may be a high one nobody assessed. */
    @Test
    fun ucPdf005_unknownCriticalitySortsBetweenHighAndLow() {
        val sorted = PdfPillarLayout.sortByCriticality(listOf("LOW", null, "HIGH", "U")) { it }

        assertEquals(listOf("HIGH", null, "U", "LOW"), sorted)
    }

    /** UC-PDF-002 — empty list: nothing shown, no overflow indicator. */
    @Test
    fun ucPdf002_emptyListHasNoRowsAndNoOverflow() {
        val plan = PdfPillarLayout.planAllergies(emptyList<Allergy>(), 3) { it.criticality }

        assertTrue(plan.shown.isEmpty())
        assertEquals(0, plan.overflowCount)
        assertFalse(plan.hasOverflow)
    }
}
