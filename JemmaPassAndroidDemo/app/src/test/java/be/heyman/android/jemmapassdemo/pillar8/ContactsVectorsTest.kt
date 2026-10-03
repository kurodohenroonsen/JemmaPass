/*
 * RED TEST (sprint 8, contacts pillar) — not to be edited by the implementer
 *
 * Replays the language-neutral vectors of qa/vectors/contacts (see its README.md) : each file gives
 * a `_j` profile, the Patient.contact array expected in the IPS document and the strings expected
 * on the text QR. The same files will be replayed by the iOS implementation : fix the app, never a
 * vector.
 *
 * Expected today : the vectors with a phone number fail (the document adds "use": "mobile"), the
 * vector with a contact address fails (the address is not exported) ; the others are locks.
 */
package be.heyman.android.jemmapassdemo.pillar8

import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.qr.JemmaTextPayloadBuilder
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsVectorsTest {

    private val candidates = listOf("../../qa/vectors/contacts", "../qa/vectors/contacts", "qa/vectors/contacts")

    private fun vectorsDir(): File = candidates.map { File(it) }.firstOrNull { it.isDirectory }
        ?: throw AssertionError(
            "The vectors directory qa/vectors/contacts was not found from the working directory " +
                "'${File("").absolutePath}' (tried $candidates).",
        )

    private fun vectorFiles(): List<File> =
        vectorsDir().listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".json") }.sortedBy { it.name }

    private class Vector(val file: File) {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val id: String = json.getString("id")
        val uiLang: String = json.optString("ui_lang", "en")
        val expect: JSONObject = json.getJSONObject("expect")

        fun profile(): JemmaProfileJ = ProfileFixtures.adapter.fromJson(json.getJSONObject("input_j").toString())
            ?: throw AssertionError("$id : input_j is not a `_j` profile")

        fun strings(key: String): List<String> {
            val array = expect.optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).map { array.getString(it) }
        }
    }

    private fun vectors(): List<Vector> = vectorFiles().map { Vector(it) }

    /** Patient.contact of the IPS document built through the production path (empty array when absent). */
    private fun patientContact(v: Vector): JSONArray {
        val bundle = JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(v.profile(), v.uiLang))
        val entries = JSONObject(bundle).getJSONArray("entry")
        val patient = (0 until entries.length())
            .map { entries.getJSONObject(it).getJSONObject("resource") }
            .single { it.getString("resourceType") == "Patient" }
        return patient.optJSONArray("contact") ?: JSONArray()
    }

    /** Structural comparison : same keys, same values, key order ignored, array order significant. */
    private fun diff(path: String, expected: Any?, actual: Any?, out: MutableList<String>) {
        when {
            expected is JSONObject && actual is JSONObject -> {
                for (key in expected.keySet().sorted()) {
                    if (!actual.has(key)) {
                        out += "$path.$key is missing (expected ${expected.get(key)})"
                    } else {
                        diff("$path.$key", expected.get(key), actual.get(key), out)
                    }
                }
                for (key in actual.keySet().sorted()) {
                    if (!expected.has(key)) {
                        out += "$path.$key is not expected (found ${actual.get(key)})"
                    }
                }
            }
            expected is JSONArray && actual is JSONArray -> {
                if (expected.length() != actual.length()) {
                    out += "$path holds ${actual.length()} element(s), expected ${expected.length()} : found $actual"
                } else {
                    for (i in 0 until expected.length()) diff("${path}[$i]", expected.get(i), actual.get(i), out)
                }
            }
            expected is JSONObject || expected is JSONArray || actual is JSONObject || actual is JSONArray -> {
                out += "$path : expected $expected, found $actual"
            }
            else -> {
                if (expected.toString() != actual.toString()) {
                    out += "$path : expected \"$expected\", found \"$actual\""
                }
            }
        }
    }

    @Test
    fun `UC-CT-V00 guard - the vectors directory is found and holds the six cases`() {
        val files = vectorFiles()
        assertTrue(
            "At least 6 vectors are expected in ${vectorsDir().path}, found ${files.map { it.name }}. " +
                "Without them the two other tests would pass without checking anything.",
            files.size >= 6,
        )
        assertTrue("README.md is missing next to the vectors", File(vectorsDir(), "README.md").isFile)
    }

    @Test
    fun `UC-CT-V01 guard - every vector is well formed`() {
        val all = vectors()
        assertEquals("vector ids must be unique", all.size, all.map { it.id }.toSet().size)
        for (v in all) {
            assertEquals("the id of a vector is its file name", v.file.name.removeSuffix(".json"), v.id)
            assertTrue("${v.id} : title missing", v.json.optString("title").isNotBlank())
            assertTrue("${v.id} : expect.patient_contact must be an array", v.expect.optJSONArray("patient_contact") != null)
            assertTrue("${v.id} : expect.text_qr_contains must be an array", v.expect.optJSONArray("text_qr_contains") != null)
            assertTrue(
                "${v.id} : ui_lang '${v.uiLang}' is not a language of the text QR",
                JemmaTextPayloadBuilder.Lang.values().any { it.isoCode == v.uiLang },
            )
            assertTrue("${v.id} : input_j must hold p.ct", v.profile().p?.ct.orEmpty().isNotEmpty())
        }
    }

    @Test
    fun `UC-CT-V02 the document holds exactly the expected patient contacts for every vector`() {
        val failures = ArrayList<String>()
        for (v in vectors()) {
            val problems = ArrayList<String>()
            try {
                diff("contact", v.expect.getJSONArray("patient_contact"), patientContact(v), problems)
            } catch (e: Exception) {
                problems += "building the document threw ${e.javaClass.simpleName} (${e.message})"
            }
            if (problems.isNotEmpty()) {
                failures += "${v.id} : " + problems.joinToString(" ; ")
            }
        }
        assertTrue(
            "${failures.size} vector(s) of qa/vectors/contacts do not match the IPS document :\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `UC-CT-V03 the text QR holds the expected lines in order for every vector`() {
        val failures = ArrayList<String>()
        for (v in vectors()) {
            val lang = JemmaTextPayloadBuilder.Lang.values().firstOrNull { it.isoCode == v.uiLang }
            if (lang == null) {
                failures += "${v.id} : ui_lang '${v.uiLang}' is not a language of the text QR"
                continue
            }
            val text = JemmaTextPayloadBuilder.build(ProfileFixtures.hydrated(v.profile(), v.uiLang), lang)
            var from = 0
            for (wanted in v.strings("text_qr_contains")) {
                val at = text.indexOf(wanted, from)
                if (at < 0) {
                    failures += "${v.id} : the text QR does not hold \"$wanted\" (or not in the expected order)"
                } else {
                    from = at + wanted.length
                }
            }
            for (banned in v.strings("text_qr_absent")) {
                if (text.contains(banned)) {
                    failures += "${v.id} : the text QR must not hold \"$banned\""
                }
            }
        }
        assertTrue(
            "${failures.size} problem(s) between qa/vectors/contacts and the text QR :\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }
}
