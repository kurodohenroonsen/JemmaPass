/*
 * IpsPregnancyCatalogTest.kt — pregnancy pillar label catalog sanity (JVM).
 *
 * The catalog must cover exactly the LOINC codes of IpsPregnancyCodes and the
 * three status answers, in six languages, and format() must localise an
 * observation the way the text QR prints it.
 */
package be.heyman.android.jemmapassdemo.pillars

import be.heyman.android.jemmapassdemo.ips.IpsPregnancyCodes
import be.heyman.android.jemmapassdemo.ips.IpsPregnancyObs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsPregnancyCatalogTest {

    private val domainCodes: List<String> =
        listOf(IpsPregnancyCodes.STATUS) + IpsPregnancyCodes.EDD_METHODS.keys + IpsPregnancyCodes.OUTCOMES.keys

    @Test
    fun catalogCoversExactlyTheDomainCodesInOrder() {
        val catalogCodes = IpsPregnancyCatalog.CODES.map { it.code }
        assertEquals(catalogCodes.size, catalogCodes.toSet().size)
        assertEquals(13, catalogCodes.size)
        assertEquals(domainCodes, catalogCodes)
        domainCodes.forEach { assertNotNull(it, IpsPregnancyCatalog.label(it, "en")) }
        catalogCodes.forEach { assertTrue(it, IpsPregnancyCodes.isPregnancyCode(it)) }
    }

    @Test
    fun everyStatusAnswerHasAnEntry() {
        assertEquals(IpsPregnancyCodes.STATUS_ANSWERS.keys.toList(), IpsPregnancyCatalog.ANSWERS.map { it.code })
        IpsPregnancyCodes.STATUS_ANSWERS.keys.forEach { assertNotNull(it, IpsPregnancyCatalog.answer(it, "en")) }
    }

    @Test
    fun noBlankLabelInAnyLanguage() {
        (IpsPregnancyCatalog.CODES + IpsPregnancyCatalog.ANSWERS).forEach { e ->
            listOf("en", "fr", "ja", "de", "nl", "zh").forEach { lang ->
                assertTrue("${e.code} / $lang", e.pick(lang).isNotBlank())
            }
            assertTrue(e.code, e.emoji.isNotBlank())
        }
    }

    @Test
    fun pickFollowsTheLanguagePrefixAndFallsBackToEnglish() {
        val status = IpsPregnancyCatalog.CODES.first { it.code == IpsPregnancyCodes.STATUS }
        assertEquals("Statut de grossesse", status.pick("fr-BE"))
        assertEquals("妊娠状態", status.pick("JA"))
        assertEquals("Schwangerschaftsstatus", status.pick("de"))
        assertEquals("Zwangerschapsstatus", status.pick("nl-BE"))
        assertEquals("妊娠状态", status.pick("zh-CN"))
        assertEquals("Pregnancy status", status.pick("xx"))
        assertEquals("Pregnancy status", status.pick(""))
    }

    @Test
    fun unknownOrBlankCodesHaveNoLabel() {
        assertNull(IpsPregnancyCatalog.label(null, "fr"))
        assertNull(IpsPregnancyCatalog.label(" ", "fr"))
        assertNull(IpsPregnancyCatalog.label("0000-0", "fr"))
        assertNull(IpsPregnancyCatalog.answer(null, "fr"))
        assertNull(IpsPregnancyCatalog.answer("LA0-0", "fr"))
    }

    @Test
    fun formatLocalisesStatusOutcomeAndEdd() {
        val status = IpsPregnancyObs(id = "pg-1", code = IpsPregnancyCodes.STATUS,
            valueCode = IpsPregnancyCodes.PREGNANT, date = "2026-09-20")
        assertEquals("Statut de grossesse: Enceinte — 2026-09-20", IpsPregnancyCatalog.format(status, "fr"))

        val outcome = IpsPregnancyObs(id = "pg-2", code = "11636-8", count = 2)
        assertEquals("Live births: 2", IpsPregnancyCatalog.format(outcome, "en"))

        val edd = IpsPregnancyObs(id = "pg-3", code = "11778-8", valueDate = "2027-03-14")
        val ja = IpsPregnancyCatalog.format(edd, "ja")
        assertTrue(ja, ja.contains("2027-03-14"))
        assertEquals("分娩予定日: 2027-03-14", ja)
    }

    @Test
    fun formatIgnoresBlankDates() {
        val outcome = IpsPregnancyObs(id = "pg-4", code = "11614-5", count = 1, date = " ")
        assertEquals("Fausses couches: 1", IpsPregnancyCatalog.format(outcome, "fr"))
    }
}
