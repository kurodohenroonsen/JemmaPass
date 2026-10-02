/*
 * ProfileUnverifiedCountTest.kt — UC-SAFE-UI-3x : the number of profile entries the safety
 * cross-checks could not verify is counted by the checks (JemmaProfileHydrator), one per
 * distinct entry, and reaches the profile banner through KbCheckReport.unverifiedItems.
 */
package be.heyman.android.jemmapassdemo.kb

import be.heyman.android.jemmapassdemo.ui.profiles.detail.SafetyBannerDecision
import be.heyman.android.jemmapassdemo.ui.profiles.detail.SafetyCheckNote
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileUnverifiedCountTest {

    /** UC-SAFE-UI-30 — medications without any ATC code are the unverifiable ones. */
    @Test
    fun `UC-SAFE-UI-30 indices of entries without a code`() {
        assertEquals(setOf(1, 3), ProfileUnverifiedCount.indicesWithout(listOf(true, false, true, false)))
        assertEquals(emptySet<Int>(), ProfileUnverifiedCount.indicesWithout(listOf(true, true)))
        assertEquals(emptySet<Int>(), ProfileUnverifiedCount.indicesWithout(emptyList()))
    }

    /** UC-SAFE-UI-31 — a medication seen by the DDI and the drug×disease pillars counts once. */
    @Test
    fun `UC-SAFE-UI-31 same medication in two pillars counts once`() {
        assertEquals(
            1,
            ProfileUnverifiedCount.distinct(
                ddiMedications = setOf(2), diseaseMedications = setOf(2), diseaseConditions = emptySet(),
            ),
        )
        assertEquals(
            3,
            ProfileUnverifiedCount.distinct(
                ddiMedications = setOf(0, 2), diseaseMedications = setOf(2, 4), diseaseConditions = emptySet(),
            ),
        )
    }

    /** UC-SAFE-UI-32 — conditions (and allergies) are other entries : they add up. */
    @Test
    fun `UC-SAFE-UI-32 conditions and allergies add to medications`() {
        assertEquals(
            4,
            ProfileUnverifiedCount.distinct(
                ddiMedications = setOf(0), diseaseMedications = setOf(0),
                diseaseConditions = setOf(0, 1), allergies = setOf(0),
            ),
        )
        assertEquals(0, ProfileUnverifiedCount.distinct(emptySet(), emptySet(), emptySet()))
    }

    /** UC-SAFE-UI-33 — the count carried by the report is the one the banner shows. */
    @Test
    fun `UC-SAFE-UI-33 report count reaches the banner`() {
        val checks = KbCheckReport(
            ddi = KbCheckStatus.INCOMPLETE,
            drugDisease = KbCheckStatus.INCOMPLETE,
            unverifiedItems = ProfileUnverifiedCount.distinct(setOf(1), setOf(1), setOf(0)),
        )
        val state = SafetyBannerDecision.decide(checks, totalAlerts = 0, majorAlerts = 0)
        assertEquals(SafetyCheckNote.INCOMPLETE, state.note)
        assertEquals(2, state.unverifiedCount)
    }

    /** UC-SAFE-UI-34 — a fully checked report never shows a count. */
    @Test
    fun `UC-SAFE-UI-34 checked report has no count`() {
        val state = SafetyBannerDecision.decide(KbCheckReport(), totalAlerts = 0, majorAlerts = 0)
        assertEquals(SafetyCheckNote.NONE, state.note)
        assertEquals(0, state.unverifiedCount)
    }
}
