/*
 * IpsResultCatalogTest.kt — sprint 3 embedded LOINC catalog sanity (JVM).
 *
 * The knowledge base has no LOINC table, so this catalog IS the picker : codes
 * must be unique LOINC identifiers, every numeric test needs a UCUM default
 * unit, and the alias search must let a French user type the usual shorthand.
 */
package be.heyman.android.jemmapassdemo.pillars

import be.heyman.android.jemmapassdemo.ips.IpsResultCategory
import be.heyman.android.jemmapassdemo.ips.IpsResultInterpretation
import be.heyman.android.jemmapassdemo.ips.IpsResultStatus
import be.heyman.android.jemmapassdemo.ui.common.normalizeForPickerSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsResultCatalogTest {

    @Test
    fun codesAreUniqueLoincIdentifiersWithThreeLabels() {
        val codes = IpsResultCatalog.ALL.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it.matches(Regex("^\\d{1,5}-\\d$")) })
        IpsResultCatalog.ALL.forEach { e ->
            assertTrue(e.code, e.displayEn.isNotBlank() && e.displayFr.isNotBlank() && e.displayJa.isNotBlank())
        }
    }

    @Test
    fun numericTestsCarryAUcumDefaultUnitAndCodedOnesDoNot() {
        IpsResultCatalog.ALL.forEach { e ->
            when (e.kind) {
                ResultValueKind.NUMERIC -> {
                    assertTrue("${e.code} needs a unit", !e.unit.isNullOrBlank())
                    assertTrue("${e.code} unit '${e.unit}' must be in the unit picker", e.unit in IpsResultCatalog.UNITS)
                }
                else -> assertNull("${e.code} is not numeric", e.unit)
            }
            assertTrue(e.category in IpsResultCategory.ALL)
        }
        assertEquals(ResultValueKind.CODED, IpsResultCatalog.byCode("882-1")!!.kind)
        assertEquals("%", IpsResultCatalog.byCode("4548-4")!!.unit)
        assertEquals("mmol/L", IpsResultCatalog.byCode("2823-3")!!.unit)
        assertEquals("10*3/uL", IpsResultCatalog.byCode("777-3")!!.unit)
    }

    @Test
    fun aliasesLetAFrenchUserTypeTheUsualShorthand() {
        val hba1c = IpsResultCatalog.byCode("4548-4")!!
        val key = normalizeForPickerSearch(hba1c.searchAliases())
        assertTrue(key.contains("hba1c"))
        assertTrue(key.contains("glyquee"))          // « glyquée » folded
        assertTrue(key.contains("4548-4"))
        val potassium = IpsResultCatalog.byCode("2823-3")!!
        assertTrue(normalizeForPickerSearch(potassium.searchAliases()).contains("kaliemie"))
        assertEquals("Hémoglobine glyquée (HbA1c)", IpsResultCatalog.getDisplay("4548-4", "fr-BE"))
        assertEquals("HbA1c（ヘモグロビンA1c）", IpsResultCatalog.getDisplay("4548-4", "ja"))
        assertNull(IpsResultCatalog.getDisplay("0000-0", "en"))
    }

    @Test
    fun statusInterpretationAndCategoryCatalogsCoverTheDomain() {
        assertEquals(IpsResultStatus.ALL.toSet(), IpsResultStatusCatalog.ALL.map { it.code }.toSet())
        assertEquals(IpsResultStatusCatalog.DEFAULT_CODE, IpsResultStatus.FINAL)
        assertEquals(IpsResultInterpretation.ALL.toSet(), IpsResultInterpretationCatalog.ALL.map { it.code }.toSet())
        assertEquals(IpsResultCategory.ALL.toSet(), IpsResultCategoryCatalog.ALL.map { it.code }.toSet())
        assertNotNull(IpsResultInterpretationCatalog.byCode("hh"))
        assertEquals("Élevé", IpsResultInterpretationCatalog.byCode("H")!!.pick("fr"))
    }
}
