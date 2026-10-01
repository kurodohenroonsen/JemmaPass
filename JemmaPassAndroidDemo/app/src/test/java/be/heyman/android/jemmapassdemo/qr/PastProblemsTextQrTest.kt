/*
 * PastProblemsTextQrTest.kt — 📜 section of the text QR (device QA cycle 16):
 * coded past illnesses are printed in the QR language when the hydrator found a
 * KB translation, English otherwise; KB "Not Translated[…]" placeholders never leak.
 */
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.ips.IpsConditionSeverity
import be.heyman.android.jemmapassdemo.ips.IpsPastProblem
import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.kb.KbTranslations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PastProblemsTextQrTest {

    private val mi = IpsPastProblem(id = "ph-mi", code = "22298006", display = "Myocardial infarction",
        onset = "2015-08-27", abatement = "2015-09", severity = IpsConditionSeverity.SEVERE)
    private val tb = IpsPastProblem(id = "ph-tb", code = "56717001", display = "Tuberculosis", onset = "1962", abatement = "1963")
    private val free = IpsPastProblem(id = "ph-free", text = "Hepatite virale enfance", clinicalStatus = "inactive")

    private fun hydrated(labels: Map<String, String>): HydratedProfile = HydratedProfile(
        raw = JemmaProfileJ(j = "1.2", sid = "x", p = JPatient(gn = "Haru", fn = "Tanaka", gs = "F"),
            ph = listOf(mi, tb, free).map { it.toJEntry() }),
        uiLang = "fr", allergies = emptyList(), medications = emptyList(), conditions = emptyList(),
        ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
        pastProblemLabels = labels,
    )

    @Test
    fun localisedLabelsWinOverTheEnglishTerm() {
        val text = JemmaTextPayloadBuilder.build(hydrated(mapOf("22298006" to "Infarctus du myocarde")), JemmaTextPayloadBuilder.Lang.FR)
        assertTrue(text.contains("📜 [ ANTÉCÉDENTS MÉDICAUX ]"))
        assertTrue(text.contains("Infarctus du myocarde — 2015-08-27 → 2015-09"))
        assertFalse(text.contains("Myocardial infarction"))
        // no translation → English term ; free text untouched ; non-resolved status shown
        assertTrue(text.contains("Tuberculosis — 1962 → 1963"))
        assertTrue(text.contains("Hepatite virale enfance (inactive)"))
    }

    @Test
    fun englishQrWithoutLabelsKeepsTheSnomedTerms() {
        val text = JemmaTextPayloadBuilder.build(hydrated(emptyMap()), JemmaTextPayloadBuilder.Lang.EN)
        assertTrue(text.contains("📜 [ PAST ILLNESSES ]"))
        assertTrue(text.contains("Myocardial infarction — 2015-08-27 → 2015-09"))
    }

    @Test
    fun kbPlaceholdersAreDropped() {
        assertNull(KbTranslations.clean("Not Translated[Tuberculosis]"))
        assertNull(KbTranslations.clean("  "))
        assertNull(KbTranslations.clean(null))
        assertEquals("結核", KbTranslations.clean("結核"))
        assertEquals("Tuberculosis", KbTranslations.unwrap("Not Translated[Tuberculosis]"))
        assertEquals("Tuberculose", KbTranslations.unwrap("Tuberculose"))
    }
}
