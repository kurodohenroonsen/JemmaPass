/*
 * KbSafetyTruthTableTest.kt — the safety verdict, exhaustively.
 *
 * Every combination of the three pillar statuses (allergy × interaction × disease, 3³ = 27)
 * is run through the three readers of a cross-check : KbCheckReport, CrossCheckResult (forms,
 * scan, assistant) and HydratedProfile (profile screen, QR, PDF), with and without hits.
 *
 * The rule under test : nothing may be shown or said as "nothing to report" unless every
 * pillar is CHECKED — and a hit is always an alert, even on a partial check.
 *
 * Use cases (qa/usecases) : UC-DDI-006 / 015 / 018, UC-ALM-019, UC-DDS-022 / 023, UC-ALR-002 / 003,
 * UC-AI-002 / 003, UC-SCAN-010 ; test ids UC-SAFE-KB-10..19 (continues KbSafetyTest 01..05).
 */
package be.heyman.android.jemmapassdemo.kb

import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KbSafetyTruthTableTest {

    private val statuses = KbCheckStatus.values().toList()

    /** The 27 (allergy, ddi, drugDisease) triples. */
    private val reports: List<KbCheckReport> =
        statuses.flatMap { a -> statuses.flatMap { d -> statuses.map { dd -> KbCheckReport(allergy = a, ddi = d, drugDisease = dd) } } }

    private fun allChecked(r: KbCheckReport) =
        r.allergy == KbCheckStatus.CHECKED && r.ddi == KbCheckStatus.CHECKED && r.drugDisease == KbCheckStatus.CHECKED

    private fun anyUnavailable(r: KbCheckReport) =
        r.allergy == KbCheckStatus.KB_UNAVAILABLE || r.ddi == KbCheckStatus.KB_UNAVAILABLE || r.drugDisease == KbCheckStatus.KB_UNAVAILABLE

    /** The verdict a profile or a resolved candidate must get for a report, without any hit. */
    private fun expectedWithoutHit(r: KbCheckReport): KbSafetyVerdict = when {
        allChecked(r) -> KbSafetyVerdict.CLEAN
        anyUnavailable(r) -> KbSafetyVerdict.NOT_CHECKED
        else -> KbSafetyVerdict.INCOMPLETE
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private fun med(name: String) = HydratedMedication(
        raw = JMedication(displayLabel = name), resolvedConcept = null, displayLocalized = name,
        timing = null, doseValue = null, doseUnit = null, route = MedicationRoute.UNKNOWN,
        atcCode = null, allAtcCodes = emptyList(), rxnormCui = null,
    )

    private fun ddiAlert(severity: DDIResult.Severity) = DdiAlert(
        medicationA = med("Warfarin"), medicationB = med("Ibuprofen"), severity = severity,
        mechanism = null, description = null, management = null, alternativeAtc = null, fuzzyMatch = false,
    )

    private fun allergyAlert(criticality: AllergyCriticality) = AllergyAlert(
        allergyDisplay = "Penicillin", allergyCode = "91936005", medication = med("Amoxicillin"),
        criticality = criticality, matchedOn = "class:J01C",
    )

    private fun diseaseAlert(severity: DDIResult.Severity) = DrugDiseaseAlert(
        medication = med("Ibuprofen"),
        condition = HydratedGenericEntry(raw = be.heyman.android.jemmapassdemo.qr.JEntryGeneric(c = "84114007"), resolvedConcept = null, displayLocalized = "Heart failure"),
        severity = severity, description = null, management = null, diseaseNameMatched = "Heart Failure",
    )

    private fun profile(
        checks: KbCheckReport,
        ddi: List<DdiAlert> = emptyList(),
        allergy: List<AllergyAlert> = emptyList(),
        disease: List<DrugDiseaseAlert> = emptyList(),
    ) = HydratedProfile(
        raw = JemmaProfileJ(j = "1.2", sid = "x"), uiLang = "en",
        allergies = emptyList(), medications = emptyList(), conditions = emptyList(),
        ddiAlerts = ddi, allergyAlerts = allergy, drugDiseaseAlerts = disease, hydrationMs = 0L, checks = checks,
    )

    private fun ddiHit(severity: CrossSeverity) = DdiHit(
        existingMedDisplay = "Warfarin", existingMedAtc = "B01AA03", candidateAtc = "M01AE01", candidateDisplay = "Ibuprofen",
        severity = severity, mechanism = null, description = null, management = null, alternativeAtc = null, queryDurationMs = 1L,
    )

    private fun allergyHit(criticality: AllergyCriticality) = AllergyHit(
        allergyDisplay = "Penicillin", allergyCode = "91936005", matchedOn = "class:J01C",
        candidateAtc = "J01CA04", candidateDisplay = "Amoxicillin", criticality = criticality,
    )

    private fun diseaseHit(severity: CrossSeverity) = DrugDiseaseHit(
        conditionDisplay = "Heart failure", conditionCode = "84114007", candidateAtc = "M01AE01", candidateDisplay = "Ibuprofen",
        severity = severity, description = null, management = null, diseaseNameMatched = "Heart Failure",
    )

    private fun result(
        checks: KbCheckReport,
        atc: String = "M01AE01",
        allergy: List<AllergyHit> = emptyList(),
        ddi: List<DdiHit> = emptyList(),
        disease: List<DrugDiseaseHit> = emptyList(),
    ) = CrossCheckResult(
        candidateAtc = atc, candidateDisplay = "Ibuprofen",
        allergyHits = allergy, ddiHits = ddi, drugDiseaseHits = disease, totalDurationMs = 1L, checks = checks,
    )

    // ── UC-SAFE-KB-10 : the verdict function itself ─────────────────────────

    @Test
    fun ucSafeKb10_verdictTable_statusTimesHits() {
        for (hits in listOf(0, 1, 2, 50)) {
            val expected = mapOf(
                KbCheckStatus.CHECKED to (if (hits > 0) KbSafetyVerdict.ALERT else KbSafetyVerdict.CLEAN),
                KbCheckStatus.INCOMPLETE to (if (hits > 0) KbSafetyVerdict.ALERT else KbSafetyVerdict.INCOMPLETE),
                KbCheckStatus.KB_UNAVAILABLE to (if (hits > 0) KbSafetyVerdict.ALERT else KbSafetyVerdict.NOT_CHECKED),
            )
            for (s in statuses) assertEquals("$s with $hits hits", expected.getValue(s), KbSafety.verdict(s, hits))
        }
        // a negative count (a caller bug) is never read as a hit, and never upgrades to CLEAN either
        assertEquals(KbSafetyVerdict.NOT_CHECKED, KbSafety.verdict(KbCheckStatus.KB_UNAVAILABLE, -1))
        assertEquals(KbSafetyVerdict.INCOMPLETE, KbSafety.verdict(KbCheckStatus.INCOMPLETE, -1))
    }

    // ── UC-SAFE-KB-11 : the report (27 combinations) ────────────────────────

    @Test
    fun ucSafeKb11_reportTable_overallIsTheWorstPillar() {
        assertEquals(27, reports.size)
        assertEquals(27, reports.toSet().size)
        for (r in reports) {
            val worst = listOf(r.allergy, r.ddi, r.drugDisease).maxByOrNull { it.ordinal }!!
            assertEquals("$r", worst, r.overall)
            assertEquals("$r", worst, KbSafety.worst(r.allergy, r.ddi, r.drugDisease))
            assertEquals("$r fullyChecked", allChecked(r), r.fullyChecked)
            assertEquals("$r kbAvailable", !anyUnavailable(r), r.kbAvailable)
        }
        assertEquals("exactly one combination is fully checked", 1, reports.count { it.fullyChecked })
        assertEquals("19 of 27 combinations have a pillar without KB", 19, reports.count { !it.kbAvailable })
        // the order of the enum is the order of gravity the rule relies on
        assertEquals(listOf(KbCheckStatus.CHECKED, KbCheckStatus.INCOMPLETE, KbCheckStatus.KB_UNAVAILABLE), statuses)
        assertEquals(KbCheckStatus.CHECKED, KbSafety.worst())
        for (s in statuses) assertEquals(KbCheckReport(s, s, s), KbCheckReport.all(s))
    }

    // ── UC-SAFE-KB-12 : the profile (fiche, QR, PDF) ────────────────────────

    @Test
    fun ucSafeKb12_profileTable_neverCleanUnlessEveryPillarIsChecked() {
        var clean = 0
        for (r in reports) {
            val h = profile(r)
            assertEquals("$r", expectedWithoutHit(r), h.safetyVerdict)
            assertEquals("$r isClean", allChecked(r), h.isClean)
            assertEquals("$r kbAvailable", !anyUnavailable(r), h.kbAvailable)
            assertFalse("$r has no alert", h.hasAlerts)
            if (h.isClean) clean++
            if (h.safetyVerdict == KbSafetyVerdict.CLEAN) assertTrue("$r CLEAN without a full check", r.fullyChecked)
        }
        assertEquals("one clean profile out of 27 status combinations", 1, clean)
    }

    @Test
    fun ucSafeKb13_profileTable_anAlertOnAnyPillarIsAlwaysAnAlert() {
        val alertSets: List<Triple<List<DdiAlert>, List<AllergyAlert>, List<DrugDiseaseAlert>>> = listOf(
            Triple(listOf(ddiAlert(DDIResult.Severity.MAJOR)), emptyList(), emptyList()),
            Triple(listOf(ddiAlert(DDIResult.Severity.MODERATE)), emptyList(), emptyList()),
            Triple(emptyList(), listOf(allergyAlert(AllergyCriticality.HIGH)), emptyList()),
            Triple(emptyList(), listOf(allergyAlert(AllergyCriticality.UNABLE_TO_ASSESS)), emptyList()),
            Triple(emptyList(), emptyList(), listOf(diseaseAlert(DDIResult.Severity.MAJOR))),
            Triple(emptyList(), emptyList(), listOf(diseaseAlert(DDIResult.Severity.MINOR))),
            Triple(listOf(ddiAlert(DDIResult.Severity.MAJOR)), listOf(allergyAlert(AllergyCriticality.LOW)), listOf(diseaseAlert(DDIResult.Severity.UNKNOWN))),
        )
        for (r in reports) for ((ddi, allergy, disease) in alertSets) {
            val h = profile(r, ddi, allergy, disease)
            assertEquals("$r", KbSafetyVerdict.ALERT, h.safetyVerdict)
            assertTrue("$r", h.hasAlerts)
            assertFalse("$r an alert is never clean", h.isClean)
            // the "not fully checked" information is still there next to the alert
            assertEquals("$r", allChecked(r), h.checks.fullyChecked)
        }
        // major counters : DDI Major + allergy HIGH + disease Major (UC-ALR-001)
        val mixed = profile(
            KbCheckReport(),
            ddi = listOf(ddiAlert(DDIResult.Severity.MAJOR), ddiAlert(DDIResult.Severity.MODERATE)),
            allergy = listOf(allergyAlert(AllergyCriticality.HIGH), allergyAlert(AllergyCriticality.UNABLE_TO_ASSESS)),
            disease = listOf(diseaseAlert(DDIResult.Severity.MAJOR), diseaseAlert(DDIResult.Severity.MINOR)),
        )
        assertEquals(1, mixed.majorDdiCount)
        assertEquals(1, mixed.majorAllergyAlertCount)
        assertEquals(1, mixed.majorDrugDiseaseCount)
        assertEquals(3, mixed.totalMajorAlerts)
        // UC-ALR-002 — only non-major alerts : still an alert, zero "major"
        val minorOnly = profile(KbCheckReport(), allergy = listOf(allergyAlert(AllergyCriticality.UNABLE_TO_ASSESS)))
        assertEquals(0, minorOnly.totalMajorAlerts)
        assertEquals(KbSafetyVerdict.ALERT, minorOnly.safetyVerdict)
    }

    // ── UC-SAFE-KB-14 : the single-drug check (forms, scan, assistant) ──────

    @Test
    fun ucSafeKb14_crossCheckTable_resolvedCandidateWithoutHit() {
        for (r in reports) {
            val c = result(r)
            assertEquals("$r", expectedWithoutHit(r), c.verdict)
            assertEquals("$r isClean", allChecked(r), c.isClean)
            assertEquals("$r checked", allChecked(r), c.checked)
            assertEquals("$r kbAvailable", !anyUnavailable(r), c.kbAvailable)
            assertTrue("$r resolved", c.candidateResolved)
            assertFalse("$r", c.hasMajor)
        }
    }

    @Test
    fun ucSafeKb15_crossCheckTable_unknownCandidateIsNeverCheckedWhateverThePillarsSay() {
        for (r in reports) {
            val c = result(r, atc = "")
            assertEquals("$r", KbSafetyVerdict.NOT_CHECKED, c.verdict)
            assertFalse("$r isClean", c.isClean)
            assertFalse("$r checked", c.checked)
            assertFalse("$r resolved", c.candidateResolved)
            val f = KbSafety.crossCheckSafetyFields(c)
            assertEquals("$r ok", false, f["ok"])
            assertEquals("$r is_clean", false, f["is_clean"])
            assertEquals("$r verdict", "NOT_CHECKED", f["verdict"])
            assertTrue("$r warning", (f["warning"] as String).startsWith("NOT CHECKED"))
        }
    }

    @Test
    fun ucSafeKb16_crossCheckTable_aHitIsAlwaysAnAlert() {
        val hitSets: List<Triple<List<AllergyHit>, List<DdiHit>, List<DrugDiseaseHit>>> = listOf(
            Triple(listOf(allergyHit(AllergyCriticality.HIGH)), emptyList(), emptyList()),
            Triple(listOf(allergyHit(AllergyCriticality.UNABLE_TO_ASSESS)), emptyList(), emptyList()),
            Triple(emptyList(), listOf(ddiHit(CrossSeverity.MAJOR)), emptyList()),
            Triple(emptyList(), listOf(ddiHit(CrossSeverity.NONE)), emptyList()),
            Triple(emptyList(), emptyList(), listOf(diseaseHit(CrossSeverity.MAJOR))),
            Triple(emptyList(), emptyList(), listOf(diseaseHit(CrossSeverity.UNKNOWN))),
        )
        for (r in reports) for (atc in listOf("M01AE01", "")) for ((allergy, ddi, disease) in hitSets) {
            val c = result(r, atc, allergy, ddi, disease)
            assertEquals("$r atc='$atc'", KbSafetyVerdict.ALERT, c.verdict)
            assertFalse("$r atc='$atc' a hit is never clean", c.isClean)
            assertEquals(1, c.totalHits)
            assertEquals(false, KbSafety.crossCheckSafetyFields(c)["is_clean"])
            assertEquals("ALERT", KbSafety.crossCheckSafetyFields(c)["verdict"])
        }
        // hasMajor : allergy HIGH, DDI MAJOR, disease MAJOR — and nothing else
        assertTrue(result(KbCheckReport(), allergy = listOf(allergyHit(AllergyCriticality.HIGH))).hasMajor)
        assertTrue(result(KbCheckReport(), ddi = listOf(ddiHit(CrossSeverity.MAJOR))).hasMajor)
        assertTrue(result(KbCheckReport(), disease = listOf(diseaseHit(CrossSeverity.MAJOR))).hasMajor)
        assertFalse(result(KbCheckReport(), allergy = listOf(allergyHit(AllergyCriticality.LOW))).hasMajor)
        assertFalse(result(KbCheckReport(), ddi = listOf(ddiHit(CrossSeverity.MODERATE))).hasMajor)
        assertFalse(result(KbCheckReport(), disease = listOf(diseaseHit(CrossSeverity.MINOR))).hasMajor)
    }

    // ── UC-SAFE-KB-17 : what the assistant is told ──────────────────────────

    @Test
    fun ucSafeKb17_assistantFields_table() {
        for (r in reports) {
            val f = KbSafety.crossCheckSafetyFields(result(r))
            val clean = allChecked(r)
            assertEquals("$r is_clean", clean, f["is_clean"])
            assertEquals("$r checked", clean, f["checked"])
            assertEquals("$r kb_available", !anyUnavailable(r), f["kb_available"])
            assertEquals("$r ok", !anyUnavailable(r), f["ok"])
            assertEquals("$r verdict", expectedWithoutHit(r).name, f["verdict"])
            assertEquals("$r allergy_check", r.allergy.name, f["allergy_check"])
            assertEquals("$r ddi_check", r.ddi.name, f["ddi_check"])
            assertEquals("$r drug_disease_check", r.drugDisease.name, f["drug_disease_check"])
            val warning = f["warning"] as String
            val reason = f["reason"] as String
            when {
                clean -> { assertEquals("$r", "", warning); assertEquals("$r", "", reason) }
                anyUnavailable(r) -> { assertEquals("$r", KbSafety.WARNING_KB_UNAVAILABLE, warning); assertEquals("$r", KbSafety.REASON_KB_UNAVAILABLE, reason) }
                else -> { assertEquals("$r", KbSafety.WARNING_INCOMPLETE, warning); assertEquals("$r", "", reason) }
            }
            // the model is never left without an instruction when it must not say "safe"
            assertEquals("$r warning present unless clean", !clean, warning.isNotBlank())
            if (!clean) assertTrue("$r", warning.contains("do not tell the user", ignoreCase = true))
        }
        // every warning text carries the instruction
        for (w in listOf(KbSafety.WARNING_KB_UNAVAILABLE, KbSafety.WARNING_CANDIDATE_NOT_RESOLVED, KbSafety.WARNING_INCOMPLETE, KbSafety.WARNING_DDI_NOT_CHECKED)) {
            assertTrue(w, w.contains("Do not tell the user", ignoreCase = true))
        }
    }

    // ── UC-SAFE-KB-18 : how one pillar gets its status ──────────────────────

    @Test
    fun ucSafeKb18_pillarStatusTable_withSomethingToCheck() {
        // With at least one entry to verify : CHECKED needs the KB and zero unverified entry.
        // (What an EMPTY profile should answer is an open question — UC-SCAN-010, see
        // qa/usecases/suspected-defects.md — and is deliberately not pinned here.)
        for (items in listOf(1, 2, 7, 50)) {
            for (unverified in listOf(-1, 0)) {
                assertEquals("items=$items unverified=$unverified", KbCheckStatus.CHECKED, KbSafety.pillarStatus(true, items, unverified))
            }
            for (unverified in listOf(1, items, items + 3)) {
                assertEquals("items=$items unverified=$unverified", KbCheckStatus.INCOMPLETE, KbSafety.pillarStatus(true, items, unverified))
            }
            for (unverified in listOf(-1, 0, 1, items)) {
                assertEquals("no KB, items=$items unverified=$unverified", KbCheckStatus.KB_UNAVAILABLE, KbSafety.pillarStatus(false, items, unverified))
            }
            assertEquals(KbCheckStatus.CHECKED, KbSafety.pillarStatus(kbAvailable = true, itemsToCheck = items))
            assertEquals(KbCheckStatus.KB_UNAVAILABLE, KbSafety.pillarStatus(kbAvailable = false, itemsToCheck = items))
        }
    }

    @Test
    fun ucSafeKb19_unverifiedItemsTable_neverMoreThanTheEntriesAndNeverNegative() {
        assertEquals(0, KbSafety.unverifiedItems(5))
        assertEquals(0, KbSafety.unverifiedItems(5, 0, 0))
        assertEquals(2, KbSafety.unverifiedItems(5, 2, 1))
        assertEquals(2, KbSafety.unverifiedItems(5, 1, 2))
        assertEquals(5, KbSafety.unverifiedItems(5, 9, 2))
        assertEquals(0, KbSafety.unverifiedItems(5, -3, -1))
        assertEquals(0, KbSafety.unverifiedItems(0, 4))
        assertEquals(0, KbSafety.unverifiedItems(-2, 4))
        for (total in 0..6) for (a in -1..7) for (b in -1..7) {
            val n = KbSafety.unverifiedItems(total, a, b)
            assertTrue("total=$total a=$a b=$b → $n", n in 0..total)
            assertTrue("total=$total a=$a b=$b → $n", n >= minOf(maxOf(a, b, 0), total))
        }
    }
}
