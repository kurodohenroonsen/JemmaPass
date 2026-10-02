/*
 * MedScanSafetyTest.kt — UC-SAFE-SCAN : the `checkInteractions` tool answer read by the
 * LLM says severity_overall = "NONE" (rendered as "safe to administer" by the agent
 * prompt) only for a CLEAN verdict. Exception / no knowledge base / unknown drug /
 * partial check → "NOT_CHECKED" / "INCOMPLETE" + "ask a pharmacist or a doctor".
 */
package be.heyman.android.jemmapassdemo.ai.gemma

import be.heyman.android.jemmapassdemo.kb.AllergyCriticality
import be.heyman.android.jemmapassdemo.kb.AllergyHit
import be.heyman.android.jemmapassdemo.kb.CrossCheckResult
import be.heyman.android.jemmapassdemo.kb.CrossSeverity
import be.heyman.android.jemmapassdemo.kb.DdiHit
import be.heyman.android.jemmapassdemo.kb.DrugDiseaseHit
import be.heyman.android.jemmapassdemo.kb.KbCheckStatus
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import be.heyman.android.jemmapassdemo.kb.PillarCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedScanSafetyTest {

    // ── fixtures ────────────────────────────────────────────────────────────

    private fun ddiHit(sev: CrossSeverity) = DdiHit(
        existingMedDisplay = "Warfarin", existingMedAtc = "B01AA03",
        candidateAtc = "M01AE01", candidateDisplay = "Ibuprofen", severity = sev,
        mechanism = null, description = null, management = null, alternativeAtc = null, queryDurationMs = 1L,
    )

    private fun allergyHit() = AllergyHit(
        allergyDisplay = "Penicillin", allergyCode = "91936005", matchedOn = "class:J01C",
        candidateAtc = "J01CR02", candidateDisplay = "Augmentin", criticality = AllergyCriticality.LOW,
    )

    private fun bundle(
        resolved: Boolean = true,
        kb: Boolean = true,
        ancestors: Boolean = true,
        allergy: PillarCheck<AllergyHit> = PillarCheck(emptyList(), KbCheckStatus.CHECKED),
        ddi: PillarCheck<DdiHit> = PillarCheck(emptyList(), KbCheckStatus.CHECKED),
        disease: PillarCheck<DrugDiseaseHit> = PillarCheck(emptyList(), KbCheckStatus.CHECKED),
        allergiesInProfile: Int = 1,
    ): CrossCheckResult = MedScanSafety.bundle(
        atc = "M01AE01", display = "Ibuprofen",
        candidateResolved = resolved, kbAvailable = kb, ancestorsResolved = ancestors,
        allergy = allergy, ddi = ddi, disease = disease, allergiesInProfile = allergiesInProfile,
    )

    private fun assertTellsToAskPharmacistOrDoctor(text: String) {
        val t = text.lowercase()
        assertTrue(text, t.contains("pharmacist"))
        assertTrue(text, t.contains("doctor"))
        assertTrue(text, t.contains("could not be"))
    }

    // ── UC-SAFE-SCAN-11 : exception → not checked wording ───────────────────

    @Test
    fun ucSafeScan11_exception_answerIsNotCheckedWithPharmacistWording() {
        val f = MedScanSafety.failureFields(MedScanSafety.REASON_CHECK_FAILED)
        assertEquals("NOT_CHECKED", f["severity_overall"])
        assertEquals("NOT_CHECKED", f["verdict"])
        assertEquals(false, f["is_clean"])
        assertEquals(false, f["checked"])
        assertEquals(false, f["ok"])
        assertEquals(MedScanSafety.REASON_CHECK_FAILED, f["error"])
        assertNotEquals("NONE", f["severity_overall"])
        assertTellsToAskPharmacistOrDoctor(f["instruction"] as String)
        assertTellsToAskPharmacistOrDoctor(f["warning"] as String)
    }

    @Test
    fun ucSafeScan11_kbUnavailable_isNotCheckedEvenWithEmptyProfile() {
        val r = bundle(kb = false, allergiesInProfile = 0)
        assertEquals(KbSafetyVerdict.NOT_CHECKED, r.verdict)
        val f = MedScanSafety.safetyFields(r)
        assertEquals("NOT_CHECKED", f["severity_overall"])
        assertEquals(false, f["is_clean"])
        assertEquals(false, f["kb_available"])
        assertEquals("kb_unavailable", f["reason"])
        assertTellsToAskPharmacistOrDoctor(f["instruction"] as String)
    }

    @Test
    fun ucSafeScan11_atcUnknownToKb_isNotChecked() {
        val r = bundle(resolved = false)
        val f = MedScanSafety.safetyFields(r)
        assertEquals("NOT_CHECKED", f["severity_overall"])
        assertEquals("candidate_not_resolved", f["reason"])
        assertEquals(false, f["is_clean"])
        assertTellsToAskPharmacistOrDoctor(f["instruction"] as String)
    }

    // ── UC-SAFE-SCAN-12 : incomplete ────────────────────────────────────────

    @Test
    fun ucSafeScan12_onePillarIncomplete_isIncompleteNotNone() {
        val r = bundle(ddi = PillarCheck(emptyList(), KbCheckStatus.INCOMPLETE))
        val f = MedScanSafety.safetyFields(r)
        assertEquals("INCOMPLETE", f["severity_overall"])
        assertEquals("INCOMPLETE", f["verdict"])
        assertEquals(false, f["is_clean"])
        assertEquals("INCOMPLETE", f["ddi_check"])
        assertTellsToAskPharmacistOrDoctor(f["instruction"] as String)
    }

    @Test
    fun ucSafeScan12_atcClassChainNotRead_allergyPillarIsIncomplete() {
        val r = bundle(ancestors = false, allergiesInProfile = 2)
        assertEquals(KbCheckStatus.INCOMPLETE, r.checks.allergy)
        assertEquals("INCOMPLETE", MedScanSafety.severityOverall(r))

        // no allergy in the profile : the class chain is not needed
        assertEquals("NONE", MedScanSafety.severityOverall(bundle(ancestors = false, allergiesInProfile = 0)))
    }

    @Test
    fun ucSafeScan12_hitOnIncompleteCheck_reportsHitAndSaysIncomplete() {
        val r = bundle(
            ddi = PillarCheck(listOf(ddiHit(CrossSeverity.MODERATE)), KbCheckStatus.CHECKED),
            disease = PillarCheck(emptyList(), KbCheckStatus.INCOMPLETE),
        )
        val f = MedScanSafety.safetyFields(r)
        assertEquals("MODERATE", f["severity_overall"])
        assertEquals("ALERT", f["verdict"])
        assertEquals(MedScanSafety.INSTRUCTION_ALERT_INCOMPLETE, f["instruction"])
        assertTrue((f["instruction"] as String).lowercase().contains("pharmacist"))
    }

    // ── UC-SAFE-SCAN-13 : clean ─────────────────────────────────────────────

    @Test
    fun ucSafeScan13_clean_isTheOnlyNone() {
        val r = bundle()
        assertEquals(KbSafetyVerdict.CLEAN, r.verdict)
        val f = MedScanSafety.safetyFields(r)
        assertEquals("NONE", f["severity_overall"])
        assertEquals(true, f["is_clean"])
        assertEquals(true, f["checked"])
        assertEquals("", f["warning"])

        val statuses = KbCheckStatus.values()
        for (a in statuses) for (d in statuses) for (c in statuses) for (resolved in listOf(true, false)) {
            val x = bundle(
                resolved = resolved,
                allergy = PillarCheck(emptyList(), a),
                ddi = PillarCheck(emptyList(), d),
                disease = PillarCheck(emptyList(), c),
            )
            val allChecked = resolved && a == KbCheckStatus.CHECKED && d == KbCheckStatus.CHECKED &&
                c == KbCheckStatus.CHECKED
            assertEquals("a=$a d=$d c=$c resolved=$resolved", allChecked, MedScanSafety.severityOverall(x) == "NONE")
            assertEquals(allChecked, MedScanSafety.safetyFields(x)["is_clean"])
        }
    }

    // ── UC-SAFE-SCAN-14 : alert ─────────────────────────────────────────────

    @Test
    fun ucSafeScan14_alert_severityFollowsTheHits() {
        val allergy = bundle(allergy = PillarCheck(listOf(allergyHit()), KbCheckStatus.CHECKED))
        assertEquals("MAJOR", MedScanSafety.severityOverall(allergy))
        assertEquals(MedScanSafety.INSTRUCTION_ALERT, MedScanSafety.instruction(allergy))

        val majorDdi = bundle(ddi = PillarCheck(listOf(ddiHit(CrossSeverity.MAJOR)), KbCheckStatus.CHECKED))
        assertEquals("MAJOR", MedScanSafety.severityOverall(majorDdi))

        val moderateDdi = bundle(ddi = PillarCheck(listOf(ddiHit(CrossSeverity.MODERATE)), KbCheckStatus.CHECKED))
        assertEquals("MODERATE", MedScanSafety.severityOverall(moderateDdi))
        assertFalse(moderateDdi.isClean)
    }

    @Test
    fun ucSafeScan14_hitFoundWithoutKb_isStillAnAlert() {
        // allergy name heuristics can hit without the KB : the hit wins over "not checked"
        val r = bundle(kb = false, allergy = PillarCheck(listOf(allergyHit()), KbCheckStatus.KB_UNAVAILABLE))
        assertEquals(KbSafetyVerdict.ALERT, r.verdict)
        assertEquals("MAJOR", MedScanSafety.severityOverall(r))
        assertEquals(MedScanSafety.INSTRUCTION_ALERT_INCOMPLETE, MedScanSafety.instruction(r))
    }
}
