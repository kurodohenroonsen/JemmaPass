/*
 * QrTextBudgetTest.kt — byte budget of the text QR (UC-QR-TEXT, QA cases
 * UC-QRT-005 / 008 / 009 / 010 / 011).
 *
 * The text channel must always fit ONE QR frame (JemmaQrFrameSplitter.QR_MAX_SINGLE,
 * counted in UTF-8 bytes), must never be cut silently, and must keep the
 * life-critical sections (identity, allergies, medications, conditions, emergency
 * contacts) when something has to go.
 */
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.kb.AllergyCriticality
import be.heyman.android.jemmapassdemo.kb.ClinicalStatus
import be.heyman.android.jemmapassdemo.kb.HydratedAllergy
import be.heyman.android.jemmapassdemo.kb.HydratedGenericEntry
import be.heyman.android.jemmapassdemo.kb.HydratedMedication
import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.kb.MedicationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrTextBudgetTest {

    private fun bytes(s: String): Int = s.toByteArray(Charsets.UTF_8).size

    private fun allergy(name: String) = HydratedAllergy(
        raw = JAllergy(c = "al-$name"), resolvedConcept = null, displayLocalized = name,
        criticality = AllergyCriticality.HIGH, clinicalStatus = ClinicalStatus.ACTIVE,
    )

    private fun medication(name: String) = HydratedMedication(
        raw = JMedication(c = "md-$name"), resolvedConcept = null, displayLocalized = name,
        timing = "1x/day", doseValue = "500", doseUnit = "mg", route = MedicationRoute.ORAL,
        atcCode = null, allAtcCodes = emptyList(), rxnormCui = null,
    )

    private fun condition(name: String) = HydratedGenericEntry(
        raw = JEntryGeneric(c = "cn-$name"), resolvedConcept = null, displayLocalized = name,
    )

    private val contacts = listOf(
        JContact(n = "Misako Kudoro", r = "spouse", p = "+32 478 45 45 45"),
        JContact(n = "Dr Tanaka", r = "doctor", e = "tanaka@example.org"),
    )

    private fun hydrated(
        allergies: List<HydratedAllergy> = emptyList(),
        medications: List<HydratedMedication> = emptyList(),
        conditions: List<HydratedGenericEntry> = emptyList(),
        vaccines: Int = 0,
        procedures: Int = 0,
        patient: JPatient = JPatient(gn = "Haru", fn = "Tanaka", gs = "F", bd = "1956-02-05", bt = "A+", ct = contacts),
    ): HydratedProfile = HydratedProfile(
        raw = JemmaProfileJ(
            j = "1.2", sid = "x", p = patient,
            im = (1..vaccines).map { JEntryGeneric(displayLabel = "Vaccine number $it of the series", date = "20%02d-01-01".format(it % 100)) },
            pr = (1..procedures).map { JEntryGeneric(displayLabel = "Procedure number $it", date = "19%02d-06-15".format(it % 100)) },
        ),
        uiLang = "en", allergies = allergies, medications = medications, conditions = conditions,
        ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
    )

    /** UC-QR-TEXT-01 (UC-QRT-010) — the cap IS the single-frame capacity, in bytes. */
    @Test
    fun ucQrText01_capEqualsSingleFrameCapacity() {
        assertEquals(JemmaQrFrameSplitter.QR_MAX_SINGLE, JemmaTextPayloadBuilder.MAX_BYTES)
    }

    /** UC-QR-TEXT-02 — a profile that fits is untouched: no marker, every section there. */
    @Test
    fun ucQrText02_smallProfileHasNoMarker() {
        val text = JemmaTextPayloadBuilder.build(
            hydrated(listOf(allergy("Penicillin")), listOf(medication("Warfarin")), listOf(condition("Atrial fibrillation")), vaccines = 2),
            JemmaTextPayloadBuilder.Lang.EN,
        )
        assertFalse(text.contains(JemmaTextPayloadBuilder.TRUNCATION_MARK))
        assertTrue(text.contains("  ▪️ Penicillin (HIGH)\r\n"))
        assertTrue(text.contains("  ▪️ Warfarin 500mg 1x/day\r\n"))
        assertTrue(text.contains("💉 [ IMMUNIZATIONS ]"))
        assertTrue(text.endsWith(JemmaTranslations.getLabel(JemmaTextPayloadBuilder.Lang.EN, "footer") + "\r\n"))
    }

    /** UC-QR-TEXT-03 (UC-QRT-005) — emergency contacts are printed, phone as typed, in 3 languages. */
    @Test
    fun ucQrText03_emergencyContactsArePrinted() {
        for (lang in listOf(JemmaTextPayloadBuilder.Lang.EN, JemmaTextPayloadBuilder.Lang.FR, JemmaTextPayloadBuilder.Lang.JA)) {
            val text = JemmaTextPayloadBuilder.build(hydrated(), lang)
            assertTrue("$lang title", text.contains("☎️ [ " + JemmaTranslations.getLabel(lang, "contacts_title") + " ]"))
            assertTrue("$lang name", text.contains("Misako Kudoro (spouse) +32 478 45 45 45"))
            assertTrue("$lang e-mail fallback", text.contains("Dr Tanaka (doctor) tanaka@example.org"))
        }
        // no contact → no section
        val none = JemmaTextPayloadBuilder.build(hydrated(patient = JPatient(gn = "Haru")), JemmaTextPayloadBuilder.Lang.EN)
        assertFalse(none.contains("☎️"))
    }

    /**
     * UC-QR-TEXT-04 (UC-QRT-008 / 011) — 50 medications: payload ≤ cap in UTF-8 bytes
     * (marker included), marker present, allergies whole, first medications kept,
     * no line cut in the middle.
     */
    @Test
    fun ucQrText04_oversizedProfileIsCappedAndMarked() {
        val allergies = listOf(allergy("Penicillin"), allergy("Latex"), allergy("Peanut"))
        val meds = (1..50).map { medication("Medication number %02d with a long name".format(it)) }
        val text = JemmaTextPayloadBuilder.build(
            hydrated(allergies, meds, listOf(condition("Heart failure")), vaccines = 20, procedures = 10),
            JemmaTextPayloadBuilder.Lang.EN,
        )

        assertTrue("size ${bytes(text)}", bytes(text) <= JemmaTextPayloadBuilder.MAX_BYTES)
        assertEquals(1, JemmaQrFrameSplitter.split(text, JemmaQrFrameSplitter.QR_MAX_SINGLE, JemmaQrFrameSplitter.QR_FRAME_CHUNK).size)

        // explicit marker, localised label, footer still last
        assertTrue(text.contains(JemmaTextPayloadBuilder.TRUNCATION_MARK + " [ INCOMPLETE RECORD ]"))
        assertTrue(text.endsWith(JemmaTranslations.getLabel(JemmaTextPayloadBuilder.Lang.EN, "footer") + "\r\n"))

        // identity + allergies intact, medications present from the first one
        assertTrue(text.contains("Haru Tanaka"))
        for (a in allergies) assertTrue(text.contains("  ▪️ ${a.displayLocalized} (HIGH)\r\n"))
        assertTrue(text.contains("💊 [ MEDICATIONS ]"))
        assertTrue(text.contains("  ▪️ Medication number 01 with a long name 500mg 1x/day\r\n"))
        assertFalse(text.contains("Medication number 50"))

        // the partially kept section says how many lines are missing
        val keptMeds = text.split("\r\n").count { it.startsWith("  ▪️ Medication number") }
        assertTrue(keptMeds in 1..49)
        assertTrue(text.contains("  " + JemmaTextPayloadBuilder.TRUNCATION_MARK + " +${50 - keptMeds}\r\n"))

        // no line cut mid-way: every medication line is a complete original line
        for (line in text.split("\r\n").filter { it.startsWith("  ▪️ Medication number") }) {
            assertTrue(line, line.endsWith(" with a long name 500mg 1x/day"))
        }
    }

    /**
     * UC-QR-TEXT-05 (UC-QRT-009) — drop order: pillars go before anything
     * life-critical. Vaccines are trimmed while allergies, medications, conditions
     * and emergency contacts are complete; patient address goes after the pillars.
     */
    @Test
    fun ucQrText05_lowPrioritySectionsGoFirst() {
        val meds = (1..8).map { medication("Medication %02d".format(it)) }
        val patient = JPatient(
            gn = "Haru", fn = "Tanaka", gs = "F", bd = "1956-02-05", bt = "A+",
            adr = "Rue de la Paix 12 / 5660 Couvin / Belgique", ct = contacts,
        )
        val text = JemmaTextPayloadBuilder.build(
            hydrated(listOf(allergy("Penicillin")), meds, listOf(condition("Heart failure")), vaccines = 60, patient = patient),
            JemmaTextPayloadBuilder.Lang.FR,
        )
        assertTrue(bytes(text) <= JemmaTextPayloadBuilder.MAX_BYTES)
        assertTrue(text.contains(JemmaTextPayloadBuilder.TRUNCATION_MARK + " [ FICHE INCOMPLÈTE ] 💉"))
        assertTrue(text.contains("  ▪️ Penicillin (HIGH)\r\n"))
        for (m in meds) assertTrue(text.contains("  ▪️ ${m.displayLocalized} 500mg 1x/day\r\n"))
        assertTrue(text.contains("  ▪️ Heart failure\r\n"))
        assertTrue(text.contains("Misako Kudoro (spouse) +32 478 45 45 45"))
        assertTrue(text.contains("Rue de la Paix 12"))
        // vaccines: most recent kept, the rest counted
        assertTrue(text.contains("💉 [ VACCINATIONS ]"))
        val keptVaccines = text.split("\r\n").count { it.startsWith("  ▪️ Vaccine number") }
        assertTrue(keptVaccines in 1..59)
        assertTrue(text.contains("  " + JemmaTextPayloadBuilder.TRUNCATION_MARK + " +${60 - keptVaccines}\r\n"))
    }

    /**
     * UC-QR-TEXT-06 (UC-QRT-010 / 021) — Japanese payload: the budget is counted in
     * UTF-8 bytes (3 per kanji), not in chars. A text well under 1800 CHARS but over
     * 1800 BYTES must still be capped, marked, and fit one frame.
     */
    @Test
    fun ucQrText06_japanesePayloadIsBudgetedInBytes() {
        val allergies = listOf(allergy("ペニシリン"), allergy("そば"))
        val meds = (1..40).map { medication("ワルファリンカリウム錠%02d".format(it)) }
        val text = JemmaTextPayloadBuilder.build(hydrated(allergies, meds), JemmaTextPayloadBuilder.Lang.JA)

        assertTrue("chars ${text.length}", text.length < JemmaTextPayloadBuilder.MAX_BYTES)
        assertTrue("bytes ${bytes(text)}", bytes(text) <= JemmaTextPayloadBuilder.MAX_BYTES)
        assertTrue("bytes > chars", bytes(text) > text.length)
        assertEquals(bytes(text), JemmaTextPayloadBuilder.utf8Size(text))
        assertTrue(text.contains(JemmaTextPayloadBuilder.TRUNCATION_MARK + " [ 記録は不完全です ]"))
        assertTrue(text.contains("  ▪️ ペニシリン (HIGH)\r\n"))
        assertTrue(text.contains("  ▪️ そば (HIGH)\r\n"))
        assertTrue(text.contains("  ▪️ ワルファリンカリウム錠01 500mg 1x/day\r\n"))
        assertEquals(1, JemmaQrFrameSplitter.split(text).size)

        // the same 40 medications in a short Latin script fit without any marker
        val latin = JemmaTextPayloadBuilder.build(
            hydrated(allergies, (1..40).map { medication("Med%02d".format(it)) }), JemmaTextPayloadBuilder.Lang.EN,
        )
        assertFalse(latin.contains(JemmaTextPayloadBuilder.TRUNCATION_MARK))
    }

    /** UC-QR-TEXT-07 — an explicit smaller cap is honoured down to the identity block, still marked. */
    @Test
    fun ucQrText07_customCapAndCodePointSafeCut() {
        val full = hydrated(listOf(allergy("Penicillin")), (1..10).map { medication("Medication %02d".format(it)) })
        for (cap in listOf(400, 250, 120)) {
            val text = JemmaTextPayloadBuilder.build(full, JemmaTextPayloadBuilder.Lang.JA, cap)
            assertTrue("cap $cap → ${bytes(text)}", bytes(text) <= cap)
            assertTrue("cap $cap marker", text.contains(JemmaTextPayloadBuilder.TRUNCATION_MARK))
            // valid UTF-16: no orphan surrogate left by the cut
            assertEquals(text, String(text.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        }
        // absurd cap, smaller than the marker line itself: still never above the cap
        assertTrue(bytes(JemmaTextPayloadBuilder.build(full, JemmaTextPayloadBuilder.Lang.JA, 40)) <= 40)
        assertEquals("患者", JemmaTextPayloadBuilder.cutUtf8("患者🏥", 9))
        assertEquals("患者🏥", JemmaTextPayloadBuilder.cutUtf8("患者🏥", 10))
        assertEquals("", JemmaTextPayloadBuilder.cutUtf8("患者", 2))
    }

    /** UC-QR-TEXT-08 (UC-QRT-004) — the "truncated" label exists in every language. */
    @Test
    fun ucQrText08_truncatedLabelExistsInEveryLanguage() {
        val en = JemmaTranslations.getLabel(JemmaTextPayloadBuilder.Lang.EN, "truncated")
        assertEquals("INCOMPLETE RECORD", en)
        for (lang in JemmaTextPayloadBuilder.Lang.values()) {
            val label = JemmaTranslations.getLabel(lang, "truncated")
            assertTrue("$lang", label.isNotBlank())
            if (lang != JemmaTextPayloadBuilder.Lang.EN) assertFalse("$lang falls back to EN", label == en)
        }
    }
}
