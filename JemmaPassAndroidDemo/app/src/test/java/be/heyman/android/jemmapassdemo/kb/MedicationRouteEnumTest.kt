/*
 * MedicationRouteEnumTest.kt — UC-MED-ROUTE-20..22 : the hydrated route of `md[].r` knows the
 * inhaled value "H" (it used to fall into UNKNOWN) and "I" keeps meaning injection.
 */
package be.heyman.android.jemmapassdemo.kb

import be.heyman.android.jemmapassdemo.pillars.IpsRouteCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MedicationRouteEnumTest {

    @Test
    fun `UC-MED-ROUTE-20 H is the inhaled route, as stored or as typed`() {
        for (raw in listOf("H", "h", " H ", IpsRouteCatalog.INHALED, mapKbRouteToShortCode("inhal.aerosol"))) {
            assertEquals("r='$raw'", MedicationRoute.INHALED, MedicationRoute.fromShortCode(raw))
        }
        assertNotEquals(MedicationRoute.INJECTION, MedicationRoute.fromShortCode("H"))
    }

    @Test
    fun `UC-MED-ROUTE-21 the historical codes are unchanged and I is still injection`() {
        assertEquals(MedicationRoute.ORAL, MedicationRoute.fromShortCode("O"))
        assertEquals(MedicationRoute.INJECTION, MedicationRoute.fromShortCode("I"))
        assertEquals(MedicationRoute.INJECTION, MedicationRoute.fromShortCode("i"))
        assertEquals(MedicationRoute.TOPICAL, MedicationRoute.fromShortCode("T"))
        assertEquals(MedicationRoute.SUBCUTANEOUS, MedicationRoute.fromShortCode("S"))
        for (raw in listOf(null, "", " ", "X", "INH", "inhaled", "HH")) {
            assertEquals("r='$raw'", MedicationRoute.UNKNOWN, MedicationRoute.fromShortCode(raw))
        }
    }

    @Test
    fun `UC-MED-ROUTE-22 every route of the catalog has its own hydrated value`() {
        val routes = IpsRouteCatalog.ALL.map { MedicationRoute.fromShortCode(it.shortCode) }
        assertEquals("no catalog route is UNKNOWN", emptyList<String>(),
            IpsRouteCatalog.ALL.map { it.shortCode }.filter { MedicationRoute.fromShortCode(it) == MedicationRoute.UNKNOWN })
        assertEquals("one value per catalog route", routes.size, routes.toSet().size)
    }
}
