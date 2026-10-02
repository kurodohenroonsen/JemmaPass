/*
 * TextQrAllLanguagesTest.kt — the text QR (the channel any phone camera can read) for the
 * three personas in each of the 25 languages of JemmaTranslations.
 *
 * Use cases (qa/usecases) : UC-QRT-001 / 002 / 003 / 004 / 007 / 010 / 011 / 012 / 017 / 021,
 * UC-I18N-004 / 005 / 008, UC-HUM-019 / 023.
 *
 * What is checked for every persona × language :
 *   • one QR frame : at most 1 800 UTF-8 bytes ;
 *   • identity, allergies, medications, conditions and emergency contacts are all printed ;
 *   • every other entry is printed or announced as left out (never dropped in silence) ;
 *   • no "null", no KB placeholder, no empty section title ;
 *   • every label of the language exists (no silent fallback to an empty string).
 */
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.qr.JemmaTextPayloadBuilder.Lang
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import be.heyman.android.jemmapassdemo.testsupport.TextQrProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextQrAllLanguagesTest {

    /** The 23 labels of the clinical summary (JemmaTranslations.CLINICAL_SUMMARIES). */
    private val summaryKeys = listOf(
        "header", "patient_title", "patient_birth", "patient_blood", "patient_lang", "patient_addr", "patient_phone",
        "patient_email", "patient_id", "allergies_title", "medications_title", "conditions_title", "immunizations_title",
        "procedures_title", "devices_title", "results_title", "past_problems_title", "pregnancy_title", "functional_title",
        "contacts_title", "footer", "empty", "truncated",
    )

    /** The 18 labels of the pocket pass (JemmaTranslations.PDF_LABELS) ; the gender ones are printed in the text QR. */
    private val pdfKeys = listOf(
        "sub_title", "fold_instruction", "passport_title", "mesh_tagline", "caption_scan", "caption_pruned", "page_title",
        "card1_title", "card2_title", "page2_footer", "born", "gender", "national_id", "blood_type",
        "gender_m", "gender_f", "gender_o", "gender_u",
    )

    private fun hydrated(sid: String, lang: Lang): HydratedProfile =
        ProfileFixtures.hydrated(ProfileFixtures.persona(sid).j, uiLang = lang.isoCode)

    @Suppress("UNCHECKED_CAST")
    private fun table(field: String): Map<Lang, Map<String, String>> {
        val f = JemmaTranslations::class.java.getDeclaredField(field)
        f.isAccessible = true
        return f.get(JemmaTranslations) as Map<Lang, Map<String, String>>
    }

    // ── translations ────────────────────────────────────────────────────────

    @Test
    fun `UC-I18N-004 there are 25 languages and each has its own header`() {
        assertEquals(25, Lang.values().size)
        assertEquals("iso codes are unique", 25, Lang.values().map { it.isoCode }.toSet().size)
        for (lang in Lang.values()) {
            val header = JemmaTranslations.getLabel(lang, "header")
            assertTrue("$lang header names its language : $header", header.contains("(${lang.name})"))
            assertEquals("$lang iso code is the lower-case name", lang.name.lowercase(), lang.isoCode)
        }
    }

    @Test
    fun `UC-I18N-005 every label exists and is not blank in each of the 25 languages`() {
        for (lang in Lang.values()) {
            for (key in summaryKeys) {
                assertTrue("$lang · $key is blank", JemmaTranslations.getLabel(lang, key).isNotBlank())
            }
            for (key in pdfKeys) {
                assertTrue("$lang · pdf $key is blank", JemmaTranslations.getPdfLabel(lang, key).isNotBlank())
            }
        }
        // an unknown key is the only thing that gives an empty label
        assertEquals("", JemmaTranslations.getLabel(Lang.EN, "no_such_key"))
    }

    @Test
    fun `UC-QRT-004 no language is missing a key or carries an extra one`() {
        // Read the private tables : getLabel falls back to English, which would hide a missing key.
        val summaries = table("CLINICAL_SUMMARIES")
        val pdf = table("PDF_LABELS")
        assertEquals(Lang.values().toSet(), summaries.keys)
        assertEquals(Lang.values().toSet(), pdf.keys)
        assertEquals(summaryKeys.toSet(), summaries.getValue(Lang.EN).keys)
        assertEquals(pdfKeys.toSet(), pdf.getValue(Lang.EN).keys)
        for (lang in Lang.values()) {
            assertEquals("$lang summary keys", summaryKeys.toSet(), summaries.getValue(lang).keys)
            assertEquals("$lang pdf keys", pdfKeys.toSet(), pdf.getValue(lang).keys)
            for ((key, value) in summaries.getValue(lang)) {
                assertTrue("$lang · $key", value.isNotBlank())
                assertFalse("$lang · $key holds a line break", value.contains("\n") || value.contains("\r"))
                assertFalse("$lang · $key holds a null literal", value.contains("null"))
            }
            // the "incomplete" label must be in the language : it is the only warning of a cut
            if (lang != Lang.EN) {
                assertFalse("$lang truncated label is the English one",
                    summaries.getValue(lang).getValue("truncated") == summaries.getValue(Lang.EN).getValue("truncated"))
            }
        }
    }

    // ── persona × language ──────────────────────────────────────────────────

    @Test
    fun `UC-QRT-010 every persona fits one QR frame in each of the 25 languages`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) for (lang in Lang.values()) {
            val text = JemmaTextPayloadBuilder.build(hydrated(sid, lang), lang)
            val bytes = ProfileFixtures.utf8(text)
            assertTrue("$sid $lang : $bytes bytes", bytes <= JemmaTextPayloadBuilder.MAX_BYTES)
            assertTrue("$sid $lang : $bytes bytes", bytes <= 1800)
            assertEquals("$sid $lang : one frame", listOf(text), JemmaQrFrameSplitter.split(text))
            // UC-QRT-021 — the payload is valid UTF-8 text : it survives an encode / decode
            assertEquals("$sid $lang", text, String(text.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        }
    }

    @Test
    fun `UC-HUM-023 identity, allergies, medications, conditions and contacts are always printed`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) for (lang in Lang.values()) {
            val h = hydrated(sid, lang)
            val p = h.raw.p!!
            val text = JemmaTextPayloadBuilder.build(h, lang)
            val ctx = "$sid $lang"

            // frame : header first, footer last
            assertTrue(ctx, text.startsWith(JemmaTranslations.getLabel(lang, "header") + TextQrProbe.EOL))
            assertTrue(ctx, text.endsWith(JemmaTranslations.getLabel(lang, "footer") + TextQrProbe.EOL))

            // identity : name, birth date, blood group, spoken language
            assertTrue("$ctx patient title", text.contains(TextQrProbe.PATIENT + " [ " + JemmaTranslations.getLabel(lang, "patient_title") + " ]"))
            assertTrue("$ctx given name", text.contains(p.gn!!))
            if (!p.fn.isNullOrBlank()) assertTrue("$ctx family name", text.contains(p.gn + " " + p.fn))
            assertTrue("$ctx birth", text.contains(JemmaTranslations.getLabel(lang, "patient_birth") + ": " + p.bd + TextQrProbe.EOL))
            assertTrue("$ctx blood group", text.contains(JemmaTranslations.getLabel(lang, "patient_blood") + ": " + p.bt + TextQrProbe.EOL))
            assertTrue("$ctx language", text.contains(JemmaTranslations.getLabel(lang, "patient_lang") + ": " + p.lang + TextQrProbe.EOL))
            assertTrue("$ctx gender", text.contains("(" + JemmaTranslations.getPdfLabel(lang, if (p.gs == "F") "gender_f" else "gender_m") + ")"))

            // life-critical sections are whole, with their localised title
            for (icon in TextQrProbe.LIFE_CRITICAL) assertTrue("$ctx $icon is not whole", TextQrProbe.isWhole(text, h, icon))
            val sections = TextQrProbe.sections(text)
            assertEquals("$ctx allergies title", JemmaTranslations.getLabel(lang, "allergies_title"), sections.getValue(TextQrProbe.ALLERGIES).title)
            // one line per allergy, in order, starting with the allergen (the criticality wording is free to change)
            val allergyLines = sections.getValue(TextQrProbe.ALLERGIES).lines
            assertEquals("$ctx allergies", h.allergies.size, allergyLines.size)
            h.allergies.forEachIndexed { i, a ->
                assertTrue("$ctx allergy $i : ${allergyLines[i]}", allergyLines[i].startsWith(a.displayLocalized))
                assertTrue("$ctx allergy $i says how critical it is", allergyLines[i].length > a.displayLocalized.length)
            }
            if (h.medications.isNotEmpty()) {
                val meds = sections.getValue(TextQrProbe.MEDICATIONS)
                assertEquals("$ctx medications title", JemmaTranslations.getLabel(lang, "medications_title"), meds.title)
                h.medications.forEachIndexed { i, m ->
                    assertTrue("$ctx medication $i : ${meds.lines[i]}", meds.lines[i].startsWith(m.displayLocalized))
                    assertTrue("$ctx medication $i dose", meds.lines[i].contains(m.doseValue + m.doseUnit))
                    assertTrue("$ctx medication $i posology", meds.lines[i].endsWith(m.timing!!))
                }
            }
            assertEquals("$ctx conditions", h.conditions.map { it.displayLocalized }, sections.getValue(TextQrProbe.CONDITIONS).lines)
            assertEquals("$ctx conditions title", JemmaTranslations.getLabel(lang, "conditions_title"), sections.getValue(TextQrProbe.CONDITIONS).title)
            for (c in p.ct) {
                val contacts = sections.getValue(TextQrProbe.CONTACTS)
                assertEquals("$ctx contacts title", JemmaTranslations.getLabel(lang, "contacts_title"), contacts.title)
                assertTrue("$ctx contact ${c.n}", contacts.lines.any { it.startsWith(c.n!!) })
            }
        }
    }

    @Test
    fun `UC-QRT-008 every entry of every pillar is printed or announced as left out`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) for (lang in Lang.values()) {
            val h = hydrated(sid, lang)
            TextQrProbe.assertAccounted("$sid $lang", JemmaTextPayloadBuilder.build(h, lang), h)
        }
    }

    @Test
    fun `UC-QRT-003 no null literal, no KB placeholder and no empty title in any language`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) for (lang in Lang.values()) {
            val text = JemmaTextPayloadBuilder.build(hydrated(sid, lang), lang)
            val ctx = "$sid $lang"
            assertFalse("$ctx null literal", text.contains("null"))
            assertFalse("$ctx KB placeholder", text.contains("Not Translated"))
            assertFalse("$ctx empty title", text.contains("[  ]"))
            assertFalse("$ctx empty bullet", text.contains(TextQrProbe.BULLET + TextQrProbe.EOL))
            for ((icon, section) in TextQrProbe.sections(text)) {
                assertTrue("$ctx $icon title", section.title.isNotBlank())
            }
            // every line ends with CR LF : no stray line feed a scanner would render differently
            assertFalse("$ctx bare LF", text.replace(TextQrProbe.EOL, "").contains("\n"))
            assertFalse("$ctx bare CR", text.replace(TextQrProbe.EOL, "").contains("\r"))
        }
    }

    @Test
    fun `UC-QRT-002 the native pillar sections carry the localised title of the language`() {
        val titles = linkedMapOf(
            TextQrProbe.IMMUNIZATIONS to "immunizations_title", TextQrProbe.PROCEDURES to "procedures_title",
            TextQrProbe.DEVICES to "devices_title", TextQrProbe.RESULTS to "results_title",
            TextQrProbe.PAST_PROBLEMS to "past_problems_title", TextQrProbe.PREGNANCY to "pregnancy_title",
            TextQrProbe.FUNCTIONAL to "functional_title",
        )
        for (sid in ProfileFixtures.PERSONA_SIDS) for (lang in Lang.values()) {
            val sections = TextQrProbe.sections(JemmaTextPayloadBuilder.build(hydrated(sid, lang), lang))
            for ((icon, key) in titles) {
                val s = sections[icon] ?: continue
                assertEquals("$sid $lang $icon", JemmaTranslations.getLabel(lang, key), s.title)
            }
        }
    }

    @Test
    fun `UC-QRT-017 dates are printed as stored and the most recent entry comes first`() {
        // Kurodo in English fits whole : the four vaccines are there, newest first.
        val h = hydrated(JemmaPersonasSeeder.SID_KURODO, Lang.EN)
        val text = JemmaTextPayloadBuilder.build(h, Lang.EN)
        val vaccines = TextQrProbe.sections(text)[TextQrProbe.IMMUNIZATIONS]
        assertNotNull("vaccines printed", vaccines)
        val dates = vaccines!!.lines.map { it.substringAfter(" — ").take(10) }
        assertEquals(dates.sortedDescending(), dates)
        assertEquals("2023-01-20", dates.first())
        // partial dates of Haru are never completed with an invented month or day
        val haru = JemmaTextPayloadBuilder.build(hydrated(JemmaPersonasSeeder.SID_HARU, Lang.EN), Lang.EN)
        assertFalse(haru.contains("1975-01"))
        assertFalse(haru.contains("2019-06-01"))
    }

    @Test
    fun `UC-QRT-007 a profile with a name only still gives a valid payload in every language`() {
        val bare = JemmaProfileJ(j = "1.2", sid = "qa_bare", p = JPatient(gn = "Jean"))
        for (lang in Lang.values()) {
            val h = ProfileFixtures.hydrated(bare, uiLang = lang.isoCode)
            val text = JemmaTextPayloadBuilder.build(h, lang)
            val lines = text.split(TextQrProbe.EOL)
            assertEquals("$lang header", JemmaTranslations.getLabel(lang, "header"), lines.first())
            assertTrue("$lang patient title", lines.contains(TextQrProbe.PATIENT + " [ " + JemmaTranslations.getLabel(lang, "patient_title") + " ]"))
            assertTrue("$lang name", lines.any { it.trim().endsWith("Jean") })
            assertTrue("$lang footer", text.endsWith(JemmaTranslations.getLabel(lang, "footer") + TextQrProbe.EOL))
            assertFalse("$lang nothing was cut", text.contains(TextQrProbe.MARK))
            assertFalse("$lang null literal", text.contains("null"))
            assertTrue("$lang small payload : ${ProfileFixtures.utf8(text)} bytes", ProfileFixtures.utf8(text) < 400)
            TextQrProbe.assertAccounted("$lang bare", text, h)
        }
        // no patient block at all : still a payload, no crash
        val none = JemmaTextPayloadBuilder.build(ProfileFixtures.hydrated(JemmaProfileJ(j = "1.2", sid = "qa_none")), Lang.EN)
        assertTrue(none.startsWith(JemmaTranslations.getLabel(Lang.EN, "header") + TextQrProbe.EOL))
        assertTrue(none.endsWith(JemmaTranslations.getLabel(Lang.EN, "footer") + TextQrProbe.EOL))
        assertFalse(none.contains("null"))
    }
}
