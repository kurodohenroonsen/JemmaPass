/*
 * RED TEST (SD-27, contacts form) — not to be edited by the implementer
 *
 * Device cycle 27 : typing "Dr Smith" made the form pick the relationship "MEDPROVR", a code that
 * exists neither in the relationship catalogue nor in HL7 v3-RoleCode. The form and the contact list
 * then showed "MEDPROVR" to the user. Kudoro's rule : an unknown relationship code is never shown raw,
 * while free text ("ami", "友人") stays word for word.
 *
 * Expected red until ContactFormLogic is implemented and the two screens use it :
 *   UC-CT-030..036, UC-CT-038, UC-CT-039.   Lock : UC-CT-037.
 *
 * All names are invented demo data.
 */
package be.heyman.android.jemmapassdemo.pillar8

import be.heyman.android.jemmapassdemo.pillars.ContactFormLogic
import be.heyman.android.jemmapassdemo.pillars.IpsRelationshipCatalog
import be.heyman.android.jemmapassdemo.qr.CodeLabelResolver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactFormLogicTest {

    private val langs = listOf("en", "fr", "ja")

    private val names = listOf(
        "Dr Smith", "dr. Kamekichi", "DR TANAKA", "Prof Tanaka", "Pr. Henro", "Médecin Dupont", "medecin Dupont",
        "Docteur Haru", "Kamekichi", "Sakura Tanaka", "田中 さくら", "Maman", "", "   ",
    )

    /** A resolver that knows one label per language for DAUC, and nothing else. */
    private val fakeLabels = CodeLabelResolver { _, code, lang ->
        if (code == "DAUC") mapOf("en" to "Daughter*", "fr" to "Fille*", "ja" to "娘*")[lang] else null
    }

    private val uiDir: File? = listOf(
        "src/main/java/be/heyman/android/jemmapassdemo/ui/profile/contacts",
        "app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/contacts",
        "JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/contacts",
    ).map { File(it) }.firstOrNull { it.isDirectory }

    // ─── suggestedRelation ────────────────────────────────────────────────

    @Test
    fun `UC-CT-030 a suggested relationship is always a code of the catalogue, or nothing`() {
        for (n in names + listOf<String?>(null)) {
            val s = ContactFormLogic.suggestedRelation(n)
            assertTrue(
                "For the name '$n' the form suggests '$s', which is not in the relationship catalogue. " +
                    "The form must never make up a code : suggest a catalogue code or nothing.",
                s == null || IpsRelationshipCatalog.isValidCode(s),
            )
        }
    }

    @Test
    fun `UC-CT-031 a doctor title in the name does not produce the invented code MEDPROVR`() {
        for (n in listOf("Dr Smith", "dr. Kamekichi", "Prof Tanaka", "Médecin Dupont")) {
            assertFalse(
                "For '$n' the form suggests MEDPROVR. This code does not exist in HL7 v3-RoleCode.",
                ContactFormLogic.suggestedRelation(n) == "MEDPROVR",
            )
        }
    }

    // ─── relationDisplay ──────────────────────────────────────────────────

    @Test
    fun `UC-CT-032 nothing is shown for an empty relationship`() {
        for (raw in listOf(null, "", "   ")) for (lang in langs) {
            assertNull("relationship '$raw' in $lang", ContactFormLogic.relationDisplay(raw, CodeLabelResolver.NONE, lang))
        }
    }

    @Test
    fun `UC-CT-033 a code without any label is never shown raw`() {
        for (raw in listOf("MEDPROVR", "medprovr", " MEDPROVR ", "XYZ_99", "ECON")) for (lang in langs) {
            for (labels in listOf(CodeLabelResolver.NONE, fakeLabels)) {
                val shown = ContactFormLogic.relationDisplay(raw, labels, lang)
                assertFalse(
                    "The screen would show '$shown' for the relationship '$raw' in $lang. A code the app has no " +
                        "word for must give null, so the screen shows nothing rather than the code.",
                    shown != null && shown.trim().equals(raw.trim(), ignoreCase = true),
                )
                if (IpsRelationshipCatalog.isRoleCode(raw.trim())) assertNull("'$raw' in $lang", shown)
            }
        }
    }

    @Test
    fun `UC-CT-034 a catalogue code is shown as a word, never as the code`() {
        for (entry in IpsRelationshipCatalog.ALL) for (lang in langs) {
            val shown = ContactFormLogic.relationDisplay(entry.code, CodeLabelResolver.NONE, lang)
            assertNotNull("${entry.code} in $lang : a catalogue code must have a word on screen", shown)
            assertTrue("${entry.code} in $lang : the word is blank", shown!!.isNotBlank())
            assertFalse("${entry.code} in $lang : the screen shows the code itself", shown == entry.code)
        }
    }

    @Test
    fun `UC-CT-035 the label of the resolver wins over the built-in one`() {
        assertEquals("Fille*", ContactFormLogic.relationDisplay("DAUC", fakeLabels, "fr"))
        assertEquals("娘*", ContactFormLogic.relationDisplay("DAUC", fakeLabels, "ja"))
        assertEquals("Daughter*", ContactFormLogic.relationDisplay("dauc", fakeLabels, "en"))
    }

    @Test
    fun `UC-CT-036 free text stays word for word in every language`() {
        for (raw in listOf("ami", "voisine du 3e étage", "友人", "幼なじみ", "Best friend", "Maman")) for (lang in langs) {
            assertEquals(
                "free text must be shown exactly as typed ($lang)",
                raw, ContactFormLogic.relationDisplay(raw, CodeLabelResolver.NONE, lang),
            )
        }
    }

    // ─── the two screens ──────────────────────────────────────────────────

    @Test
    fun `UC-CT-037 the contacts screens are found by the test - lock`() {
        assertNotNull("ui/profile/contacts not found from ${File(".").absolutePath}", uiDir)
        assertTrue(File(uiDir, "ContactFormBottomSheet.kt").isFile)
        assertTrue(File(uiDir, "ContactsAdapter.kt").isFile)
    }

    @Test
    fun `UC-CT-038 no contacts screen carries the invented code MEDPROVR`() {
        val hits = uiDir!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l -> if (l.contains("MEDPROVR")) "${f.name}:${i + 1}" else null }
        }.toList()
        assertTrue("MEDPROVR is still written in the contacts screens : $hits", hits.isEmpty())
    }

    @Test
    fun `UC-CT-039 the form and the list take the relationship text from ContactFormLogic`() {
        for (name in listOf("ContactFormBottomSheet.kt", "ContactsAdapter.kt")) {
            val src = File(uiDir!!, name).readText()
            assertTrue("$name does not call ContactFormLogic.relationDisplay", src.contains("ContactFormLogic.relationDisplay"))
            assertFalse(
                "$name still calls IpsRelationshipCatalog.getDisplay, which falls back to the raw code",
                src.contains("IpsRelationshipCatalog.getDisplay"),
            )
        }
        assertTrue(
            "ContactFormBottomSheet.kt does not call ContactFormLogic.suggestedRelation",
            File(uiDir!!, "ContactFormBottomSheet.kt").readText().contains("ContactFormLogic.suggestedRelation"),
        )
    }
}
