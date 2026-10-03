/*
 * RED TEST (wave 3, "KB only", PROTOCOL 9.1) — interface labels of curated codes.
 * The text QR receives its labels from a CodeLabelResolver handed to the builder : no hidden global state,
 * so the QR printed in Japanese on a French phone speaks Japanese, and the same contract can be replayed on iOS.
 * Families covered here : administration routes (resources only) and devices (resources + text QR).
 * Not to be edited by the implementer.
 */
package be.heyman.android.jemmapassdemo.kbonly

import be.heyman.android.jemmapassdemo.qr.CodeLabelResolver
import be.heyman.android.jemmapassdemo.qr.JEntryGeneric
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.qr.JemmaTextPayloadBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaTextPayloadBuilder.Lang
import be.heyman.android.jemmapassdemo.qr.codeLabelResourceName
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import be.heyman.android.jemmapassdemo.testsupport.ResXmlCodeLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeLabelResolverTest {

    private val SCT = "http://snomed.info/sct"
    private val PACEMAKER = "14106009"
    private val STORED_ENGLISH = "Cardiac pacemaker"

    /** A profile whose only device is a pacemaker, stored with the English label of the knowledge base. */
    private fun profile(): JemmaProfileJ =
        JemmaProfileJ(dv = listOf(JEntryGeneric(c = PACEMAKER, displayLabel = STORED_ENGLISH)))

    private fun qr(lang: Lang, uiLang: String, labels: CodeLabelResolver): String =
        JemmaTextPayloadBuilder.build(ProfileFixtures.hydrated(profile(), uiLang = uiLang), lang, labels = labels)

    @Test
    fun `UC-KB-020 the resource name of a code follows the agreed convention`() {
        assertEquals("code_label_sct_26643006", codeLabelResourceName(SCT, "26643006"))
        assertEquals("code_label_loinc_882_1", codeLabelResourceName("http://loinc.org", "882-1"))
        assertEquals("code_label_v3_FRND", codeLabelResourceName("http://terminology.hl7.org/CodeSystem/v3-RoleCode", "FRND"))
        assertNull("a system the app does not curate has no resource name", codeLabelResourceName("http://www.whocc.no/atc", "M01AE01"))
    }

    @Test
    fun `UC-KB-021 the text QR asks for the label in the language of the QR, not of the phone`() {
        val asked = ArrayList<Triple<String, String, String>>()
        qr(Lang.JA, uiLang = "fr") { system, code, lang -> asked += Triple(system, code, lang); null }
        assertTrue(
            "the builder never asked the resolver for the device $PACEMAKER (asked : $asked). The label of a curated code " +
                "must come from the resolver handed to build().",
            asked.any { it.first == SCT && it.second == PACEMAKER },
        )
        val langs = asked.filter { it.second == PACEMAKER }.map { it.third }.distinct()
        assertEquals("a Japanese QR produced on a French phone must ask for Japanese labels", listOf("ja"), langs)
    }

    @Test
    fun `UC-KB-022 the label given by the resolver is the one printed`() {
        val marker = "LABEL-GIVEN-BY-THE-RESOLVER"
        val text = qr(Lang.FR, uiLang = "fr") { _, code, _ -> if (code == PACEMAKER) marker else null }
        assertTrue("the text QR does not print the label of the resolver :\n$text", text.contains(marker))
    }

    @Test
    fun `UC-KB-023 without a resolver the stored English label is printed, never a label kept in the code`() {
        val text = qr(Lang.JA, uiLang = "ja", labels = CodeLabelResolver.NONE)
        assertTrue("with no interface label the QR must fall back to the label stored with the entry :\n$text", text.contains(STORED_ENGLISH))
    }

    @Test
    fun `UC-KB-024 a resolver that knows nothing never makes the raw code appear`() {
        val text = qr(Lang.FR, uiLang = "fr", labels = CodeLabelResolver.NONE)
        assertFalse("the SNOMED code $PACEMAKER is printed raw on the text QR :\n$text", text.contains(PACEMAKER))
    }

    @Test
    fun `UC-KB-025 the resources hold the route and device labels in French and Japanese`() {
        val res = ResXmlCodeLabels.locate()
        assertTrue("res folder not found", res != null)
        val labels = ResXmlCodeLabels(res!!)
        // The texts were written by the product owner's team in the Kotlin catalogues : they move, they are not rewritten.
        assertEquals("Orale", labels.getLabel(SCT, "26643006", "fr"))
        assertEquals("経口", labels.getLabel(SCT, "26643006", "ja"))
        assertEquals("Stimulateur cardiaque (pacemaker)", labels.getLabel(SCT, PACEMAKER, "fr"))
        assertEquals("心臓ペースメーカー", labels.getLabel(SCT, PACEMAKER, "ja"))
        assertEquals("a language without its own text falls back to the default one",
            labels.getLabel(SCT, PACEMAKER, "en"), labels.getLabel(SCT, PACEMAKER, "sv"))
    }

    @Test
    fun `UC-KB-026 with the resources of the app the Japanese QR names the device in Japanese`() {
        val res = ResXmlCodeLabels.locate()
        assertTrue("res folder not found", res != null)
        val text = qr(Lang.JA, uiLang = "fr", labels = ResXmlCodeLabels(res!!))
        assertTrue("the Japanese text QR does not name the pacemaker in Japanese :\n$text", text.contains("心臓ペースメーカー"))
    }
}
