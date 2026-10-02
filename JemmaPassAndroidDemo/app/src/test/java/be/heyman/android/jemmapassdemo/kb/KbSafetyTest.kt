/*
 * KbSafetyTest.kt — UC-SAFE-KB : a safety cross-check that did not run (knowledge base
 * absent, not downloaded, failed to open / query, drug unknown to the KB) must never be
 * reported as "checked, nothing found". Covers the three readers of the result :
 * CrossCheckResult (forms / scan), HydratedProfile (profile display / QR) and the LLM
 * tool answer (JemmaTools.crossCheckResultToMap → KbSafety.crossCheckSafetyFields).
 */
package be.heyman.android.jemmapassdemo.kb

import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KbSafetyTest {

    // ── fixtures ────────────────────────────────────────────────────────────

    private fun ddiHit(sev: CrossSeverity = CrossSeverity.MAJOR) = DdiHit(
        existingMedDisplay = "Warfarin", existingMedAtc = "B01AA03",
        candidateAtc = "M01AE01", candidateDisplay = "Ibuprofen", severity = sev,
        mechanism = null, description = null, management = null, alternativeAtc = null, queryDurationMs = 1L,
    )

    private fun result(
        atc: String = "M01AE01",
        ddi: List<DdiHit> = emptyList(),
        checks: KbCheckReport = KbCheckReport(),
    ) = CrossCheckResult(
        candidateAtc = atc, candidateDisplay = "Ibuprofen",
        allergyHits = emptyList(), ddiHits = ddi, drugDiseaseHits = emptyList(),
        totalDurationMs = 3L, checks = checks,
    )

    private fun med(name: String) = HydratedMedication(
        raw = JMedication(displayLabel = name), resolvedConcept = null, displayLocalized = name,
        timing = null, doseValue = null, doseUnit = null, route = MedicationRoute.UNKNOWN,
        atcCode = null, allAtcCodes = emptyList(), rxnormCui = null,
    )

    private fun ddiAlert(sev: DDIResult.Severity, note: String? = null) = DdiAlert(
        medicationA = med("Warfarin"), medicationB = med("Ibuprofen"), severity = sev,
        mechanism = null, description = note, management = null, alternativeAtc = null, fuzzyMatch = false,
    )

    private fun hydrated(
        ddi: List<DdiAlert> = emptyList(),
        checks: KbCheckReport? = null,
    ): HydratedProfile {
        val raw = JemmaProfileJ(j = "1.2", sid = "x")
        return if (checks == null) {
            HydratedProfile(
                raw = raw, uiLang = "en", allergies = emptyList(), medications = emptyList(),
                conditions = emptyList(), ddiAlerts = ddi, allergyAlerts = emptyList(),
                drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
            )
        } else {
            HydratedProfile(
                raw = raw, uiLang = "en", allergies = emptyList(), medications = emptyList(),
                conditions = emptyList(), ddiAlerts = ddi, allergyAlerts = emptyList(),
                drugDiseaseAlerts = emptyList(), hydrationMs = 0L, checks = checks,
            )
        }
    }

    // ── UC-SAFE-KB-01 : KB absent → not checked ─────────────────────────────

    @Test
    fun ucSafeKb01_kbAbsent_pillarIsNotChecked() {
        assertEquals(KbCheckStatus.KB_UNAVAILABLE, KbSafety.pillarStatus(kbAvailable = false, itemsToCheck = 3))
        assertEquals(KbSafetyVerdict.NOT_CHECKED, KbSafety.verdict(KbCheckStatus.KB_UNAVAILABLE, totalHits = 0))
    }

    @Test
    fun ucSafeKb01_kbAbsent_crossCheckResultIsNeverClean() {
        // what KbCrossCheck.checkOneDrugAgainstProfile returns when the KB cannot be opened
        val r = result(atc = "", checks = KbCheckReport.all(KbCheckStatus.KB_UNAVAILABLE))
        assertEquals(0, r.totalHits)
        assertFalse(r.isClean)
        assertFalse(r.checked)
        assertFalse(r.kbAvailable)
        assertEquals(KbSafetyVerdict.NOT_CHECKED, r.verdict)
    }

    @Test
    fun ucSafeKb01_kbAbsent_onOnePillarOnly_isStillNotClean() {
        // ATC fast path : candidate known, but the DDI pillar could not reach the KB
        val r = result(checks = KbCheckReport(ddi = KbCheckStatus.KB_UNAVAILABLE))
        assertFalse(r.isClean)
        assertFalse(r.kbAvailable)
        assertEquals(KbSafetyVerdict.NOT_CHECKED, r.verdict)
    }

    @Test
    fun ucSafeKb01_kbAbsent_llmToolAnswerSaysNotChecked() {
        val f = KbSafety.crossCheckSafetyFields(result(atc = "", checks = KbCheckReport.all(KbCheckStatus.KB_UNAVAILABLE)))
        assertEquals(false, f["ok"])
        assertEquals(false, f["is_clean"])
        assertEquals(false, f["checked"])
        assertEquals(false, f["kb_available"])
        assertEquals(KbSafety.REASON_KB_UNAVAILABLE, f["reason"])
        assertEquals("NOT_CHECKED", f["verdict"])
        assertEquals("KB_UNAVAILABLE", f["ddi_check"])
        assertTrue((f["warning"] as String).startsWith("NOT CHECKED"))
    }

    @Test
    fun ucSafeKb01_kbAbsent_hydratedProfileIsNotClean() {
        val h = hydrated(checks = KbCheckReport.all(KbCheckStatus.KB_UNAVAILABLE))
        assertFalse(h.hasAlerts)
        assertFalse("no alert must not read as clean", h.isClean)
        assertFalse(h.kbAvailable)
        assertEquals(KbSafetyVerdict.NOT_CHECKED, h.safetyVerdict)
    }

    // ── UC-SAFE-KB-02 : KB present, no hit → clean ──────────────────────────

    @Test
    fun ucSafeKb02_kbPresentNoHit_isClean() {
        assertEquals(KbCheckStatus.CHECKED, KbSafety.pillarStatus(kbAvailable = true, itemsToCheck = 3))
        val r = result()
        assertTrue(r.isClean)
        assertTrue(r.checked)
        assertTrue(r.kbAvailable)
        assertEquals(KbSafetyVerdict.CLEAN, r.verdict)

        val f = KbSafety.crossCheckSafetyFields(r)
        assertEquals(true, f["ok"])
        assertEquals(true, f["is_clean"])
        assertEquals(true, f["checked"])
        assertEquals("CLEAN", f["verdict"])
        assertEquals("", f["warning"])
        assertEquals("", f["reason"])
    }

    @Test
    fun ucSafeKb02_kbPresentNoHit_hydratedProfileIsClean() {
        val h = hydrated()                      // legacy constructor : defaults to "all checked"
        assertTrue(h.isClean)
        assertTrue(h.kbAvailable)
        assertEquals(KbSafetyVerdict.CLEAN, h.safetyVerdict)
    }

    @Test
    fun ucSafeKb02_nothingToCheck_isCleanEvenWithoutKb() {
        // an empty pillar (no medication on the profile) cannot collide with anything
        assertEquals(KbCheckStatus.CHECKED, KbSafety.pillarStatus(kbAvailable = false, itemsToCheck = 0))
    }

    // ── UC-SAFE-KB-03 : KB present, hit → alert ─────────────────────────────

    @Test
    fun ucSafeKb03_kbPresentWithHit_isAlert() {
        val r = result(ddi = listOf(ddiHit()))
        assertFalse(r.isClean)
        assertTrue(r.hasMajor)
        assertEquals(KbSafetyVerdict.ALERT, r.verdict)
        val f = KbSafety.crossCheckSafetyFields(r)
        assertEquals(true, f["ok"])
        assertEquals(false, f["is_clean"])
        assertEquals("ALERT", f["verdict"])

        val h = hydrated(ddi = listOf(ddiAlert(DDIResult.Severity.MAJOR)))
        assertTrue(h.hasAlerts)
        assertFalse(h.isClean)
        assertEquals(KbSafetyVerdict.ALERT, h.safetyVerdict)
    }

    @Test
    fun ucSafeKb03_hitFoundOnPartialCheck_staysAnAlertAndKeepsTheWarning() {
        // heuristic allergy hit found while the KB is down : alert, and still flagged unverified
        val r = result(ddi = listOf(ddiHit(CrossSeverity.MODERATE)), checks = KbCheckReport(allergy = KbCheckStatus.KB_UNAVAILABLE))
        assertEquals(KbSafetyVerdict.ALERT, r.verdict)
        assertFalse(r.checked)
        assertTrue((KbSafety.crossCheckSafetyFields(r)["warning"] as String).isNotEmpty())
    }

    // ── UC-SAFE-KB-04 : partial check / unknown drug → not clean ────────────

    @Test
    fun ucSafeKb04_unverifiedEntry_makesTheCheckIncomplete() {
        // one profile medication unknown to the KB, or one failed query
        assertEquals(KbCheckStatus.INCOMPLETE, KbSafety.pillarStatus(kbAvailable = true, itemsToCheck = 3, itemsUnverified = 1))
        val r = result(checks = KbCheckReport(ddi = KbCheckStatus.INCOMPLETE))
        assertFalse(r.isClean)
        assertTrue(r.kbAvailable)
        assertEquals(KbSafetyVerdict.INCOMPLETE, r.verdict)
        val f = KbSafety.crossCheckSafetyFields(r)
        assertEquals(true, f["ok"])
        assertEquals(false, f["is_clean"])
        assertEquals(false, f["checked"])
        assertTrue((f["warning"] as String).startsWith("INCOMPLETE"))

        assertFalse(hydrated(checks = KbCheckReport(drugDisease = KbCheckStatus.INCOMPLETE)).isClean)
    }

    @Test
    fun ucSafeKb04_candidateUnknownToKb_isNotCheckedEvenWithLegacyDefaults() {
        val r = result(atc = "")                // KB up, drug not found, default checks
        assertFalse(r.isClean)
        assertEquals(KbSafetyVerdict.NOT_CHECKED, r.verdict)
        val f = KbSafety.crossCheckSafetyFields(r)
        assertEquals(false, f["ok"])
        assertEquals(KbSafety.REASON_CANDIDATE_NOT_RESOLVED, f["reason"])
        assertEquals(true, f["kb_available"])
    }

    @Test
    fun ucSafeKb04_worstStatusWins() {
        assertEquals(KbCheckStatus.CHECKED, KbSafety.worst())
        assertEquals(
            KbCheckStatus.KB_UNAVAILABLE,
            KbSafety.worst(KbCheckStatus.CHECKED, KbCheckStatus.KB_UNAVAILABLE, KbCheckStatus.INCOMPLETE),
        )
        assertEquals(KbCheckStatus.INCOMPLETE, KbCheckReport(allergy = KbCheckStatus.INCOMPLETE).overall)
        assertTrue(KbCheckReport().fullyChecked)
    }

    // ── UC-SAFE-KB-05 : several KB rows for one drug pair → most severe kept ─

    @Test
    fun ucSafeKb05_mostSevereRowWins_whateverTheRowOrder() {
        val moderateFirst = listOf(
            ddiAlert(DDIResult.Severity.MODERATE, "moderate"),
            ddiAlert(DDIResult.Severity.MAJOR, "major"),
            ddiAlert(DDIResult.Severity.MINOR, "minor"),
        )
        assertEquals("major", KbSafety.mostSevere(moderateFirst) { it.severity }?.description)
        assertEquals("major", KbSafety.mostSevere(moderateFirst.reversed()) { it.severity }?.description)
        assertNull(KbSafety.mostSevere(emptyList<DdiAlert>()) { it.severity })
    }

    @Test
    fun ucSafeKb05_severityRankOrder() {
        val ranked = DDIResult.Severity.values().sortedBy { KbSafety.ddiSeverityRank(it) }
        assertEquals(
            listOf(
                DDIResult.Severity.MAJOR, DDIResult.Severity.MODERATE, DDIResult.Severity.MINOR,
                DDIResult.Severity.UNKNOWN, DDIResult.Severity.NONE,
            ),
            ranked,
        )
    }
}
