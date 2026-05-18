/*
 * TriageSaltBottomSheet.kt — JEMMA Pass · JemmaAppDemo · L5 v2.5.6
 *
 * Bottom sheet that lets a rescuer assign a SALT status to a detected
 * victim. Faithfully ports `jemma_triage_panel.js` (HTML Plan A,
 * L44.16.76) to native Kotlin.
 *
 * ── UX rules (Council R2 verdict, preserved verbatim) ───────────
 *   • Tap pin → bottom sheet with 6 buttons, 72dp tall
 *   • One tap for non-destructive transitions (WAIT, EVAL, STAB, HELP)
 *   • Two-tap modal confirmation for DCD and EVAC (irreversible-adjacent)
 *   • "Cancel deceased" explicit button when victim is currently DCD
 *     → triggers isExplicitOverride=true to bypass StatusResolver's
 *       30-second grace window. Use sparingly — this is the
 *       false-positive-pulse-detection recovery path.
 *   • Hop count badge "via N hops" rendered next to status (TODO L7,
 *     not part of v2.5.6 — chunk metadata not yet exposed via JSON)
 *
 * ── Native bridge contract ──────────────────────────────────────
 *   RadarController.triagePublishEvent(victimSid, statusCode,
 *                                       rescuerSid, isExplicitOverride)
 *   → returns Result.Ok with wire-format "E|" chunk, or Result.Error
 */
package be.heyman.android.jemmapassdemo.ui.radar

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.radar.RadarController
import be.heyman.android.jemmapassdemo.sos.JemmaDeviceId
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TriageSaltBottomSheet : DialogFragment() {

    companion object {
        private const val TAG = "JEMMA-TRIAGE-UI"
        private const val ARG_VICTIM_SID  = "victim_sid"
        private const val ARG_VICTIM_NAME = "victim_name"
        private const val ARG_CURRENT_SALT = "current_salt"  // null/empty if no event yet

        /**
         * Build the sheet for a given victim.
         *
         * @param victimSid    4-char hex SID of the victim
         * @param victimName   display name (anonymous fallback handled here)
         * @param currentSalt  current SALT status if known (e.g. "EVAL") or null
         */
        fun newInstance(
            victimSid: String,
            victimName: String,
            currentSalt: String?,
        ): TriageSaltBottomSheet = TriageSaltBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_VICTIM_SID, victimSid)
                putString(ARG_VICTIM_NAME, victimName)
                putString(ARG_CURRENT_SALT, currentSalt)
            }
        }
    }

    @Inject lateinit var radar: RadarController

    private lateinit var victimSid: String
    private lateinit var victimName: String
    private var currentSalt: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Default DialogFragment style — no custom theme needed for v2.5.6.
        // We position the dialog at the bottom in onCreateDialog.
        victimSid  = arguments?.getString(ARG_VICTIM_SID) ?: "????"
        victimName = arguments?.getString(ARG_VICTIM_NAME)?.ifBlank { null }
            ?: getString(R.string.victim_anon)
        currentSalt = arguments?.getString(ARG_CURRENT_SALT)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 newInstance · victim=$victimSid name='$victimName' current=$currentSalt")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.BOTTOM)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
            background = roundedTopBg()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        // Drag handle (visual cue for swipe-to-dismiss — pure cosmetic for now)
        root.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(14)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(2).toFloat()
                setColor(0xFF94A3B8.toInt())
            }
        })

        // Header : victim name + SID + current status (if any)
        root.addView(TextView(requireContext()).apply {
            text = getString(R.string.triage_sheet_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xFF94A3B8.toInt())
            isAllCaps = true
            letterSpacing = 0.12f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        })

        root.addView(TextView(requireContext()).apply {
            text = victimName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(0xFFF1F5F9.toInt())
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(2))
        })

        root.addView(TextView(requireContext()).apply {
            text = "#$victimSid"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(0xFF94A3B8.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
        })

        // Current status badge (if any event has been published before)
        val currentSpec = SaltUi.resolve(currentSalt)
        if (currentSpec != null) {
            root.addView(TextView(requireContext()).apply {
                text = getString(R.string.triage_current_status, currentSpec.emoji, getString(currentSpec.labelRes))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(0xFFF1F5F9.toInt())
                background = pillBg(currentSpec.colorInt)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(10) }
                layoutParams = lp
            })
        }

        // Cancel-deceased revival button (only when currently DCD)
        if (currentSalt?.uppercase()?.trim() == "DCD") {
            root.addView(TextView(requireContext()).apply {
                text = getString(R.string.triage_btn_cancel_deceased)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(0xFFFFFFFF.toInt())
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                background = pillBg(0xFF7C2D12.toInt())  // dark red, distinct from HELP
                setPadding(dp(16), dp(12), dp(16), dp(12))
                isClickable = true
                isFocusable = true
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(14) }
                layoutParams = lp
                setOnClickListener {
                    // Revive = explicit STAB override, bypasses 30s grace window
                    onSaltTap("STAB", isExplicitOverride = true)
                }
            })
        }

        // 6 SALT buttons (2 columns × 3 rows for thumbability)
        val grid = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(18) }
            layoutParams = lp
        }

        SaltUi.ALL.chunked(2).forEach { rowSpecs ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) }
                layoutParams = lp
            }
            rowSpecs.forEachIndexed { idx, spec ->
                val btn = TextView(requireContext()).apply {
                    text = "${spec.emoji}  ${getString(spec.labelRes)}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(if (spec.wireCode == "DCD") 0xFFFFFFFF.toInt() else 0xFFFFFFFF.toInt())
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    background = pillBg(spec.colorInt)
                    setPadding(dp(8), dp(20), dp(8), dp(20))
                    isClickable = true
                    isFocusable = true
                    val lp = LinearLayout.LayoutParams(0, dp(72), 1f).apply {
                        marginStart = if (idx == 0) 0 else dp(8)
                    }
                    layoutParams = lp
                    setOnClickListener {
                        if (spec.requiresConfirm) {
                            showConfirmDialog(spec)
                        } else {
                            onSaltTap(spec.wireCode, isExplicitOverride = false)
                        }
                    }
                }
                row.addView(btn)
            }
            grid.addView(row)
        }
        root.addView(grid)

        // Dismiss button
        root.addView(TextView(requireContext()).apply {
            text = getString(R.string.triage_btn_cancel)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0xFF94A3B8.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(8))
            isClickable = true
            isFocusable = true
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(16) }
            layoutParams = lp
            setOnClickListener { dismiss() }
        })

        return root
    }

    override fun onStart() {
        super.onStart()
        // Match width to screen
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    /**
     * Confirmation modal for EVAC and DCD (semi-irreversible transitions).
     * Two-tap behavior : tap the sheet button → see this dialog → tap again
     * "Confirmer" to actually publish. Tap "Annuler" to back out.
     */
    private fun showConfirmDialog(spec: SaltUiSpec) {
        val ctx = requireContext()
        val msg = getString(R.string.triage_confirm_msg, spec.emoji, getString(spec.labelRes), victimName)
        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.triage_confirm_title))
            .setMessage(msg)
            .setPositiveButton(R.string.triage_confirm_yes) { _, _ ->
                onSaltTap(spec.wireCode, isExplicitOverride = false)
            }
            .setNegativeButton(R.string.triage_confirm_no, null)
            .show()
    }

    private fun onSaltTap(wireCode: String, isExplicitOverride: Boolean) {
        val tNow = System.currentTimeMillis()
        val mySid = JemmaDeviceId.get(requireContext())
        Log.i(
            TAG,
            "[t=$tNow] 🚑 publish · victim=$victimSid status=$wireCode rescuer=$mySid override=$isExplicitOverride",
        )
        // triagePublishEvent returns the wire-format "E|..." chunk on success,
        // or "err:reason" on failure.
        val wire = radar.triagePublishEvent(
            victimSid = victimSid,
            statusCode = wireCode,
            rescuerSid = mySid,
            isExplicitOverride = isExplicitOverride,
        )
        Log.i(TAG, "[t=$tNow]   ↳ $wire")
        dismiss()
    }

    // ── tiny helpers ──────────────────────────────────────────────

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun roundedTopBg(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(0xFF1E293B.toInt())
        cornerRadii = floatArrayOf(
            dp(20).toFloat(), dp(20).toFloat(),
            dp(20).toFloat(), dp(20).toFloat(),
            0f, 0f, 0f, 0f,
        )
    }

    private fun pillBg(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(36).toFloat()
    }
}
