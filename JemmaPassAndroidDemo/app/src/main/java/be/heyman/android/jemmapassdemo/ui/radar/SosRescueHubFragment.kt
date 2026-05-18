/*
 * SosRescueHubFragment.kt — JEMMA Pass · Plan B · L3 v2.5.2
 *
 * The "🚨 SOS/Rescue" tab landing fragment. Replaces the L2 stub
 * RadarFragment that was previously sitting on this tab.
 *
 * Behavior:
 *   1. onViewCreated → show the ModeSelectionBottomSheet automatically
 *   2. The sheet exposes two cards (🆘 SOS / ⛑️ Rescue)
 *   3. On selection, the sheet calls the host callback which:
 *        - asks the @Inject-ed RadarController to flip the radar mode
 *        - navigates to either dest_sos_broadcast or dest_radar_rescuer
 *   4. If the user dismisses the sheet without choosing, this fragment
 *      shows a fallback "tap to choose mode again" button so they can
 *      reopen it without leaving the tab.
 *
 * The actual BLE/Nearby/SOS work is NOT triggered from this fragment.
 * Both the SosBroadcastFragment (victim) and the rescuer-side
 * RadarFragment (rescuer) are responsible for invoking
 * RadarController.sosStart() / radarStart() respectively, after their
 * own pre-flight checks (current profile selected, GPS fix, etc.).
 *
 * What gets logged at runtime (verifiable via capture_jemma.sh):
 *   📋 onCreateView (SosRescueHubFragment v2.5.2)
 *   🎯 ModeSelectionBottomSheet shown — awaiting user choice
 *   👆 user chose mode=SOS    (or RESCUER)
 *   🔀 RadarController.radarSetMode → SOS  (or RESCUER)
 *   🧭 navigating to dest_sos_broadcast  (or dest_radar_rescuer)
 */
package be.heyman.android.jemmapassdemo.ui.radar

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.radar.RadarController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SosRescueHubFragment : Fragment() {

    @Inject lateinit var radarController: RadarController

    companion object {
        private const val TAG = "JEMMA-RADAR"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView (SosRescueHubFragment v2.5.2)")
        return inflater.inflate(R.layout.fragment_sos_rescue_hub, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Listen for the user's choice from the bottom sheet. The sheet
        // posts the result via setFragmentResult() with the key below.
        setFragmentResultListener(ModeSelectionBottomSheet.RESULT_KEY) { _, bundle ->
            val mode = bundle.getString(ModeSelectionBottomSheet.RESULT_MODE) ?: return@setFragmentResultListener
            handleChoice(mode)
        }

        // The "Choose mode again" fallback button — only meaningful when
        // the user dismissed the sheet without selecting a card.
        view.findViewById<Button>(R.id.sos_rescue_hub_open_button).setOnClickListener {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 👆 user tapped 'choose mode again'")
            showModeSheet()
        }

        // Auto-show on first creation only. Re-creations (rotation, back
        // nav return) keep whatever the user was doing.
        if (savedInstanceState == null) {
            showModeSheet()
        }
    }

    private fun showModeSheet() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎯 ModeSelectionBottomSheet shown — awaiting user choice")
        ModeSelectionBottomSheet().show(parentFragmentManager, ModeSelectionBottomSheet.TAG)
    }

    private fun handleChoice(mode: String) {
        val tNow = System.currentTimeMillis()
        Log.i(TAG, "[t=$tNow] 👆 user chose mode=$mode")
        when (mode) {
            ModeSelectionBottomSheet.MODE_SOS -> {
                val result = radarController.radarSetMode(RadarController.RadarMode.SOS)
                Log.i(
                    TAG,
                    "[t=$tNow] 🔀 RadarController.radarSetMode → SOS · result=${result.asWireString()}",
                )
                Log.i(TAG, "[t=$tNow] 🧭 navigating to dest_sos_broadcast")
                findNavController().navigate(R.id.action_hub_to_sos_broadcast)
            }
            ModeSelectionBottomSheet.MODE_RESCUER -> {
                val result = radarController.radarSetMode(RadarController.RadarMode.RESCUER)
                Log.i(
                    TAG,
                    "[t=$tNow] 🔀 RadarController.radarSetMode → RESCUER · result=${result.asWireString()}",
                )
                Log.i(TAG, "[t=$tNow] 🧭 navigating to dest_radar_rescuer")
                findNavController().navigate(R.id.action_hub_to_radar_rescuer)
            }
            else -> {
                Log.w(TAG, "[t=$tNow] ⚠ unknown mode='$mode' — ignored")
            }
        }
    }
}
