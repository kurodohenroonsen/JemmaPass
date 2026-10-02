/*
 * PatientFormMergeTest.kt — UC-PAT-011 / UC-PAT-005 (qa/usecases/01-pillar-editing.md) :
 * saving the identity form keeps the addresses, telecoms and identifiers it does not
 * show, and a partial birth date.
 */
package be.heyman.android.jemmapassdemo.ui.profile.perso

import be.heyman.android.jemmapassdemo.qr.JAddress
import be.heyman.android.jemmapassdemo.qr.JContact
import be.heyman.android.jemmapassdemo.qr.JIdentifier
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JTelecom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatientFormMergeTest {

    private val home = JAddress(use = "home", line = "Rue de la Paix 12", city = "Couvin", postalCode = "5660", state = "Namur", country = "BE")
    private val work = JAddress(use = "work", line = "Marunouchi 1-1", city = "Tokyo", postalCode = "100-0005", country = "JP")
    private val fax = JTelecom(system = "fax", value = "+3260000000", use = "work")
    private val workPhone = JTelecom(system = "phone", value = "+3225550000", use = "work")
    private val mobile = JTelecom(system = "phone", value = "+32470000000", use = "mobile")
    private val mail = JTelecom(system = "email", value = "haru@example.org", use = "home")
    private val workMail = JTelecom(system = "email", value = "haru@work.example", use = "work")
    private val nrn = JIdentifier(system = "BE-NRN", value = "46.02.05-123.45")
    private val passport = JIdentifier(system = "PASSPORT", value = "EP123456")
    private val ehic = JIdentifier(system = "EU-EHIC", value = "80056000000000000000")
    private val spouse = JContact(n = "Misaki", r = "spouse", p = "+81900000000")

    private val stored = JPatient(
        gn = "Haru", fn = "Sato", gs = "F", bd = "1946", nat = "JP", bt = "O+",
        adr = "Rue de la Paix 12, Couvin, 5660, BE", tel = "+3225550000", eml = "haru@example.org", idn = "46.02.05-123.45",
        ids = listOf(nrn, passport, ehic),
        adrs = listOf(home, work),
        tels = listOf(fax, workPhone, mobile, mail, workMail),
        gp = "Dr Dupont", lang = "fr-BE",
        ct = listOf(spouse),
    )

    /** The form as it is pre-filled from [p] (same rules as PatientEditFragment.populateForm). */
    private fun formOf(p: JPatient) = PatientFormInput(
        givenName = p.gn, familyName = p.fn, gender = p.gs, birthDate = p.bd, nationality = p.nat, bloodType = p.bt,
        addressUse = p.adrs.firstOrNull()?.use ?: "home",
        addressLine = p.adrs.firstOrNull()?.line ?: p.adr,
        addressCity = p.adrs.firstOrNull()?.city,
        addressPostalCode = p.adrs.firstOrNull()?.postalCode,
        addressCountry = p.adrs.firstOrNull()?.country,
        phone = PatientFormMerge.displayedPhone(p.tels)?.value ?: p.tel,
        email = PatientFormMerge.displayedEmail(p.tels)?.value ?: p.eml,
        identifierSystem = p.ids.firstOrNull()?.system,
        identifierValue = p.ids.firstOrNull()?.value ?: p.idn,
        generalPractitioner = p.gp, language = p.lang,
    )

    @Test
    fun `UC-PAT-011 opening and saving without any change loses nothing`() {
        assertEquals(stored, PatientFormMerge.buildPatient(stored, formOf(stored)))
    }

    @Test
    fun `UC-PAT-005 changing the phone keeps the other entries and the partial birth date`() {
        val saved = PatientFormMerge.buildPatient(stored, formOf(stored).copy(phone = "+32471111111"))
        assertEquals("1946", saved.bd)
        assertEquals(
            listOf(fax, workPhone.copy(value = "+32471111111"), mobile, mail, workMail),
            saved.tels,
        )
        assertEquals("+32471111111", saved.tel)
        assertEquals(stored.adrs, saved.adrs)
        assertEquals(stored.ids, saved.ids)
        assertEquals(listOf(spouse), saved.ct)
    }

    @Test
    fun `UC-PAT-011 editing the address keeps the second address and the region`() {
        val saved = PatientFormMerge.buildPatient(
            stored,
            formOf(stored).copy(addressLine = "Rue Neuve 3", addressCity = "Chimay", addressPostalCode = "6460"),
        )
        assertEquals(
            listOf(home.copy(line = "Rue Neuve 3", city = "Chimay", postalCode = "6460"), work),
            saved.adrs,
        )
        assertEquals("Namur", saved.adrs[0].state)
        assertEquals("Rue Neuve 3, Chimay, 6460, BE", saved.adr)
    }

    @Test
    fun `UC-PAT-011 clearing the displayed entry removes only that one`() {
        val saved = PatientFormMerge.buildPatient(
            stored,
            formOf(stored).copy(
                addressLine = null, addressCity = null, addressPostalCode = null, addressCountry = null,
                phone = null, email = null, identifierSystem = null, identifierValue = null,
            ),
        )
        assertEquals(listOf(work), saved.adrs)
        assertEquals(listOf(fax, mobile, workMail), saved.tels)
        assertEquals(listOf(passport, ehic), saved.ids)
        assertNull(saved.adr)
        assertNull(saved.tel)
        assertNull(saved.eml)
        assertNull(saved.idn)
    }

    @Test
    fun `UC-PAT-011 editing the identifier keeps the other identifiers`() {
        val saved = PatientFormMerge.buildPatient(
            stored,
            formOf(stored).copy(identifierSystem = "FR-NSS", identifierValue = "1 46 02 75 000 000 00"),
        )
        assertEquals(listOf(JIdentifier("FR-NSS", "1 46 02 75 000 000 00"), passport, ehic), saved.ids)
        assertEquals("1 46 02 75 000 000 00", saved.idn)
    }

    @Test
    fun `UC-PAT-011 an sms telecom keeps its system and use when its number changes`() {
        val sms = JTelecom(system = "sms", value = "+81900000001", use = "old")
        val p = stored.copy(tels = listOf(sms, mail))
        val saved = PatientFormMerge.buildPatient(p, formOf(p).copy(phone = "+81900000002"))
        assertEquals(listOf(sms.copy(value = "+81900000002"), mail), saved.tels)
    }

    @Test
    fun `UC-PAT-001 a new profile gets one address, phone, email and identifier`() {
        val form = PatientFormInput(
            givenName = "Kenji", familyName = null, gender = "M", birthDate = "1950", nationality = "JP", bloodType = null,
            addressUse = "home", addressLine = "1-2-3", addressCity = "Kumakogen", addressPostalCode = null, addressCountry = "JP",
            phone = "+81800000000", email = "kenji@example.org",
            identifierSystem = "OTHER", identifierValue = "X1",
            generalPractitioner = null, language = "ja-JP",
        )
        val saved = PatientFormMerge.buildPatient(null, form)
        assertEquals(
            JPatient(
                gn = "Kenji", fn = null, gs = "M", bd = "1950", nat = "JP", bt = null,
                adr = "1-2-3, Kumakogen, JP", tel = "+81800000000", eml = "kenji@example.org", idn = "X1",
                ids = listOf(JIdentifier("OTHER", "X1")),
                adrs = listOf(JAddress(use = "home", line = "1-2-3", city = "Kumakogen", country = "JP")),
                tels = listOf(
                    JTelecom(system = "phone", value = "+81800000000", use = "mobile"),
                    JTelecom(system = "email", value = "kenji@example.org", use = "home"),
                ),
                lang = "ja-JP",
            ),
            saved,
        )
    }

    @Test
    fun `UC-PAT-011 legacy single fields are promoted without duplicating`() {
        val legacy = JPatient(gn = "Old", bd = "1960-01-01", adr = "Somewhere 1", tel = "+3211111111", eml = "old@example.org", idn = "ID9")
        val saved = PatientFormMerge.buildPatient(legacy, formOf(legacy).copy(identifierSystem = "OTHER"))
        assertEquals(listOf(JAddress(use = "home", line = "Somewhere 1")), saved.adrs)
        assertEquals(
            listOf(JTelecom("phone", "+3211111111", "mobile"), JTelecom("email", "old@example.org", "home")),
            saved.tels,
        )
        assertEquals(listOf(JIdentifier("OTHER", "ID9")), saved.ids)
        // Saving again is stable.
        assertEquals(saved, PatientFormMerge.buildPatient(saved, formOf(saved)))
    }
}
