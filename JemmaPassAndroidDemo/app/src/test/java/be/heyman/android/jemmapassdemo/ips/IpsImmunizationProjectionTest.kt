/*
 * IpsImmunizationProjectionTest.kt
 *
 * The `_j 1.2` payload (QR / mesh) only carries a pruned PROJECTION of the
 * FHIR-native immunizations. These tests pin the projection contract :
 * defaults are omitted, nothing the QR can carry is lost, legacy payloads
 * without the new keys still parse, and rebuilding from a projection is
 * deterministic.
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JEntryGeneric
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsImmunizationProjectionTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(JemmaProfileJ::class.java)

    private val tdap = IpsImmunization(
        id = "im-1", code = "871876003", display = "Tdap", date = "2022-05-17",
        doseNumber = 2, seriesDoses = 3, lotNumber = "LOT-1", manufacturer = "Sanofi", note = "booster",
    )

    @Test
    fun projectionOmitsDefaultsAndKeepsTheEssentials() {
        val e = tdap.toJEntry()
        assertEquals("871876003", e.c)
        assertEquals("Tdap", e.displayLabel)
        assertEquals("2022-05-17", e.date)
        assertEquals(2, e.doseNumber)
        assertEquals("booster", e.d)
        assertNull("SNOMED is the default system → omitted", e.codeSystem)
        assertNull("completed is the default status → omitted", e.status)
    }

    @Test
    fun projectionKeepsNonDefaultSystemAndStatus() {
        val e = tdap.copy(system = IpsCodeSystems.CVX, code = "115", status = IpsImmunizationStatus.NOT_DONE).toJEntry()
        assertEquals(IpsCodeSystems.CVX, e.codeSystem)
        assertEquals(IpsImmunizationStatus.NOT_DONE, e.status)
    }

    @Test
    fun rebuildFromProjectionPreservesWhatTheQrCarries() {
        val back = IpsImmunization.fromJEntry(tdap.toJEntry(), index = 0)
        assertEquals(tdap.code, back.code)
        assertEquals(IpsCodeSystems.SNOMED, back.system)
        assertEquals(tdap.display, back.display)
        assertEquals(tdap.date, back.date)
        assertEquals(tdap.status, back.status)
        assertEquals(tdap.doseNumber, back.doseNumber)
        assertEquals(tdap.note, back.note)
        // Lost on purpose (not carried by the QR) :
        assertNull(back.lotNumber)
        assertNull(back.manufacturer)
        assertNull(back.seriesDoses)
    }

    @Test
    fun rebuildIsDeterministic() {
        val a = IpsNativePillars.fromJEntries(listOf(tdap.toJEntry(), tdap.copy(id = "x", code = "836374004", display = "HepB").toJEntry()))
        val b = IpsNativePillars.fromJEntries(listOf(tdap.toJEntry(), tdap.copy(id = "x", code = "836374004", display = "HepB").toJEntry()))
        assertEquals(a, b)
        assertEquals(2, a.immunizations.map { it.id }.toSet().size)
    }

    @Test
    fun freeTextEntryProjectsIntoDisplayAndComesBackAsText() {
        val im = IpsImmunization(id = "im-t", code = null, system = null, text = "Vaccin du village")
        val e = im.toJEntry()
        assertNull(e.c)
        assertEquals("Vaccin du village", e.displayLabel)
        val back = IpsImmunization.fromJEntry(e)
        assertNull(back.code)
        assertNull(back.display)
        assertEquals("Vaccin du village", back.text)
    }

    @Test
    fun moshiRoundTripKeepsTheCompactKeys() {
        val profile = JemmaProfileJ(j = "1.2", sid = "t", im = listOf(tdap.toJEntry()))
        val json = adapter.toJson(profile)
        // Inspect the `im[0]` object itself (the profile also has a top-level `cs` array).
        val entry = org.json.JSONObject(json).getJSONArray("im").getJSONObject(0)
        assertEquals("2022-05-17", entry.getString("dt"))
        assertEquals(2, entry.getInt("dn"))
        assertEquals("871876003", entry.getString("c"))
        assertTrue("null defaults must not be serialised", !entry.has("cs"))
        assertTrue(!entry.has("st"))
        val back = adapter.fromJson(json)
        assertNotNull(back)
        assertEquals(profile.im, back!!.im)
    }

    @Test
    fun legacyPayloadWithoutTheNewKeysStillParses() {
        val legacy = """{"_j":"1.2","sid":"old","im":[{"c":"836374004","d_display":"Hepatitis B vaccine"}]}"""
        val back = adapter.fromJson(legacy)!!
        assertEquals(1, back.im.size)
        assertEquals(JEntryGeneric(c = "836374004", displayLabel = "Hepatitis B vaccine"), back.im.first())
        val native = IpsNativePillars.fromJEntries(back.im)
        assertEquals("836374004", native.immunizations.single().code)
        assertNull(native.immunizations.single().date)
        assertEquals(IpsImmunizationStatus.COMPLETED, native.immunizations.single().status)
    }
}
