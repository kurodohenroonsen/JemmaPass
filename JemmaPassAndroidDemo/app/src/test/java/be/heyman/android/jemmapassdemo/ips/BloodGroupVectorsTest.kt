/*
 * TEST — not to be edited by the implementer
 *
 * Replays the language-neutral vectors of qa/vectors/bloodgroup (see its README.md) : each file gives
 * a `_j` profile and the ABO/Rh results (LOINC 882-1) expected in the IPS document. The same files are
 * replayed by the iOS, Chrome and USB implementations : fix the app, never a vector.
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BloodGroupVectorsTest {

    private val candidates = listOf("../../qa/vectors/bloodgroup", "../qa/vectors/bloodgroup", "qa/vectors/bloodgroup")

    private fun vectorsDir(): File = candidates.map { File(it) }.firstOrNull { it.isDirectory }
        ?: throw AssertionError("qa/vectors/bloodgroup was not found from '${File("").absolutePath}' (tried $candidates).")

    private fun vectors(): List<JSONObject> = vectorsDir().listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".json") }.sortedBy { it.name }
        .map { f -> JSONObject(f.readText(Charsets.UTF_8)).also { assertEquals(f.name.removeSuffix(".json"), it.getString("id")) } }

    private fun profile(v: JSONObject): JemmaProfileJ =
        ProfileFixtures.adapter.fromJson(v.getJSONObject("input_j").toString())
            ?: throw AssertionError("${v.getString("id")} : input_j is not a `_j` profile")

    /** ABO/Rh observations of the document, `id` removed, references to the Patient entry written "@patient". */
    private fun bloodGroupObservations(v: JSONObject): JSONArray {
        val bundle = JSONObject(JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(profile(v), "en")))
        val entries = bundle.getJSONArray("entry")
        val all = (0 until entries.length()).map { entries.getJSONObject(it) }
        val patientUrl = all.single { it.getJSONObject("resource").getString("resourceType") == "Patient" }.getString("fullUrl")
        val out = JSONArray()
        for (e in all) {
            val r = e.getJSONObject("resource")
            if (r.getString("resourceType") != "Observation") continue
            val codings = r.optJSONObject("code")?.optJSONArray("coding") ?: continue
            if ((0 until codings.length()).none { codings.getJSONObject(it).optString("code") == "882-1" }) continue
            val copy = JSONObject(r.toString())
            copy.remove("id")
            copy.optJSONObject("subject")?.let { if (it.optString("reference") == patientUrl) it.put("reference", "@patient") }
            copy.optJSONArray("performer")?.let { p ->
                for (i in 0 until p.length()) p.getJSONObject(i).let { if (it.optString("reference") == patientUrl) it.put("reference", "@patient") }
            }
            out.put(copy)
        }
        return out
    }

    private fun diff(path: String, expected: Any?, actual: Any?, out: MutableList<String>) {
        when {
            expected is JSONObject && actual is JSONObject -> {
                for (key in expected.keys().asSequence().toList().sorted()) {
                    if (!actual.has(key)) out += "$path.$key is missing" else diff("$path.$key", expected.get(key), actual.get(key), out)
                }
                for (key in actual.keys().asSequence().toList().sorted()) {
                    if (!expected.has(key)) out += "$path.$key is not expected (found ${actual.get(key)})"
                }
            }
            expected is JSONArray && actual is JSONArray -> {
                if (expected.length() != actual.length()) {
                    out += "$path holds ${actual.length()} element(s), expected ${expected.length()}"
                } else {
                    for (i in 0 until expected.length()) diff("$path[$i]", expected.get(i), actual.get(i), out)
                }
            }
            expected is JSONObject || expected is JSONArray || actual is JSONObject || actual is JSONArray ->
                out += "$path : expected $expected, found $actual"
            else -> if (expected.toString() != actual.toString()) out += "$path : expected \"$expected\", found \"$actual\""
        }
    }

    @Test
    fun `UC-BG-V00 guard - the vectors directory holds the ten cases and its README`() {
        assertTrue("found ${vectors().map { it.getString("id") }}", vectors().size >= 10)
        assertTrue(File(vectorsDir(), "README.md").isFile)
    }

    @Test
    fun `UC-BG-V01 the document holds exactly the expected ABO Rh results for every vector`() {
        val failures = ArrayList<String>()
        for (v in vectors()) {
            val problems = ArrayList<String>()
            try {
                diff("observations", v.getJSONObject("expect").getJSONArray("blood_group_observations"), bloodGroupObservations(v), problems)
            } catch (e: Exception) {
                problems += "building the document threw ${e.javaClass.simpleName} (${e.message})"
            }
            if (problems.isNotEmpty()) failures += "${v.getString("id")} : " + problems.joinToString(" ; ")
        }
        assertTrue("${failures.size} vector(s) of qa/vectors/bloodgroup do not match the IPS document :\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
