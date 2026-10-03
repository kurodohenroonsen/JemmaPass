/*
 * RED TEST (wave 3, "KB only") — rule set by the product owner on 2026-10-02 :
 * medical knowledge lives in the knowledge base, built from official sources and updatable
 * without shipping a new app. The Kotlin source holds NO medical code : no ATC code, no SNOMED CT
 * concept, no LOINC code, no table "word → drug class", no list of synonyms of diseases.
 * Not to be edited by the implementer, except to REMOVE a line from STRUCTURAL or DEMO_DATA
 * when the file is cleaned ; adding a file to an allowlist needs the product owner's decision.
 *
 * What stays allowed in code :
 *  - the structure of the IPS document itself (section codes, profile URLs) — one file ;
 *  - the demo personas — one file, test data, not knowledge.
 * Everything else must be read from the KB (value sets, translations, class membership,
 * cross-reactivity, synonyms), and the app must say "not verified" when the KB lacks it.
 */
package be.heyman.android.jemmapassdemo.kbonly

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class NoClinicalCodeInSourceTest {

    private val sourceRoot: File? = listOf(
        "src/main/java/be/heyman/android/jemmapassdemo",
        "app/src/main/java/be/heyman/android/jemmapassdemo",
        "JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo",
    ).map { File(it) }.firstOrNull { it.isDirectory }

    /** The IPS document format itself : LOINC section codes and profile URLs. */
    private val STRUCTURAL = setOf("ips/IpsFhirCodec.kt")

    /** Demo personas : test data shipped for the demonstration, not medical knowledge. */
    private val DEMO_DATA = setOf("qr/JemmaPersonasSeeder.kt")

    private val ATC = Regex("\"[A-Z][0-9]{2}[A-Z]{2}[0-9]{2}\"")
    private val ATC_CLASS = Regex("\"[A-Z][0-9]{2}[A-Z]{1,2}\"")
    private val SNOMED = Regex("\"[0-9]{6,18}\"")
    private val LOINC = Regex("\"(LA)?[0-9]{3,5}-[0-9]\"")

    /** Digit strings that are not concepts : the digit alphabet of sos/JemmaDeviceId.kt. */
    private val NOT_A_CODE = setOf("\"0123456789\"")

    private data class Hit(val file: String, val line: Int, val kind: String, val literal: String)

    private fun scan(): List<Hit> {
        val root = sourceRoot!!
        val hits = ArrayList<Hit>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
            if (rel in STRUCTURAL || rel in DEMO_DATA) return@forEach
            f.readLines().forEachIndexed { i, raw ->
                val line = raw.substringBefore("//")
                if (line.trimStart().startsWith("*") || line.trimStart().startsWith("/*")) return@forEachIndexed
                ATC.findAll(line).forEach { hits += Hit(rel, i + 1, "ATC", it.value) }
                ATC_CLASS.findAll(line).forEach { hits += Hit(rel, i + 1, "ATC class", it.value) }
                SNOMED.findAll(line).filter { it.value !in NOT_A_CODE }.forEach { hits += Hit(rel, i + 1, "SNOMED CT", it.value) }
                LOINC.findAll(line).forEach { hits += Hit(rel, i + 1, "LOINC", it.value) }
            }
        }
        return hits
    }

    private fun report(hits: List<Hit>): String =
        hits.groupBy { it.file }.toSortedMap().entries.joinToString("\n") { (file, list) ->
            "  $file : ${list.size} — " + list.take(4).joinToString(", ") { "l.${it.line} ${it.kind} ${it.literal}" } +
                if (list.size > 4) ", …" else ""
        }

    @Test
    fun `UC-KB-001 no ATC code or ATC class is written in the source`() {
        assumeTrue("source tree not found from ${File(".").absolutePath}", sourceRoot != null)
        val hits = scan().filter { it.kind.startsWith("ATC") }
        assertTrue(
            "${hits.size} ATC literals in ${hits.map { it.file }.distinct().size} files. Drug classes and codes come from the " +
                "knowledge base (official ATC index), never from the source :\n${report(hits)}",
            hits.isEmpty(),
        )
    }

    @Test
    fun `UC-KB-002 no SNOMED CT concept is written in the source`() {
        assumeTrue("source tree not found from ${File(".").absolutePath}", sourceRoot != null)
        val hits = scan().filter { it.kind == "SNOMED CT" }
        assertTrue(
            "${hits.size} SNOMED-like literals in ${hits.map { it.file }.distinct().size} files. Vaccines, procedures, devices, " +
                "routes, blood groups are IPS value sets of the knowledge base :\n${report(hits)}",
            hits.isEmpty(),
        )
    }

    @Test
    fun `UC-KB-003 no LOINC code is written in the source outside the document structure`() {
        assumeTrue("source tree not found from ${File(".").absolutePath}", sourceRoot != null)
        val hits = scan().filter { it.kind == "LOINC" }
        assertTrue(
            "${hits.size} LOINC literals in ${hits.map { it.file }.distinct().size} files. Result codes, pregnancy codes and " +
                "answer lists are IPS value sets of the knowledge base :\n${report(hits)}",
            hits.isEmpty(),
        )
    }

    @Test
    fun `UC-KB-000 the scanner finds the source tree`() {
        // Guard : if the path changes, the three tests above would pass by seeing nothing.
        assertTrue("source tree not found from ${File(".").absolutePath} : the KB-only rule is not being checked", sourceRoot != null)
        assertTrue("fewer than 100 Kotlin files scanned", sourceRoot!!.walkTopDown().count { it.isFile && it.extension == "kt" } > 100)
    }
}
