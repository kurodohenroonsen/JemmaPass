/*
 * RED TEST (wave 1) — SD-15, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * The import dialog names the person whose profile is about to be imported, also when the
 * profile only holds a family name (mononym stored as family name).
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.qr.displayName
import org.junit.Assert.assertEquals
import org.junit.Test

class Sd15FamilyNameOnlyDisplayNameTest {

    private val message = "A profile that only holds the family name 'Dupont' is announced as an unknown profile : " +
        "the caregiver imports or refuses a record without knowing whose it is. The family name must be shown."

    @Test
    fun `SD-15 UC-HUM-005 a profile with a family name only shows that name`() {
        assertEquals(message, "Dupont", JemmaProfileJ(j = "1.2", p = JPatient(fn = "Dupont")).displayName())
    }

    @Test
    fun `SD-15 UC-HUM-005 a blank given name does not hide the family name`() {
        assertEquals(message, "Dupont", JemmaProfileJ(j = "1.2", p = JPatient(gn = " ", fn = "Dupont")).displayName())
        assertEquals(message, "Dupont", JemmaProfileJ(j = "1.2", p = JPatient(gn = "", fn = "Dupont")).displayName())
    }
}
