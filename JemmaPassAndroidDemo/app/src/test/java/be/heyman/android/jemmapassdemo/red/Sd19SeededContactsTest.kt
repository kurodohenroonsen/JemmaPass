/*
 * RED TEST (wave 1) — SD-19, qa/usecases/suspected-defects.md. Expected to FAIL until the demo data is fixed.
 * Not to be edited by the implementer : fix the seeded data, not the test.
 *
 * The emergency contacts of the demo personas can be reached (phone or e-mail) and their
 * relation is a code of the relationship catalogue, so that it is translated in the 25
 * languages and the device steps can check that a phone number reaches the rescuer.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.pillars.IpsRelationshipCatalog
import be.heyman.android.jemmapassdemo.qr.JContact
import be.heyman.android.jemmapassdemo.qr.JemmaPersonasSeeder
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd19SeededContactsTest {

    /** (persona id, contact) of every seeded emergency contact. */
    private fun seededContacts(): List<Pair<String, JContact>> =
        JemmaPersonasSeeder.getDemoProfiles().flatMap { profile ->
            profile.p?.ct.orEmpty().map { (profile.sid ?: "?") to it }
        }

    @Test
    fun `SD-19 UC-QRT-005 every seeded emergency contact can be reached by phone or e-mail`() {
        val contacts = seededContacts()
        assertTrue("the demo personas must hold at least one emergency contact", contacts.isNotEmpty())
        for ((sid, c) in contacts) {
            assertTrue(
                "$sid : the emergency contact '${c.n}' has neither phone nor e-mail. The text QR prints a name the " +
                    "rescuer cannot call, and no device step can check that a contact's phone reaches the rescuer. " +
                    "Give each seeded contact a (fictional) phone number.",
                !c.p.isNullOrBlank() || !c.e.isNullOrBlank(),
            )
        }
    }

    @Test
    fun `SD-19 UC-PAT-016 every seeded emergency contact has a relation of the catalogue`() {
        val contacts = seededContacts()
        assertTrue("the demo personas must hold at least one emergency contact", contacts.isNotEmpty())
        for ((sid, c) in contacts) {
            assertTrue(
                "$sid : the relation '${c.r}' of the contact '${c.n}' is not a code of the relationship catalogue " +
                    "(a friend is 'FRND') : it stays in English in the 25 languages instead of being translated.",
                IpsRelationshipCatalog.isValidCode(c.r),
            )
        }
    }
}
