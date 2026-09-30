/*
 * IpsVaccineCatalogTest.kt — picker aliases + catalog sanity (JVM).
 *
 * Device QA cycle 2 (Pixel 9, fr-BE) showed that typing the international
 * name "influenza" hid « Vaccin grippe saisonnière » because the picker only
 * searched the displayed label. Search keys now carry every language.
 */
package be.heyman.android.jemmapassdemo.pillars

import be.heyman.android.jemmapassdemo.ui.common.normalizeForPickerSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsVaccineCatalogTest {

    @Test
    fun codesAreUniqueNumericSnomedIdentifiers() {
        val codes = IpsVaccineCatalog.ALL.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it.matches(Regex("^\\d{6,18}$")) })
    }

    @Test
    fun everyEntryHasThreeLabels() {
        IpsVaccineCatalog.ALL.forEach { v ->
            assertTrue(v.code, v.displayEn.isNotBlank() && v.displayFr.isNotBlank() && v.displayJa.isNotBlank())
        }
    }

    @Test
    fun searchAliasesLetAFrenchUserTypeTheInternationalName() {
        val flu = IpsVaccineCatalog.byCode("1181000221105")!!
        val key = normalizeForPickerSearch(flu.searchAliases())
        assertTrue(key.contains("influenza"))
        assertTrue(key.contains("grippe"))
        // The dialog normalises the needle the same way (NFD), so compare like for like.
        assertTrue(key.contains(normalizeForPickerSearch("インフルエンザ")))
        assertTrue(key.contains("1181000221105"))
        // Diacritics are folded: "saisonnière" is found by "saisonniere".
        assertTrue(key.contains("saisonniere"))
    }

    @Test
    fun displayFallsBackToNullOutsideTheCatalog() {
        assertEquals(null, IpsVaccineCatalog.getDisplay("000000", "fr"))
        assertEquals("Vaccin COVID-19 (ARNm)", IpsVaccineCatalog.getDisplay("1119349007", "fr"))
        assertEquals("COVID-19 mRNA vaccine", IpsVaccineCatalog.getDisplay("1119349007", "xx"))
    }

    @Test
    fun statusCatalogCoversTheThreeFhirEventStatuses() {
        assertEquals(listOf("completed", "not-done", "entered-in-error"), IpsImmunizationStatusCatalog.ALL.map { it.code })
        assertEquals("Non administré", IpsImmunizationStatusCatalog.getDisplay("not-done", "fr"))
    }
}
