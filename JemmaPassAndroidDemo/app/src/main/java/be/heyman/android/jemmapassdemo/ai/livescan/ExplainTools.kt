/*
 * ExplainTools.kt — JEMMA Pass · Live Scan v0.4 refonte · Lot 5
 *
 * ToolSet exposé à Gemma pendant Phase D streaming explanation.
 *
 * Deux tools :
 *   • getInteractionsForCurrentPatient(atc) — retourne CrossCheckReport JSON
 *   • findSafeAlternativeForCurrentPatient(atc, reason) — cascade Kotlin
 *
 * Les deux lisent `PatientContextHolder.current` en backend. Aucune
 * info patient ne passe par le LLM prompt — seulement des ATC codes
 * et display names anonymisés dans le JSON retour.
 *
 * Side effect : lastSafeAlt capture l'alternative pour le Fragment
 * (rendu badge vert après stream).
 *
 * Log channels :
 *   JEMMA-TOOL-INTER  · getInteractionsForCurrentPatient
 *   JEMMA-TOOL-ALT    · findSafeAlternativeForCurrentPatient
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.util.Log
import be.heyman.android.jemmapassdemo.kb.KbCrossCheck
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.getDrugDisplayForAtc
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

private const val TAG_INTER = "JEMMA-TOOL-INTER"
private const val TAG_ALT = "JEMMA-TOOL-ALT"

@Singleton
class ExplainTools @Inject constructor(
    private val patientContextHolder: PatientContextHolder,
    private val kbCrossCheck: KbCrossCheck,
    private val kb: KnowledgeBaseService,
    private val alternativeFinder: AlternativeFinder,
    private val orchestrator: CrossCheckOrchestrator,
) {
    @Volatile
    var lastSafeAlt: SafeAlternative? = null
        private set

    fun resetSession() {
        lastSafeAlt = null
    }

    fun buildToolSet(): ExplainToolSet = ExplainToolSet(this, patientContextHolder, kbCrossCheck, kb, alternativeFinder, orchestrator)
}

class ExplainToolSet(
    private val parent: ExplainTools,
    private val patientContextHolder: PatientContextHolder,
    private val kbCrossCheck: KbCrossCheck,
    private val kb: KnowledgeBaseService,
    private val alternativeFinder: AlternativeFinder,
    private val orchestrator: CrossCheckOrchestrator,
) : ToolSet {

    @Tool(description = """
        Get the clinical cross-check report for a drug against the
        CURRENT patient (anonymized). Returns JSON with overall severity,
        allergy hits, drug-drug interactions, and condition hits, plus :
          - verdict     : "ALERT" / "CLEAN" / "INCOMPLETE" / "NOT_CHECKED"
          - checked     : true only when the whole profile was verified
          - instruction : what you must tell the user for this result
        Never includes patient name, age, or any identifying info. Use this
        FIRST in your reasoning to understand WHY the drug was flagged.
        Only verdict "CLEAN" means nothing was found. If verdict is
        "NOT_CHECKED" or "INCOMPLETE", or the answer has an "error" field,
        the check could NOT be completed : never say the drug is safe or
        OK — say the check could not be completed and to ask a pharmacist
        or a doctor.
    """)
    fun getInteractionsForCurrentPatient(
        @ToolParam(description = "ATC code of the drug to check (e.g. 'J01CR02')")
        atc: String,
    ): String {
        val tStart = System.currentTimeMillis()
        val patient = patientContextHolder.current
            ?: return JSONObject(ExplainSafety.failureFields("no_patient_loaded")).toString()

        val raw = try {
            runBlocking {
                kbCrossCheck.checkOneDrugAgainstProfile(
                    candidateName = atc,
                    allergies = patient.allergies,
                    meds = patient.medications,
                    conditions = patient.conditions,
                    lang = "en",
                )
            }
        } catch (e: Exception) {
            Log.e(TAG_INTER, "[t=${System.currentTimeMillis()}] ❌ check threw", e)
            return notCheckedJson(atc)
        }

        // UC-SAFE-SCAN — a failure while building the report is also "not checked".
        val report = try {
            val drugName = runBlocking { kb.getDrugDisplayForAtc(atc, "en") }
            runBlocking { orchestrator.adaptToReport(raw, atc, drugName) }
        } catch (e: Exception) {
            Log.e(TAG_INTER, "[t=${System.currentTimeMillis()}] ❌ report build threw", e)
            return notCheckedJson(atc)
        }

        val json = reportToJson(report)
        Log.i(
            TAG_INTER,
            "[t=${System.currentTimeMillis()}] 🩺 getInteractions atc=$atc · " +
                "verdict=${report.verdict} · overall=${report.overall} · ${System.currentTimeMillis() - tStart}ms",
        )
        return json
    }

    @Tool(description = """
        When the current drug is contraindicated, find a SAFE alternative
        for the current patient. The system runs a deterministic cascade :
        gets sibling ATCs from the knowledge base, filters by allergy class,
        cross-checks each candidate against the patient, returns the first
        one that's clean. Returns JSON with the recommended alternative
        and rationale. Use after getInteractionsForCurrentPatient confirms Major.
    """)
    fun findSafeAlternativeForCurrentPatient(
        @ToolParam(description = "ATC code of the contraindicated drug")
        originalAtc: String,
        @ToolParam(description = "Reason : 'allergy' / 'ddi' / 'condition'")
        reason: String,
    ): String {
        val tStart = System.currentTimeMillis()
        val patient = patientContextHolder.current
            ?: return """{"error":"no_patient_loaded"}"""

        val result = try {
            runBlocking { alternativeFinder.findSafe(originalAtc, reason, patient, "en") }
        } catch (e: Exception) {
            Log.e(TAG_ALT, "[t=${System.currentTimeMillis()}] ❌ findSafe threw", e)
            return """{"error":"cascade_failed","original":"$originalAtc"}"""
        }

        // Side-effect : capture pour le Fragment
        ExplainToolsInternal.captureSafeAlt(parent, result.safe)

        val json = JSONObject().apply {
            if (result.safe != null) {
                put("status", "safe_alternative_found")
                put("originalAtc", originalAtc)
                put("originalReason", reason)
                put("alternative", JSONObject().apply {
                    put("atc", result.safe.atc)
                    put("name", result.safe.displayName)
                    put("rationale", result.safe.rationale)
                    put("overallCheck", result.safe.checkedReport.overall.name)
                })
                put("candidatesTested", result.candidatesTested)
                if (result.alsoSafe.isNotEmpty()) {
                    put("alsoSafe", JSONArray().also { arr ->
                        result.alsoSafe.forEach {
                            arr.put(JSONObject().apply {
                                put("atc", it.atc)
                                put("name", it.displayName)
                            })
                        }
                    })
                }
            } else {
                put("status", "no_safe_alternative_found")
                put("candidatesTested", result.candidatesTested)
            }
        }.toString()

        Log.i(
            TAG_ALT,
            "[t=${System.currentTimeMillis()}] 🔁 findSafe · " +
                "safe=${result.safe?.atc ?: "none"} · " +
                "tested=${result.candidatesTested} · ${System.currentTimeMillis() - tStart}ms",
        )
        return json
    }

    /** NOT_CHECKED answer (exception) — never readable as "nothing found". */
    private fun notCheckedJson(atc: String): String =
        JSONObject(ExplainSafety.failureFields("cross_check_failed")).apply {
            put("atc", atc)
        }.toString()

    private fun reportToJson(report: CrossCheckReport): String {
        return JSONObject().apply {
            put("drug", JSONObject().apply {
                put("atc", report.drugAtc)
                put("name", report.drugName)
            })
            put("overall", report.overall.name)
            // UC-SAFE-SCAN — verdict / checked / is_clean / instruction : empty hit lists
            // alone must never be read as "safe".
            for ((k, v) in ExplainSafety.safetyFields(report.verdict, report.fullyChecked)) put(k, v)
            put("allergies", JSONArray().also { arr ->
                report.allergyHits.forEach {
                    arr.put(JSONObject().apply {
                        put("with", it.patientAllergyName)
                        put("class", it.crossReactiveClass)
                        put("severity", it.severity.name)
                    })
                }
            })
            put("interactions", JSONArray().also { arr ->
                report.ddiHits.forEach {
                    arr.put(JSONObject().apply {
                        put("withName", it.withDrugName)
                        put("withAtc", it.withDrugAtc)
                        put("severity", it.severity.name)
                        put("mechanism", it.mechanism ?: "")
                        put("description", it.description?.take(200) ?: "")
                    })
                }
            })
            put("conditions", JSONArray().also { arr ->
                report.conditionHits.forEach {
                    arr.put(JSONObject().apply {
                        put("name", it.conditionName)
                        put("severity", it.severity.name)
                    })
                }
            })
        }.toString()
    }
}

/**
 * Helper interne pour muter `parent.lastSafeAlt` via reflection.
 * Pattern identique à IdentifyDrugToolsInternal.
 */
internal object ExplainToolsInternal {
    fun captureSafeAlt(parent: ExplainTools, alt: SafeAlternative?) {
        val field = ExplainTools::class.java.getDeclaredField("lastSafeAlt").apply { isAccessible = true }
        field.set(parent, alt)
    }
}
