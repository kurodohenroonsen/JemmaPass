/*
 * PillarAssistantFragment.kt — Lot 14.3 (PHASE 14) · DESIGN ONLY
 *
 * Remplace les "Coming soon" pour les 14 piliers IPS inactifs.
 * Au lieu d'un placeholder bête, on affiche un écran assistant-first
 * avec 3 actions (Scan / Voice / Text) qui ouvrent le bottom sheet
 * ou directement le pipeline au Lot 14.4+.
 *
 * Argument :
 *   ARG_PILLAR = un des codes définis dans [Pillar] enum ci-dessous
 *
 * Utilisable depuis le PillarsFragment du ProfileDetail en remplaçant
 * `action_detail_to_pillar_stub` par `action_detail_to_pillar_assistant`
 * (à câbler dans un lot suivant — ce lot 14.3 ne touche pas encore le
 * PillarsFragment).
 */
package be.heyman.android.jemmapassdemo.ui.assistant

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R

class PillarAssistantFragment : Fragment(R.layout.fragment_pillar_assistant) {

    companion object {
        private const val TAG = "JEMMA-ASSISTANT"

        const val ARG_PILLAR = "pillar"

        /** Définit le titre + l'emoji XL pour chaque pilier IPS stub. */
        enum class Pillar(val emoji: String, val titleRes: Int) {
            VACCINATIONS("💉", R.string.assistant_stub_title_vaccinations),
            PROCEDURES("🏥", R.string.assistant_stub_title_procedures),
            DEVICES("⚙️", R.string.assistant_stub_title_devices),
            PAST_PROBLEMS("📜", R.string.assistant_stub_title_past_problems),
            FUNCTIONAL("♿", R.string.assistant_stub_title_functional),
            PREGNANCY("🤰", R.string.assistant_stub_title_pregnancy),
            RESULTS("🧪", R.string.assistant_stub_title_results),
            ADVANCE_DIRECTIVES("📜", R.string.assistant_stub_title_advance_directives),
            CONSENTS("✍️", R.string.assistant_stub_title_consents),
            GOALS("🎯", R.string.assistant_stub_title_goals),
            ENCOUNTERS("🚑", R.string.assistant_stub_title_encounters),
            OCCUPATIONAL("💼", R.string.assistant_stub_title_occupational),
            PROVIDERS("👨‍⚕️", R.string.assistant_stub_title_providers),
            CONDITIONS("🩺", R.string.assistant_stub_title_conditions),
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pillarName = arguments?.getString(ARG_PILLAR) ?: Pillar.VACCINATIONS.name
        val pillar = runCatching { Pillar.valueOf(pillarName) }.getOrDefault(Pillar.VACCINATIONS)
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🪨 pillar stub open · pillar=$pillar",
        )

        view.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            R.id.pillar_assistant_toolbar
        )?.apply {
            setTitle(pillar.titleRes)
            setNavigationOnClickListener { findNavController().navigateUp() }
        }

        view.findViewById<TextView>(R.id.pillar_assistant_emoji)?.text = pillar.emoji

        // Les 3 boutons : pour le Lot 14.3 ouvrent juste le bottom sheet
        // d'assistant en mode GENERIC. Le Lot 14.4 pourra mapper directement
        // chaque button → un mode spécifique du pipeline.
        view.findViewById<View>(R.id.pillar_assistant_btn_scan)?.setOnClickListener {
            openSheet(pillar)
        }
        view.findViewById<View>(R.id.pillar_assistant_btn_voice)?.setOnClickListener {
            openSheet(pillar)
        }
        view.findViewById<View>(R.id.pillar_assistant_btn_text)?.setOnClickListener {
            openSheet(pillar)
        }
    }

    private fun openSheet(pillar: Pillar) {
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🤖 opening assistant sheet from pillar stub · $pillar",
        )
        // Au Lot 14.3 on ne wire pas encore le pipeline. Toast + log.
        Toast.makeText(
            requireContext(),
            R.string.assistant_toast_design_preview,
            Toast.LENGTH_SHORT,
        ).show()
        // Au Lot 14.4 : navigate vers AssistantPipelineFragment
        // avec Pillar.GENERIC + mode adapté.
    }
}
