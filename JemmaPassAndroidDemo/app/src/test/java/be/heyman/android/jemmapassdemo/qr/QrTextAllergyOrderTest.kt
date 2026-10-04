/*
 * RED TEST (improvement Analyse-0001, safety) — not to be edited by the implementer
 *
 * The text QR is capped at one frame. When the allergies section itself has to lose lines, the
 * builder removes the LAST line. Allergies are printed in the order they were typed, so a
 * life-threatening allergy typed after a mild one is the first to disappear.
 *
 * Rule : on the text QR, allergies are printed HIGH first, then UNABLE_TO_ASSESS, then LOW ;
 * inside one level the order typed by the person is kept. A HIGH allergy is never dropped while
 * a lower one is still printed.
 *
 * Expected red today : UC-QRT-020, UC-QRT-021, UC-QRT-022.   Lock : UC-QRT-023.
 */
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.kb.AllergyCriticality
import be.heyman.android.jemmapassdemo.kb.ClinicalStatus
import be.heyman.android.jemmapassdemo.kb.HydratedAllergy
import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrTextAllergyOrderTest {

    private fun bytes(s: String): Int = s.toByteArray(Charsets.UTF_8).size

    private fun allergy(name: String, crit: AllergyCriticality) = HydratedAllergy(
        raw = JAllergy(c = "al-$name"), resolvedConcept = null, displayLocalized = name,
        criticality = crit, clinicalStatus = ClinicalStatus.ACTIVE,
    )

    private fun hydrated(allergies: List<HydratedAllergy>): HydratedProfile = HydratedProfile(
        raw = JemmaProfileJ(j = "1.2", sid = "x", p = JPatient(gn = "Kurodo", bd = "1980-01-01")),
        uiLang = "en", allergies = allergies, medications = emptyList(), conditions = emptyList(),
        ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
    )

    /** Names of the allergies printed on the text, in printing order. */
    private fun printed(text: String, names: List<String>): List<String> =
        text.lines().mapNotNull { line -> names.firstOrNull { line.contains(it) } }

    private val mild = (1..40).map { "Mild seasonal allergy number %02d".format(it) }

    @Test
    fun `UC-QRT-020 allergies are printed by criticality, highest first`() {
        val typed = listOf(
            allergy("Dust mites", AllergyCriticality.LOW),
            allergy("Shellfish", AllergyCriticality.UNABLE_TO_ASSESS),
            allergy("Penicillin", AllergyCriticality.HIGH),
        )
        val text = JemmaTextPayloadBuilder.build(hydrated(typed), JemmaTextPayloadBuilder.Lang.EN)
        assertEquals(
            "A rescuer reads the first lines first : the life-threatening allergy must lead the section.",
            listOf("Penicillin", "Shellfish", "Dust mites"),
            printed(text, listOf("Dust mites", "Shellfish", "Penicillin")),
        )
    }

    @Test
    fun `UC-QRT-021 a HIGH allergy typed last survives the cut of the allergies section`() {
        val typed = mild.map { allergy(it, AllergyCriticality.LOW) } + allergy("Penicillin", AllergyCriticality.HIGH)
        val full = JemmaTextPayloadBuilder.build(hydrated(typed), JemmaTextPayloadBuilder.Lang.EN)
        val cap = bytes(full) / 2
        val text = JemmaTextPayloadBuilder.build(hydrated(typed), JemmaTextPayloadBuilder.Lang.EN, cap)

        assertTrue("the test needs a real cut : ${bytes(text)} bytes for a cap of $cap", bytes(text) <= cap)
        assertTrue("the cut must be marked", text.contains(JemmaTextPayloadBuilder.TRUNCATION_MARK))
        assertTrue("the test needs mild allergies still printed", printed(text, mild).isNotEmpty())
        assertTrue(
            "Penicillin (HIGH) was typed last and has been removed from the text QR, while " +
                "${printed(text, mild).size} mild allergies are still printed. A rescuer would give penicillin.",
            text.contains("Penicillin"),
        )
    }

    @Test
    fun `UC-QRT-022 no lower allergy is printed while a HIGH one has been dropped`() {
        val high = (1..40).map { "Severe allergy number %02d".format(it) }
        val typed = mild.map { allergy(it, AllergyCriticality.LOW) } + high.map { allergy(it, AllergyCriticality.HIGH) }
        val full = JemmaTextPayloadBuilder.build(hydrated(typed), JemmaTextPayloadBuilder.Lang.EN)
        val text = JemmaTextPayloadBuilder.build(hydrated(typed), JemmaTextPayloadBuilder.Lang.EN, bytes(full) / 3)

        val keptHigh = printed(text, high).size
        val keptMild = printed(text, mild).size
        assertTrue("the test needs a real cut", keptHigh + keptMild < typed.size)
        assertTrue(
            "$keptMild mild allergies are printed while ${high.size - keptHigh} severe ones were dropped.",
            keptMild == 0 || keptHigh == high.size,
        )
    }

    @Test
    fun `UC-QRT-023 inside one criticality the typed order is kept - lock`() {
        val names = listOf("Latex", "Peanut", "Aspirin")
        val text = JemmaTextPayloadBuilder.build(
            hydrated(names.map { allergy(it, AllergyCriticality.HIGH) }), JemmaTextPayloadBuilder.Lang.EN,
        )
        assertEquals(names, printed(text, names))
    }
}
