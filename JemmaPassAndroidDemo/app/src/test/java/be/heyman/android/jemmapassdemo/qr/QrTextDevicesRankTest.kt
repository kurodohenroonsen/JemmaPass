/*
 * RED TEST (improvement Analyse-0002, safety) — not to be edited by the implementer
 *
 * The text QR is capped at one frame. When lines have to go, the builder removes them from the
 * section with the HIGHEST rank first. Today RANK_DEVICES = 7 : an implanted device (pacemaker,
 * defibrillator, insulin pump) is dropped BEFORE the functional status (rank 5, "walks with a
 * cane") and before the contacts (rank 6). A rescuer who shocks or sends to MRI a patient whose
 * pacemaker line was cut can kill them.
 *
 * Rule : a device line is never dropped while a line of functional status, contacts, patient
 * extras, past problems, procedures, results or immunizations is still printed. Allergies and
 * medications still come before devices (lock). Inside the devices section an inactive device
 * goes before an active one (lock).
 *
 * Expected red today : UC-QRT-030, UC-QRT-031, UC-QRT-032.   Locks : UC-QRT-033, UC-QRT-034.
 */
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.kb.AllergyCriticality
import be.heyman.android.jemmapassdemo.kb.ClinicalStatus
import be.heyman.android.jemmapassdemo.kb.HydratedAllergy
import be.heyman.android.jemmapassdemo.kb.HydratedMedication
import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.kb.MedicationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrTextDevicesRankTest {

    private fun bytes(s: String): Int = s.toByteArray(Charsets.UTF_8).size

    private fun entries(prefix: String, n: Int, status: String? = null) =
        (1..n).map { JEntryGeneric(displayLabel = "$prefix %02d".format(it), date = "2020-01-%02d".format(it % 28 + 1), status = status) }

    private fun allergy(name: String) = HydratedAllergy(
        raw = JAllergy(c = "al-$name"), resolvedConcept = null, displayLocalized = name,
        criticality = AllergyCriticality.HIGH, clinicalStatus = ClinicalStatus.ACTIVE,
    )

    private fun medication(name: String) = HydratedMedication(
        raw = JMedication(c = "md-$name"), resolvedConcept = null, displayLocalized = name,
        timing = "1x/day", doseValue = "500", doseUnit = "mg", route = MedicationRoute.ORAL,
        atcCode = null, allAtcCodes = emptyList(), rxnormCui = null,
    )

    private fun hydrated(
        devices: List<JEntryGeneric>,
        functional: List<JEntryGeneric> = emptyList(),
        contacts: List<JContact> = emptyList(),
        pastProblems: List<JEntryGeneric> = emptyList(),
        procedures: List<JEntryGeneric> = emptyList(),
        results: List<JEntryGeneric> = emptyList(),
        immunizations: List<JEntryGeneric> = emptyList(),
        allergies: List<HydratedAllergy> = emptyList(),
        medications: List<HydratedMedication> = emptyList(),
        patient: JPatient = JPatient(gn = "Haru", fn = "Tanaka", bd = "1956-02-05", bt = "A+", ct = contacts),
    ): HydratedProfile = HydratedProfile(
        raw = JemmaProfileJ(
            j = "1.2", sid = "x", p = patient, dv = devices, fs = functional, ph = pastProblems,
            pr = procedures, rs = results, im = immunizations,
        ),
        uiLang = "en", allergies = allergies, medications = medications, conditions = emptyList(),
        ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
    )

    /** Labels of [entries] that are printed on [text]. */
    private fun printed(text: String, entries: List<JEntryGeneric>): List<String> =
        entries.mapNotNull { e -> e.displayLabel?.takeIf { text.contains(it) } }

    private fun build(h: HydratedProfile, cap: Int = JemmaTextPayloadBuilder.MAX_BYTES): String =
        JemmaTextPayloadBuilder.build(h, JemmaTextPayloadBuilder.Lang.EN, cap)

    private val pacemaker = JEntryGeneric(displayLabel = "Cardiac pacemaker, MRI conditional", date = "2019-03-12")

    @Test
    fun `UC-QRT-030 an implanted device is not dropped while functional status lines are still printed`() {
        val canes = entries("Walks with a cane, functional status line", 30)
        val h = hydrated(devices = listOf(pacemaker), functional = canes)
        val cap = bytes(build(h)) / 2
        val text = build(h, cap)

        assertTrue("the test needs a real cut : ${bytes(text)} bytes for a cap of $cap", bytes(text) <= cap)
        assertTrue("the cut must be marked", text.contains(JemmaTextPayloadBuilder.TRUNCATION_MARK))
        val keptFs = printed(text, canes).size
        assertTrue("the test needs functional status lines still printed", keptFs > 0)
        assertTrue(
            "The pacemaker line was cut from the text QR while $keptFs functional status lines (\"walks with a cane\") " +
                "are still printed. A rescuer would defibrillate or send to MRI without knowing.",
            text.contains(pacemaker.displayLabel!!),
        )
    }

    @Test
    fun `UC-QRT-031 an implanted device is not dropped while contact lines are still printed`() {
        val contacts = (1..30).map { JContact(n = "Contact number %02d".format(it), r = "friend", p = "+32 470 00 00 %02d".format(it)) }
        val h = hydrated(devices = listOf(pacemaker), contacts = contacts)
        val cap = bytes(build(h)) / 2
        val text = build(h, cap)

        assertTrue("the test needs a real cut", bytes(text) <= cap && text.contains(JemmaTextPayloadBuilder.TRUNCATION_MARK))
        val keptCt = contacts.count { text.contains(it.n!!) }
        assertTrue("the test needs contacts still printed", keptCt > 0)
        assertTrue(
            "The pacemaker line was cut while $keptCt emergency contacts are still printed.",
            text.contains(pacemaker.displayLabel!!),
        )
    }

    @Test
    fun `UC-QRT-032 no device is dropped while any lower section still has a line`() {
        val devices = entries("Implanted device number", 12)
        val ph = entries("Past problem number", 12)
        val pr = entries("Procedure number", 12)
        val rs = entries("Result number", 12)
        val im = entries("Vaccine number", 12)
        val fs = entries("Functional status number", 12)
        val contacts = (1..12).map { JContact(n = "Contact number %02d".format(it), r = "friend", p = "+32 470 00 00 %02d".format(it)) }
        val patient = JPatient(
            gn = "Haru", fn = "Tanaka", bd = "1956-02-05", bt = "A+", ct = contacts,
            adr = "12 rue de la Gare, 5660 Couvin", tel = "+32 60 00 00 00", eml = "haru@example.org", idn = "56.02.05-123.45",
        )
        val h = hydrated(devices = devices, functional = fs, contacts = contacts, pastProblems = ph, procedures = pr, results = rs, immunizations = im, patient = patient)
        val full = build(h)
        // Cap chosen so that the devices section itself has to lose lines today.
        val text = build(h, bytes(full) / 4)

        val keptDevices = printed(text, devices).size
        assertTrue("the test needs a real cut", keptDevices + printed(text, ph).size + printed(text, pr).size < devices.size + ph.size + pr.size)
        val lower = printed(text, ph).size + printed(text, pr).size + printed(text, rs).size + printed(text, im).size +
            printed(text, fs).size + contacts.count { text.contains(it.n!!) } +
            listOf(patient.adr!!, patient.tel!!, patient.eml!!, patient.idn!!).count { text.contains(it) }
        assertTrue(
            "${devices.size - keptDevices} device lines were dropped while $lower lines of lower sections " +
                "(functional status, contacts, patient extras, past problems, procedures, results, vaccines) are still printed.",
            keptDevices == devices.size || lower == 0,
        )
    }

    @Test
    fun `UC-QRT-033 allergies and medications still go before devices - lock`() {
        val devices = entries("Implanted device number", 30)
        val h = hydrated(
            devices = devices,
            allergies = listOf(allergy("Penicillin"), allergy("Peanut")),
            medications = listOf(medication("Warfarin"), medication("Insulin")),
        )
        val text = build(h, bytes(build(h)) / 2)
        assertTrue("the test needs a real cut", printed(text, devices).size < devices.size)
        for (name in listOf("Penicillin", "Peanut", "Warfarin", "Insulin")) {
            assertTrue("$name must still be printed : allergies and medications are cut after devices", text.contains(name))
        }
    }

    @Test
    fun `UC-QRT-034 inside the devices section an inactive device is dropped before an active one - lock`() {
        val active = entries("Active device number", 10)
        val inactive = entries("Removed device number", 10, status = "inactive")
        val h = hydrated(devices = inactive + active)
        val text = build(h, bytes(build(h)) * 2 / 3)
        val keptActive = printed(text, active).size
        val keptInactive = printed(text, inactive).size
        assertTrue("the test needs a real cut inside the devices section", keptActive + keptInactive < 20)
        assertEquals("every active device is kept while inactive ones are cut", active.size, keptActive)
        assertEquals(
            "the devices section is printed in the order active first, then inactive",
            printed(text, active + inactive), printed(text, active) + printed(text, inactive),
        )
    }
}
