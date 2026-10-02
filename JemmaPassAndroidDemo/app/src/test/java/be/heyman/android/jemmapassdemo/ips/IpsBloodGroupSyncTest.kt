/*
 * IpsBloodGroupSyncTest.kt — UC-BLOOD-01..06 : the 882-1 Observation mirrored from `p.bt`
 * is recognised by its derivation rule, not by its id, so an imported copy follows the
 * patient's blood group; a contradicting result entered by hand is replaced by the
 * profile value and reported, never dropped without a trace.
 */
package be.heyman.android.jemmapassdemo.ips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsBloodGroupSyncTest {

    private val sid = "demo_bg"
    private val potassium = IpsResult(id = "rs-1", code = "2823-3", display = "Potassium", value = "4.1", unit = "mmol/L")

    /** What the receiver gets: the sender's derived Observation through `_j.rs`, with a new id. */
    private fun imported(bt: String, index: Int = 0): IpsResult =
        IpsResult.fromJEntry(IpsBloodGroup.derivedResult("other_device", bt)!!.toJEntry(), index)

    private fun byHand(code: String, id: String = "rs-user") = IpsResult(
        id = id, code = IpsBloodGroup.LOINC_ABO_RH, display = IpsBloodGroup.DISPLAY_ABO_RH, date = "2015-09-01",
        valueCode = code, valueDisplay = IpsBloodGroup.labelFromSnomed(code), performer = "Lab Dupont",
    )

    private fun groups(results: List<IpsResult>) = results.filter { IpsBloodGroup.isBloodGroup(it) }

    @Test
    fun `UC-BLOOD-01 an imported derived observation is recognised without its id`() {
        val copy = imported("A+")
        assertFalse("the import gives it a new id", IpsBloodGroup.isDerived(copy))
        assertTrue(IpsBloodGroup.looksDerived(copy))
        assertTrue(IpsBloodGroup.isBloodGroup(copy))
        assertEquals("A+", IpsBloodGroup.labelOf(copy))
        // An ordinary result, and a blood group entered by hand with its date, are not derived.
        assertFalse(IpsBloodGroup.looksDerived(potassium))
        assertFalse(IpsBloodGroup.isBloodGroup(potassium))
        assertFalse(IpsBloodGroup.looksDerived(byHand("278149003")))
    }

    @Test
    fun `UC-BLOOD-02 after an import the single 882-1 follows the patient blood group`() {
        val stored = listOf(imported("A+"), potassium)

        // Same group as the profile: one entry, now carrying this profile's stable id.
        val same = IpsBloodGroup.reconcile(stored, sid, "A+")
        assertEquals(listOf(IpsBloodGroup.derivedResult(sid, "A+"), potassium), same.results)
        assertFalse(same.hasConflict)

        // The patient's group is corrected: the imported copy is updated, not left stale.
        val changed = IpsBloodGroup.reconcile(stored, sid, "O-")
        assertEquals(1, groups(changed.results).size)
        assertEquals("278148006", groups(changed.results).single().valueCode)
        assertEquals(IpsBloodGroup.derivedId(sid), groups(changed.results).single().id)
        assertEquals(potassium, changed.results.last())
        assertFalse("a stale derived copy is not a conflict", changed.hasConflict)

        // Idempotent.
        assertEquals(changed.results, IpsBloodGroup.sync(changed.results, sid, "O-"))
    }

    @Test
    fun `UC-BLOOD-03 a contradicting hand-entered result is replaced and reported`() {
        val user = byHand("278148006") // O-
        val out = IpsBloodGroup.reconcile(listOf(potassium, user), sid, "O+")
        assertEquals(listOf(IpsBloodGroup.derivedResult(sid, "O+"), potassium), out.results)
        assertEquals(listOf(user), out.conflicts)
        assertTrue(out.hasConflict)
        assertTrue(IpsBloodGroup.contradictsProfile(user, "O+"))
        assertFalse(IpsBloodGroup.contradictsProfile(user, "O-"))
        assertFalse(IpsBloodGroup.contradictsProfile(user, null))
        assertFalse(IpsBloodGroup.contradictsProfile(potassium, "O+"))
        // A text value that is not a readable group cannot confirm the profile either.
        val vague = IpsResult(id = "rs-text", code = IpsBloodGroup.LOINC_ABO_RH, date = "2020-01-01", valueText = "see card")
        assertEquals(listOf(vague), IpsBloodGroup.reconcile(listOf(vague), sid, "O+").conflicts)
    }

    @Test
    fun `UC-BLOOD-04 a matching hand-entered result is kept as is with no derived twin`() {
        val user = byHand("278147001") // O+
        val stale = IpsBloodGroup.derivedResult(sid, "B+")!!
        val out = IpsBloodGroup.reconcile(listOf(stale, potassium, user, imported("O+", 3), user.copy(id = "rs-user-2")), sid, "o +")
        assertEquals(listOf(potassium, user), out.results)
        assertFalse(out.hasConflict)
        // Typed as text ("O pos"): same group, kept too.
        val typed = IpsResult(id = "rs-typed", code = IpsBloodGroup.LOINC_ABO_RH, date = "2019-05-02", valueText = "O pos")
        assertEquals(listOf(typed), IpsBloodGroup.sync(listOf(typed), sid, "O+"))
    }

    @Test
    fun `UC-BLOOD-05 without a profile blood group nothing is invented or destroyed`() {
        val user = byHand("278148006")
        val copy = imported("A+")
        val stale = IpsBloodGroup.derivedResult(sid, "B+")!!
        for (bt in listOf(null, "", "0+", "unknown")) {
            val out = IpsBloodGroup.reconcile(listOf(stale, user, copy, potassium), sid, bt)
            assertEquals("bt='$bt'", listOf(user, copy, potassium), out.results)
            assertFalse(out.hasConflict)
        }
    }

    @Test
    fun `UC-BLOOD-06 a copy whose coded value was lost in the projection is still the mirror`() {
        val noCode = IpsResult.fromJEntry(IpsBloodGroup.derivedResult("other_device", "AB-")!!.toJEntry().copy(valueCode = null))
        assertEquals("AB-", IpsBloodGroup.labelOf(noCode))
        assertTrue(IpsBloodGroup.looksDerived(noCode))
        val out = IpsBloodGroup.reconcile(listOf(noCode), sid, "B+")
        assertEquals(listOf(IpsBloodGroup.derivedResult(sid, "B+")), out.results)
        assertFalse(out.hasConflict)
    }
}
