/*
 * TextQrProbe.kt — reads a text QR payload (JemmaTextPayloadBuilder.build) the way a person
 * would : section by section, counting the entries printed and the entries announced as
 * left out ("✂️ … +N"). Not a test class.
 *
 * The invariant checked by [assertAccounted] is the one that matters to a responder :
 * every entry of the profile is either printed, or the payload says it is incomplete.
 * Nothing disappears without a notice (qa/usecases UC-QRT-008 / 009, UC-I18N-008).
 *
 * Icons are spelled by code point so the comparison does not depend on how an editor
 * saved a variation selector.
 */
package be.heyman.android.jemmapassdemo.testsupport

import be.heyman.android.jemmapassdemo.ips.IpsPregnancyObs
import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.qr.JemmaTextPayloadBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

object TextQrProbe {

    private fun cp(vararg points: Int): String = points.joinToString("") { String(Character.toChars(it)) }

    const val EOL = "\r\n"
    val MARK: String = JemmaTextPayloadBuilder.TRUNCATION_MARK
    val BULLET: String = "  " + cp(0x25AA, 0xFE0F) + " "

    val PATIENT = cp(0x1F464)
    val ALLERGIES = cp(0x26A0, 0xFE0F)
    val MEDICATIONS = cp(0x1F48A)
    val CONDITIONS = cp(0x1FA7A)
    val CONTACTS = cp(0x260E, 0xFE0F)
    val IMMUNIZATIONS = cp(0x1F489)
    val PROCEDURES = cp(0x1F3E5)
    val DEVICES = cp(0x1F4DF)
    val RESULTS = cp(0x1F9EA)
    val PAST_PROBLEMS = cp(0x1F4DC)
    val PREGNANCY = cp(0x1F930)
    val FUNCTIONAL = cp(0x267F)

    /** First characters of the four optional patient lines (address, phone, e-mail, id). */
    private val PATIENT_EXTRA_PREFIXES = listOf(cp(0x1F4CD), cp(0x1F4DE), cp(0x1F4E7), cp(0x1F194)).map { " $it " }

    /** Sections whose loss changes what a responder does first. */
    val LIFE_CRITICAL: List<String> = listOf(ALLERGIES, MEDICATIONS, CONDITIONS, CONTACTS)

    class Section(val title: String) {
        var bullets: Int = 0
        var dropped: Int = 0
        val lines = ArrayList<String>()
    }

    /** icon → section, in the order they are printed (the patient block included). */
    fun sections(text: String): LinkedHashMap<String, Section> {
        val out = LinkedHashMap<String, Section>()
        var current: Section? = null
        for (line in text.split(EOL)) {
            when {
                line.startsWith(MARK) -> current = null
                line.startsWith(BULLET) -> current?.let { it.bullets++; it.lines += line.removePrefix(BULLET) }
                line.startsWith("  $MARK +") -> current?.let { it.dropped = line.substringAfterLast("+").trim().toInt() }
                !line.startsWith(" ") && line.contains(" [ ") && line.endsWith(" ]") -> {
                    val s = Section(line.substringAfter(" [ ").removeSuffix(" ]"))
                    out[line.substringBefore(" [ ")] = s
                    current = s
                }
            }
        }
        return out
    }

    /** The final "✂️ … [ INCOMPLETE RECORD ] <icons>" line, or null when the payload is complete. */
    fun incompleteLine(text: String): String? = text.split(EOL).firstOrNull { it.startsWith(MARK) }

    /** Number of entries the profile holds for each section of the text QR. */
    fun modelCounts(h: HydratedProfile): LinkedHashMap<String, Int> = linkedMapOf(
        ALLERGIES to h.allergies.size,
        MEDICATIONS to h.medications.size,
        CONDITIONS to h.conditions.size,
        CONTACTS to h.raw.p?.ct.orEmpty().count { c ->
            !c.n.isNullOrBlank() || !c.r.isNullOrBlank() || !c.p.isNullOrBlank() || !c.e.isNullOrBlank()
        },
        IMMUNIZATIONS to h.raw.im.size,
        PROCEDURES to h.raw.pr.size,
        DEVICES to h.raw.dv.size,
        RESULTS to h.raw.rs.size,
        PAST_PROBLEMS to h.raw.ph.size,
        PREGNANCY to h.raw.pg.filterIndexed { i, e -> IpsPregnancyObs.fromJEntry(e, i) != null }.size,
        FUNCTIONAL to h.raw.fs.size,
    )

    private fun patientExtrasExpected(h: HydratedProfile): Int {
        val p = h.raw.p ?: return 0
        return listOf(p.adr, p.tel, p.eml, p.idn).count { !it.isNullOrBlank() }
    }

    private fun patientExtrasPrinted(text: String): Int =
        text.split(EOL).count { line -> PATIENT_EXTRA_PREFIXES.any { line.startsWith(it) } }

    /**
     * Every profile entry is printed or announced as left out ; the "incomplete record"
     * line is there exactly when something was left out, and names each affected section.
     * Pillars without entries are ignored, so a future "none recorded" line does not break it.
     */
    fun assertAccounted(context: String, text: String, h: HydratedProfile) {
        val found = sections(text)
        val incomplete = incompleteLine(text)
        var somethingLeftOut = false
        for ((icon, n) in modelCounts(h)) {
            // A pillar without any entry may be omitted or carry a "none recorded" line : not checked here.
            if (n == 0) continue
            val s = found[icon]
            if (s == null) {
                somethingLeftOut = true
                assertTrue("$context $icon : $n entries left out without notice", incomplete != null && incomplete.contains(icon))
            } else {
                assertTrue("$context $icon : section printed without any entry", s.bullets > 0)
                assertEquals("$context $icon : printed + announced as left out", n, s.bullets + s.dropped)
                if (s.dropped > 0) {
                    somethingLeftOut = true
                    assertTrue("$context $icon : cut section not named in the incomplete line", incomplete != null && incomplete.contains(icon))
                }
            }
        }
        val extrasMissing = patientExtrasExpected(h) - patientExtrasPrinted(text)
        assertTrue("$context patient lines", extrasMissing >= 0)
        if (extrasMissing > 0) {
            somethingLeftOut = true
            assertTrue("$context patient contact lines left out without notice", incomplete != null && incomplete.contains(PATIENT))
        }
        assertEquals("$context incomplete marker present exactly when something was left out", somethingLeftOut, text.contains(MARK))
    }

    /**
     * Drop order (UC-QRT-009, decided 2026-10-02, mailbox message 0037 A ; devices moved up
     * on 2026-10-04, improvement Analyse-0002, UC-QRT-030..034) : most important first —
     * allergies, medications, active problems, pregnancy, devices, functional status,
     * emergency contacts, patient address / phone / e-mail / id, past illnesses, procedures,
     * results, immunizations. A rescuer must know about a pacemaker before knowing that a
     * person walks with a cane, and about the cane before knowing her vaccines.
     * As long as a less important block still prints something, every more important
     * block is whole.
     */
    fun assertPriority(context: String, text: String, h: HydratedProfile) {
        val found = sections(text)
        val counts = modelCounts(h)
        val order = listOf(
            ALLERGIES, MEDICATIONS, CONDITIONS, PREGNANCY, DEVICES, FUNCTIONAL, CONTACTS,
            PATIENT, PAST_PROBLEMS, PROCEDURES, RESULTS, IMMUNIZATIONS,
        )
        val expected = order.map { if (it == PATIENT) patientExtrasExpected(h) else counts.getValue(it) }
        val printed = order.map { if (it == PATIENT) patientExtrasPrinted(text) else (found[it]?.bullets ?: 0) }
        for (j in order.indices) {
            if (printed[j] == 0 || expected[j] == 0) continue
            for (i in 0 until j) {
                if (expected[i] == 0) continue
                assertEquals(
                    "$context ${order[i]} lost entries while ${order[j]} (less important) is still printed",
                    expected[i], printed[i],
                )
            }
        }
    }

    /** True when every entry of [icon] is printed (the section is whole, or the profile has none). */
    fun isWhole(text: String, h: HydratedProfile, icon: String): Boolean {
        val n = modelCounts(h).getValue(icon)
        val s = sections(text)[icon]
        return n == 0 || (s != null && s.bullets == n && s.dropped == 0)
    }
}
