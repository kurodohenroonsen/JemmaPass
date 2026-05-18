/*
 * SaltUi.kt — JEMMA Pass · JemmaAppDemo · L5 v2.5.6
 *
 * UI-side helper to resolve a 4-char SALT wire code into rendering
 * material (emoji, hex color, label string-res). KEEP IN SYNC with
 * the native `triage/SaltCode.kt` enum.
 *
 * Why a separate file rather than importing SaltCode directly?
 *   The triage package has dependencies (StatusResolver, BloomDedup,
 *   etc.) and is a domain layer. UI should not transitively pull that.
 *   Also, this layer adds the i18n string-res mapping which is purely
 *   a UI concern.
 *
 * Wire codes : "WAIT", "EVAL", "STAB", "HELP", "EVAC", "DCD"
 * (DCD is 3 chars per the codec, no padding).
 */
package be.heyman.android.jemmapassdemo.ui.radar

import androidx.annotation.ColorInt
import android.graphics.Color
import be.heyman.android.jemmapassdemo.R

/**
 * Rendering material for one SALT code.
 *
 * @param wireCode   4-char ASCII wire code (uppercased, trimmed)
 * @param emoji      one-char display emoji
 * @param colorHex   hex color (matches `SaltCode.kt` colorHex)
 * @param labelRes   string-res for the localised name ("Stable" / "Aide!" / etc.)
 * @param descRes    string-res for the longer description
 * @param requiresConfirm  true for EVAC and DCD — double-tap modal in UI
 */
data class SaltUiSpec(
    val wireCode: String,
    val emoji: String,
    val colorHex: String,
    val labelRes: Int,
    val descRes: Int,
    val requiresConfirm: Boolean,
) {
    @get:ColorInt
    val colorInt: Int get() = Color.parseColor(colorHex)
}

object SaltUi {

    /**
     * The 6 SALT codes in canonical UI order (matches jemma_triage_panel.js
     * SALT_BUTTONS array). This is the order the bottom-sheet displays them.
     */
    val ALL: List<SaltUiSpec> = listOf(
        SaltUiSpec(
            wireCode = "WAIT",
            emoji = "⏳",
            colorHex = "#9E9E9E",
            labelRes = R.string.salt_wait_label,
            descRes  = R.string.salt_wait_desc,
            requiresConfirm = false,
        ),
        SaltUiSpec(
            wireCode = "EVAL",
            emoji = "🔍",
            colorHex = "#FFC107",
            labelRes = R.string.salt_eval_label,
            descRes  = R.string.salt_eval_desc,
            requiresConfirm = false,
        ),
        SaltUiSpec(
            wireCode = "STAB",
            emoji = "✅",
            colorHex = "#4CAF50",
            labelRes = R.string.salt_stab_label,
            descRes  = R.string.salt_stab_desc,
            requiresConfirm = false,
        ),
        SaltUiSpec(
            wireCode = "HELP",
            emoji = "🆘",
            colorHex = "#F44336",
            labelRes = R.string.salt_help_label,
            descRes  = R.string.salt_help_desc,
            requiresConfirm = false,
        ),
        SaltUiSpec(
            wireCode = "EVAC",
            emoji = "🚑",
            colorHex = "#2196F3",
            labelRes = R.string.salt_evac_label,
            descRes  = R.string.salt_evac_desc,
            requiresConfirm = true,
        ),
        SaltUiSpec(
            wireCode = "DCD",
            emoji = "🕊️",
            colorHex = "#000000",
            labelRes = R.string.salt_dcd_label,
            descRes  = R.string.salt_dcd_desc,
            requiresConfirm = true,
        ),
    )

    /**
     * Resolve a wire code to its spec. Tolerant : uppercased, trimmed.
     * Returns null for unknown codes (parser layer drops malformed
     * events, but UI prefers a safe null over a crash).
     */
    fun resolve(code: String?): SaltUiSpec? {
        if (code.isNullOrBlank()) return null
        val cleaned = code.uppercase().trim()
        return ALL.firstOrNull { it.wireCode == cleaned }
    }
}
