/*
 * RED TEST (improvement UX-0001, safety) — not to be edited by the implementer
 *
 * The rescuer's sheet (ui/radar/PatientDetailFragment) prints only the name of an allergy : the
 * criticality and the reaction received with it are dropped. A rescuer cannot tell an anaphylaxis
 * from a mild intolerance.
 *
 * RescueAllergyFormat.line gives the line of one allergy : `text` for the screen, `severe` for the
 * style, `spoken` for the screen reader. Severity is said with a sign and a word, never by colour alone.
 *
 * Expected red until implemented : all, plus UC-RSQ-009 until the screen uses the formatter.
 */
package be.heyman.android.jemmapassdemo.pillars

import be.heyman.android.jemmapassdemo.qr.CodeLabelResolver
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RescueAllergyFormatTest {

    private val warning = "⚠"

    /** Knows one word for the criticality "high" in each language, nothing else. */
    private val labels = CodeLabelResolver { _, code, lang ->
        if (code.equals("high", ignoreCase = true)) mapOf("en" to "SEVERE*", "fr" to "GRAVE*", "ja" to "重度*")[lang] else null
    }

    private fun entry(vararg pairs: Pair<String, String>) = JSONObject().apply { pairs.forEach { put(it.first, it.second) } }

    private fun line(e: JSONObject, lang: String = "en", l: CodeLabelResolver = labels) = RescueAllergyFormat.line(e, l, lang)

    @Test
    fun `UC-RSQ-001 a high criticality allergy is marked severe, with its reaction`() {
        val r = line(entry("display" to "Penicillin allergy", "criticality" to "high", "manifestations" to "anaphylaxis"))
        assertTrue("severe must be true", r.severe)
        assertTrue("the name must be printed : '${r.text}'", r.text.contains("Penicillin allergy"))
        assertTrue("the reaction must be printed : '${r.text}'", r.text.contains("anaphylaxis"))
        assertTrue("a warning sign must lead the line, colour alone is not enough : '${r.text}'", r.text.startsWith(warning))
        assertTrue("the word for the severity must be printed : '${r.text}'", r.text.contains("SEVERE*"))
    }

    @Test
    fun `UC-RSQ-002 every way the criticality arrives is understood`() {
        for ((key, value) in listOf("criticality" to "high", "criticality" to "HIGH", "criticality" to "H", "s" to "H", "s" to "h", "s" to "high")) {
            assertTrue("$key=$value must be severe", line(entry("display" to "Latex", key to value)).severe)
        }
        for ((key, value) in listOf("criticality" to "low", "criticality" to "L", "s" to "L", "criticality" to "unable-to-assess", "s" to "U", "s" to "")) {
            assertFalse("$key=$value must not be severe", line(entry("display" to "Latex", key to value)).severe)
        }
    }

    @Test
    fun `UC-RSQ-003 a low or unknown criticality carries no warning sign and no severity word`() {
        for (e in listOf(entry("display" to "Dust mites", "criticality" to "low"), entry("display" to "Dust mites"))) {
            val r = line(e)
            assertFalse(r.severe)
            assertFalse("no warning sign expected : '${r.text}'", r.text.contains(warning))
            assertFalse("no severity word expected : '${r.text}'", r.text.contains("SEVERE*"))
            assertTrue(r.text.contains("Dust mites"))
        }
    }

    @Test
    fun `UC-RSQ-004 the reaction is printed whatever the criticality, from either key`() {
        assertTrue(line(entry("display" to "Shellfish", "criticality" to "low", "d" to "hives")).text.contains("hives"))
        assertTrue(line(entry("display" to "Shellfish", "manifestations" to "hives")).text.contains("hives"))
        assertTrue(line(entry("name" to "Shellfish", "s" to "H", "d" to "throat swelling")).text.contains("throat swelling"))
    }

    @Test
    fun `UC-RSQ-005 nothing empty is printed when a field is missing`() {
        for (e in listOf(
            entry("display" to "Aspirin", "criticality" to "high"),
            entry("display" to "Aspirin", "criticality" to "high", "manifestations" to "  "),
            entry("display" to "Aspirin"),
        )) {
            val r = line(e)
            for (junk in listOf("null", "()", "( )", " — ", " - ")) {
                assertFalse("'${r.text}' holds '$junk'", r.text.trim().endsWith(junk.trim()) && junk.isNotBlank() && r.text.contains(junk))
            }
            assertFalse("'${r.text}' holds the word null", r.text.contains("null"))
            assertEquals("no leading or trailing blank", r.text.trim(), r.text)
            assertFalse("'${r.spoken}' holds the word null", r.spoken.contains("null"))
        }
    }

    @Test
    fun `UC-RSQ-006 the name comes from display, then from name`() {
        assertTrue(line(entry("display" to "Penicillin allergy", "name" to "other")).text.contains("Penicillin allergy"))
        assertTrue(line(entry("name" to "Penicillin allergy")).text.contains("Penicillin allergy"))
    }

    @Test
    fun `UC-RSQ-007 the severity word follows the language of the reader`() {
        val e = entry("display" to "ペニシリン", "criticality" to "high", "manifestations" to "アナフィラキシー")
        assertTrue(line(e, "fr").text.contains("GRAVE*"))
        assertTrue(line(e, "ja").text.contains("重度*"))
        val bare = line(e, "ja", CodeLabelResolver.NONE)
        assertTrue("without any label the sign still says it : '${bare.text}'", bare.severe && bare.text.startsWith(warning))
    }

    @Test
    fun `UC-RSQ-008 the spoken text says the severity in words and holds no sign`() {
        val r = line(entry("display" to "Penicillin allergy", "criticality" to "high", "manifestations" to "anaphylaxis"))
        assertTrue(r.spoken.contains("Penicillin allergy"))
        assertTrue(r.spoken.contains("anaphylaxis"))
        assertTrue("a screen reader must hear the severity : '${r.spoken}'", r.spoken.contains("SEVERE*"))
        assertFalse("no sign in the spoken text : '${r.spoken}'", r.spoken.contains(warning))
    }

    @Test
    fun `UC-RSQ-009 the rescuer screen takes its allergy lines from RescueAllergyFormat`() {
        val file = listOf("src/main/java", "app/src/main/java", "JemmaPassAndroidDemo/app/src/main/java")
            .map { File(it, "be/heyman/android/jemmapassdemo/ui/radar/PatientDetailFragment.kt") }.firstOrNull { it.isFile }
            ?: throw AssertionError("PatientDetailFragment.kt not found from ${File("").absolutePath}")
        assertTrue(
            "PatientDetailFragment.kt does not call RescueAllergyFormat.line : the criticality is still dropped on screen",
            file.readText().contains("RescueAllergyFormat.line"),
        )
    }
}
