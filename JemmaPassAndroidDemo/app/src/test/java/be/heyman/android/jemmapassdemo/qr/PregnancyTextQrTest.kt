/*
 * PregnancyTextQrTest.kt — 🤰 section of the text QR: pregnancy observations are
 * printed with the localised short labels of IpsPregnancyCatalog, not the raw
 * LOINC names.
 */
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.ips.IpsPregnancyObs
import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PregnancyTextQrTest {

    private val liveBirths = IpsPregnancyObs(id = "pg-live", code = "11636-8", count = 2, date = "2026-02-10")

    private fun hydrated(): HydratedProfile = HydratedProfile(
        raw = JemmaProfileJ(j = "1.2", sid = "x", p = JPatient(gn = "Haru", fn = "Tanaka", gs = "F"),
            pg = listOf(liveBirths.toJEntry())),
        uiLang = "fr", allergies = emptyList(), medications = emptyList(), conditions = emptyList(),
        ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
    )

    @Test
    fun frenchQrPrintsTheLocalisedOutcomeLabel() {
        val text = JemmaTextPayloadBuilder.build(hydrated(), JemmaTextPayloadBuilder.Lang.FR)
        assertTrue(text.contains("🤰 [ GROSSESSES ]"))
        assertTrue(text.contains("Naissances vivantes: 2 — 2026-02-10"))
        assertFalse(text.contains("[#] Births.live"))
    }

    @Test
    fun englishQrPrintsTheEnglishShortLabel() {
        val text = JemmaTextPayloadBuilder.build(hydrated(), JemmaTextPayloadBuilder.Lang.EN)
        assertTrue(text.contains("Live births: 2"))
    }
}
