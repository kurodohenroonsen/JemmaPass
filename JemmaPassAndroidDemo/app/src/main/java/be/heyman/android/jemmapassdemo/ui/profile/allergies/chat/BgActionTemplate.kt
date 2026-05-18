/*
 * BgActionTemplate.kt — JEMMA Pass · Lot 14.5c30 (PHASE 14)
 *
 * Central registry of bg-action display templates. When a @Tool from
 * [AllergiesAgentTools] is invoked, we look up its template here to get
 * the user-facing emoji + label + (optional) detail formatter.
 *
 * Keeping the templates centralized means :
 *   • Translations in one place (TODO: hoist to strings.xml when stable)
 *   • Consistent emoji/styling across all tool invocations
 *   • Easy to add a tool — define @Tool, add a TEMPLATE entry, done
 *
 * The fragment renders these in a Material 3 chip row inline in the chat.
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies.chat

import android.content.Context
import androidx.annotation.StringRes
import be.heyman.android.jemmapassdemo.R

/**
 * A static display template for a tool invocation. The tool callsite
 * provides the value at runtime; the template provides the emoji + label.
 */
data class BgActionTemplate(
    val emoji: String,
    @StringRes val labelRes: Int,
)

/**
 * Central registry. Keys are @Tool method names (or pseudo-names for
 * async completions like "kb_substance_resolved"). Values are templates.
 *
 * Localized FR labels — for the hackathon demo. EN fallback is the same
 * for now (production would hoist this to strings.xml + resolveString).
 */
object BgActionTemplates {

    // ─── Draft setters ──────────────────────────────────────────────
    val SET_SUBSTANCE = BgActionTemplate(emoji = "💊", labelRes = R.string.allergies_chat_bg_substance)
    val SET_CATEGORY = BgActionTemplate(emoji = "🏷️", labelRes = R.string.allergies_chat_bg_category)
    val SET_SEVERITY = BgActionTemplate(emoji = "⚠️", labelRes = R.string.allergies_chat_bg_severity)
    val SET_STATUS = BgActionTemplate(emoji = "🟢", labelRes = R.string.allergies_chat_bg_status)
    val SET_MANIFESTATION = BgActionTemplate(emoji = "🔥", labelRes = R.string.allergies_chat_bg_manifestation)
    val SET_ONSET_DATE = BgActionTemplate(emoji = "📅", labelRes = R.string.allergies_chat_bg_onset_date)

    // ─── KB lookups (async completion) ──────────────────────────────
    val KB_SEARCH_INTOLERANCE = BgActionTemplate(emoji = "🔍", labelRes = R.string.allergies_chat_bg_search_intolerance)
    val KB_SEARCH_REACTION = BgActionTemplate(emoji = "🔍", labelRes = R.string.allergies_chat_bg_search_reaction)
    val KB_RESOLVE_INTOLERANCE = BgActionTemplate(emoji = "📚", labelRes = R.string.allergies_chat_bg_resolve_intolerance)
    val KB_RESOLVE_REACTION = BgActionTemplate(emoji = "📚", labelRes = R.string.allergies_chat_bg_resolve_reaction)
    val KB_CONFIRM_CODE = BgActionTemplate(emoji = "✅", labelRes = R.string.allergies_chat_bg_confirm_code)
    val KB_CONFIRM_REACTION = BgActionTemplate(emoji = "✅", labelRes = R.string.allergies_chat_bg_confirm_reaction)

    // ─── Cross-check / profile ──────────────────────────────────────
    val GET_PROFILE_SUMMARY = BgActionTemplate(emoji = "👤", labelRes = R.string.allergies_chat_bg_get_profile)
    val CROSS_CHECK_MEDICATIONS = BgActionTemplate(emoji = "🩺", labelRes = R.string.allergies_chat_bg_cross_check)
    val RED_ALERT = BgActionTemplate(emoji = "🚨", labelRes = R.string.allergies_chat_bg_red_alert)

    // ─── Interaction ────────────────────────────────────────────────
    val PLAY_TTS = BgActionTemplate(emoji = "🔊", labelRes = R.string.allergies_chat_bg_play_tts)
    val ASK_CHOICE = BgActionTemplate(emoji = "❓", labelRes = R.string.allergies_chat_bg_ask_choice)

    // ─── Commit ─────────────────────────────────────────────────────
    val SAVE_DRAFT = BgActionTemplate(emoji = "💾", labelRes = R.string.allergies_chat_bg_save_draft)
    val CANCEL_AND_EXIT = BgActionTemplate(emoji = "🛑", labelRes = R.string.allergies_chat_bg_cancel_exit)

    /**
     * Display formatter for FHIR/IPS enum values → user-friendly FR
     * label. Used in chip "value" slot.
     */
    fun translateValue(context: Context, field: String, raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return when (field) {
            "category" -> when (raw) {
                "food" -> context.getString(R.string.allergies_chat_cat_food)
                "medication" -> context.getString(R.string.allergies_chat_cat_medication)
                "environment" -> context.getString(R.string.allergies_chat_cat_environment)
                "biologic" -> context.getString(R.string.allergies_chat_cat_biologic)
                else -> raw
            }
            "severity" -> when (raw) {
                "High" -> context.getString(R.string.allergies_chat_sev_high)
                "Low" -> context.getString(R.string.allergies_chat_sev_low)
                "Unknown" -> context.getString(R.string.allergies_chat_sev_unknown)
                else -> raw
            }
            "status" -> when (raw) {
                "Active" -> context.getString(R.string.allergies_chat_status_active)
                "Inactive" -> context.getString(R.string.allergies_chat_status_inactive)
                "Resolved" -> context.getString(R.string.allergies_chat_status_resolved)
                else -> raw
            }
            else -> raw
        }
    }
}
