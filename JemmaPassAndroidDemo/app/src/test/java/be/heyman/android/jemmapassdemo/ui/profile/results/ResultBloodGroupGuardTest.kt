/*
 * ResultBloodGroupGuardTest.kt — UC-BLOOD-13..16 : the results form refuses a blood-group
 * result (LOINC 882-1) that does not state the profile's blood group.
 */
package be.heyman.android.jemmapassdemo.ui.profile.results

import be.heyman.android.jemmapassdemo.ips.IpsBloodGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResultBloodGroupGuardTest {

    private fun check(label: String?, profile: String?, code: String? = IpsBloodGroup.LOINC_ABO_RH) =
        ResultBloodGroupGuard.check(
            code = code,
            valueCode = IpsBloodGroup.snomedCode(label),
            valueDisplay = IpsBloodGroup.snomedDisplay(label),
            valueText = null,
            profileBloodType = profile,
        )

    @Test
    fun `UC-BLOOD-13 a different group than the profile is refused, whatever the pair`() {
        for (profile in IpsBloodGroup.LABELS) {
            for (entered in IpsBloodGroup.LABELS) {
                val conflict = check(entered, profile)
                if (entered == profile) {
                    assertNull("$entered vs $profile", conflict)
                } else {
                    assertEquals("$entered vs $profile", ResultBloodGroupGuard.Conflict(profile, entered), conflict)
                }
            }
        }
        // Rh alone is a contradiction too, and the profile value is read as typed.
        assertEquals(ResultBloodGroupGuard.Conflict("O+", "O-"), check("O-", " o rh+ "))
    }

    @Test
    fun `UC-BLOOD-14 a bare value the repository would silently replace is refused in the form`() {
        // No date, no note: IpsBloodGroup treats it as a derived copy, not as a contradiction…
        val bare = be.heyman.android.jemmapassdemo.ips.IpsResult(
            id = "rs-new", code = IpsBloodGroup.LOINC_ABO_RH, display = IpsBloodGroup.DISPLAY_ABO_RH,
            valueCode = IpsBloodGroup.snomedCode("A+"), valueDisplay = IpsBloodGroup.snomedDisplay("A+"),
        )
        assertEquals(false, IpsBloodGroup.contradictsProfile(bare, "O+"))
        // …the form still refuses it: the user just entered A+ on an O+ profile.
        assertEquals(ResultBloodGroupGuard.Conflict("O+", "A+"), check("A+", "O+"))
    }

    @Test
    fun `UC-BLOOD-15 an unreadable blood group value cannot be saved next to a profile group`() {
        val conflict = ResultBloodGroupGuard.check(IpsBloodGroup.LOINC_ABO_RH, null, null, "see card", "B+")
        assertEquals(ResultBloodGroupGuard.Conflict("B+", null), conflict)
        assertEquals(ResultBloodGroupGuard.Conflict("B+", null), ResultBloodGroupGuard.check(" 882-1 ", null, null, null, "B+"))
        // A value typed as text is read like the profile's.
        assertNull(ResultBloodGroupGuard.check(IpsBloodGroup.LOINC_ABO_RH, null, null, "B positive", "B+"))
        assertEquals(
            ResultBloodGroupGuard.Conflict("B+", "AB-"),
            ResultBloodGroupGuard.check(IpsBloodGroup.LOINC_ABO_RH, null, "Blood group AB Rh(D) negative", null, "B+"),
        )
    }

    @Test
    fun `UC-BLOOD-16 other tests and profiles without a blood group are not blocked`() {
        assertNull(check("A+", "O+", code = "2823-3"))
        assertNull(check("A+", "O+", code = null))
        for (profile in listOf(null, "", "  ", "unknown")) assertNull("profile='$profile'", check("A+", profile))
    }
}
