/*
 * PersonaSeedIntegrityTest.kt — the three demo personas (JemmaPersonasSeeder) through
 * every storage hop the app makes, without a device :
 *
 *   seed → `_j` projection → FHIR Bundle → parse → native pillars → `_j` projection
 *
 * Use cases (qa/usecases) : UC-STO-029 (seed), UC-FHIR-001..005 (document shape, references,
 * sections, projection), UC-STO-020 / UC-QRF-014 (stable ids), UC-IMP-002 / UC-IMP-007 /
 * UC-IMP-023 (`_j` JSON, deterministic rebuild, FHIR QR channel), UC-QRF-006 (allergies and
 * medications in the document), UC-QRF-017 (pruned compact payload), UC-HUM-004 (mononym),
 * UC-STO-023 (one blood group, the profile's).
 *
 * The seeder is pure Kotlin, so the real seed data is used. The hydration (KB labels) and the
 * repository write path are reproduced by testsupport/ProfileFixtures.
 */
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.ips.IpsBloodGroup
import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaSeedIntegrityTest {

    private data class Counts(
        val al: Int, val md: Int,
        val im: Int, val pr: Int, val dv: Int, val rs: Int, val ph: Int, val cn: Int, val pg: Int, val fs: Int,
    )

    /** Seed expectations of qa/device/README.md (💉 K4 H3 Ka0 · 🏥 K2 H2 · 📟 H2 · 🧪 K4 H5 Ka1 · 📜 K2 H2 · 🩺 K1 H2 Ka3 · 🤰 H3 · ♿ H2). */
    private val expected: Map<String, Counts> = mapOf(
        JemmaPersonasSeeder.SID_KURODO to Counts(al = 3, md = 0, im = 4, pr = 2, dv = 0, rs = 4, ph = 2, cn = 1, pg = 0, fs = 0),
        JemmaPersonasSeeder.SID_HARU to Counts(al = 1, md = 3, im = 3, pr = 2, dv = 2, rs = 5, ph = 2, cn = 2, pg = 3, fs = 2),
        JemmaPersonasSeeder.SID_KAMEKICHI to Counts(al = 3, md = 5, im = 0, pr = 0, dv = 0, rs = 1, ph = 0, cn = 3, pg = 0, fs = 0),
    )

    private fun countsOf(j: JemmaProfileJ): Counts = Counts(
        al = j.al.size, md = j.md.size,
        im = j.im.size, pr = j.pr.size, dv = j.dv.size, rs = j.rs.size, ph = j.ph.size, cn = j.cn.size, pg = j.pg.size, fs = j.fs.size,
    )

    private fun resources(bundleJson: String): List<JSONObject> {
        val entries = JSONObject(bundleJson).getJSONArray("entry")
        return (0 until entries.length()).map { entries.getJSONObject(it).getJSONObject("resource") }
    }

    @Test
    fun `UC-STO-029 the three personas exist with the expected number of entries per pillar`() {
        assertEquals(
            ProfileFixtures.PERSONA_SIDS.toSet(),
            JemmaPersonasSeeder.getDemoProfiles().mapNotNull { it.sid }.toSet(),
        )
        for (sid in ProfileFixtures.PERSONA_SIDS) {
            val stored = ProfileFixtures.persona(sid)
            assertEquals(sid, expected.getValue(sid), countsOf(stored.j))
            assertEquals("$sid `_j` version", "1.2", stored.j.j)
            assertEquals("$sid sid", sid, stored.j.sid)
        }
    }

    @Test
    fun `UC-FHIR-004 every seeded entry survives build then parse`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) {
            val stored = ProfileFixtures.persona(sid)
            val back = IpsFhirCodec.nativeOf(ProfileFixtures.parse(ProfileFixtures.bundleJson(stored)))
            assertEquals("$sid 💉", stored.native.immunizations, back.immunizations)
            assertEquals("$sid 🏥", stored.native.procedures, back.procedures)
            assertEquals("$sid 📟", stored.native.devices, back.devices)
            assertEquals("$sid 🧪", stored.native.results, back.results)
            assertEquals("$sid 📜", stored.native.pastProblems, back.pastProblems)
            assertEquals("$sid 🩺", stored.native.problems, back.problems)
            assertEquals("$sid 🤰", stored.native.pregnancy, back.pregnancy)
            assertEquals("$sid ♿", stored.native.functional, back.functional)
            // …and the projection of what was read back is the stored `_j`, entry for entry.
            assertEquals("$sid projection", stored.j, ProfileFixtures.project(stored.j, sid, back))
        }
    }

    @Test
    fun `UC-STO-023 each persona has exactly one blood group result and it is the profile group`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) {
            val stored = ProfileFixtures.persona(sid)
            val back = IpsFhirCodec.resultsOf(ProfileFixtures.parse(ProfileFixtures.bundleJson(stored)))
            val groups = back.filter { it.code == IpsBloodGroup.LOINC_ABO_RH }
            assertEquals("$sid one 882-1", 1, groups.size)
            assertEquals("$sid group", IpsBloodGroup.snomedCode(stored.j.p?.bt), groups.single().valueCode)
            assertEquals("$sid derived id", IpsBloodGroup.derivedId(sid), groups.single().id)
            assertEquals("$sid derived first", groups.single(), back.first())
        }
    }

    @Test
    fun `UC-STO-020 a rebuild of an unchanged persona gives the same entries and the same ids`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) {
            val stored = ProfileFixtures.persona(sid)
            val first = ProfileFixtures.parse(ProfileFixtures.bundleJson(stored))
            // second generation : what a later save does (read the Bundle, write it again)
            val reread = ProfileFixtures.store(sid, stored.j, IpsFhirCodec.nativeOf(first))
            assertEquals("$sid native pillars", stored.native, reread.native)
            assertEquals("$sid `_j`", stored.j, reread.j)
            val second = ProfileFixtures.parse(ProfileFixtures.bundleJson(reread))
            assertEquals("$sid fullUrls", first.entry.map { it.fullUrl?.value }, second.entry.map { it.fullUrl?.value })
        }
    }

    @Test
    fun `UC-FHIR-003 a section exists exactly when its pillar has entries and lists each entry once`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) {
            val stored = ProfileFixtures.persona(sid)
            val c = expected.getValue(sid)
            val bundle = ProfileFixtures.parse(ProfileFixtures.bundleJson(stored))
            val sections: Map<String, Int> = linkedMapOf(
                "48765-2" to c.al,
                "10160-0" to c.md,
                IpsFhirCodec.LOINC_SECTION_PROBLEMS to c.cn,
                IpsFhirCodec.LOINC_SECTION_PAST_ILLNESS to c.ph,
                IpsFhirCodec.LOINC_SECTION_PREGNANCY to c.pg,
                IpsFhirCodec.LOINC_SECTION_FUNCTIONAL to c.fs,
                IpsFhirCodec.LOINC_SECTION_IMMUNIZATIONS to c.im,
                IpsFhirCodec.LOINC_SECTION_PROCEDURES to c.pr,
                IpsFhirCodec.LOINC_SECTION_DEVICES to c.dv,
                IpsFhirCodec.LOINC_SECTION_RESULTS to c.rs,
            )
            val fullUrls = bundle.entry.mapNotNull { it.fullUrl?.value }
            // UC-FHIR-002 — every entry has its own urn:uuid
            assertEquals("$sid every entry has a fullUrl", bundle.entry.size, fullUrls.size)
            assertEquals("$sid fullUrls are unique", fullUrls.size, fullUrls.toSet().size)
            assertTrue("$sid urn:uuid", fullUrls.all { it.startsWith("urn:uuid:") })
            // Composition + Patient + allergies + (Medication + MedicationStatement) + native pillars (Device + DeviceUseStatement)
            val entryCount = 2 + c.al + 2 * c.md + c.cn + c.im + c.pr + 2 * c.dv + c.rs + c.ph + c.pg + c.fs
            assertEquals("$sid entry count", entryCount, bundle.entry.size)

            val seen = HashSet<String>()
            for ((loinc, n) in sections) {
                val refs = ProfileFixtures.sectionRefs(bundle, loinc)
                assertEquals("$sid section $loinc present", n > 0, ProfileFixtures.hasSection(bundle, loinc))
                assertEquals("$sid section $loinc size", n, refs.size)
                assertEquals("$sid section $loinc lists each entry once", refs.size, refs.toSet().size)
                assertTrue("$sid section $loinc points at Bundle entries", fullUrls.containsAll(refs))
                for (r in refs) assertTrue("$sid $r is listed by one section only", seen.add(r))
            }
        }
    }

    @Test
    fun `UC-FHIR-001 the document starts with the Composition then the Patient with the right identity`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) {
            val stored = ProfileFixtures.persona(sid)
            val json = ProfileFixtures.bundleJson(stored)
            assertEquals("$sid", "document", JSONObject(json).getString("type"))
            val all = resources(json)
            assertEquals("$sid", "Composition", all[0].getString("resourceType"))
            val patient = all[1]
            assertEquals("$sid", "Patient", patient.getString("resourceType"))
            val p = stored.j.p!!
            val name = patient.getJSONArray("name").getJSONObject(0)
            assertEquals("$sid given", p.gn, name.getJSONArray("given").getString(0))
            // UC-HUM-004 — a mononym never produces an empty family name
            if (p.fn.isNullOrBlank()) assertFalse("$sid no empty family", name.has("family"))
            else assertEquals("$sid family", p.fn, name.getString("family"))
            assertEquals("$sid birth date", p.bd, patient.getString("birthDate"))
            assertEquals("$sid gender", if (p.gs == "F") "female" else "male", patient.getString("gender"))
            assertEquals("$sid one Patient", 1, all.count { it.getString("resourceType") == "Patient" })
        }
    }

    @Test
    fun `UC-QRF-006 allergies and medications of the personas are in the document, in order`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) {
            val stored = ProfileFixtures.persona(sid)
            val all = resources(ProfileFixtures.bundleJson(stored))

            val allergies = all.filter { it.getString("resourceType") == "AllergyIntolerance" }
            assertEquals("$sid allergy codes", stored.j.al.map { it.c },
                allergies.map { it.getJSONObject("code").getJSONArray("coding").getJSONObject(0).getString("code") })
            assertEquals("$sid allergy labels", stored.j.al.map { it.displayLabel },
                allergies.map { it.getJSONObject("code").getString("text") })
            assertEquals("$sid criticality", stored.j.al.map { if (it.s == "H") "high" else "low" },
                allergies.map { it.getString("criticality") })

            val medications = all.filter { it.getString("resourceType") == "Medication" }
            assertEquals("$sid medication codes", stored.j.md.map { it.c },
                medications.map { it.getJSONObject("code").getJSONArray("coding").getJSONObject(0).getString("code") })
            assertEquals("$sid medication systems", stored.j.md.map { it.codeSystem },
                medications.map { it.getJSONObject("code").getJSONArray("coding").getJSONObject(0).getString("system") })
            val statements = all.filter { it.getString("resourceType") == "MedicationStatement" }
            assertEquals("$sid statements", stored.j.md.size, statements.size)
            assertTrue("$sid every seeded treatment is active", statements.all { it.getString("status") == "active" })
            // the typed posology stays readable in the dosage text
            stored.j.md.forEachIndexed { i, m ->
                val text = statements[i].getJSONArray("dosage").getJSONObject(0).getString("text")
                assertTrue("$sid dosage text of ${m.c} : $text", text.contains(m.t!!))
            }
        }
    }

    @Test
    fun `UC-IMP-002 the _j file of each persona is read back identical`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) {
            val stored = ProfileFixtures.persona(sid)
            val json = ProfileFixtures.adapter.toJson(stored.j)
            assertEquals(sid, stored.j, ProfileFixtures.adapter.fromJson(json))
            // UC-STO-001 — the file carries the version and the id it is stored under
            val o = JSONObject(json)
            assertEquals("1.2", o.getString("_j"))
            assertEquals(sid, o.getString("sid"))
            assertFalse("$sid no null literal in the file", json.contains("null"))
        }
    }

    @Test
    fun `UC-IMP-023 the FHIR QR channel rebuilt from the projection keeps every entry the QR carries`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) {
            val j = ProfileFixtures.persona(sid).j
            val back = IpsFhirCodec.nativeOf(ProfileFixtures.parse(ProfileFixtures.bundleJsonFromProjection(j)))
            val again = ProfileFixtures.project(j, sid, back)
            assertEquals("$sid counts", expected.getValue(sid), countsOf(again))
            assertEquals("$sid 💉", j.im, again.im)
            assertEquals("$sid 🏥", j.pr, again.pr)
            assertEquals("$sid 📟", j.dv, again.dv)
            assertEquals("$sid 🧪", j.rs, again.rs)
            assertEquals("$sid 📜", j.ph, again.ph)
            assertEquals("$sid 🩺", j.cn, again.cn)
            assertEquals("$sid 🤰", j.pg, again.pg)
            assertEquals("$sid ♿", j.fs, again.fs)
        }
    }

    @Test
    fun `UC-IMP-007 importing the same persona payload twice gives the same resources`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) {
            val j = ProfileFixtures.persona(sid).j
            val a = ProfileFixtures.parse(ProfileFixtures.bundleJsonFromProjection(j))
            val b = ProfileFixtures.parse(ProfileFixtures.bundleJsonFromProjection(j))
            assertEquals("$sid native", IpsFhirCodec.nativeOf(a), IpsFhirCodec.nativeOf(b))
            assertEquals("$sid fullUrls", a.entry.map { it.fullUrl?.value }, b.entry.map { it.fullUrl?.value })
        }
    }

    @Test
    fun `UC-QRF-017 pruning for the compact QR removes empty fields only`() {
        for (sid in ProfileFixtures.PERSONA_SIDS) {
            val j = ProfileFixtures.persona(sid).j
            val pruned = JemmaPayloadPruner.pruneToJson(j, ProfileFixtures.adapter)
            assertEquals("$sid nothing filled is lost", j, ProfileFixtures.adapter.fromJson(pruned))
            val o = JSONObject(pruned)
            for (key in listOf("ad", "cs", "gl", "en", "oc", "pv")) assertFalse("$sid empty `$key` dropped", o.has(key))
            assertTrue("$sid pruned is not larger", ProfileFixtures.utf8(pruned) <= ProfileFixtures.utf8(ProfileFixtures.adapter.toJson(j)))
        }
    }
}
