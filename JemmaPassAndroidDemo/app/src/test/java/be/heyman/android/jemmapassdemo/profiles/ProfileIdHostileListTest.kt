/*
 * ProfileIdHostileListTest.kt — the profile id of a scanned QR becomes a file name.
 *
 * Use cases (qa/usecases) : UC-IMP-009 (hostile `sid` : the id is a file name, it must never
 * leave the profiles folder), UC-ROB-001 (atomic write).
 *
 * ProfileFilesTest already rejects a dozen broken ids. This class runs a long hostile list
 * (path traversal in every spelling, look-alike characters, control characters, length
 * limits) and a seeded random campaign, and checks on a real directory that an accepted id
 * can only ever name a file directly inside the profiles folder.
 *
 * Not covered here on purpose (recorded in qa/usecases/suspected-defects.md) : ids ending
 * in ".fhir" and the id "meta", which are accepted today and collide with the file layout.
 */
package be.heyman.android.jemmapassdemo.profiles

import java.io.File
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileIdHostileListTest {

    private val traversal = listOf(
        "..", "../", "../x", "../../x", "../../../../etc/passwd", "..\\x", "..\\..\\x", "x/..", "x/../y", "a/../../b",
        "/", "/x", "/sdcard/x", "//x", "\\x", "\\\\server\\share", "C:\\x", "C:x", "x/", "x\\", "a/b", "a\\b", "./x", ".\\x",
        "a..b", "a...b", "x..", "..x", "x/./y", "~", "~/x", "~root", "file:///sdcard/x", "content://x",
        "%2e%2e%2fx", "%2e%2e/x", "..%2fx", "..%5cx", "%00", "x%00.json", "..;/x", "....//x", "..././x",
    )

    private val hiddenOrOddStart = listOf(
        ".", ".hidden", ".json", ".fhir.json", ".nomedia", "-", "-rf", "--help", "_", "_x", " ", "", "\t", "\n", "\r\n", " \t \n ",
    )

    private val controlAndSpace = listOf(
        "id\u0000", "\u0000id", "a\u0000b", "a\nb", "a\rb", "a\tb", "a b", "a\u000Bb", "a\u007Fb", "a\u001Bb", "a\u0085b",
        "a\u00A0b", "a\u200Bb", "demo_haru\u200B", "\u200Bdemo_haru", "a\u2028b", "a\u2029b", "a\uFEFFb", "\uFEFFdemo_haru",
        "a\u202Eb", "\u202Edemo_haru", "a\u2066b",
    )

    /** Characters that look like safe ASCII but are not. */
    private val lookAlikes = listOf(
        "d\u0435mo_haru",            // Cyrillic е
        "demo_h\u0430ru",            // Cyrillic а
        "dem\u03BF_haru",            // Greek omicron
        "\uFF44\uFF45\uFF4D\uFF4F",  // full-width "demo"
        "demo\uFF3Fharu",            // full-width low line
        "demo\u2010haru",            // hyphen U+2010
        "demo\u2212haru",            // minus sign
        "demo\u2024haru",            // one dot leader
        "demo\uFF0Eharu",            // full-width full stop
        "a\u2215b", "a\uFF0Fb", "a\u2044b", "a\u29F8b",   // slash look-alikes
        "a\uFF3Cb", "a\u29F5b",                            // backslash look-alikes
        "\u0661\u0662\u0663",        // Arabic-Indic digits
        "\uFF11\uFF12\uFF13",        // full-width digits
        "e\u0301t\u00E9",            // combining accent + é
        "\u00E9t\u00E9", "\u7530\u4E2D", "\u0645\u062D\u0645\u062F", "\uD83D\uDE42", "a\uD83D\uDE42b", "\u00DF", "\u0131d",
    )

    private val punctuation =
        ":*?\"<>|;&\$`'(){}[]=+,#@!^%~".map { "a${it}b" } + listOf("a b", "a,b", "a;b", "a'b", "a\"b")

    private val tooLong = listOf("x".repeat(65), "x".repeat(66), "x".repeat(255), "x".repeat(4096), "a" + ".b".repeat(40))

    private val accepted = listOf(
        "demo_kurodo", "demo_haru", "demo_kamekichi",
        "p-3f2c9a1e-0b7d-4c55-9d2e-1a2b3c4d5e6f",
        "a", "Z", "0", "9", "a.b", "a-b", "a_b", "a.b-c_d", "A1.b2-C3_d4", "x".repeat(64), "0" + "-".repeat(63),
    )

    private fun allHostile(): List<String> =
        traversal + hiddenOrOddStart + controlAndSpace + lookAlikes + punctuation + tooLong

    @Test
    fun `UC-IMP-009 every hostile id of the list is rejected`() {
        assertNull(ProfileFiles.safeIdOrNull(null))
        for (bad in allHostile()) {
            assertNull("should reject '${bad.take(40)}' (${bad.length} chars)", ProfileFiles.safeIdOrNull(bad))
        }
    }

    @Test
    fun `UC-IMP-009 a hostile id stays rejected with surrounding blanks`() {
        for (bad in traversal + lookAlikes + tooLong) {
            for (padded in listOf(" $bad", "$bad ", "  $bad  ", "\t$bad\n")) {
                assertNull("should reject padded '${bad.take(40)}'", ProfileFiles.safeIdOrNull(padded))
            }
        }
    }

    @Test
    fun `UC-IMP-009 plain ids are kept exactly and the limit is 64 characters`() {
        for (ok in accepted) {
            assertEquals(ok, ProfileFiles.safeIdOrNull(ok))
            assertEquals("idempotent", ok, ProfileFiles.safeIdOrNull(ProfileFiles.safeIdOrNull(ok)))
        }
        assertEquals("surrounding blanks are removed, nothing else", "demo_haru", ProfileFiles.safeIdOrNull("  demo_haru\n"))
        assertNotNull(ProfileFiles.safeIdOrNull("x".repeat(64)))
        assertNull(ProfileFiles.safeIdOrNull("x".repeat(65)))
        assertNull("64 after the first character, not 64 plus blanks inside", ProfileFiles.safeIdOrNull("x".repeat(32) + " " + "x".repeat(31)))
    }

    /** An accepted id, used as `<id>.json` / `<id>.fhir.json`, names a file directly inside [dir]. */
    private fun assertStaysInside(dir: File, raw: String) {
        val id = ProfileFiles.safeIdOrNull(raw) ?: return
        assertEquals("accepted ids are the trimmed input", raw.trim(), id)
        assertTrue("'$id' length", id.length in 1..64)
        assertTrue("'$id' has only plain characters", id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' })
        assertFalse("'$id' starts with a dot", id.startsWith("."))
        assertFalse("'$id' holds a parent step", id.contains(".."))
        for (name in listOf("$id.json", "$id.fhir.json", "$id.json.tmp")) {
            val f = File(dir, name)
            assertEquals("'$name' escapes the profiles folder", dir.canonicalFile, f.canonicalFile.parentFile)
            assertEquals(name, f.canonicalFile.name)
        }
    }

    @Test
    fun `UC-IMP-009 an accepted id can only name a file directly inside the profiles folder`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "jp-ids-${System.nanoTime()}")
        try {
            assertTrue(dir.mkdirs())
            for (candidate in allHostile() + accepted) assertStaysInside(dir, candidate)

            // Seeded random campaign : ids built from the characters an attacker would use.
            val alphabet = listOf(
                "a", "Z", "0", "9", ".", ".", "..", "/", "\\", "_", "-", " ", "\t", "\n", "%2e", "%2f", "~", ":", "*",
                "\u0000", "\u200B", "\u00E9", "\u65E5", "\u0435", "\uFF0E", "\u2215", "json", "fhir", "demo_haru",
            )
            val random = Random(20261002L)
            var acceptedCount = 0
            repeat(20_000) {
                val parts = 1 + random.nextInt(8)
                val candidate = (1..parts).joinToString("") { alphabet[random.nextInt(alphabet.size)] }
                if (ProfileFiles.safeIdOrNull(candidate) != null) acceptedCount++
                assertStaysInside(dir, candidate)
            }
            assertTrue("the campaign must exercise the accepting branch too ($acceptedCount)", acceptedCount > 100)
            assertEquals("the checks never create a file", 0, dir.list()!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `UC-ROB-001 a profile written under a boundary id is whole and alone in its folder`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "jp-ids-${System.nanoTime()}")
        try {
            val id = ProfileFiles.safeIdOrNull("x".repeat(64))!!
            val json = File(dir, "$id.json")
            val text = "{\"_j\":\"1.2\",\"sid\":\"$id\",\"p\":{\"gn\":\"\u592A\u90CE \uD83D\uDE42\"}}"
            ProfileFiles.writeAtomic(json, text)
            ProfileFiles.writeAtomic(File(dir, "$id.fhir.json"), "{\"resourceType\":\"Bundle\"}")
            assertEquals(text, json.readText())
            assertEquals(setOf("$id.json", "$id.fhir.json"), dir.list()!!.toSet())
            // a second write replaces the first, no temporary file is left behind
            ProfileFiles.writeAtomic(json, "{}")
            assertEquals("{}", json.readText())
            assertEquals(2, dir.list()!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
