/*
 * BloodGroupConflictTest.kt — UC-BLOOD-10..12 : what ProfilesRepository reports when a write
 * replaced a blood-group result contradicting the profile's blood group (`p.bt`).
 */
package be.heyman.android.jemmapassdemo.profiles

import be.heyman.android.jemmapassdemo.ips.IpsBloodGroup
import be.heyman.android.jemmapassdemo.ips.IpsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BloodGroupConflictTest {

    private val sid = "demo_bg"
    private val potassium = IpsResult(id = "rs-1", code = "2823-3", display = "Potassium", value = "4.1", unit = "mmol/L")

    private fun byHand(label: String, id: String = "rs-user") = IpsResult(
        id = id, code = IpsBloodGroup.LOINC_ABO_RH, display = IpsBloodGroup.DISPLAY_ABO_RH, date = "2015-09-01",
        valueCode = IpsBloodGroup.snomedCode(label), valueDisplay = IpsBloodGroup.snomedDisplay(label),
        performer = "Lab Dupont",
    )

    @Test
    fun `UC-BLOOD-10 a contradicting result is reported with what it stated and is not stored`() {
        val reconciled = IpsBloodGroup.reconcile(listOf(byHand("A+"), potassium), sid, "o +")
        val conflict = BloodGroupConflict.of(sid, "o +", reconciled)

        assertNotNull(conflict)
        assertEquals(sid, conflict!!.profileId)
        assertEquals("O+", conflict.profileBloodGroup)
        assertEquals(listOf(BloodGroupConflict.Replaced("rs-user", "A+", "2015-09-01")), conflict.replaced)
        // What is written holds one blood group only: the profile's.
        val stored = reconciled.results.filter { IpsBloodGroup.isBloodGroup(it) }
        assertEquals(listOf("O+"), stored.map { IpsBloodGroup.labelOf(it) })
        assertTrue(reconciled.results.contains(potassium))
    }

    @Test
    fun `UC-BLOOD-11 no report when nothing contradicts the profile`() {
        // Same group entered by hand, no blood-group result at all, or a stale derived copy.
        for (results in listOf(listOf(byHand("O+")), listOf(potassium), emptyList())) {
            assertNull(BloodGroupConflict.of(sid, "O+", IpsBloodGroup.reconcile(results, sid, "O+")))
        }
        val stale = IpsBloodGroup.derivedResult(sid, "A+")!!
        assertNull(BloodGroupConflict.of(sid, "O+", IpsBloodGroup.reconcile(listOf(stale), sid, "O+")))
        // Profile without a recognised blood group: results entered by hand are left alone.
        for (bt in listOf(null, "", "unknown")) {
            val reconciled = IpsBloodGroup.reconcile(listOf(byHand("A+")), sid, bt)
            assertNull(BloodGroupConflict.of(sid, bt, reconciled))
            assertEquals(listOf("A+"), reconciled.results.map { IpsBloodGroup.labelOf(it) })
        }
    }

    @Test
    fun `UC-BLOOD-12 every contradicting result is listed, an unreadable value included`() {
        val unreadable = IpsResult(
            id = "rs-odd", code = IpsBloodGroup.LOINC_ABO_RH, date = "2020-01-02", valueText = "see card",
        )
        val reconciled = IpsBloodGroup.reconcile(listOf(byHand("A+", "rs-a"), byHand("B-", "rs-b"), unreadable), sid, "AB-")
        val conflict = BloodGroupConflict.of(sid, "AB-", reconciled)!!

        assertEquals(listOf("rs-a", "rs-b", "rs-odd"), conflict.replaced.map { it.resultId })
        assertEquals(listOf("A+", "B-", null), conflict.replaced.map { it.stated })
        assertEquals(1, reconciled.results.count { IpsBloodGroup.isBloodGroup(it) })
        assertEquals("AB-", IpsBloodGroup.labelOf(reconciled.results.single { IpsBloodGroup.isBloodGroup(it) }))
    }
}
