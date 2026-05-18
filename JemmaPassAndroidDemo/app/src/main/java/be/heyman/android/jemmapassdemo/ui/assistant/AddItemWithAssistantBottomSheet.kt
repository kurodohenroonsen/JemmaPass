/*
 * AddItemWithAssistantBottomSheet.kt — Lot 14.3 (PHASE 14) · DESIGN ONLY
 *
 * BottomSheetDialogFragment qui propose 4 modes Assistant + entrée
 * manuelle. Trigger : FAB + sur les écrans listes (Médicaments,
 * Allergies, Contacts, Conditions, ou un pilier stub).
 *
 * Pour ce Lot 14.3 (design only), le sheet :
 *   • Affiche le bon titre selon le pilier (passé en argument)
 *   • Logue chaque tap mode pour vérification visuelle
 *   • Affiche un Toast "design preview only" sur tap des modes
 *     assistant (photo/livescan/voice/text)
 *   • Manual mode : émet un callback `onManualPicked` pour que le
 *     Fragment appelant ouvre son form classique existant
 *
 * Le branchement réel des pipelines viendra au Lot 14.4+.
 *
 * Le caller fait par exemple :
 *   AddItemWithAssistantBottomSheet
 *       .newInstance(Pillar.MEDICATIONS)
 *       .setOnManualPicked { openClassicMedicationForm() }
 *       .show(parentFragmentManager, "add_med")
 */
package be.heyman.android.jemmapassdemo.ui.assistant

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.gemma.GemmaSession
import be.heyman.android.jemmapassdemo.ai.gemma.InitStatus
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AddItemWithAssistantBottomSheet : BottomSheetDialogFragment() {

    @Inject lateinit var gemma: GemmaSession

    companion object {
        private const val TAG = "JEMMA-ASSISTANT"
        private const val ARG_PILLAR = "pillar"

        /** 🆕 Lot 14.5a — FragmentResult émis vers le parent quand l'user choisit
         *  un mode IA (autre que manuel). Le parent listen via
         *  `setFragmentResultListener(RESULT_KEY_MODE_PICKED)` puis lance le
         *  flow correspondant (photo source chooser, voice recorder, etc.). */
        const val RESULT_KEY_MODE_PICKED = "assistant_mode_picked"
        const val RESULT_PILLAR = "pillar"
        const val RESULT_MODE = "mode"

        /** Pillars supportés (par cle interne, mapping vers strings.xml). */
        enum class Pillar(val titleRes: Int) {
            MEDICATIONS(R.string.assistant_sheet_title_medications),
            ALLERGIES(R.string.assistant_sheet_title_allergies),
            CONTACTS(R.string.assistant_sheet_title_contacts),
            CONDITIONS(R.string.assistant_sheet_title_conditions),
            PATIENT(R.string.assistant_sheet_title_patient),
            GENERIC(R.string.assistant_sheet_title_generic),
        }

        fun newInstance(pillar: Pillar): AddItemWithAssistantBottomSheet {
            val f = AddItemWithAssistantBottomSheet()
            f.arguments = Bundle().apply { putString(ARG_PILLAR, pillar.name) }
            return f
        }
    }

    /** Callback wired par le fragment appelant pour ouvrir le form manuel. */
    private var onManualPicked: (() -> Unit)? = null

    fun setOnManualPicked(cb: () -> Unit): AddItemWithAssistantBottomSheet = apply {
        onManualPicked = cb
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        dialog.behavior.skipCollapsed = true
        dialog.behavior.isFitToContents = true
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.bottom_sheet_add_with_assistant, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pillarName = arguments?.getString(ARG_PILLAR) ?: Pillar.GENERIC.name
        val pillar = runCatching { Pillar.valueOf(pillarName) }.getOrDefault(Pillar.GENERIC)
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🤖 sheet shown · pillar=$pillar",
        )

        // Titre contextuel.
        view.findViewById<TextView>(R.id.assistant_sheet_title)?.setText(pillar.titleRes)

        // Refs vers les badges et trailing TextViews (utilisés par
        // setupActiveMode + setupLockedMode pour swap visibility/texte).
        val photoBadge = view.findViewById<View>(R.id.assistant_mode_photo_demo_badge)
        val photoTrailing = view.findViewById<TextView>(R.id.assistant_mode_photo_trailing)
        val voiceBadge = view.findViewById<View>(R.id.assistant_mode_voice_demo_badge)
        val voiceTrailing = view.findViewById<TextView>(R.id.assistant_mode_voice_trailing)

        val photoRow = view.findViewById<View>(R.id.assistant_mode_photo)
        val livescanRow = view.findViewById<View>(R.id.assistant_mode_livescan)
        val voiceRow = view.findViewById<View>(R.id.assistant_mode_voice)
        val textRow = view.findViewById<View>(R.id.assistant_mode_text)

        // 🆕 Lot 14.5c14 — Branching par pillar :
        //   • MEDICATIONS : mode_photo ACTIF + badge DÉMO ; voice/livescan/text grisés
        //   • ALLERGIES   : mode_voice ACTIF + badge DÉMO ; photo/livescan/text grisés
        //   • autres      : tout grisé sauf manual (fallback safe)
        // Le mode "Saisir manuellement" reste actif partout.
        when (pillar) {
            Pillar.MEDICATIONS -> {
                setupActiveMode(photoRow, photoBadge, photoTrailing, pillar, "photo")
                setupLockedMode(voiceRow, voiceBadge, voiceTrailing, "voice")
                disableForDemo(livescanRow, "livescan")
                disableForDemo(textRow, "text")
            }
            Pillar.ALLERGIES -> {
                // 🆕 Lot 14.5c15 — Pas de "Scanner un médicament" en contexte
                // allergies → on cache complètement mode_photo. Le mode
                // livescan ("Scanner un document") prend la 1ère position
                // avec son sub-text override : "Carte des allergies, rapport
                // de tests, IgE…".
                photoRow?.visibility = View.GONE
                view.findViewById<TextView>(R.id.assistant_mode_livescan_sub)
                    ?.setText(R.string.assistant_mode_livescan_sub_allergies)
                setupActiveMode(voiceRow, voiceBadge, voiceTrailing, pillar, "voice")
                disableForDemo(livescanRow, "livescan")
                disableForDemo(textRow, "text")
            }
            Pillar.PATIENT -> {
                // 🆕 Lot 14.5c36 — Patient pillar : ONLY photo + voice
                // visible (livescan + text hidden completely). Custom
                // texts + emoji override the medication-themed defaults
                // WITHOUT modifying the strings (which are still used by
                // MEDICATIONS). The medication-pill emoji 💊 is replaced
                // by 🪪 (ID card), and the row title/sub get patient-
                // specific copy.
                photoRow?.visibility = View.VISIBLE
                voiceRow?.visibility = View.VISIBLE
                livescanRow?.visibility = View.GONE
                textRow?.visibility = View.GONE
                // Override photo row visuals for PATIENT context
                view.findViewById<TextView>(R.id.assistant_mode_photo_emoji)
                    ?.text = "🪪"
                view.findViewById<TextView>(R.id.assistant_mode_photo_title)
                    ?.setText(R.string.assistant_mode_photo_patient_title)
                view.findViewById<TextView>(R.id.assistant_mode_photo_sub)
                    ?.setText(R.string.assistant_mode_photo_patient_sub)
                // Voice row : keep the generic title, just refine the subtitle
                view.findViewById<TextView>(R.id.assistant_mode_voice_sub)
                    ?.setText(R.string.assistant_mode_voice_patient_sub)
                setupActiveMode(photoRow, photoBadge, photoTrailing, pillar, "photo")
                setupActiveMode(voiceRow, voiceBadge, voiceTrailing, pillar, "voice")
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🚨 PATIENT sheet configured · photo=🪪+active · voice=🎤+active · livescan=GONE · text=GONE",
                )
            }
            else -> {
                setupLockedMode(photoRow, photoBadge, photoTrailing, "photo")
                setupLockedMode(voiceRow, voiceBadge, voiceTrailing, "voice")
                disableForDemo(livescanRow, "livescan")
                disableForDemo(textRow, "text")
            }
        }

        // Manual mode : fire le callback du caller pour ouvrir son form classique.
        view.findViewById<View>(R.id.assistant_mode_manual)?.setOnClickListener {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] ✏️ manual mode picked · dismissing sheet",
            )
            dismiss()
            onManualPicked?.invoke()
        }
    }

    /** 🆕 Lot 14.5c14 — Configure un mode comme actif pour la démo : alpha 1,
     *  badge DÉMO visible, trailing = chevron `▸`, tap émet le FragmentResult. */
    private fun setupActiveMode(
        row: View?,
        badge: View?,
        trailing: TextView?,
        pillar: Companion.Pillar,
        modeName: String,
    ) {
        if (row == null) return
        row.alpha = 1f
        badge?.visibility = View.VISIBLE
        trailing?.text = "▸"
        trailing?.textSize = 20f
        row.setOnClickListener {
            onModeTap(pillar, modeName, emitResult = true)
        }
    }

    /** 🆕 Lot 14.5c14 — Configure un mode comme verrouillé pour la démo :
     *  alpha 0.35, badge gone, trailing = cadenas, tap = Toast démo. */
    private fun setupLockedMode(
        row: View?,
        badge: View?,
        trailing: TextView?,
        modeName: String,
    ) {
        if (row == null) return
        row.alpha = 0.35f
        badge?.visibility = View.GONE
        trailing?.text = "🔒"
        trailing?.textSize = 16f
        row.setOnClickListener {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 🚫 mode disabled (demo) · mode=$modeName",
            )
            Toast.makeText(
                requireContext(),
                R.string.assistant_mode_disabled_demo_toast,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** 🆕 Lot 14.5c11 — Grise un mode et y branche un Toast démo (modes sans
     *  badge swappable, type livescan/text qui restent verrouillés tout le temps). */
    private fun disableForDemo(row: View?, modeName: String) {
        if (row == null) return
        row.alpha = 0.35f
        row.setOnClickListener {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 🚫 mode disabled (demo) · mode=$modeName",
            )
            Toast.makeText(
                requireContext(),
                R.string.assistant_mode_disabled_demo_toast,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * Lot 14.5a — pour mode=photo on émet un FragmentResult vers le parent
     * (qui ouvre le chooser caméra/galerie via [AssistantPhotoCaptureHelper]).
     * Les autres modes (livescan/voice/text) restent en preview design avec
     * Toast jusqu'aux lots 14.6/14.7.
     *
     * 🆕 Lot 14.5c30 v3 — mode=voice : on lance gemma.prewarm() AVANT de
     * dismiss + setFragmentResult, et on bloque le sheet sur l'item voice
     * avec un état "⏳ Initialisation de Jemma…". Quand l'init est complète
     * (Gemma Ready), on dismiss et le ChatFragment ouvre avec un Gemma
     * déjà tiède (init ~0ms côté chat). ~11s d'attente devient une attente
     * VISIBLE et compréhensible pour l'utilisateur.
     */
    private fun onModeTap(
        pillar: Companion.Pillar,
        mode: String,
        emitResult: Boolean,
    ) {
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🎯 mode tap · pillar=$pillar · mode=$mode · emitResult=$emitResult",
        )
        if (!emitResult) {
            Toast.makeText(
                requireContext(),
                R.string.assistant_toast_design_preview,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (mode == "voice") {
            // Show loading state, prewarm Gemma, then dismiss + setFragmentResult.
            switchVoiceRowToLoading()
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] ⏳ voice mode picked · prewarming Gemma before navigate",
            )
            gemma.prewarm()
            viewLifecycleOwner.lifecycleScope.launch {
                val tStart = System.currentTimeMillis()
                gemma.status.first { it is InitStatus.Ready || it is InitStatus.Failed }
                val elapsed = System.currentTimeMillis() - tStart
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ✅ Gemma ready after $elapsed ms · navigating",
                )
                if (!isAdded) return@launch
                parentFragmentManager.setFragmentResult(
                    RESULT_KEY_MODE_PICKED,
                    Bundle().apply {
                        putString(RESULT_PILLAR, pillar.name)
                        putString(RESULT_MODE, mode)
                    },
                )
                dismiss()
            }
            return
        }
        // Other modes : default path (dismiss + result immediately).
        parentFragmentManager.setFragmentResult(
            RESULT_KEY_MODE_PICKED,
            Bundle().apply {
                putString(RESULT_PILLAR, pillar.name)
                putString(RESULT_MODE, mode)
            },
        )
        dismiss()
    }

    /**
     * 🆕 Lot 14.5c30 v3 — Switch the voice row to a "loading" presentation :
     * sub-text replaced by "Initialisation de Jemma… ⏳", trailing chevron
     * replaced by spinning ⏳, tap disabled, badge hidden. Visual proof that
     * something is happening during the ~11 s Gemma cold-start.
     */
    private fun switchVoiceRowToLoading() {
        val v = view ?: return
        v.findViewById<View>(R.id.assistant_mode_voice)?.let { row ->
            row.setOnClickListener(null)
            row.isClickable = false
            row.alpha = 0.75f
        }
        v.findViewById<TextView>(R.id.assistant_mode_voice_sub)?.text =
            "⏳ Initialisation de Jemma…"
        v.findViewById<View>(R.id.assistant_mode_voice_demo_badge)?.visibility = View.GONE
        v.findViewById<TextView>(R.id.assistant_mode_voice_trailing)?.let { tv ->
            tv.text = "⏳"
            tv.textSize = 20f
        }
        // Block the dismiss-on-outside-tap so the user can't accidentally
        // dismiss the sheet during init. They'll see the spinner state and
        // wait for Gemma.
        isCancelable = false
    }
}
