/*
 * AlternativeFinder.kt — JEMMA Pass · Live Scan v0.4 refonte · Lot 4
 *
 * Phase D helper : quand un drug fail cross-check (overall=Major),
 * trouve une alternative SAFE pour le patient courant.
 *
 * ─── Cascade algorithm ───
 *
 *   1. Get alternatives from atc_alternatives table
 *   2. If reason='allergy' : drop candidates dont avoid_if_allergic
 *      overlaps les classes d'allergie du patient
 *   3. Pour chaque viable, run kbCrossCheck → CrossCheckReport
 *   4. Early-stop sur le 1er candidate avec overall ≤ MINOR
 *   5. Continue jusqu'à 3 safe alternatives (1 primaire + 2 backups UI)
 *
 * Entièrement déterministe. Zero LLM. Auditable ligne par ligne.
 *
 * ─── Latence cible ───
 *
 *   • 30 candidates max × ~50ms cross-check = <2s
 *   • Early-stop typique : trouve 1ère safe en 2-5 tests = <300ms
 *
 * Log channel : JEMMA-ALT
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.util.Log
import be.heyman.android.jemmapassdemo.ai.medscan.VictimProfileSnapshot
import be.heyman.android.jemmapassdemo.kb.AtcAlternative
import be.heyman.android.jemmapassdemo.kb.KbCrossCheck
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseManager
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.getAlternativesForAtc
import be.heyman.android.jemmapassdemo.kb.getAllergyAvoidClasses
import be.heyman.android.jemmapassdemo.kb.getDrugDisplayForAtc
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "JEMMA-ALT"

@Singleton
class AlternativeFinder @Inject constructor(
    private val kb: KnowledgeBaseService,
    private val kbManager: KnowledgeBaseManager,  // 🆕 v4.1 FIX-AWAIT-DB
    private val kbCrossCheck: KbCrossCheck,
    private val orchestrator: CrossCheckOrchestrator,
) {
    /**
     * Résultat de la cascade.
     *
     * @param safe la 1ère alternative trouvée safe (ou null si épuisée)
     * @param alsoSafe alternatives supplémentaires (top 2 backups UI)
     * @param candidatesTested combien de candidates on a cross-check
     * @param candidatesSafeCount combien étaient safe (au moins 1 si `safe` != null)
     */
    data class CascadeResult(
        val safe: SafeAlternative?,
        val alsoSafe: List<SafeAlternative>,
        val candidatesTested: Int,
        val candidatesSafeCount: Int,
    )

    suspend fun findSafe(
        originalAtc: String,
        reason: String,
        patient: VictimProfileSnapshot,
        lang: String = "en",
    ): CascadeResult {
        val tStart = System.currentTimeMillis()
        Log.i(TAG, "[t=$tStart] 🔁 cascade · original=$originalAtc · reason=$reason")

        // 1. Get raw candidates
        // 🆕 v4.1 FIX-AWAIT-DB : passer kbManager (plus de reflection silencieusement broken)
        val rawCandidates = kb.getAlternativesForAtc(kbManager, originalAtc, maxCandidates = 30)
        if (rawCandidates.isEmpty()) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ no alternatives in atc_alternatives for $originalAtc",
            )
            return CascadeResult(null, emptyList(), 0, 0)
        }

        // 2. Filter by avoid_if_allergic if reason is allergy
        // 🆕 v4.1 FIX-ALLERGY-XR : utiliser allergyAtcClasses (suspend, lookup DB)
        // au lieu de l'ancien allergyAtcClass qui retournait null pour les codes
        // SNOMED → maintenant on map via la table allergy_cross_reactivity.
        val patientAllergyClasses = if (reason == "allergy") {
            patient.allergies.flatMap { allergy ->
                allergyAtcClasses(allergy.c)
            }.toSet()
        } else emptySet()
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] · patientAllergyClasses=$patientAllergyClasses",
        )

        val viableCandidates = if (patientAllergyClasses.isEmpty()) {
            rawCandidates
        } else {
            rawCandidates.filter { alt ->
                val avoidClasses = alt.avoidIfAllergic
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                avoidClasses.none { avoid ->
                    patientAllergyClasses.any { pa ->
                        avoid.startsWith(pa) || pa.startsWith(avoid)
                    }
                }
            }
        }
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] · ${rawCandidates.size} → " +
                "${viableCandidates.size} after allergy filter",
        )

        // 3. Cross-check chaque viable, early-stop sur le 1er safe
        val safeList = mutableListOf<SafeAlternative>()
        var tested = 0
        for (cand in viableCandidates) {
            tested++
            val rawCheck = try {
                kbCrossCheck.checkOneDrugAgainstProfile(
                    candidateName = cand.altAtc,
                    allergies = patient.allergies,
                    meds = patient.medications,
                    conditions = patient.conditions,
                    lang = lang,
                )
            } catch (e: Exception) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ check threw for ${cand.altAtc}: ${e.message}")
                continue
            }

            val altDisplay = kb.getDrugDisplayForAtc(cand.altAtc, lang)
            val report = orchestrator.adaptToReport(rawCheck, cand.altAtc, altDisplay)

            if (report.overall == Severity.NONE || report.overall == Severity.MINOR) {
                val rationale = buildRationale(cand, reason)
                safeList.add(
                    SafeAlternative(
                        atc = cand.altAtc,
                        displayName = report.drugName,
                        rationale = rationale,
                        checkedReport = report,
                    )
                )
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ✅ safe alt ${cand.altAtc} " +
                        "(${report.drugName}) overall=${report.overall} after $tested tests",
                )
                if (safeList.size >= 3) break
            }
        }

        val elapsed = System.currentTimeMillis() - tStart
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🔁 cascade done · tested=$tested · " +
                "safe=${safeList.size} · ${elapsed}ms",
        )

        return CascadeResult(
            safe = safeList.firstOrNull(),
            alsoSafe = safeList.drop(1),
            candidatesTested = tested,
            candidatesSafeCount = safeList.size,
        )
    }

    /**
     * Extract une classe ATC prefix depuis un code allergie patient.
     *
     * Implémentation v1 simple : si le code ressemble à un ATC
     * ([A-Z]\d{2}[A-Z]{1,2}\d{0,2}), prend les 4 premiers chars (= L4).
     *
     * 🆕 v4.1 FIX-ALLERGY-XR : query `allergy_cross_reactivity` table
     * (52 rows seed clinique dans DIAMOND v1.1) pour mapper SNOMED/RxNorm
     * → ATC classes. Ex: "penicillin allergy" (SNOMED 91936005) → ["J01C"]
     * via cette table. Le quick-win pour codes ATC-shaped reste actif.
     *
     * Signature changée : suspend (à cause du lookup DB) + retourne Set
     * (peut y avoir plusieurs classes à éviter pour une même allergie).
     */
    private suspend fun allergyAtcClasses(allergyCode: String?): Set<String> {
        if (allergyCode.isNullOrBlank()) return emptySet()

        // Quick win 1 : si le code est lui-même un ATC, prendre L4 prefix.
        if (allergyCode.matches(Regex("[A-Z]\\d{2}[A-Z]{1,2}\\d{0,2}"))) {
            return setOf(allergyCode.take(4))
        }

        // 🆕 v4.1 FIX-ALLERGY-XR — Lookup dans allergy_cross_reactivity
        return kb.getAllergyAvoidClasses(kbManager, allergyCode).toSet()
    }

    private fun buildRationale(alt: AtcAlternative, reason: String): String = when (reason) {
        "allergy" -> "Not in the same drug class as the original (${alt.relation}) ; " +
            "no cross-reactive allergy hit ; cross-check clean."
        "ddi" -> "Alternative from ${alt.relation} ; no DDI with patient's current medications."
        "condition" -> "Alternative from ${alt.relation} ; not contraindicated by patient's conditions."
        else -> "Alternative from ${alt.relation} ; cross-check clean."
    }
}
