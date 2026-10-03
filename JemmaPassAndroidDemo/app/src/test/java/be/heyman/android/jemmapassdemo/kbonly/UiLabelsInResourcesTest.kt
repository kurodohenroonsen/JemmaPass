/*
 * RED TEST (wave 3, "KB only") — decision of the product owner on 2026-10-03 : "libellés d'interface".
 * The medical CODE and its membership of a value set come from the knowledge base. The short label shown
 * for a code the app curates (a route, a blood group, a relationship…) is an INTERFACE TEXT : it lives in
 * the Android string resources, translated like the rest of the app, never in a Kotlin catalogue.
 *
 * Contract of the resource name : code_label_<sct|loinc|v3>_<code>, any character of the code that is not
 * a letter or a digit replaced by '_'. Example : SNOMED CT 26643006 → code_label_sct_26643006.
 * A code without such a resource is shown with the English label of the KB, marked as not translated.
 *
 * Not to be edited by the implementer. First family : the five administration routes.
 */
package be.heyman.android.jemmapassdemo.kbonly

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiLabelsInResourcesTest {

    private fun firstDir(vararg paths: String): File? = paths.map { File(it) }.firstOrNull { it.isDirectory }

    private val res: File? = firstDir("src/main/res", "app/src/main/res", "JemmaPassAndroidDemo/app/src/main/res")
    private val src: File? = firstDir(
        "src/main/java/be/heyman/android/jemmapassdemo",
        "app/src/main/java/be/heyman/android/jemmapassdemo",
        "JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo",
    )

    private val STRING = Regex("<string\\s+name=\"(code_label_[A-Za-z0-9_]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)

    /** name → text of every code_label_* string of one values folder. */
    private fun labels(folder: String): Map<String, String> {
        val dir = File(res!!, folder)
        if (!dir.isDirectory) return emptyMap()
        return dir.listFiles { f -> f.extension == "xml" }.orEmpty()
            .flatMap { STRING.findAll(it.readText()).map { m -> m.groupValues[1] to m.groupValues[2].trim() }.toList() }
            .toMap()
    }

    /** The five routes the medication form offers : oral, intravenous, topical, subcutaneous, respiratory. */
    private val ROUTES = listOf("26643006", "47625008", "6064005", "34206005", "447694001").map { "code_label_sct_$it" }

    @Test
    fun `UC-KB-010 the trees are found`() {
        assertTrue("res folder not found from ${File(".").absolutePath}", res != null)
        assertTrue("source folder not found from ${File(".").absolutePath}", src != null)
    }

    @Test
    fun `UC-KB-011 each route has its interface label in the default, French and Japanese resources`() {
        val missing = listOf("values", "values-fr", "values-ja").flatMap { folder ->
            val have = labels(folder)
            ROUTES.filter { have[it].isNullOrBlank() }.map { "$folder/$it" }
        }
        assertTrue("${missing.size} route labels missing from the string resources : $missing", missing.isEmpty())
    }

    @Test
    fun `UC-KB-012 a French or Japanese route label is a translation, not the default text copied`() {
        val base = labels("values")
        val copies = listOf("values-fr", "values-ja").flatMap { folder ->
            // "Injection" is the same word in French : only the Japanese folder is checked for that one.
            labels(folder).filter { (name, text) -> name in ROUTES && text == base[name] && !(folder == "values-fr" && text.length < 12) }
                .map { "$folder/${it.key}" }
        }
        assertTrue("labels copied from the default language : $copies", copies.isEmpty())
    }

    @Test
    fun `UC-KB-013 no translated code label exists without a default one`() {
        val base = labels("values").keys
        val orphans = res!!.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty().sortedBy { it.name }
            .flatMap { dir -> labels(dir.name).keys.filter { it !in base }.map { "${dir.name}/$it" } }
        assertTrue("code labels without a default text (the app would crash or show nothing in other languages) : $orphans", orphans.isEmpty())
    }

    @Test
    fun `UC-KB-014 no catalogue of the source carries a French or Japanese label`() {
        val field = Regex("\\b(display|label|name|text)(Fr|Ja|Jp)\\b\\s*[=:]")
        val foreignLiteral = Regex("\"[^\"\\n]*[\\u00C0-\\u024F\\u3040-\\u30FF\\u4E00-\\u9FFF][^\"\\n]*\"")
        val hits = File(src!!, "pillars").walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.name }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, raw ->
                val line = raw.substringBefore("//")
                if (line.trimStart().startsWith("*") || line.trimStart().startsWith("/*")) null
                else if (field.containsMatchIn(line) || foreignLiteral.containsMatchIn(line)) "${f.name}:${i + 1}" else null
            }
        }.toList()
        assertTrue(
            "${hits.size} lines of pillars/ carry a translated label in Kotlin. Interface labels belong to the string " +
                "resources (code_label_*), one text per language : ${hits.take(12)}${if (hits.size > 12) ", …" else ""}",
            hits.isEmpty(),
        )
    }
}
