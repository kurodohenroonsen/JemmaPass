/*
 * FormCrossCheckHelper.kt — JEMMA Pass · Plan B · v2.6.0 · L_BLINDAGE
 *
 * Façade haut-niveau qui simplifie l'usage de `KbCrossCheck` depuis les
 * BottomSheet forms (Allergy / Medication). L'API existante est puissante
 * mais demande de passer toute la liste profile à chaque check ; ici on
 * fournit 2 wrappers ergonomiques :
 *
 *   1. checkNewAllergyAgainstProfile(allergyDisplay, profile, lang)
 *      → quand l'user ajoute une nouvelle allergie : on resolve son code
 *        en ATC class ancestors et on cherche les médics du profile
 *        existant qui appartiennent à cette classe.
 *
 *   2. checkNewMedicationAgainstProfile(medDisplay, profile, lang)
 *      → quand l'user ajoute un nouveau médic : on cherche dans la KB
 *        toutes les DDI vs les médics existants + les conflits avec les
 *        allergies du profile.
 *
 * Performance budget (Pixel 9, KB warm, profile typique 3-5 allergies +
 * 3-5 médics) :
 *   • Allergy → meds check : ≤ 80 ms
 *   • Medication → DDI + allergy check : ≤ 150 ms
 *
 * Pour le killer demo "Warfarin × Ibuprofen flagged in <200ms" → on tape
 * dans ce budget.
 *
 * Logging : tag JEMMA-FORM-XCHK
 */
package be.heyman.android.jemmapassdemo.ui.profile.common

import android.util.Log
import be.heyman.android.jemmapassdemo.kb.AllergyHit
import be.heyman.android.jemmapassdemo.kb.CrossCheckResult
import be.heyman.android.jemmapassdemo.kb.CrossSeverity
import be.heyman.android.jemmapassdemo.kb.DdiHit
import be.heyman.android.jemmapassdemo.kb.KbCrossCheck
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JCondition
import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class FormCrossCheckHelper @Inject constructor(
    private val kb: KnowledgeBaseService,
    private val crossCheck: KbCrossCheck,
) {

    companion object {
        private const val TAG = "JEMMA-FORM-XCHK"
    }

    /**
     * Lance le cross-check pour un médicament qui va être AJOUTÉ au profile.
     *
     * Stratégie :
     *   1. resolveDrug(medDisplay) → ATC code
     *   2. Pour chaque allergie du profile : check class ancestry (Penicillin
     *      allergy → bloque tous les J01C…)
     *   3. Pour chaque med existant : query v_ddi_emergency (Warfarin ×
     *      Ibuprofen → Major)
     *
     * @param medDisplay le nom du médic tel que tapé (ex: "Augmentin")
     *                   OU un code ATC direct (ex: "J01CR02")
     * @param profile    le profile courant — utilisé pour récupérer `al` + `md`
     * @param lang       langue UI pour les displays localisés
     * @return CrossCheckResult avec allergy + DDI hits, OU null si pas
     *         résolvable (le caller doit alors sauver sans alerte)
     */
    suspend fun checkNewMedicationAgainstProfile(
        medDisplay: String,
        profile: JemmaProfileJ?,
        lang: String = "en",
    ): CrossCheckResult? = withContext(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        if (medDisplay.isBlank()) {
            Log.w(TAG, "[t=$tStart] ⚠ checkNewMed: empty display")
            return@withContext null
        }
        if (profile == null) {
            Log.i(TAG, "[t=$tStart] ↪ checkNewMed: no profile yet (first med) — skip xchk")
            return@withContext null
        }
        val allergies = profile.al
        val meds = profile.md
        val conditions = profile.cn
        if (allergies.isEmpty() && meds.isEmpty() && conditions.isEmpty()) {
            Log.i(TAG, "[t=$tStart] ↪ checkNewMed: empty profile — skip xchk")
            return@withContext null
        }

        Log.i(TAG, "[t=$tStart] 💊 checkNewMed · display='$medDisplay' · " +
            "al=${allergies.size} md=${meds.size} cn=${conditions.size}")

        val result = try {
            crossCheck.checkOneDrugAgainstProfile(
                candidateName = medDisplay,
                allergies = allergies,
                meds = meds,
                conditions = conditions,
                lang = lang,
            )
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ xchk failed: ${e.message}", e)
            return@withContext null
        }
        val totalMs = System.currentTimeMillis() - tStart
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ checkNewMed done · ${totalMs}ms · " +
            "al=${result.allergyHits.size} ddi=${result.ddiHits.size} dd=${result.drugDiseaseHits.size} · " +
            "hasMajor=${result.hasMajor}")
        result
    }

    /**
     * Lance le cross-check pour une ALLERGIE qui va être AJOUTÉE au profile.
     *
     * Stratégie inverse : pour chaque med existant du profile, voir s'il
     * tombe dans la classe ATC de l'allergen — par exemple :
     *   user a Augmentin (J01CR02) en `md[]`
     *   user ajoute "Penicillin allergy" (allergen → ATC class J01C)
     *   → match : Augmentin (J01C ancestor) collide
     *
     * Implementation : on simule l'ajout en re-utilisant `KbCrossCheck` 
     * dans le sens inverse — pour chaque med, on check si son ATC ancestry
     * contient la classe allergène. C'est le même algo que dans
     * `JemmaProfileHydrator.matchAllergyToMed`.
     *
     * @param allergyDisplay nom de l'allergen (ex: "Pénicilline")
     * @param allergyCodeSystem URI du système (typiquement SNOMED)
     * @param allergyCode SNOMED code optionnel (ex: "91936005")
     * @param profile profile courant
     * @param lang lang UI
     * @return un set de medication hits OU null
     */
    suspend fun checkNewAllergyAgainstMeds(
        allergyDisplay: String,
        allergyCode: String?,
        allergyCodeSystem: String?,
        profile: JemmaProfileJ?,
        lang: String = "en",
    ): NewAllergyConflicts? = withContext(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        if (profile == null) {
            Log.i(TAG, "[t=$tStart] ↪ checkNewAllergy: no profile yet — skip")
            return@withContext null
        }
        val meds = profile.md
        if (meds.isEmpty()) {
            Log.i(TAG, "[t=$tStart] ↪ checkNewAllergy: no existing meds — skip")
            return@withContext null
        }

        Log.i(TAG, "[t=$tStart] 🩹 checkNewAllergy · '$allergyDisplay' · " +
            "code=$allergyCode · vs md=${meds.size}")

        // Stratégie : pour chaque med existant, on simule "checkOneAtcAgainstAllergies"
        // en passant le med candidat × notre nouvelle allergie temporaire.
        val tempAllergyList = listOf(
            JAllergy(
                c = allergyCode,
                codeSystem = allergyCodeSystem,
                displayLabel = allergyDisplay,
            )
        )
        val hits = mutableListOf<NewAllergyConflict>()
        for ((idx, med) in meds.withIndex()) {
            val medAtc = med.c?.takeIf { it.isNotBlank() } ?: continue
            // Resolve med → check ATC ancestors against allergy
            val result = try {
                crossCheck.checkOneAtcAgainstAllergies(
                    allergies = tempAllergyList,
                    candidateAtc = medAtc,
                    candidateAllAtcs = listOf(medAtc),
                    candidateDisplay = med.displayLabel ?: medAtc,
                    lang = lang,
                )
            } catch (e: Exception) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ xchk med[$idx] failed: ${e.message}")
                continue
            }
            for (hit in result) {
                hits.add(
                    NewAllergyConflict(
                        existingMedDisplay = med.displayLabel ?: medAtc,
                        existingMedAtc = medAtc,
                        existingMedIndex = idx,
                        matchedOn = hit.matchedOn,
                        criticality = hit.criticality,
                    )
                )
            }
        }
        val totalMs = System.currentTimeMillis() - tStart
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ checkNewAllergy done · ${totalMs}ms · hits=${hits.size}")
        NewAllergyConflicts(hits = hits, durationMs = totalMs)
    }
}

/**
 * Résultat d'une nouvelle allergie checkée contre les médics existants.
 * Indique quels médicaments du profile sont maintenant cliniquement
 * incompatibles avec cette allergie.
 */
data class NewAllergyConflict(
    val existingMedDisplay: String,
    val existingMedAtc: String,
    val existingMedIndex: Int,
    val matchedOn: String,                            // "code" | "name" | "class:Xnnn"
    val criticality: be.heyman.android.jemmapassdemo.kb.AllergyCriticality,
)

data class NewAllergyConflicts(
    val hits: List<NewAllergyConflict>,
    val durationMs: Long,
) {
    val isEmpty: Boolean get() = hits.isEmpty()
    val isNotEmpty: Boolean get() = hits.isNotEmpty()
    /** True si au moins 1 hit avec criticality HIGH (urticaria, anaphylaxis). */
    val hasHigh: Boolean
        get() = hits.any { it.criticality == be.heyman.android.jemmapassdemo.kb.AllergyCriticality.HIGH }
}
