/*
 * PatientDetailFragment.kt — JEMMA Pass · JemmaAppDemo · v2.6.2
 *
 * Opened over the rescuer radar when the user taps a victim card. Shows
 * a glance-able sheet of the patient's _j 1.2 data : hero (emoji+name+
 * crit), 4 vital cells (sex, age, blood, triage), inline SALT row,
 * then the four code sections with KB-resolved labels in the device
 * locale.
 *
 * 🆕 v2.6.2 — Live OCR scan migration (Lot 2/4 — Plan B Gemma 4 Good).
 *   Le bouton "📷 Scan a medication" navigue désormais vers
 *   `JemmaLiveScanFragment` au lieu d'ouvrir la modale GmsDocumentScanner.
 *   Architecture cible : caméra live + ML Kit streaming + FTS5 sub-2ms
 *   sur la KB DIAMOND. Au retour (Lot 4), le live fragment appellera
 *   directement `medScanController.startWithImage()`, ce qui déclenchera
 *   notre observer `medScanController.state.collect` ci-dessous et
 *   ouvrira le bottom sheet — exactement comme avant le live scan,
 *   mais sans la modale GMS intermédiaire.
 *
 *   ⚠️ Lot 2 transitoire : le helper `buildVictimSnapshot()` et la
 *   variable `currentSnapshot` deviennent inutilisés (le live fragment
 *   reconstruit son propre snapshot). Ils sont gardés en place pour
 *   minimiser le surface change avant le hackathon. À supprimer
 *   post-hackathon en même temps que l'extraction du
 *   `VictimSnapshotResolver` Singleton partagé.
 *
 * 🔙 v2.6.1.1 — FULL DEBUG LOGGING for the killer demo flow.
 *   Every step of the scan flow now logs explicitly so we can diagnose
 *   "tap → nothing happens" by reading the logcat alone. Concretely :
 *     • Button wiring at onViewCreated (visibility, enabled, click attach)
 *     • Tap event with timestamp
 *     • Snapshot build (per-pillar parsing counts)
 *     • Each step transition in the pipeline (delegated to MedScanController)
 *   Tag : JEMMA-PATIENT (must be in capture_jemma.sh — see capture_jemma_v2.5.0.sh)
 *
 * 🔙 v2.6.1 — Killer demo pipeline UI (preserved from previous delivery).
 *   See 2.6.1 README for the full pipeline architecture.
 *
 * 🔙 v2.5.11.2 — Inline SALT row + KB hydration (unchanged in 2.6.1/2.6.1.1).
 *
 * Source of peer data : navArg `peerSid` (4-char SID). We look up the
 * peer via `RadarController.radarGetPeersJson()` and parse its JSON
 * entry. SALT triage state via `triageGetEventForJson(sid)`.
 *
 * Log channel : JEMMA-PATIENT
 */
package be.heyman.android.jemmapassdemo.ui.radar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.medscan.MedScanController
import be.heyman.android.jemmapassdemo.ai.medscan.MedScanPipelineState
import be.heyman.android.jemmapassdemo.ai.livescan.LiveScanRepository
import be.heyman.android.jemmapassdemo.ai.livescan.LiveScanState
import be.heyman.android.jemmapassdemo.ai.livescan.Severity
import be.heyman.android.jemmapassdemo.ai.medscan.StepKey
import be.heyman.android.jemmapassdemo.ai.medscan.StepLifecycle
import be.heyman.android.jemmapassdemo.ai.medscan.StepRow
import be.heyman.android.jemmapassdemo.ai.medscan.Verdict
import be.heyman.android.jemmapassdemo.ai.medscan.VictimProfileSnapshot
import be.heyman.android.jemmapassdemo.databinding.FragmentPatientDetailBinding
import be.heyman.android.jemmapassdemo.databinding.ItemMedscanStepBinding
import be.heyman.android.jemmapassdemo.databinding.ViewMedscanPipelineBinding
import be.heyman.android.jemmapassdemo.databinding.ViewVitalCellBinding
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.ResolvedConcept
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JCondition
import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.radar.RadarController
import be.heyman.android.jemmapassdemo.sos.JemmaDeviceId
import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@AndroidEntryPoint
class PatientDetailFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-PATIENT"

        // 🆕 v4.2 HOTFIX-RESET-PATIENT — mémorisation du dernier peerSid
        // affiché à travers toute l'app. Permet de détecter le changement
        // de patient et reset le LiveScanRepo (Singleton, sinon le verdict
        // du précédent patient pollue l'écran du nouveau).
        @Volatile
        private var lastViewedPeerSid: String? = null

        // Same SALT meta as VictimCardAdapter (Plan A buildSaltRow parity).
        private val SALT_META = listOf(
            SaltMeta("WAIT", "⏳", "#9E9E9E", false),
            SaltMeta("EVAL", "🔍", "#FFC107", false),
            SaltMeta("STAB", "✅", "#4CAF50", false),
            SaltMeta("HELP", "🆘", "#F44336", false),
            SaltMeta("EVAC", "🚑", "#2196F3", true),
            SaltMeta("DCD",  "🕊\uFE0F", "#FFFFFF", true),
        )

        private data class SaltMeta(
            val code: String,
            val emoji: String,
            val colorHex: String,
            val needsConfirm: Boolean,
        )
    }

    @Inject lateinit var radar: RadarController
    @Inject lateinit var kb: KnowledgeBaseService
    @Inject lateinit var medScanController: MedScanController

    // 🆕 FIX4 — Observer du pipeline LiveScan v4 (parallèle au medScan legacy)
    @Inject lateinit var liveScanRepo: LiveScanRepository

    private val docScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uri = scanResult?.pages?.firstOrNull()?.imageUri
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📄 doc scan OK · uri=$uri")
            if (uri != null) {
                val snapshot = buildVictimSnapshot()
                if (snapshot != null) {
                    medScanController.startWithImage(uri, snapshot, uiLang)
                } else {
                    Log.w(TAG, "⚠️ buildVictimSnapshot returned null, cannot start MedScanController")
                }
            }
        } else {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🚫 doc scan cancelled")
        }
    }

    private var _binding: FragmentPatientDetailBinding? = null
    private val binding get() = _binding!!

    /** Inflated lazily into patient_scan_result_container on first scan tap. */
    private var medScanBinding: ViewMedscanPipelineBinding? = null

    /** 🆕 FIX4 — Inflated lazily for LiveScan v4 pipeline (separate panel). */
    private var liveScanPanelView: View? = null

    // 🆕 v4.2 UX-COLLAPSE — animator pour le caret clignotant pendant streaming
    // Phase D. Démarré quand l'UI affiche un RenderedWithVerdict avec
    // explanationDone=false, stoppé sinon.
    private var streamCursorAnimator: ValueAnimator? = null

    /** Snapshot of the current peer's profile — captured on each scan kick-off. */
    private var currentSnapshot: VictimProfileSnapshot? = null

    private val peerSid: String by lazy { arguments?.getString("peerSid") ?: "" }
    private val uiLang: String by lazy {
        Locale.getDefault().language.take(2).ifBlank { "en" }
    }

    /** Cached display name for confirm dialogs. */
    private var peerDisplayName: String = ""

    // ── 🆕 v2.6.2 — DocumentScanner launcher removed ─────────────────────
    //
    // The GmsDocumentScanner modal flow has been migrated to a live
    // CameraX OCR flow in `JemmaLiveScanFragment`. `handleScanMedTap()`
    // below now just navigates to that destination — the live fragment
    // handles camera, OCR streaming, candidate detection, lock and
    // high-res capture, then invokes `medScanController.startWithImage()`
    // itself and navigateUp()s. Our `medScanController.state.collect`
    // observer (attached in `onViewCreated` below) will see the resulting
    // state update when this Fragment resumes from `STARTED`, and will
    // open the bottom-sheet `dialog_med_scan` automatically.
    //
    // The helper `buildVictimSnapshot()` further down is now dead code
    // (the live fragment builds its own snapshot from `peerSid`). Kept
    // in place for v2.6.2 transitional safety; to be removed in v2.7
    // along with extracting the parsing into a shared
    // `VictimSnapshotResolver` @Singleton.

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView · peerSid=$peerSid · uiLang=$uiLang")
        _binding = FragmentPatientDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onViewCreated · peerSid=$peerSid · " +
            "uiLang=$uiLang · activity=${activity?.javaClass?.simpleName}")

        // 🆕 v4.2 HOTFIX-RESET-PATIENT — si on entre dans un patient
        // différent du précédent, reset le LiveScanRepo Singleton sinon le
        // verdict du précédent scan reste à l'écran. Cross-pollution
        // dangereuse au pitch (verdict patient A affiché pour patient B).
        //
        // Premier lancement : previous=null → pas de reset (repo déjà vide).
        // Même patient (back depuis live scan) : pas de reset, on préserve
        // le verdict en cours d'affichage.
        // Nouveau patient : reset.
        val previousPeer = lastViewedPeerSid
        if (peerSid.isNotBlank() && previousPeer != null && previousPeer != peerSid) {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔄 patient changed " +
                "($previousPeer → $peerSid) — resetting live scan state")
            liveScanRepo.resetAsync()
            // detachLiveScanPanel ne suffit pas car le panel sera ré-attaché
            // dès la prochaine émission RenderedWithVerdict du précédent
            // patient. Le reset au repo coupe la source.
        }
        lastViewedPeerSid = peerSid

        binding.patientBtnClose.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 CLOSE tap")
            findNavController().navigateUp()
        }

        // 🆕 v2.6.1.1 — verbose button wiring
        val btn = binding.patientBtnScanMed
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 wiring patient_btn_scan_med · " +
            "class=${btn.javaClass.simpleName} · visible=${btn.visibility} " +
            "(VISIBLE=${View.VISIBLE}) · enabled=${btn.isEnabled} · " +
            "clickable=${btn.isClickable} · alpha=${btn.alpha}")
        btn.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 SCAN MEDOC tap · " +
                "btnEnabled=${btn.isEnabled} · btnClickable=${btn.isClickable} · " +
                "fragmentResumed=${isResumed} · viewAttached=${view.isAttachedToWindow}")
            handleScanMedTap()
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 patient_btn_scan_med listener attached")

        // Observe the med-scan controller's state.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 starting medScanController.state.collect")
                medScanController.state.collect { state ->
                    Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔄 state update · " +
                        "isActive=${state.isActive} · steps=${state.steps.size} · " +
                        "verdict=${state.verdict::class.simpleName} · totalDur=${state.totalDurationMs}ms")
                    if (state.isActive || state.verdict !is Verdict.None) {
                        ensureMedScanPanelAttached()
                        renderMedScanState(state)
                    } else {
                        detachMedScanPanel()
                    }
                }
            }
        }

        // 🆕 FIX4 — Observe the LiveScan v4 pipeline in parallel.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 starting liveScanRepo.state.collect")
                liveScanRepo.state.collect { state ->
                    Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔄 livescan state → ${state::class.simpleName}")
                    when (state) {
                        is LiveScanState.IdentifyingWithGemma,
                        is LiveScanState.CrossChecking,
                        is LiveScanState.RenderedWithVerdict,
                        is LiveScanState.NoSafeFound -> {
                            ensureLiveScanPanelAttached()
                            renderLiveScanState(state)
                        }
                        is LiveScanState.Error -> {
                            Toast.makeText(requireContext(), state.reason, Toast.LENGTH_LONG).show()
                            detachLiveScanPanel()
                        }
                        is LiveScanState.Accumulating -> {
                            // Phase A se passe sur l'écran caméra, pas sur PatientDetail.
                            // Si on est revenu ici en Accumulating c'est un reset post-scan.
                            detachLiveScanPanel()
                        }
                    }
                }
            }
        }

        renderPeer()
    }

    /**
     * Locate the peer in `radarGetPeersJson()` and bind every field of
     * the layout. Falls back to "(unknown)" placeholders if the peer
     * vanished between the tap and the navigation.
     */
    private fun renderPeer() {
        val raw = radar.radarGetPeersJson()
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 renderPeer · peerSid=$peerSid · " +
            "peers.length=${arr.length()}")
        var match: JSONObject? = null
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val full = p.optString("sessionId", "")
            if (full == peerSid || full.take(4) == peerSid) {
                match = p
                break
            }
        }
        if (match == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ peer sid=$peerSid not found in snapshot")
            binding.patientHeroEmoji.text = "❓"
            binding.patientHeroName.text = getString(R.string.victim_anon)
            binding.patientHeroSubline.text = "#$peerSid"
            bindVital(binding.patientVitalSex.root,    "SEX",    "—")
            bindVital(binding.patientVitalAge.root,    "AGE",    "—")
            bindVital(binding.patientVitalBlood.root,  "BLOOD",  "—")
            bindVital(binding.patientVitalTriage.root, "TRIAGE", "—")
            binding.patientSaltRow.visibility = View.GONE
            return
        }
        val p = match
        val name = p.optString("name", "").ifBlank { getString(R.string.victim_anon) }
        peerDisplayName = name
        val sex = p.optString("sex", "?").firstOrNull() ?: '?'
        val ageDecade = p.optInt("ageDecade", 0)
        val ageExact = p.optInt("ageExact", ageDecade * 10)
        val blood = p.optString("bloodType", "").ifBlank { "—" }
        val langCode = p.optString("langCode", "")

        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 renderPeer match found · " +
            "name='$name' sex=$sex age=$ageExact blood=$blood lang='$langCode' · " +
            "al=${p.optJSONArray("allergies")?.length()} md=${p.optJSONArray("medications")?.length()} " +
            "cn=${p.optJSONArray("conditions")?.length()} im=${p.optJSONArray("immunizations")?.length()}")

        // Hero
        binding.patientHeroEmoji.text = sexAgeEmoji(sex, ageDecade)
        binding.patientHeroName.text = name
        binding.patientHeroSubline.text = buildString {
            append("#$peerSid")
            if (langCode.isNotBlank()) append(" · 🗣 ${langCode.uppercase()}")
        }

        // Vital cells
        val sexLabel = when (sex) { 'M', 'm' -> "♂"; 'F', 'f' -> "♀"; else -> "•" }
        bindVital(binding.patientVitalSex.root,    "SEX",    sexLabel)
        bindVital(binding.patientVitalAge.root,    "AGE",
                  if (ageExact > 0) "$ageExact" else "—")
        bindVital(binding.patientVitalBlood.root,  "BLOOD",  blood)
        refreshTriageCell()

        // SALT inline row
        bindSaltRow()

        // Code sections — hydration is async because the KB is in SQLite.
        renderRawFirst(binding.patientAllergiesList,    p.optJSONArray("allergies"))
        renderRawFirst(binding.patientMedicationsList,  p.optJSONArray("medications"))
        renderRawFirst(binding.patientConditionsList,   p.optJSONArray("conditions"))
        renderRawFirst(binding.patientImmunizationsList, p.optJSONArray("immunizations"))

        viewLifecycleOwner.lifecycleScope.launch {
            renderCodeListHydrated(
                container = binding.patientAllergiesList,
                arr = p.optJSONArray("allergies"),
                primarySystem = KnowledgeBaseService.SYSTEM_SNOMED,
                tryDrug = false,
            )
            renderCodeListHydrated(
                container = binding.patientMedicationsList,
                arr = p.optJSONArray("medications"),
                primarySystem = KnowledgeBaseService.SYSTEM_RXNORM,
                tryDrug = true,
            )
            renderCodeListHydrated(
                container = binding.patientConditionsList,
                arr = p.optJSONArray("conditions"),
                primarySystem = KnowledgeBaseService.SYSTEM_SNOMED,
                tryDrug = false,
            )
            renderCodeListHydrated(
                container = binding.patientImmunizationsList,
                arr = p.optJSONArray("immunizations"),
                primarySystem = KnowledgeBaseService.SYSTEM_SNOMED,
                tryDrug = false,
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  🆕 v2.6.1 — Med-scan pipeline wiring (with FULL DEBUG LOGGING in v2.6.1.1)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Build a [VictimProfileSnapshot] from the current peer's JSON. We do
     * a fresh lookup every scan to capture any radar-mesh updates.
     */
    private fun buildVictimSnapshot(): VictimProfileSnapshot? {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎬 buildVictimSnapshot · peerSid=$peerSid")
        val raw = radar.radarGetPeersJson()
        val arr = try { JSONArray(raw) } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ radarGetPeersJson JSON parse FAIL · raw=${raw.take(200)}", e)
            return null
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎬   ↳ peers.length=${arr.length()}")
        var match: JSONObject? = null
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val full = p.optString("sessionId", "")
            if (full == peerSid || full.take(4) == peerSid) {
                match = p
                break
            }
        }
        if (match == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ snapshot peer sid=$peerSid NOT FOUND in ${arr.length()} peers")
            return null
        }

        val allergies = parsePillarToAllergies(match.optJSONArray("allergies"))
        val medications = parsePillarToMedications(match.optJSONArray("medications"))
        val conditions = parsePillarToConditions(match.optJSONArray("conditions"))
        val display = match.optString("name", "").ifBlank { peerDisplayName.ifBlank { "?" } }

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎬   ↳ snapshot OK · " +
            "name='$display' al=${allergies.size} md=${medications.size} cn=${conditions.size}")
        return VictimProfileSnapshot(
            displayName = display,
            allergies = allergies,
            medications = medications,
            conditions = conditions,
        )
    }

    private fun parsePillarToAllergies(arr: JSONArray?): List<JAllergy> {
        if (arr == null) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}]    ↳ allergies array null → empty list")
            return emptyList()
        }
        val out = mutableListOf<JAllergy>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.optString("code", "").ifBlank { o.optString("c", "") }
            if (code.isBlank()) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}]    ⚠ allergy[$i] no code · keys=${o.keys().asSequence().toList()}")
                continue
            }
            out.add(JAllergy(
                c = code,
                s = o.optString("criticality", "").firstOrNull()?.toString(),
                st = o.optString("status", "").firstOrNull()?.toString(),
                d = o.optString("details", "").ifBlank { null },
                m = o.optString("mechanism", "").ifBlank { null },
                displayLabel = o.optString("display", "").ifBlank { o.optString("name", "").ifBlank { null } },
            ))
        }
        Log.d(TAG, "[t=${System.currentTimeMillis()}]    ↳ parsed ${out.size}/${arr.length()} allergies")
        return out
    }

    private fun parsePillarToMedications(arr: JSONArray?): List<JMedication> {
        if (arr == null) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}]    ↳ medications array null → empty list")
            return emptyList()
        }
        val out = mutableListOf<JMedication>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.optString("code", "").ifBlank { o.optString("c", "") }
            if (code.isBlank()) continue
            out.add(JMedication(
                c = code,
                t = o.optString("timing", "").ifBlank { null },
                r = o.optString("route", "").firstOrNull()?.toString(),
                v = o.optString("doseValue", "").ifBlank { null },
                u = o.optString("doseUnit", "").ifBlank { null },
                rs = o.optString("reasonSource", "").ifBlank { null },
                rc = o.optString("reasonCode", "").ifBlank { null },
                displayLabel = o.optString("display", "").ifBlank { o.optString("name", "").ifBlank { null } },
            ))
        }
        Log.d(TAG, "[t=${System.currentTimeMillis()}]    ↳ parsed ${out.size}/${arr.length()} medications")
        return out
    }

    private fun parsePillarToConditions(arr: JSONArray?): List<JCondition> {
        if (arr == null) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}]    ↳ conditions array null → empty list")
            return emptyList()
        }
        val out = mutableListOf<JCondition>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.optString("code", "").ifBlank { o.optString("c", "") }
            if (code.isBlank()) continue
            out.add(JCondition(
                c = code,
                s = o.optString("severity", "").firstOrNull()?.toString(),
                st = o.optString("status", "").firstOrNull()?.toString(),
                d = o.optString("details", "").ifBlank { null },
                rs = o.optString("reasonSource", "").ifBlank { null },
                rc = o.optString("reasonCode", "").ifBlank { null },
                displayLabel = o.optString("display", "").ifBlank { o.optString("name", "").ifBlank { null } },
            ))
        }
        Log.d(TAG, "[t=${System.currentTimeMillis()}]    ↳ parsed ${out.size}/${arr.length()} conditions")
        return out
    }

    /**
     * 🆕 v2.6.2 — Navigates to the live OCR scan fragment instead of
     * opening the GmsDocumentScanner modal. The live fragment handles
     * the camera, OCR streaming, candidate detection, lock and
     * high-res capture, then invokes `medScanController.startWithImage()`
     * itself and `navigateUp()`s. Our `medScanController.state.collect`
     * observer in `onViewCreated` will pick up the state update when
     * this Fragment resumes from STARTED and open the bottom-sheet
     * `dialog_med_scan`.
     */
    private fun handleScanMedTap() {
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🎬 handleScanMedTap → launching document scanner · peerSid=$peerSid",
        )
        if (peerSid.isBlank()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ blank peerSid, cannot scan")
            Toast.makeText(
                requireContext(),
                "⚠️ Profil victime introuvable",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setPageLimit(1)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(requireActivity())
            .addOnSuccessListener { intentSender ->
                val request = IntentSenderRequest.Builder(intentSender).build()
                docScanLauncher.launch(request)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ getStartScanIntent failed", e)
                Toast.makeText(requireContext(), getString(R.string.scanner_unavailable, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
    }

    /** Lazily inflate the pipeline panel into patient_scan_result_container. */
    private fun ensureMedScanPanelAttached() {
        if (medScanBinding != null) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 panel already attached, skip")
            return
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 attaching medscan panel into patient_scan_result_container")
        val container = binding.patientScanResultContainer
        container.removeAllViews()
        val inflated = ViewMedscanPipelineBinding.inflate(
            LayoutInflater.from(container.context), container, true,
        )
        medScanBinding = inflated
        inflated.medscanBtnClose.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 medscan panel close tap")
            medScanController.reset()
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋   ↳ panel attached")
    }

    private fun detachMedScanPanel() {
        if (medScanBinding == null) return
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 detaching medscan panel")
        binding.patientScanResultContainer.removeAllViews()
        medScanBinding = null
    }

    // ─── 🆕 FIX4 : LiveScan v4 panel ─────────────────────────────────

    private fun ensureLiveScanPanelAttached() {
        if (liveScanPanelView != null) return
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 attaching livescan v4 panel")
        val container = binding.patientScanResultContainer
        // Si medscan legacy panel attaché, on le détache (mutex)
        if (medScanBinding != null) detachMedScanPanel()
        container.removeAllViews()
        val inflated = LayoutInflater.from(container.context)
            .inflate(R.layout.view_livescan_pipeline, container, true)
        liveScanPanelView = inflated
        inflated.findViewById<View>(R.id.livescan_panel_btn_close).setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 livescan panel close tap")
            liveScanRepo.resetAsync()
            detachLiveScanPanel()
        }

        // 🆕 v4.2 UX-COLLAPSE — wire click listeners pour toggle chaque zone.
        // Tap sur header → toggle visibility content + flip chevron ▼/▶.
        // Permet aux juges de voir le détail step-by-step pendant le scan,
        // puis de plier les zones une fois terminées pour ne garder que
        // le verdict + streaming.
        setupZoneToggle(
            inflated,
            R.id.livescan_zone_ocr_header,
            R.id.livescan_zone_ocr_chevron,
            R.id.livescan_zone_ocr_content,
        )
        setupZoneToggle(
            inflated,
            R.id.livescan_zone_kb_header,
            R.id.livescan_zone_kb_chevron,
            R.id.livescan_zone_kb_content,
        )
        setupZoneToggle(
            inflated,
            R.id.livescan_zone_gemma_header,
            R.id.livescan_zone_gemma_chevron,
            R.id.livescan_zone_gemma_content,
        )
        setupZoneToggle(
            inflated,
            R.id.livescan_zone_xcheck_header,
            R.id.livescan_zone_xcheck_chevron,
            R.id.livescan_zone_xcheck_content,
        )
        setupZoneToggle(
            inflated,
            R.id.livescan_zone_stream_header,
            R.id.livescan_zone_stream_chevron,
            R.id.livescan_zone_stream_content,
        )
    }

    /**
     * 🆕 v4.2 UX-COLLAPSE — wire un header cliquable qui toggle visibility
     * du content + flip chevron ▼/▶. Idempotent : setupZoneToggle peut être
     * appelé plusieurs fois sans empiler les listeners (replace l'existant).
     */
    private fun setupZoneToggle(root: View, headerId: Int, chevronId: Int, contentId: Int) {
        val headerView = root.findViewById<View>(headerId) ?: return
        val chevronView = root.findViewById<TextView>(chevronId) ?: return
        val contentView = root.findViewById<View>(contentId) ?: return
        headerView.setOnClickListener {
            val isVisible = contentView.visibility == View.VISIBLE
            contentView.visibility = if (isVisible) View.GONE else View.VISIBLE
            chevronView.text = if (isVisible) "▶" else "▼"
            val newState = if (isVisible) "COLLAPSED" else "EXPANDED"
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔽 zone toggle " +
                "headerId=$headerId · now=$newState")
        }
    }

    /**
     * 🆕 v4.2 UX-COLLAPSE — démarre l'animation caret clignotant sur la
     * vue passée en paramètre. Idempotent : si déjà running, on stoppe
     * l'ancien et on relance proprement.
     */
    private fun startStreamCursorBlink(cursorView: TextView) {
        streamCursorAnimator?.cancel()
        cursorView.visibility = View.VISIBLE
        cursorView.alpha = 1f
        streamCursorAnimator = ValueAnimator.ofFloat(1f, 0.15f, 1f).apply {
            duration = 800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                cursorView.alpha = anim.animatedValue as Float
            }
            start()
        }
    }

    /**
     * 🆕 v4.2 UX-COLLAPSE — stoppe l'animation caret et cache la vue.
     */
    private fun stopStreamCursorBlink(cursorView: TextView) {
        streamCursorAnimator?.cancel()
        streamCursorAnimator = null
        cursorView.visibility = View.GONE
    }

    /**
     * 🆕 v4.2 UX-COLLAPSE — scroll le NestedScrollView du panneau au bas.
     * Appelé à chaque émission streaming pour garder le dernier token visible.
     * Idempotent et safe si scroll absent.
     */
    private fun autoScrollLiveScanPanel() {
        val scroll = liveScanPanelView?.findViewById<NestedScrollView>(R.id.livescan_panel_scroll)
            ?: return
        scroll.post {
            scroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun detachLiveScanPanel() {
        if (liveScanPanelView == null) return
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 detaching livescan v4 panel")
        // 🆕 v4.2 UX-COLLAPSE — stop l'animator avant de drop la vue.
        streamCursorAnimator?.cancel()
        streamCursorAnimator = null
        binding.patientScanResultContainer.removeAllViews()
        liveScanPanelView = null
        binding.jemmaVoiceNotification.visibility = View.GONE
    }

    private fun renderLiveScanState(state: LiveScanState) {
        val root = liveScanPanelView ?: return
        val header = root.findViewById<TextView>(R.id.livescan_panel_header)
        val banner = root.findViewById<TextView>(R.id.livescan_panel_banner)
        val badges = root.findViewById<LinearLayout>(R.id.livescan_panel_badges)
        val stream = root.findViewById<TextView>(R.id.livescan_panel_stream)
        val safeAlt = root.findViewById<TextView>(R.id.livescan_panel_safe_alt)

        // 🆕 FIX7 — 5 verbose zones for judges
        val zoneOcr = root.findViewById<LinearLayout>(R.id.livescan_zone_ocr)
        val zoneOcrContent = root.findViewById<TextView>(R.id.livescan_zone_ocr_content)
        val zoneKb = root.findViewById<LinearLayout>(R.id.livescan_zone_kb)
        val zoneKbContent = root.findViewById<TextView>(R.id.livescan_zone_kb_content)
        val zoneGemma = root.findViewById<LinearLayout>(R.id.livescan_zone_gemma)
        val zoneGemmaContent = root.findViewById<TextView>(R.id.livescan_zone_gemma_content)
        val zoneXcheck = root.findViewById<LinearLayout>(R.id.livescan_zone_xcheck)
        val zoneTts = root.findViewById<TextView>(R.id.livescan_zone_tts)
        val zoneStream = root.findViewById<LinearLayout>(R.id.livescan_zone_stream)

        when (state) {
            is LiveScanState.IdentifyingWithGemma -> {
                header.text = "💊 …"
                banner.text = getString(R.string.livescan_panel_phase_identifying)
                banner.setBackgroundColor(0xFF1E40AF.toInt())
                badges.removeAllViews()

                // Zone 1 OCR : visible dès Phase B (on a le payload)
                zoneOcr.visibility = View.VISIBLE
                zoneOcrContent.text = state.payload.ocrTexts.mapIndexed { i, t ->
                    "Frame $i: \"${t.replace("\n", " | ").take(120)}\""
                }.joinToString("\n")

                // Zone 2 KB : visible dès Phase B
                zoneKb.visibility = View.VISIBLE
                val candDisplays = state.payload.candidates
                    .sortedByDescending { it.frequency }
                    .take(10)
                zoneKbContent.text = if (candDisplays.isEmpty()) {
                    "(no candidates found by FTS5)"
                } else {
                    candDisplays.joinToString("\n") { c ->
                        "${c.atc.padEnd(8)} ${c.displayName.take(40)} · freq=${c.frequency}"
                    }
                }

                // 🆕 v4.2 UX-B1-STREAM — Zone Gemma visible pendant Phase B1
                // pour afficher le JSON {"boxes":[...]} qui se construit
                // token-par-token via askStreaming. Effet visuel "Gemma
                // raisonne en direct" pour les juges.
                if (state.b1StreamingText.isNotEmpty()) {
                    zoneGemma.visibility = View.VISIBLE
                    zoneGemmaContent.text = state.b1StreamingText
                    // Caret dédié dans le header de zone Gemma (cursor B1).
                    // Quand B1 finit → Phase C démarre → state ≠ IdentifyingWithGemma →
                    // le cursor est stoppé par les autres branches du when().
                    val gemmaCursor = root.findViewById<TextView>(R.id.livescan_zone_gemma_cursor)
                    if (gemmaCursor != null) {
                        startStreamCursorBlink(gemmaCursor)
                        autoScrollLiveScanPanel()
                    }
                } else {
                    zoneGemma.visibility = View.GONE
                }

                // Zones 4/5 cachées
                zoneXcheck.visibility = View.GONE
                zoneStream.visibility = View.GONE
                safeAlt.visibility = View.GONE
            }
            is LiveScanState.CrossChecking -> {
                header.text = "💊 ${state.drugName} (${state.bestCode})"
                banner.text = getString(R.string.livescan_panel_phase_crosschecking)
                banner.setBackgroundColor(0xFF7C3AED.toInt())
                badges.removeAllViews()

                // Zones 1/2 toujours visibles (déjà remplies en Phase B)
                // Zone 3 Gemma : on n'a pas encore la reason ici (vient en Phase C complete)
                // donc on montre juste le ATC lock
                zoneGemma.visibility = View.VISIBLE
                zoneGemmaContent.text = "🔒 ${state.bestCode} (${state.drugName})"

                zoneXcheck.visibility = View.GONE
                zoneStream.visibility = View.GONE
                safeAlt.visibility = View.GONE
            }
            is LiveScanState.RenderedWithVerdict -> {
                val report = state.crossCheckReport
                val severity = report.overall
                val (bgColor, prefix) = when (severity) {
                    Severity.MAJOR -> 0xFFDC2626.toInt() to "🚨"
                    Severity.MODERATE -> 0xFFEA580C.toInt() to "⚠️"
                    Severity.MINOR -> 0xFFCA8A04.toInt() to "🟡"
                    Severity.NONE -> 0xFF15803D.toInt() to "✅"
                }
                header.text = "💊 ${state.drugName} (${state.bestCode})"
                banner.setBackgroundColor(bgColor)
                banner.text = "$prefix ${state.ttsStaticPhrase}"

                // Zone 1 OCR — remplir depuis le state si pas encore fait (re-attach après nav)
                if (state.ocrFrames.isNotEmpty()) {
                    zoneOcr.visibility = View.VISIBLE
                    zoneOcrContent.text = state.ocrFrames.mapIndexed { i, t ->
                        "Frame $i: \"${t.replace("\n", " | ").take(120)}\""
                    }.joinToString("\n")
                }

                // Zone 2 KB candidates
                if (state.kbCandidates.isNotEmpty()) {
                    zoneKb.visibility = View.VISIBLE
                    zoneKbContent.text = state.kbCandidates.take(10).joinToString("\n") { (atc, name) ->
                        "${atc.padEnd(8)} ${name.take(50)}"
                    }
                }

                // Zone 3 Gemma decision + reason + duration
                zoneGemma.visibility = View.VISIBLE
                val reasonShown = state.lockReason.ifBlank { "(no reason provided by Gemma)" }
                zoneGemmaContent.text = "🔒 ${state.bestCode} · ${state.phaseBDurationMs}ms\n💭 \"$reasonShown\""

                // Zone 4 Cross-check verdict + badges + TTS
                zoneXcheck.visibility = View.VISIBLE
                badges.removeAllViews()
                report.allergyHits.forEach { h ->
                    badges.addView(buildLiveScanBadge("🤧 ${h.patientAllergyName}", h.severity))
                }
                report.ddiHits.forEach { h ->
                    badges.addView(buildLiveScanBadge("💊 ${h.withDrugName}", h.severity))
                }
                report.conditionHits.forEach { h ->
                    badges.addView(buildLiveScanBadge("🩺 ${h.conditionName}", h.severity))
                }
                if (report.allergyHits.isEmpty() && report.ddiHits.isEmpty() && report.conditionHits.isEmpty()) {
                    badges.addView(buildLiveScanBadge(
                        "✓ ${getString(R.string.livescan_verdict_safe)}",
                        Severity.NONE,
                    ))
                }
                val durationSuffix = if (state.phaseCDurationMs > 0) " · ${state.phaseCDurationMs}ms" else ""
                zoneTts.text = "🔊 \"${state.ttsStaticPhrase}\"$durationSuffix"

                // Zone 5 streaming explanation (live)
                zoneStream.visibility = View.VISIBLE
                val notifCard = binding.jemmaVoiceNotification
                val notifBody = binding.jemmaNotificationBody
                val streamingText = state.streamingExplanationText

                if (streamingText.isNotEmpty()) {
                    if (notifCard.visibility != View.VISIBLE) {
                        notifCard.alpha = 0f
                        notifCard.visibility = View.VISIBLE
                        notifCard.animate().alpha(0.85f).setDuration(300).start()
                    }
                    notifBody.text = streamingText
                    stream.text = if (state.explanationDone) streamingText else "🔊 Explication vocale en cours..."
                } else {
                    notifCard.visibility = View.GONE
                    stream.text = if (state.explanationDone) streamingText else getString(R.string.livescan_explain_waiting)
                }

                // 🆕 v4.2 UX-COLLAPSE — caret clignotant pendant streaming
                // + auto-scroll au bas pour que le dernier token reste visible.
                // Quand stream done, cursor caché et on n'auto-scroll plus.
                val cursorView = root.findViewById<TextView>(R.id.livescan_zone_stream_cursor)
                if (cursorView != null) {
                    if (!state.explanationDone) {
                        startStreamCursorBlink(cursorView)
                        autoScrollLiveScanPanel()
                    } else {
                        stopStreamCursorBlink(cursorView)
                    }
                }

                if (state.safeAlternative != null) {
                    safeAlt.visibility = View.VISIBLE
                    safeAlt.text = getString(
                        R.string.livescan_safe_alt,
                        state.safeAlternative.displayName,
                        state.safeAlternative.atc,
                    )
                } else {
                    safeAlt.visibility = View.GONE
                }
            }
            is LiveScanState.NoSafeFound -> {
                header.text = "⚠️ ${state.bestCode}"
                banner.text = getString(R.string.livescan_no_safe_banner)
                banner.setBackgroundColor(0xFFDC2626.toInt())
                badges.removeAllViews()
                zoneXcheck.visibility = View.VISIBLE
                badges.addView(buildLiveScanBadge(
                    getString(R.string.livescan_no_safe_found, state.candidatesTested),
                    Severity.MAJOR,
                ))
                zoneStream.visibility = View.VISIBLE
                stream.text = getString(R.string.livescan_no_safe_advise)
                safeAlt.visibility = View.GONE
            }
            else -> {
                // Accumulating / Error : rien à rendre côté PatientDetail
            }
        }
    }

    private fun buildLiveScanBadge(text: String, severity: Severity): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            setPadding(16, 8, 16, 8)
            setTextColor(Color.WHITE)
            val bg = when (severity) {
                Severity.MAJOR -> 0xFFDC2626.toInt()
                Severity.MODERATE -> 0xFFEA580C.toInt()
                Severity.MINOR -> 0xFFCA8A04.toInt()
                Severity.NONE -> 0xFF15803D.toInt()
            }
            setBackgroundColor(bg)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.setMargins(0, 4, 0, 4)
            layoutParams = lp
        }
    }

    /** Diff state against the rendered views and update what changed. */
    private fun renderMedScanState(state: MedScanPipelineState) {
        val mb = medScanBinding ?: run {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ renderMedScanState but panel binding is null")
            return
        }

        // Total duration ticker
        mb.medscanHeaderTotal.text = if (state.totalDurationMs > 0) {
            "· ${formatDuration(state.totalDurationMs)}"
        } else ""

        val stepsContainer = mb.medscanStepsContainer
        val needed = state.steps.size
        while (stepsContainer.childCount < needed) {
            val itemBinding = ItemMedscanStepBinding.inflate(
                LayoutInflater.from(stepsContainer.context), stepsContainer, false,
            )
            stepsContainer.addView(itemBinding.root)
        }
        while (stepsContainer.childCount > needed) {
            stepsContainer.removeViewAt(stepsContainer.childCount - 1)
        }
        for ((i, step) in state.steps.withIndex()) {
            val rowView = stepsContainer.getChildAt(i)
            val rowBinding = ItemMedscanStepBinding.bind(rowView)
            bindStepRow(rowBinding, step)
        }

        renderVerdict(mb, state.verdict)
    }

    private fun bindStepRow(rb: ItemMedscanStepBinding, step: StepRow) {
        rb.medscanStepEmoji.text = step.key.emoji
        rb.medscanStepLabel.text = getString(step.labelResId)
        rb.medscanStepState.text = when (step.state) {
            StepLifecycle.PENDING -> "⏳"
            StepLifecycle.RUNNING -> "🔄"
            StepLifecycle.OK -> "✅"
            StepLifecycle.WARN -> "⚠️"
            StepLifecycle.FAIL -> "❌"
        }
        val det = step.detail
        if (det.isNullOrBlank()) {
            rb.medscanStepDetail.visibility = View.GONE
        } else {
            rb.medscanStepDetail.visibility = View.VISIBLE
            rb.medscanStepDetail.text = det
        }
        val dur = step.durationMs
        rb.medscanStepDur.text = if (dur != null && dur > 0) formatDuration(dur) else ""
        val labelColor = when (step.state) {
            StepLifecycle.PENDING -> 0xFF64748B.toInt()
            StepLifecycle.RUNNING -> 0xFFFBBF24.toInt()
            StepLifecycle.OK -> 0xFFE2E8F0.toInt()
            StepLifecycle.WARN -> 0xFFFB923C.toInt()
            StepLifecycle.FAIL -> 0xFFEF4444.toInt()
        }
        rb.medscanStepLabel.setTextColor(labelColor)
    }

    private fun renderVerdict(mb: ViewMedscanPipelineBinding, verdict: Verdict) {
        if (verdict is Verdict.None) {
            mb.medscanVerdictContainer.visibility = View.GONE
            return
        }
        mb.medscanVerdictContainer.visibility = View.VISIBLE
        val ctx = mb.medscanVerdictCard.context
        val (bgColor, title, body) = when (verdict) {
            is Verdict.Major -> Triple(0xFFB91C1C.toInt(), verdict.title, verdict.body)
            is Verdict.Moderate -> Triple(0xFFEA580C.toInt(), verdict.title, verdict.body)
            is Verdict.Minor -> Triple(0xFFCA8A04.toInt(), verdict.title, verdict.body)
            is Verdict.Clean -> Triple(
                0xFF15803D.toInt(),
                getString(R.string.medscan_verdict_clean_title),
                verdict.candidateDisplay,
            )
            is Verdict.NotIdentified -> Triple(
                0xFF475569.toInt(),
                getString(R.string.medscan_verdict_not_identified_title),
                verdict.reason,
            )
            is Verdict.Failed -> Triple(
                0xFF7F1D1D.toInt(),
                getString(R.string.medscan_verdict_failed_title),
                "${verdict.stage.emoji} ${verdict.stage.name} · ${verdict.reason}",
            )
            Verdict.None -> Triple(0, "", "")
            is Verdict.AgentText -> {
                // v2.6.2b — Gemma agent verdict (free-form text in victim's language).
                // Pick banner colour from keywords in the generated text :
                //   - "NE PAS DONNER" / "DO NOT ADMINISTER" / etc. → red (same as Major)
                //   - everything else → green (same as Clean)
                val upper = verdict.text.uppercase()
                val isDanger = "DO NOT ADMINISTER" in upper
                    || "NE PAS DONNER" in upper
                    || "NO ADMINISTRAR" in upper
                    || "投与しないで" in verdict.text
                    || "투여하지" in verdict.text
                val color = if (isDanger) 0xFFB91C1C.toInt() else 0xFF15803D.toInt()
                Triple(color, verdict.victimDisplayName, verdict.text)
            }
        }
        mb.medscanVerdictCard.background = verdictBackground(ctx, bgColor)
        mb.medscanVerdictTitle.text = title
        mb.medscanVerdictBody.text = body
        mb.medscanVerdictIcon.visibility = if (verdict is Verdict.AgentText) View.VISIBLE else View.GONE
    }

    private fun verdictBackground(ctx: Context, color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(ctx, 8).toFloat()
        }

    private fun formatDuration(ms: Long): String = when {
        ms < 1000 -> "${ms}ms"
        ms < 60_000 -> String.format(Locale.US, "%.1fs", ms / 1000.0)
        else -> "${ms / 1000}s"
    }

    // ──────────────────────────────────────────────────────────────────
    //  SALT row
    // ──────────────────────────────────────────────────────────────────

    private fun bindSaltRow() {
        val ctx = requireContext()
        val currentSalt = parseSaltCode(radar.triageGetEventForJson(peerSid))

        bindSaltButton(ctx, binding.patientSaltBtnWait, SALT_META[0], currentSalt)
        bindSaltButton(ctx, binding.patientSaltBtnEval, SALT_META[1], currentSalt)
        bindSaltButton(ctx, binding.patientSaltBtnStab, SALT_META[2], currentSalt)
        bindSaltButton(ctx, binding.patientSaltBtnHelp, SALT_META[3], currentSalt)
        bindSaltButton(ctx, binding.patientSaltBtnEvac, SALT_META[4], currentSalt)
        bindSaltButton(ctx, binding.patientSaltBtnDcd,  SALT_META[5], currentSalt)

        if (currentSalt == "DCD") {
            binding.patientSaltBtnCancelDcd.visibility = View.VISIBLE
            binding.patientSaltBtnCancelDcd.text =
                resources.getString(R.string.triage_btn_cancel_deceased)
            binding.patientSaltBtnCancelDcd.background = pillBackground(
                ctx, fillHex = "#1E293B", strokeHex = "#F44336", strokeDp = 2,
            )
            binding.patientSaltBtnCancelDcd.setOnClickListener {
                showConfirmDialog(
                    ctx = ctx,
                    title = getString(R.string.triage_confirm_title),
                    message = getString(
                        R.string.triage_confirm_msg,
                        "STAB", "✅", peerDisplayName.ifBlank { peerSid },
                    ),
                    onConfirm = { publishTriage("STAB", isExplicitOverride = true) },
                )
            }
        } else {
            binding.patientSaltBtnCancelDcd.visibility = View.GONE
            binding.patientSaltBtnCancelDcd.setOnClickListener(null)
        }
    }

    private fun bindSaltButton(
        ctx: Context,
        btn: TextView,
        meta: SaltMeta,
        currentSalt: String?,
    ) {
        val isActive = (currentSalt == meta.code)
        btn.text = meta.emoji
        btn.background = pillBackground(
            ctx,
            fillHex   = if (isActive) meta.colorHex else "#1E293B",
            strokeHex = meta.colorHex,
            strokeDp  = if (isActive) 2 else 1,
        )
        btn.setOnClickListener {
            if (meta.needsConfirm) {
                showConfirmDialog(
                    ctx = ctx,
                    title = getString(R.string.triage_confirm_title),
                    message = getString(
                        R.string.triage_confirm_msg,
                        meta.code, meta.emoji, peerDisplayName.ifBlank { peerSid },
                    ),
                    onConfirm = { publishTriage(meta.code, isExplicitOverride = false) },
                )
            } else {
                publishTriage(meta.code, isExplicitOverride = false)
            }
        }
    }

    private fun publishTriage(wireCode: String, isExplicitOverride: Boolean) {
        val tNow = System.currentTimeMillis()
        val mySid = JemmaDeviceId.get(requireContext())
        Log.i(TAG, "[t=$tNow] 🚑 patient-publish · victim=$peerSid status=$wireCode " +
            "rescuer=$mySid override=$isExplicitOverride")
        val wire = radar.triagePublishEvent(
            victimSid = peerSid,
            statusCode = wireCode,
            rescuerSid = mySid,
            isExplicitOverride = isExplicitOverride,
        )
        Log.i(TAG, "[t=$tNow]   ↳ $wire")
        Handler(Looper.getMainLooper()).post {
            bindSaltRow()
            refreshTriageCell()
        }
    }

    private fun refreshTriageCell() {
        val saltCode = parseSaltCode(radar.triageGetEventForJson(peerSid))
        val saltSpec = SaltUi.resolve(saltCode)
        val triageText = if (saltSpec != null) "${saltSpec.emoji} $saltCode" else "—"
        bindVital(binding.patientVitalTriage.root, "TRIAGE", triageText)
    }

    // ──────────────────────────────────────────────────────────────────
    //  Code list rendering — raw first, then hydrated async
    // ──────────────────────────────────────────────────────────────────

    private fun renderRawFirst(container: LinearLayout, arr: JSONArray?) {
        container.removeAllViews()
        if (arr == null || arr.length() == 0) {
            container.addView(makeRow("—", isPlaceholder = true))
            return
        }
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val code = entry.optString("code", "").ifBlank { entry.optString("c", "") }
            val provisional = entry.optString("display", "")
                .ifBlank { entry.optString("name", "") }
                .ifBlank { code }
                .ifBlank { "—" }
            container.addView(makeRow(provisional, isPlaceholder = false))
        }
    }

    private suspend fun renderCodeListHydrated(
        container: LinearLayout,
        arr: JSONArray?,
        primarySystem: String,
        tryDrug: Boolean,
    ) {
        if (arr == null || arr.length() == 0) return
        val deferred = (0 until arr.length()).map { i ->
            val entry = arr.optJSONObject(i)
            val code = entry?.optString("code", "")?.ifBlank { entry.optString("c", "") } ?: ""
            viewLifecycleOwner.lifecycleScope.async {
                resolveCodeLocalized(code, primarySystem, tryDrug)
            }
        }
        val hydrated = deferred.awaitAll()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? TextView ?: continue
            val display = hydrated.getOrNull(i) ?: continue
            if (display.isNotBlank() && display != child.text.toString().removePrefix("• ")) {
                child.text = "• $display"
            }
        }
    }

    private suspend fun resolveCodeLocalized(
        code: String,
        primarySystem: String,
        tryDrug: Boolean,
    ): String {
        if (code.isBlank()) return "—"
        var resolved: ResolvedConcept = kb.resolveByCode(code, primarySystem)
        if (resolved is ResolvedConcept.NotFound && tryDrug) {
            resolved = kb.resolveDrug(code)
        }
        if (resolved is ResolvedConcept.NotFound) {
            if (primarySystem == KnowledgeBaseService.SYSTEM_RXNORM) {
                resolved = kb.resolveByCode(code, KnowledgeBaseService.SYSTEM_SNOMED)
            }
        }
        val concept = resolved.concept ?: return code
        return kb.pickLocalizedDisplay(concept, uiLang).ifBlank { code }
    }

    private fun makeRow(text: String, isPlaceholder: Boolean): TextView =
        TextView(requireContext()).apply {
            this.text = if (isPlaceholder) text else "• $text"
            textSize = 13f
            setTextColor(if (isPlaceholder) 0xFF94A3B8.toInt() else 0xFFE2E8F0.toInt())
            setPadding(0, dp(3), 0, dp(3))
        }

    // ──────────────────────────────────────────────────────────────────
    //  Tiny helpers
    // ──────────────────────────────────────────────────────────────────

    private fun bindVital(rootView: View, label: String, value: String) {
        val vb = ViewVitalCellBinding.bind(rootView)
        vb.vitalCellLabel.text = label
        vb.vitalCellValue.text = value
    }

    private fun parseSaltCode(json: String): String? {
        return try {
            val o = JSONObject(json)
            if (o.length() == 0) null
            else o.optString("status", "").ifBlank { null }
        } catch (_: Exception) { null }
    }

    private fun pillBackground(
        ctx: Context,
        fillHex: String,
        strokeHex: String,
        strokeDp: Int,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.parseColor(fillHex))
        setStroke(dp(ctx, strokeDp), Color.parseColor(strokeHex))
        cornerRadius = dp(ctx, 8).toFloat()
    }

    private fun showConfirmDialog(
        ctx: Context,
        title: String,
        message: String,
        onConfirm: () -> Unit,
    ) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.triage_confirm_yes) { d, _ ->
                d.dismiss()
                onConfirm()
            }
            .setNegativeButton(R.string.triage_confirm_no) { d, _ -> d.dismiss() }
            .show()
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            ctx.resources.displayMetrics,
        ).toInt()

    private fun sexAgeEmoji(sex: Char?, ageDecade: Int?): String {
        val d = ageDecade ?: -1
        return when {
            d == 0 -> "\uD83D\uDC76"
            d == 1 -> when (sex) {
                'F', 'f' -> "\uD83D\uDC67"
                'M', 'm' -> "\uD83D\uDC66"
                else      -> "\uD83E\uDDD2"
            }
            d in 2..6 -> when (sex) {
                'F', 'f' -> "\uD83D\uDC69"
                'M', 'm' -> "\uD83D\uDC68"
                else      -> "\uD83E\uDDD1"
            }
            d >= 7 -> when (sex) {
                'F', 'f' -> "\uD83D\uDC75"
                'M', 'm' -> "\uD83D\uDC74"
                else      -> "\uD83E\uDDD3"
            }
            else -> "\uD83E\uDDD1"
        }
    }

    override fun onDestroyView() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onDestroyView · peerSid=$peerSid")
        medScanController.reset()
        medScanBinding = null
        // 🆕 v4.2 UX-COLLAPSE — stop l'animator pour éviter leak.
        streamCursorAnimator?.cancel()
        streamCursorAnimator = null
        // 🆕 v4.2 HOTFIX-PATIENT-DETAIL — Nullify liveScanPanelView ici,
        // sinon la référence stale survit à la destruction de la View hierarchy
        // (le Fragment instance est réutilisé entre nav back). Conséquence :
        // au 2e scan, `ensureLiveScanPanelAttached()` voit liveScanPanelView != null
        // et skip → panel jamais re-attaché → UI vide alors que le state
        // transitionne bien vers IdentifyingWithGemma/RenderedWithVerdict.
        liveScanPanelView = null
        super.onDestroyView()
        _binding = null
    }
}
