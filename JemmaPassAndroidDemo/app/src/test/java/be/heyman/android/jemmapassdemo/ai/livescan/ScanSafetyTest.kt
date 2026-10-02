/*
 * ScanSafetyTest.kt — UC-SAFE-SCAN : the live-scan verdict (banner colour + spoken
 * sentence) may say "no interaction" only when the KB verdict is CLEAN. A cross-check
 * that threw, had no knowledge base, did not recognise the drug or only partly ran is
 * told as "could not be checked — ask a pharmacist or a doctor".
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import be.heyman.android.jemmapassdemo.kb.CrossCheckResult
import be.heyman.android.jemmapassdemo.kb.KbCheckReport
import be.heyman.android.jemmapassdemo.kb.KbCheckStatus
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ScanSafetyTest {

    // ── fixtures ────────────────────────────────────────────────────────────

    private fun kbResult(
        atc: String = "M01AE01",
        checks: KbCheckReport = KbCheckReport(),
    ) = CrossCheckResult(
        candidateAtc = atc, candidateDisplay = "Ibuprofen",
        allergyHits = emptyList(), ddiHits = emptyList(), drugDiseaseHits = emptyList(),
        totalDurationMs = 1L, checks = checks,
    )

    /** Mirrors CrossCheckOrchestrator.adaptToReport for a result without / with unified hits. */
    private fun report(
        raw: CrossCheckResult,
        ddi: List<DdiHit> = emptyList(),
        verdict: KbSafetyVerdict = if (ddi.isNotEmpty()) KbSafetyVerdict.ALERT else raw.verdict,
    ): CrossCheckReport {
        val max = ddi.map { it.severity }.maxByOrNull { it.ordinal }
        return CrossCheckReport(
            drugAtc = "M01AE01", drugName = "Ibuprofen",
            overall = ScanSafety.displaySeverity(verdict, max, raw.checked),
            allergyHits = emptyList(), ddiHits = ddi, conditionHits = emptyList(),
            verdict = verdict, fullyChecked = raw.checked,
        )
    }

    private fun ddi(sev: Severity) = DdiHit(
        withDrugAtc = "B01AA03", withDrugName = "Warfarin", severity = sev, mechanism = null, description = null,
    )

    // ── UC-SAFE-SCAN-01 : exception → not checked ───────────────────────────

    @Test
    fun ucSafeScan01_exceptionFallback_isNotCheckedNeverClean() {
        val raw = ScanSafety.notCheckedResult("M01AE01", "Ibuprofen")
        assertEquals(KbSafetyVerdict.NOT_CHECKED, raw.verdict)
        assertFalse(raw.isClean)
        assertFalse(raw.checked)
        assertFalse(raw.kbAvailable)
    }

    @Test
    fun ucSafeScan01_exceptionFallback_speaksNotCheckedAndIsNotGreen() {
        val r = report(ScanSafety.notCheckedResult("M01AE01", "Ibuprofen"))
        assertEquals(ScanSafety.Phrase.NOT_CHECKED, ScanSafety.phrasePlan(r).phrase)
        assertNotEquals(Severity.NONE, r.overall)
        assertEquals(Severity.MODERATE, r.overall)
        assertFalse(r.isClean)
    }

    @Test
    fun ucSafeScan01_reportBuiltWithoutVerdict_defaultsToNotChecked() {
        val legacy = CrossCheckReport(
            drugAtc = "M01AE01", drugName = "Ibuprofen", overall = Severity.NONE,
            allergyHits = emptyList(), ddiHits = emptyList(), conditionHits = emptyList(),
        )
        assertEquals(KbSafetyVerdict.NOT_CHECKED, legacy.verdict)
        assertEquals(ScanSafety.Phrase.NOT_CHECKED, ScanSafety.phrasePlan(legacy).phrase)

        val legacyWithHit = legacy.copy(ddiHits = listOf(ddi(Severity.MAJOR)), verdict = KbSafetyVerdict.ALERT)
        assertEquals(ScanSafety.Phrase.MAJOR_DDI, ScanSafety.phrasePlan(legacyWithHit).phrase)
    }

    @Test
    fun ucSafeScan01_kbUnavailableOrUnknownDrug_isNotChecked() {
        val noKb = report(kbResult(atc = "", checks = KbCheckReport.all(KbCheckStatus.KB_UNAVAILABLE)))
        assertEquals(ScanSafety.Phrase.NOT_CHECKED, ScanSafety.phrasePlan(noKb).phrase)
        assertNotEquals(Severity.NONE, noKb.overall)

        val unknownDrug = report(kbResult(atc = "", checks = KbCheckReport.all(KbCheckStatus.INCOMPLETE)))
        assertEquals(ScanSafety.Phrase.NOT_CHECKED, ScanSafety.phrasePlan(unknownDrug).phrase)
        assertNotEquals(Severity.NONE, unknownDrug.overall)
    }

    // ── UC-SAFE-SCAN-02 : incomplete ────────────────────────────────────────

    @Test
    fun ucSafeScan02_incompleteWithoutHit_speaksIncompleteAndIsNotGreen() {
        val r = report(kbResult(checks = KbCheckReport(ddi = KbCheckStatus.INCOMPLETE)))
        assertEquals(KbSafetyVerdict.INCOMPLETE, r.verdict)
        assertEquals(ScanSafety.PhrasePlan(ScanSafety.Phrase.INCOMPLETE), ScanSafety.phrasePlan(r))
        assertEquals(Severity.MODERATE, r.overall)
    }

    @Test
    fun ucSafeScan02_hitOnIncompleteCheck_keepsHitAndAddsIncompleteNote() {
        val r = report(
            kbResult(checks = KbCheckReport(allergy = KbCheckStatus.KB_UNAVAILABLE)),
            ddi = listOf(ddi(Severity.MINOR)),
        )
        val plan = ScanSafety.phrasePlan(r)
        assertEquals(ScanSafety.Phrase.MINOR, plan.phrase)
        assertEquals("Warfarin", plan.arg)
        assertTrue(plan.appendIncompleteNote)
        // a minor hit on a partial check is never good enough to be a "safe alternative"
        assertEquals(Severity.MODERATE, r.overall)
    }

    // ── UC-SAFE-SCAN-03 : clean ─────────────────────────────────────────────

    @Test
    fun ucSafeScan03_clean_isTheOnlyPathToSafe() {
        val r = report(kbResult())
        assertEquals(KbSafetyVerdict.CLEAN, r.verdict)
        assertTrue(r.isClean)
        assertEquals(Severity.NONE, r.overall)
        assertEquals(ScanSafety.PhrasePlan(ScanSafety.Phrase.SAFE), ScanSafety.phrasePlan(r))
    }

    @Test
    fun ucSafeScan03_noOtherVerdictGivesSafeOrGreen() {
        for (verdict in KbSafetyVerdict.values()) {
            if (verdict == KbSafetyVerdict.CLEAN) continue
            for (fully in listOf(true, false)) {
                assertNotEquals(
                    "verdict=$verdict fully=$fully",
                    Severity.NONE, ScanSafety.displaySeverity(verdict, null, fully),
                )
                assertNotEquals(
                    "verdict=$verdict fully=$fully hit=NONE",
                    Severity.NONE, ScanSafety.displaySeverity(verdict, Severity.NONE, fully),
                )
            }
            val r = CrossCheckReport(
                drugAtc = "X", drugName = "X", overall = Severity.NONE,
                allergyHits = emptyList(), ddiHits = emptyList(), conditionHits = emptyList(),
                verdict = verdict, fullyChecked = false,
            )
            assertNotEquals("verdict=$verdict", ScanSafety.Phrase.SAFE, ScanSafety.phrasePlan(r).phrase)
        }
        // a CLEAN verdict that is not backed by a full check is not green either
        assertNotEquals(Severity.NONE, ScanSafety.displaySeverity(KbSafetyVerdict.CLEAN, null, false))
    }

    // ── UC-SAFE-SCAN-04 : alert ─────────────────────────────────────────────

    @Test
    fun ucSafeScan04_alert_speaksTheWorstHit() {
        val r = report(kbResult(), ddi = listOf(ddi(Severity.MODERATE), ddi(Severity.MAJOR)))
        val plan = ScanSafety.phrasePlan(r)
        assertEquals(ScanSafety.Phrase.MAJOR_DDI, plan.phrase)
        assertEquals("Warfarin", plan.arg)
        assertFalse(plan.appendIncompleteNote)
        assertEquals(Severity.MAJOR, r.overall)
    }

    @Test
    fun ucSafeScan04_alertWithSeverityNoneHit_isNeverSafe() {
        val r = report(kbResult(), ddi = listOf(ddi(Severity.NONE)))
        assertEquals(ScanSafety.Phrase.MINOR, ScanSafety.phrasePlan(r).phrase)
        assertEquals(Severity.MINOR, r.overall)
    }

    // ── UC-SAFE-SCAN-05 : wording of the new sentences ──────────────────────

    @Test
    fun ucSafeScan05_englishWording_saysNotCheckedAndPharmacistOrDoctor() {
        val file = listOf(
            File("src/main/res/values/strings_jemma_scan_safety.xml"),
            File("app/src/main/res/values/strings_jemma_scan_safety.xml"),
            File("JemmaPassAndroidDemo/app/src/main/res/values/strings_jemma_scan_safety.xml"),
        ).firstOrNull { it.isFile }
        assumeTrue("resource file not reachable from the test working directory", file != null)
        val xml = file!!.readText()
        fun text(name: String): String =
            Regex("<string name=\"$name\">(.*?)</string>").find(xml)?.groupValues?.get(1)
                ?: throw AssertionError("missing string $name")

        for (name in listOf("tts_scan_not_checked", "tts_scan_incomplete", "tts_scan_incomplete_note")) {
            val t = text(name).lowercase()
            assertTrue("$name must send to a pharmacist", t.contains("pharmacist"))
            assertTrue("$name must send to a doctor", t.contains("doctor"))
            assertFalse("$name must not say safe-ish 'no interaction'", t.contains("no interaction"))
            assertFalse("$name must not say 'is safe'", t.contains("is safe"))
        }
        assertTrue(text("tts_scan_not_checked").lowercase().contains("could not be done"))
        assertTrue(text("tts_scan_incomplete").lowercase().contains("incomplete"))
    }
}
