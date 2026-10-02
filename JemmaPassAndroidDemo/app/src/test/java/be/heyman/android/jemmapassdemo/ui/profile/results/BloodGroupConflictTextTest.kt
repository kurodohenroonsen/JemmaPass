/*
 * BloodGroupConflictTextTest.kt — UC-SAFE-UI-3x : wording of the blood-group conflict the
 * Results screen shows when ProfilesRepository dropped a contradicting result.
 */
package be.heyman.android.jemmapassdemo.ui.profile.results

import be.heyman.android.jemmapassdemo.profiles.BloodGroupConflict
import org.junit.Assert.assertEquals
import org.junit.Test

class BloodGroupConflictTextTest {

    private fun conflict(vararg stated: String?) = BloodGroupConflict(
        profileId = "p1",
        profileBloodGroup = "O+",
        replaced = stated.mapIndexed { i, s -> BloodGroupConflict.Replaced("r$i", s, null) },
    )

    @Test
    fun `UC-SAFE-UI-35 one dropped result gives its group`() {
        assertEquals("A+", BloodGroupConflictText.stated(conflict("A+"), "unreadable"))
    }

    @Test
    fun `UC-SAFE-UI-36 several results are listed once each`() {
        assertEquals("A+, B-", BloodGroupConflictText.stated(conflict("A+", "B-", "A+"), "unreadable"))
    }

    @Test
    fun `UC-SAFE-UI-37 unreadable value uses the placeholder`() {
        assertEquals("unreadable", BloodGroupConflictText.stated(conflict(null), "unreadable"))
        assertEquals("A+, unreadable", BloodGroupConflictText.stated(conflict("A+", " "), "unreadable"))
    }
}
