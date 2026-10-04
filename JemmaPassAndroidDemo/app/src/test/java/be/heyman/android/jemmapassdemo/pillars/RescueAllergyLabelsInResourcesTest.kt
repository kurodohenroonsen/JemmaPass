/*
 * RED TEST (review of ag/0091-rescue-allergy-line @ 9238644) — not to be edited by the implementer
 *
 * The rescue line is right (UC-RSQ-001..009 green, suite 525/0). But the word of severity shown to the
 * rescuer ("SEVERE" / "GRAVE" / "重度") is written in Kotlin, in PatientDetailFragment. PROTOCOL §9.1
 * (decision of 2026-10-03) : a short label shown by the app is an INTERFACE TEXT, it lives in the
 * Android string resources, translated like the rest of the app, reviewed by the product owner.
 *
 * Rule : the severity word comes from the resource `rescue_allergy_severe`, present in the default,
 * French and Japanese resources, and no translated severity word is written in the Kotlin sources
 * of the rescue card.
 *
 * Expected red today : UC-RSQ-010, UC-RSQ-011.
 */
package be.heyman.android.jemmapassdemo.pillars

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RescueAllergyLabelsInResourcesTest {

    private fun firstDir(vararg paths: String): File? = paths.map { File(it) }.firstOrNull { it.isDirectory }

    private val res: File? = firstDir("src/main/res", "app/src/main/res", "JemmaPassAndroidDemo/app/src/main/res")
    private val src: File? = firstDir(
        "src/main/java/be/heyman/android/jemmapassdemo",
        "app/src/main/java/be/heyman/android/jemmapassdemo",
        "JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo",
    )

    private val STRING = Regex("<string\\s+name=\"rescue_allergy_severe\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)

    private fun severeLabel(folder: String): String? {
        val dir = File(res!!, folder)
        if (!dir.isDirectory) return null
        return dir.listFiles { f -> f.extension == "xml" }.orEmpty()
            .firstNotNullOfOrNull { STRING.find(it.readText())?.groupValues?.get(1)?.trim()?.ifBlank { null } }
    }

    /** The Kotlin files of the rescue card : the formatter and the screen that shows it. */
    private fun rescueSources(): List<File> = listOf(
        File(src!!, "pillars/RescueAllergyFormat.kt"),
        File(src!!, "ui/radar/PatientDetailFragment.kt"),
    ).filter { it.isFile }

    /** Translated severity words that must not be written in Kotlin. */
    private val FORBIDDEN = listOf("\"SEVERE\"", "\"GRAVE\"", "\"重度\"", "\"重篤\"", "\"Severe\"", "\"Grave\"")

    @Test
    fun `UC-RSQ-010 the severity word of the rescue line is a string resource in the three languages`() {
        assertTrue("res folder not found from ${File(".").absolutePath}", res != null)
        val missing = listOf("values", "values-fr", "values-ja").filter { severeLabel(it) == null }
        assertTrue(
            "rescue_allergy_severe is missing from : $missing (PROTOCOL §9.1 : interface texts live in the string resources)",
            missing.isEmpty(),
        )
    }

    @Test
    fun `UC-RSQ-011 no translated severity word is written in the Kotlin sources of the rescue card`() {
        assertTrue("source folder not found from ${File(".").absolutePath}", src != null)
        val hits = rescueSources().flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                FORBIDDEN.firstOrNull { line.contains(it) }?.let { "${f.name}:${i + 1} $it" }
            }
        }
        assertTrue("translated severity words found in Kotlin, they belong to strings.xml : $hits", hits.isEmpty())
    }
}
