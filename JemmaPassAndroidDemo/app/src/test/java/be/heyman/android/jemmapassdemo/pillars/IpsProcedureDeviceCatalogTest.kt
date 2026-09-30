/*
 * IpsProcedureDeviceCatalogTest.kt — sprint 2 catalogs + UDI plausibility (JVM).
 *
 * The device form accepts a UDI typed from an implant card ; the check must
 * reject obvious typos without refusing the three issuing-agency formats.
 */
package be.heyman.android.jemmapassdemo.pillars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsProcedureDeviceCatalogTest {

    @Test
    fun procedureCodesAreUniqueSnomedIdentifiersWithThreeLabels() {
        val codes = IpsProcedureCatalog.ALL.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it.matches(Regex("^\\d{6,18}$")) })
        IpsProcedureCatalog.ALL.forEach { p ->
            assertTrue(p.code, p.displayEn.isNotBlank() && p.displayFr.isNotBlank() && p.displayJa.isNotBlank())
        }
        assertEquals("Appendicectomie", IpsProcedureCatalog.getDisplay("80146002", "fr"))
        assertEquals("虫垂切除術", IpsProcedureCatalog.getDisplay("80146002", "ja-JP"))
        assertNull(IpsProcedureCatalog.getDisplay("000000", "en"))
    }

    @Test
    fun deviceCodesAreUniqueSnomedIdentifiersWithThreeLabels() {
        val codes = IpsDeviceCatalog.ALL.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it.matches(Regex("^\\d{6,18}$")) })
        IpsDeviceCatalog.ALL.forEach { d ->
            assertTrue(d.code, d.displayEn.isNotBlank() && d.displayFr.isNotBlank() && d.displayJa.isNotBlank())
        }
        assertEquals("Stimulateur cardiaque (pacemaker)", IpsDeviceCatalog.getDisplay("14106009", "fr"))
    }

    @Test
    fun statusCatalogsCoverEveryDomainStatus() {
        val procedureStatuses = IpsProcedureStatusCatalog.ALL.map { it.code }.toSet()
        assertEquals(be.heyman.android.jemmapassdemo.ips.IpsProcedureStatus.ALL.toSet(), procedureStatuses)
        assertEquals(IpsProcedureStatusCatalog.DEFAULT_CODE, be.heyman.android.jemmapassdemo.ips.IpsProcedureStatus.COMPLETED)

        val deviceStatuses = IpsDeviceStatusCatalog.ALL.map { it.code }.toSet()
        assertEquals(be.heyman.android.jemmapassdemo.ips.IpsDeviceStatus.ALL.toSet(), deviceStatuses)
        assertEquals(IpsDeviceStatusCatalog.DEFAULT_CODE, be.heyman.android.jemmapassdemo.ips.IpsDeviceStatus.ACTIVE)
    }

    @Test
    fun udiPlausibilityAcceptsTheIssuingAgencyFormats() {
        assertTrue(IpsUdi.isPlausible("(01)00643169007222(21)PJN1234567"))
        assertTrue(IpsUdi.isPlausible("(01)00643169007222(17)250101(10)LOT1(21)SN1"))
        assertTrue(IpsUdi.isPlausible("(01) 00643169007222 (21) PJN1234567"))   // spaces from the card
        assertTrue(IpsUdi.isPlausible("00643169007222"))                         // bare GTIN-14
        assertTrue(IpsUdi.isPlausible("0643169007222"))                          // GTIN-13
        assertTrue(IpsUdi.isPlausible("+H123ABC1234/\$\$420020216LOT123/SXYZ456789012345678/16D20130202C"))
        assertTrue(IpsUdi.isPlausible("=/W4146EB0010T0475=,000025=A99971312345600=>014032=}013032&,1000000000000XYZ123"))
        assertEquals("00643169007222", IpsUdi.gs1DeviceIdentifier("(01)00643169007222(21)PJN1234567"))
        assertNull(IpsUdi.gs1DeviceIdentifier("00643169007222"))
    }

    @Test
    fun udiPlausibilityRejectsTypos() {
        assertFalse(IpsUdi.isPlausible("ABC"))
        assertFalse(IpsUdi.isPlausible("(01)0064316900722"))      // 13 digits after (01)
        assertFalse(IpsUdi.isPlausible("1234567"))                 // too short for a GTIN
        assertFalse(IpsUdi.isPlausible("(01)00643169007222 extra"))
        assertFalse(IpsUdi.isPlausible(""))
        assertFalse(IpsUdi.isPlausible(null))
        assertNull(IpsUdi.normalize("   "))
    }
}
