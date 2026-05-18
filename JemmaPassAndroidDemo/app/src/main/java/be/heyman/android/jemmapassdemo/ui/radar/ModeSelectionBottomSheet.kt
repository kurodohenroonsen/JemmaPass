/*
 * ModeSelectionBottomSheet.kt — JEMMA Pass · Plan B · L3 v2.5.2
 *
 * BottomSheetDialogFragment with two large cards : 🆘 SOS (victim) and
 * ⛑️ Rescue. Posts the user's choice back to the host fragment via
 * setFragmentResult, decoupling navigation from this sheet's lifecycle
 * (the sheet dismisses immediately ; the host handles the navigate call
 * in its setFragmentResultListener).
 *
 * Why a BottomSheet vs inline cards on the hub fragment :
 *   - User explicitly asked for a "modal" choice picker (handoff
 *     screenshot conversation).
 *   - The sheet keeps the tab content stable when the user dismisses
 *     it — the hub fragment can offer a "choose mode again" button
 *     to re-trigger the sheet without a full re-navigation.
 */
package be.heyman.android.jemmapassdemo.ui.radar

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import be.heyman.android.jemmapassdemo.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ModeSelectionBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "ModeSelectionBottomSheet"
        private const val LOG_TAG = "JEMMA-RADAR"

        /** Key used by the host fragment's setFragmentResultListener. */
        const val RESULT_KEY = "be.heyman.jemma.mode_selection"

        /** Bundle key containing the chosen mode string (MODE_SOS / MODE_RESCUER). */
        const val RESULT_MODE = "mode"

        const val MODE_SOS = "SOS"
        const val MODE_RESCUER = "RESCUER"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d(LOG_TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView (ModeSelectionBottomSheet)")
        return inflater.inflate(R.layout.bottomsheet_mode_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.mode_card_sos).setOnClickListener {
            Log.i(LOG_TAG, "[t=${System.currentTimeMillis()}] 👆 sheet card tapped · mode=SOS")
            postChoice(MODE_SOS)
        }
        view.findViewById<View>(R.id.mode_card_rescuer).setOnClickListener {
            Log.i(LOG_TAG, "[t=${System.currentTimeMillis()}] 👆 sheet card tapped · mode=RESCUER")
            postChoice(MODE_RESCUER)
        }
    }

    private fun postChoice(mode: String) {
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_MODE to mode))
        dismiss()
    }
}
