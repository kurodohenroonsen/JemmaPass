/*
 * RandomProfileInvariantsTest.kt — property-style tests : many random profiles, generated
 * from a fixed seed (same profiles on every run, on every machine), pushed through the
 * Bundle builder, the `_j` file, the compact-QR pruner / frame splitter and the text QR.
 *
 * Invariants (qa/usecases UC-STO-004 / 005, UC-FHIR-002 / 003 / 004, UC-IMP-001 / 007 / 023,
 * UC-QRF-002 / 004 / 017, UC-QRT-008 / 009 / 010 / 011, UC-HUM-014 / 016 / 017, UC-I18N-008) :
 *
 *   1. nothing throws ;
 *   2. the Bundle is valid JSON, a FHIR document, and reads back to the very same entries ;
 *   3. every Composition section lists exactly the entries of its pillar ;
 *   4. the `_j` file reads back identical ; re-exporting an imported `_j` gives the same `_j` ;
 *   5. the compact payload survives pruning and multi-frame split / join ;
 *   6. the text QR never exceeds one frame, never loses an entry in silence, never cuts a
 *      line, and gives up the least important sections first.
 *
 * A failure message carries the profile number : `profile(n)` rebuilds it.
 */
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.ips.IpsCodeSystems
import be.heyman.android.jemmapassdemo.ips.IpsConditionSeverity
import be.heyman.android.jemmapassdemo.ips.IpsDevice
import be.heyman.android.jemmapassdemo.ips.IpsDeviceStatus
import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.ips.IpsFunctional
import be.heyman.android.jemmapassdemo.ips.IpsFunctionalStatus
import be.heyman.android.jemmapassdemo.ips.IpsImmunization
import be.heyman.android.jemmapassdemo.ips.IpsImmunizationStatus
import be.heyman.android.jemmapassdemo.ips.IpsNativePillars
import be.heyman.android.jemmapassdemo.ips.IpsPastProblem
import be.heyman.android.jemmapassdemo.ips.IpsPastProblemStatus
import be.heyman.android.jemmapassdemo.ips.IpsPregnancyCodes
import be.heyman.android.jemmapassdemo.ips.IpsPregnancyObs
import be.heyman.android.jemmapassdemo.ips.IpsProblem
import be.heyman.android.jemmapassdemo.ips.IpsProblemStatus
import be.heyman.android.jemmapassdemo.ips.IpsProcedure
import be.heyman.android.jemmapassdemo.ips.IpsProcedureStatus
import be.heyman.android.jemmapassdemo.ips.IpsResult
import be.heyman.android.jemmapassdemo.ips.IpsResultCategory
import be.heyman.android.jemmapassdemo.ips.IpsResultInterpretation
import be.heyman.android.jemmapassdemo.ips.IpsResultStatus
import be.heyman.android.jemmapassdemo.qr.JemmaTextPayloadBuilder.Lang
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import be.heyman.android.jemmapassdemo.testsupport.TextQrProbe
import java.util.Locale
import java.util.Random
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomProfileInvariantsTest {

    private val seed = 20261002L
    private val profileCount = 240

    // ── generator ───────────────────────────────────────────────────────────

    private class Coded(val code: String?, val system: String, val display: String?, val text: String?)

    private class Gen(seed: Long) {
        private val r = Random(seed)

        private val words = listOf(
            "Pénicilline", "Amoxicillin", "ワーファリン", "慢性腰痛", "結核", "心不全用利尿薬", "محمد", "حساسية", "Ibuprofène",
            "mal au ❤️", "🙂", "O'Brien", "\"quoted\"", "Größe", "naïve café", "Ελληνικά", "हिन्दी", "ไทย", "한국어", "100 %",
            "a/b", "<b>x</b>", "C:\\temp", "—", "· ·", "left knee", "松山赤十字病院", "Ünïcödé", "x".repeat(30),
        )

        fun int(bound: Int): Int = r.nextInt(bound)
        fun chance(percent: Int): Boolean = r.nextInt(100) < percent
        fun <T> of(list: List<T>): T = list[r.nextInt(list.size)]

        fun label(maxWords: Int = 4): String = (1..(1 + int(maxWords))).joinToString(" ") { of(words) }
        fun optLabel(maxWords: Int = 4): String? = if (chance(50)) label(maxWords) else null

        fun note(): String? = when {
            chance(60) -> null
            chance(15) -> (label() + " ").repeat(1 + int(12)).trim()
            else -> label()
        }

        /** null, year, year-month, leap day or a full date — always a date that exists. */
        fun date(): String? {
            val year = 1900 + int(200)
            return when (int(7)) {
                0 -> null
                1 -> String.format(Locale.ROOT, "%04d", year)
                2 -> String.format(Locale.ROOT, "%04d-%02d", year, 1 + int(12))
                3 -> "2024-02-29"
                else -> String.format(Locale.ROOT, "%04d-%02d-%02d", year, 1 + int(12), 1 + int(28))
            }
        }

        fun fullDate(): String = String.format(Locale.ROOT, "%04d-%02d-%02d", 1900 + int(126), 1 + int(12), 1 + int(28))

        /**
         * A coded entry, a free-text entry, a code alone, a code with a label and a different
         * typed text, or a code no catalog knows. [plain] = codes that have no official display
         * (a code alone would come back with its official term as display).
         */
        fun coded(codes: List<String>, plain: List<String>, system: String): Coded = when (int(10)) {
            0, 1, 2 -> Coded(null, system, null, label())
            3 -> Coded(of(plain), system, null, null)
            4 -> label().let { d -> Coded(of(codes), system, d, "$d (typed)") }
            5 -> Coded("ZZ-UNKNOWN-${int(1000)}", "urn:oid:1.2.3.4.5", label(), null)
            else -> Coded(of(codes), system, label(), null)
        }
    }

    private val vaccineCodes = listOf("871876003", "871803007", "836378001", "1119349007", "836374004")
    private val vaccinePlain = listOf("1119349007", "836374004")
    private val procedureCodes = listOf("80146002", "73761001", "232717009", "11466000")
    private val deviceCodes = listOf("14106009", "6012004", "303619002")
    private val resultCodes = listOf("2823-3", "718-7", "4548-4", "2345-7", "777-3")
    private val resultPlain = listOf("2345-7", "777-3")
    private val conditionCodes = listOf("84114007", "433144002", "59621000", "22298006", "56717001", "15188001")
    private val numbers = listOf("0", "4.1", "120", "-0.5", "37.25", "250000", "0.001", "11.8", "5.9")
    private val units = listOf<String?>("mmol/L", "mg/dL", "%", "g/dL", null)
    private val severities = IpsConditionSeverity.ALL + listOf<String?>(null)

    private fun immunization(g: Gen, id: String): IpsImmunization {
        val c = g.coded(vaccineCodes, vaccinePlain, IpsCodeSystems.SNOMED)
        val dose = if (g.chance(40)) null else 1 + g.int(5)
        val series = if (dose == null || g.chance(50)) null else dose + g.int(3)
        return IpsImmunization(
            id = id, code = c.code, system = c.system, display = c.display, text = c.text, date = g.date(),
            status = g.of(IpsImmunizationStatus.ALL), doseNumber = dose, seriesDoses = series,
            lotNumber = g.optLabel(1), manufacturer = g.optLabel(2), performer = g.optLabel(), note = g.note(),
        )
    }

    private fun procedure(g: Gen, id: String): IpsProcedure {
        val c = g.coded(procedureCodes, procedureCodes, IpsCodeSystems.SNOMED)
        return IpsProcedure(
            id = id, code = c.code, system = c.system, display = c.display, text = c.text, date = g.date(),
            status = g.of(IpsProcedureStatus.ALL), bodySite = g.optLabel(2), outcome = g.optLabel(),
            performer = g.optLabel(), location = g.optLabel(), note = g.note(),
        )
    }

    private fun device(g: Gen, id: String): IpsDevice {
        val c = g.coded(deviceCodes, deviceCodes, IpsCodeSystems.SNOMED)
        return IpsDevice(
            id = id, code = c.code, system = c.system, display = c.display, text = c.text,
            udi = if (g.chance(40)) "(01)0064316900722${g.int(10)}(21)SN${g.int(100000)}" else null,
            manufacturer = g.optLabel(2), model = g.optLabel(2), serial = g.optLabel(1), date = g.date(),
            status = g.of(IpsDeviceStatus.ALL), bodySite = g.optLabel(2), note = g.note(),
        )
    }

    private fun result(g: Gen, id: String): IpsResult {
        val c = g.coded(resultCodes, resultPlain, IpsCodeSystems.LOINC)
        val base = IpsResult(
            id = id, code = c.code, system = c.system, display = c.display, text = c.text, date = g.date(),
            status = g.of(IpsResultStatus.ALL), category = g.of(IpsResultCategory.ALL),
            interpretation = g.of(IpsResultInterpretation.ALL + listOf<String?>(null)),
            performer = g.optLabel(), note = g.note(),
        )
        return when (g.int(3)) {
            0 -> base.copy(
                value = g.of(numbers), unit = g.of(units),
                refLow = if (g.chance(50)) g.of(numbers) else null, refHigh = if (g.chance(50)) g.of(numbers) else null,
            )
            1 -> base.copy(
                valueCode = g.of(listOf("371244009", "260385009", "10828004")),
                valueCodeSystem = g.of(listOf(IpsCodeSystems.SNOMED, IpsCodeSystems.LOINC)), valueDisplay = g.optLabel(2),
            )
            else -> base.copy(valueText = g.label())
        }
    }

    private fun pastProblem(g: Gen, id: String): IpsPastProblem {
        val c = g.coded(conditionCodes, conditionCodes, IpsCodeSystems.SNOMED)
        return IpsPastProblem(
            id = id, code = c.code, system = c.system, display = c.display, text = c.text, onset = g.date(), abatement = g.date(),
            clinicalStatus = g.of(IpsPastProblemStatus.ALL), severity = g.of(severities), note = g.note(),
        )
    }

    private fun problem(g: Gen, id: String): IpsProblem {
        val c = g.coded(conditionCodes, conditionCodes, IpsCodeSystems.SNOMED)
        return IpsProblem(
            id = id, code = c.code, system = c.system, display = c.display, text = c.text, onset = g.date(),
            clinicalStatus = g.of(IpsProblemStatus.ALL), severity = g.of(severities), note = g.note(),
        )
    }

    private fun functional(g: Gen, id: String): IpsFunctional {
        val c = g.coded(conditionCodes, conditionCodes, IpsCodeSystems.SNOMED)
        return IpsFunctional(
            id = id, code = c.code, system = c.system, display = c.display, text = c.text, onset = g.date(),
            clinicalStatus = g.of(IpsFunctionalStatus.ALL), note = g.note(),
        )
    }

    private fun pregnancy(g: Gen, id: String): IpsPregnancyObs = when (g.int(3)) {
        0 -> IpsPregnancyObs(
            id = id, code = IpsPregnancyCodes.STATUS,
            valueCode = g.of(IpsPregnancyCodes.STATUS_ANSWERS.keys.toList() + listOf<String?>(null)), date = g.date(), note = g.note(),
        )
        1 -> IpsPregnancyObs(id = id, code = g.of(IpsPregnancyCodes.EDD_METHODS.keys.toList()), valueDate = g.date(), date = g.date(), note = g.note())
        else -> IpsPregnancyObs(
            id = id, code = g.of(IpsPregnancyCodes.OUTCOMES.keys.toList()),
            count = g.of(listOf<Int?>(null, 0, 1, 2, 12, 99)), date = g.date(), note = g.note(),
        )
    }

    private fun allergy(g: Gen): JAllergy = JAllergy(
        c = if (g.chance(15)) "ZZ-UNKNOWN-${g.int(1000)}" else g.of(listOf("91936005", "91935009", "300916003", "417532002", "419263009")),
        s = g.of(listOf<String?>("H", "L", "U", null)), st = g.of(listOf<String?>("A", "I", "R", null)),
        d = g.note(), m = g.optLabel(), displayLabel = if (g.chance(85)) g.label() else null,
        codeSystem = if (g.chance(70)) IpsCodeSystems.SNOMED else null,
        category = g.of(listOf<String?>("food", "medication", "environment", "biologic", null)), onset = g.date(),
    )

    private fun medication(g: Gen): JMedication = JMedication(
        c = if (g.chance(15)) "ZZ-UNKNOWN-${g.int(1000)}" else g.of(listOf("B01AA03", "M01AE01", "C07AB07", "R03AC02", "C03CA01", "R06AX26")),
        t = g.optLabel(), r = g.of(listOf<String?>("O", "I", "T", "S", "H", "rectal", null)),
        v = g.of(listOf<String?>("5", "0,5", "2.5", "1/2", "1,000", "1e3", "400", null)), u = g.of(listOf<String?>("mg", "tab", "mL", "puff", null)),
        displayLabel = if (g.chance(85)) g.label() else null, codeSystem = if (g.chance(70)) IpsCodeSystems.ATC else null,
        status = g.of(listOf<String?>("active", "stopped", "on-hold", "completed", "paused-by-me", " Active ", null)),
        effective = g.date(),
    )

    private fun contact(g: Gen): JContact = JContact(
        n = g.label(2), r = g.of(listOf<String?>("SPS", "MTH", "friend", "Dr", null)),
        p = if (g.chance(70)) "+32 478 12 34 5${g.int(10)}" else null, e = if (g.chance(30)) "c${g.int(100)}@example.org" else null,
    )

    /** Profile number [n] of the campaign : the same on every run. Every 10th one is a very large record. */
    private fun profile(n: Int): ProfileFixtures.Stored {
        val g = Gen(seed + n)
        val heavy = n % 10 == 9
        fun size(max: Int, heavyMax: Int): Int = if (heavy) heavyMax / 2 + g.int(heavyMax / 2 + 1) else g.int(max + 1)
        val sid = "qa_rnd_$n"
        val patient = JPatient(
            gn = g.label(2), fn = g.optLabel(2), gs = g.of(listOf<String?>("M", "F", "O", "U", null)),
            bd = if (g.chance(85)) g.fullDate() else null,
            bt = g.of(listOf<String?>("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-", "0+", "?", "", null)),
            adr = g.optLabel(), tel = if (g.chance(50)) "+32 478 45 45 4${g.int(10)}" else null,
            eml = if (g.chance(40)) "p${g.int(100)}@example.org" else null, idn = if (g.chance(40)) "BE-${g.int(1000000)}" else null,
            lang = g.of(listOf<String?>("fr-BE", "ja-JP", "ar", "en-US", null)),
            ct = (0 until size(3, 6)).map { contact(g) },
        )
        val raw = JemmaProfileJ(
            j = "1.2", sid = sid, p = patient,
            al = (0 until size(6, 14)).map { allergy(g) },
            md = (0 until size(12, 50)).map { medication(g) },
        )
        val native = IpsNativePillars(
            immunizations = (0 until size(10, 30)).map { immunization(g, "im-$n-$it") },
            procedures = (0 until size(5, 12)).map { procedure(g, "pr-$n-$it") },
            devices = (0 until size(4, 8)).map { device(g, "dv-$n-$it") },
            results = (0 until size(8, 24)).map { result(g, "rs-$n-$it") },
            pastProblems = (0 until size(5, 16)).map { pastProblem(g, "ph-$n-$it") },
            problems = (0 until size(5, 24)).map { problem(g, "cn-$n-$it") },
            pregnancy = (0 until size(6, 12)).map { pregnancy(g, "pg-$n-$it") },
            functional = (0 until size(4, 8)).map { functional(g, "fs-$n-$it") },
        )
        return ProfileFixtures.store(sid, raw, native)
    }

    // ── invariants ──────────────────────────────────────────────────────────

    @Test
    fun `UC-STO-005 random profiles - the Bundle is a valid document that reads back to the same entries`() {
        for (n in 0 until profileCount) {
            val ctx = "profile($n)"
            val stored = profile(n)
            val json = ProfileFixtures.bundleJson(stored)

            // valid JSON, document, Composition first, then the Patient
            val o = JSONObject(json)
            assertEquals(ctx, "Bundle", o.getString("resourceType"))
            assertEquals(ctx, "document", o.getString("type"))
            val entries = o.getJSONArray("entry")
            assertEquals(ctx, "Composition", entries.getJSONObject(0).getJSONObject("resource").getString("resourceType"))
            assertEquals(ctx, "Patient", entries.getJSONObject(1).getJSONObject("resource").getString("resourceType"))

            val bundle = ProfileFixtures.parse(json)
            val back = IpsFhirCodec.nativeOf(bundle)
            assertEquals("$ctx 💉", stored.native.immunizations, back.immunizations)
            assertEquals("$ctx 🏥", stored.native.procedures, back.procedures)
            assertEquals("$ctx 📟", stored.native.devices, back.devices)
            assertEquals("$ctx 🧪", stored.native.results, back.results)
            assertEquals("$ctx 📜", stored.native.pastProblems, back.pastProblems)
            assertEquals("$ctx 🩺", stored.native.problems, back.problems)
            assertEquals("$ctx 🤰", stored.native.pregnancy, back.pregnancy)
            assertEquals("$ctx ♿", stored.native.functional, back.functional)

            // section entry counts = model counts ; every reference resolves ; no entry in two sections
            val counts: Map<String, Int> = linkedMapOf(
                "48765-2" to stored.j.al.size,
                "10160-0" to stored.j.md.size,
                IpsFhirCodec.LOINC_SECTION_PROBLEMS to stored.native.problems.size,
                IpsFhirCodec.LOINC_SECTION_PAST_ILLNESS to stored.native.pastProblems.size,
                IpsFhirCodec.LOINC_SECTION_PREGNANCY to stored.native.pregnancy.size,
                IpsFhirCodec.LOINC_SECTION_FUNCTIONAL to stored.native.functional.size,
                IpsFhirCodec.LOINC_SECTION_IMMUNIZATIONS to stored.native.immunizations.size,
                IpsFhirCodec.LOINC_SECTION_PROCEDURES to stored.native.procedures.size,
                IpsFhirCodec.LOINC_SECTION_DEVICES to stored.native.devices.size,
                IpsFhirCodec.LOINC_SECTION_RESULTS to stored.native.results.size,
            )
            val fullUrls = bundle.entry.mapNotNull { it.fullUrl?.value }
            assertEquals("$ctx fullUrls are unique", fullUrls.size, fullUrls.toSet().size)
            val seen = HashSet<String>()
            for ((loinc, expected) in counts) {
                val refs = ProfileFixtures.sectionRefs(bundle, loinc)
                assertEquals("$ctx section $loinc", expected, refs.size)
                assertEquals("$ctx section $loinc present", expected > 0, ProfileFixtures.hasSection(bundle, loinc))
                assertTrue("$ctx section $loinc resolves", fullUrls.containsAll(refs))
                for (ref in refs) assertTrue("$ctx $ref listed twice", seen.add(ref))
            }
            val entryCount = 2 + stored.j.al.size + 2 * stored.j.md.size + counts.values.sum() -
                stored.j.al.size - stored.j.md.size + stored.native.devices.size
            assertEquals("$ctx entry count", entryCount, bundle.entry.size)

            // a rebuild of the same profile gives the same document ids (UC-STO-020)
            val again = ProfileFixtures.parse(ProfileFixtures.bundleJson(ProfileFixtures.store(stored.j.sid!!, stored.j, back)))
            assertEquals("$ctx stable fullUrls", fullUrls, again.entry.mapNotNull { it.fullUrl?.value })
        }
    }

    @Test
    fun `UC-IMP-001 random profiles - the _j file reads back identical and an import re-exports the same _j`() {
        for (n in 0 until profileCount) {
            val ctx = "profile($n)"
            val j = profile(n).j
            val json = ProfileFixtures.adapter.toJson(j)
            JSONObject(json)   // valid JSON
            val reread = ProfileFixtures.adapter.fromJson(json)!!
            assertEquals("$ctx `_j` file", j, reread)

            // import : native pillars rebuilt from `_j` alone, stored as a Bundle, read back, projected again
            val imported = IpsFhirCodec.nativeOf(ProfileFixtures.parse(ProfileFixtures.bundleJsonFromProjection(reread)))
            val again = ProfileFixtures.project(reread, j.sid!!, imported)
            assertEquals("$ctx 💉", j.im, again.im)
            assertEquals("$ctx 🏥", j.pr, again.pr)
            assertEquals("$ctx 📟", j.dv, again.dv)
            assertEquals("$ctx 🧪", j.rs, again.rs)
            assertEquals("$ctx 📜", j.ph, again.ph)
            assertEquals("$ctx 🩺", j.cn, again.cn)
            assertEquals("$ctx 🤰", j.pg, again.pg)
            assertEquals("$ctx ♿", j.fs, again.fs)
        }
    }

    @Test
    fun `UC-QRF-002 random profiles - the compact payload survives pruning and the multi-frame split`() {
        var multiFrame = 0
        for (n in 0 until profileCount) {
            val ctx = "profile($n)"
            val j = profile(n).j
            val pruned = JemmaPayloadPruner.pruneToJson(j, ProfileFixtures.adapter)
            assertEquals("$ctx nothing filled is lost by pruning", j, ProfileFixtures.adapter.fromJson(pruned))

            val frames = JemmaQrFrameSplitter.split(pruned)
            if (frames.size == 1) {
                assertEquals(ctx, pruned, frames.single())
                assertTrue(ctx, ProfileFixtures.utf8(pruned) <= JemmaQrFrameSplitter.QR_MAX_SINGLE)
            } else {
                multiFrame++
                assertEquals("$ctx ${frames.size} frames", pruned, JemmaQrFrameAssembler.join(frames))
                assertEquals("$ctx any scan order", pruned, JemmaQrFrameAssembler.join(frames.reversed()))
                for (f in frames) {
                    assertTrue("$ctx frame of ${ProfileFixtures.utf8(f)} bytes", ProfileFixtures.utf8(f) <= JemmaQrFrameSplitter.QR_MAX_SINGLE)
                    assertTrue(ctx, JemmaQrFrameAssembler.isFrame(f))
                }
                assertEquals("$ctx a missing frame is detected", null, JemmaQrFrameAssembler.join(frames.drop(1)))
            }
        }
        assertTrue("the campaign must include large records ($multiFrame multi-frame payloads)", multiFrame >= profileCount / 10)
    }

    @Test
    fun `UC-QRT-008 random profiles - the text QR fits one frame and never loses an entry in silence`() {
        var cut = 0
        for (n in 0 until profileCount) {
            val stored = profile(n)
            for (lang in listOf(Lang.EN, Lang.JA, Lang.values()[n % Lang.values().size]).distinct()) {
                val ctx = "profile($n) $lang"
                val h = ProfileFixtures.hydrated(stored.j, uiLang = lang.isoCode)
                val text = JemmaTextPayloadBuilder.build(h, lang)

                // one frame, in UTF-8 bytes (UC-QRT-010 / 011)
                assertTrue("$ctx ${ProfileFixtures.utf8(text)} bytes", ProfileFixtures.utf8(text) <= JemmaTextPayloadBuilder.MAX_BYTES)
                assertEquals(ctx, 1, JemmaQrFrameSplitter.split(text).size)
                assertEquals("$ctx deterministic", text, JemmaTextPayloadBuilder.build(h, lang))
                assertTrue(ctx, text.startsWith(JemmaTranslations.getLabel(lang, "header") + TextQrProbe.EOL))
                assertTrue(ctx, text.endsWith(JemmaTranslations.getLabel(lang, "footer") + TextQrProbe.EOL))
                assertFalse("$ctx null literal", text.contains("null"))
                assertTrue("$ctx name", text.contains(stored.j.p!!.gn!!))

                // every entry printed or announced ; least important sections go first (UC-QRT-009)
                TextQrProbe.assertAccounted(ctx, text, h)
                TextQrProbe.assertPriority(ctx, text, h)

                // without a cap everything is printed, and the capped text only holds whole lines of it
                val full = JemmaTextPayloadBuilder.build(h, lang, 10_000_000)
                assertFalse("$ctx full text has no marker", full.contains(TextQrProbe.MARK))
                TextQrProbe.assertAccounted("$ctx full", full, h)
                val fullLines = full.split(TextQrProbe.EOL).toHashSet()
                for (line in text.split(TextQrProbe.EOL)) {
                    if (line.startsWith(TextQrProbe.MARK) || line.startsWith("  " + TextQrProbe.MARK)) continue
                    assertTrue("$ctx line cut or invented : $line", line in fullLines)
                }
                if (text.contains(TextQrProbe.MARK)) cut++ else assertEquals("$ctx untouched when it fits", full, text)
            }
        }
        assertTrue("the campaign must include records that overflow ($cut)", cut >= profileCount / 10)
    }

    @Test
    fun `UC-IMP-007 the generator itself is deterministic`() {
        for (n in listOf(0, 9, 57, profileCount - 1)) {
            assertEquals(profile(n).j, profile(n).j)
        }
        assertFalse(profile(0).j == profile(1).j)
    }
}
