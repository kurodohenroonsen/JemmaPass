/*
 * PatientContextHolder.kt — JEMMA Pass · Live Scan v0.4 refonte · Lot 1
 *
 * Singleton Hilt qui détient le profil victime ACTUELLEMENT actif.
 *
 * ─── Pourquoi ce design ───
 *
 * Gemma function calling (LiteRT-LM @Tool) ne permet pas d'injecter
 * de l'état Kotlin via les @ToolParam — les tools reçoivent uniquement
 * du JSON depuis le LLM. Or les Phase D tools (getInteractions,
 * findSafeAlternative) ont besoin de lire le profil patient EN BACKEND
 * sans que ses données passent par le prompt.
 *
 * Solution : un Singleton Hilt @Inject qui contient le snapshot. Le
 * Fragment l'alimente à onViewCreated, les tools le lisent à
 * l'invocation, le Fragment le clear à onDestroyView.
 *
 * ─── PII isolation invariant ───
 *
 * **Le contenu de ce holder ne doit JAMAIS être inséré dans un prompt
 * LLM.** Les tools qui le lisent doivent retourner du JSON anonymisé
 * (ATC codes + display names, jamais le nom du patient, son âge, son
 * sexe, son ID, ses coords).
 *
 * Validation possible côté CI : grep des prompts buildés pour
 * `patient.displayName`, `patient.id`, etc. — doit retourner zéro hit.
 *
 * ─── Coexistence avec le legacy ───
 *
 * Le `PatientDetailFragment` continue son flow normal avec son
 * `currentSnapshot` privé. Ce holder est un PROXY ADDITIONNEL pour
 * le path A v4. Le legacy `MedScanController` reste intouché.
 *
 * ─── Lifecycle ───
 *
 *   • setCurrent() — JemmaLiveScanFragment.onViewCreated()
 *   • current      — lecture par les tools (@Inject backend)
 *   • clear()      — JemmaLiveScanFragment.onDestroyView()
 *
 * **clear() doit être appelé dans onDestroyView, pas onPause.** Sinon
 * en revenant au fragment après une notif Android, le patient context
 * est perdu et les tools retournent {"error":"no_patient_loaded"}.
 *
 * Log channel : JEMMA-PATIENT-CTX
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.util.Log
import be.heyman.android.jemmapassdemo.ai.medscan.VictimProfileSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "JEMMA-PATIENT-CTX"

@Singleton
class PatientContextHolder @Inject constructor() {

    private val _currentFlow = MutableStateFlow<VictimProfileSnapshot?>(null)

    /**
     * Flow réactif pour observer les changements de patient courant.
     * Utile si un UI panel doit re-render quand le patient change.
     * Les tools de Phase D utilisent plutôt la propriété `current`
     * pour un accès synchrone.
     */
    val currentFlow: StateFlow<VictimProfileSnapshot?> = _currentFlow.asStateFlow()

    /**
     * Accès synchrone au patient actuel. Retourne null si aucun
     * fragment Live Scan n'est actif. Les tools doivent gérer ce
     * cas en retournant `{"error":"no_patient_loaded"}`.
     */
    val current: VictimProfileSnapshot?
        get() = _currentFlow.value

    /**
     * Set le patient actif. Appelé par JemmaLiveScanFragment.onViewCreated
     * APRÈS résolution du peerSid en VictimProfileSnapshot.
     *
     * Log volontairement verbose (compteurs allergies/médocs/conditions)
     * pour pouvoir tracer dans les logs que le bon patient a été chargé
     * — sans logger le nom (potentiellement PII).
     */
    fun setCurrent(snapshot: VictimProfileSnapshot) {
        val al = snapshot.allergies.size
        val md = snapshot.medications.size
        val cn = snapshot.conditions.size
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 👤 set · " +
                "name='${snapshot.displayName}' · al=$al md=$md cn=$cn",
        )
        _currentFlow.value = snapshot
    }

    /**
     * Clear le patient actif. À appeler dans onDestroyView du Fragment.
     * Idempotent : safe d'appeler plusieurs fois.
     *
     * Une fois clear, les tools Phase D retournent
     * `{"error":"no_patient_loaded"}` jusqu'au prochain setCurrent.
     */
    fun clear() {
        if (_currentFlow.value != null) {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔴 cleared")
        }
        _currentFlow.value = null
    }
}
