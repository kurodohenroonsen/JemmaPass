/*
 * CrossCheckAlertDialog.kt — JEMMA Pass · Plan B · v2.6.0 · L_PHASE13
 *
 * Modal d'alerte clinique unifié pour les 3 types d'interactions :
 *   1. AllergyHit         : nouveau médoc × allergie existante du profile
 *                           OU nouvelle allergie × médoc existant du profile
 *   2. DdiHit (DDInter)   : nouveau médoc × médoc existant (Warfarin × Ibuprofen)
 *   3. DrugDiseaseHit     : nouveau médoc × condition (Aspirin × asthme)
 *
 * Stratégie d'affichage (Stratégie A — toujours afficher si ≥1 hit) :
 *   - Header avec emoji selon la sévérité max (🔴 MAJOR / 🟠 MODERATE / 🟡 MINOR / ⚪ UNKNOWN)
 *   - Liste de tous les hits, regroupés par type
 *   - Texte descriptif EN (la KB ne contient PAS les traductions clinical FR/JA)
 *   - Disclaimer "Information in English (clinical reference language)"
 *   - 2 boutons : [Review] (cancel) / [Save anyway] (confirm)
 *
 * API context-based (object singleton, pas DialogFragment) : on construit
 * un MaterialAlertDialog direct depuis le Context. Plus simple, et le
 * caller gère le cycle de vie via `viewLifecycleOwner.lifecycleScope`.
 *
 * Pourquoi le texte EN-only :
 *   La table `ddi_facts` n'a que `description_en` et `management_en` (DDInter2
 *   source originale est EN). Pas de traduction clinique fiable disponible
 *   pour J-5. Pour la prod, intégration Gemma 4 ou GPT translation possible.
 *
 * Tag log : JEMMA-XCHK-DIALOG
 */
package be.heyman.android.jemmapassdemo.ui.profile.common

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.kb.AllergyCriticality
import be.heyman.android.jemmapassdemo.kb.AllergyHit
import be.heyman.android.jemmapassdemo.kb.CrossCheckResult
import be.heyman.android.jemmapassdemo.kb.CrossSeverity
import be.heyman.android.jemmapassdemo.kb.DdiHit
import be.heyman.android.jemmapassdemo.kb.DrugDiseaseHit
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object CrossCheckAlertDialog {

    private const val TAG = "JEMMA-XCHK-DIALOG"

    private const val COLOR_RED = 0xFFEF4444.toInt()
    private const val COLOR_ORANGE = 0xFFF97316.toInt()
    private const val COLOR_YELLOW = 0xFFEAB308.toInt()
    private const val COLOR_GRAY = 0xFF9CA3AF.toInt()
    private const val COLOR_INFO = 0xFF94A3B8.toInt()

    /**
     * Affiche le dialog pour le cas Medication × Profile (le plus riche :
     * allergie + DDI + drug-disease).
     */
    fun showForNewMedication(
        context: Context,
        result: CrossCheckResult,
        candidateDisplay: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 showing med xchk · " +
            "newMed='$candidateDisplay' · al=${result.allergyHits.size} " +
            "ddi=${result.ddiHits.size} dd=${result.drugDiseaseHits.size}")

        val message = buildMedicationMessage(context, result, candidateDisplay)
        val severity = computeMedSeverity(result)
        showDialog(context, severity, message, onConfirm, onCancel)
    }

    /**
     * Affiche le dialog pour le cas Allergy × Medications (simpler : juste
     * les médics du profile qui collisionnent avec la nouvelle allergie).
     */
    fun showForNewAllergy(
        context: Context,
        hits: List<NewAllergyConflict>,
        candidateDisplay: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 showing allergy xchk · " +
            "newAllergy='$candidateDisplay' · hits=${hits.size}")

        val message = buildAllergyMessage(context, hits, candidateDisplay)
        val severity = computeAllergySeverity(hits)
        showDialog(context, severity, message, onConfirm, onCancel)
    }

    private fun showDialog(
        context: Context,
        globalSeverity: String,
        message: SpannableStringBuilder,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        val (titleEmoji, titleColor) = when (globalSeverity) {
            "MAJOR" -> "🔴" to COLOR_RED
            "MODERATE" -> "🟠" to COLOR_ORANGE
            "MINOR" -> "🟡" to COLOR_YELLOW
            else -> "⚪" to COLOR_GRAY
        }
        val title = SpannableStringBuilder()
        title.append("$titleEmoji  ")
        val titleStart = title.length
        title.append(context.getString(R.string.xchk_title))
        title.setSpan(
            ForegroundColorSpan(titleColor),
            titleStart, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton(R.string.xchk_save_anyway) { _, _ ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ user confirmed save anyway")
                onConfirm()
            }
            .setNegativeButton(R.string.xchk_cancel) { _, _ ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ↩ user cancelled save")
                onCancel()
            }
            .setOnCancelListener {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ↩ user dismissed (back/touch outside)")
                onCancel()
            }
            .show()
    }

    // ─── Message builders ──────────────────────────────────────────────

    private fun buildMedicationMessage(
        ctx: Context,
        r: CrossCheckResult,
        candidateDisplay: String,
    ): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        appendBold(sb, ctx.getString(R.string.xchk_new_item_prefix) + " ")
        sb.append(candidateDisplay)
        sb.append("\n\n")

        if (r.allergyHits.isNotEmpty()) {
            appendSection(sb, "🩹  " + ctx.getString(R.string.xchk_section_allergies))
            for (h in r.allergyHits) appendAllergyHitBullet(sb, h, ctx)
            sb.append("\n")
        }
        if (r.ddiHits.isNotEmpty()) {
            appendSection(sb, "💊  " + ctx.getString(R.string.xchk_section_ddi))
            for (h in r.ddiHits) appendDdiBullet(sb, h, ctx)
            sb.append("\n")
        }
        if (r.drugDiseaseHits.isNotEmpty()) {
            appendSection(sb, "🩺  " + ctx.getString(R.string.xchk_section_drug_disease))
            for (h in r.drugDiseaseHits) appendDdBullet(sb, h, ctx)
            sb.append("\n")
        }
        appendDisclaimer(sb, ctx)
        return sb
    }

    private fun buildAllergyMessage(
        ctx: Context,
        hits: List<NewAllergyConflict>,
        candidateDisplay: String,
    ): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        appendBold(sb, ctx.getString(R.string.xchk_new_item_prefix) + " ")
        sb.append(candidateDisplay)
        sb.append("\n\n")

        appendSection(sb, "💊  " + ctx.getString(R.string.xchk_section_existing_meds))
        for (h in hits) appendAllergyConflictBullet(sb, h, ctx)
        sb.append("\n")
        appendDisclaimer(sb, ctx)
        return sb
    }

    // ─── Severity computation ──────────────────────────────────────────

    private fun computeMedSeverity(r: CrossCheckResult): String {
        if (r.allergyHits.any { it.criticality == AllergyCriticality.HIGH }) return "MAJOR"
        if (r.ddiHits.any { it.severity == CrossSeverity.MAJOR }) return "MAJOR"
        if (r.drugDiseaseHits.any { it.severity == CrossSeverity.MAJOR }) return "MAJOR"
        if (r.ddiHits.any { it.severity == CrossSeverity.MODERATE }) return "MODERATE"
        if (r.drugDiseaseHits.any { it.severity == CrossSeverity.MODERATE }) return "MODERATE"
        if (r.allergyHits.any { it.criticality == AllergyCriticality.LOW }) return "MINOR"
        if (r.ddiHits.any { it.severity == CrossSeverity.MINOR }) return "MINOR"
        return "UNKNOWN"
    }

    private fun computeAllergySeverity(hits: List<NewAllergyConflict>): String {
        if (hits.any { it.criticality == AllergyCriticality.HIGH }) return "MAJOR"
        if (hits.any { it.criticality == AllergyCriticality.LOW }) return "MINOR"
        return "UNKNOWN"
    }

    // ─── Span builders ──────────────────────────────────────────────────

    private fun appendBold(sb: SpannableStringBuilder, text: String) {
        val start = sb.length
        sb.append(text)
        sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun appendSection(sb: SpannableStringBuilder, title: String) {
        val start = sb.length
        sb.append(title)
        sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("\n")
    }

    private fun appendAllergyHitBullet(sb: SpannableStringBuilder, hit: AllergyHit, ctx: Context) {
        val emoji = when (hit.criticality) {
            AllergyCriticality.HIGH -> "🔴"
            AllergyCriticality.LOW -> "🟡"
            AllergyCriticality.UNABLE_TO_ASSESS -> "⚪"
        }
        sb.append("$emoji  ")
        appendBold(sb, hit.allergyDisplay)
        sb.append(" × ${hit.candidateDisplay}\n")
        appendDetail(sb, ctx.getString(R.string.xchk_match_on, hit.matchedOn))
        sb.append("\n")
    }

    private fun appendAllergyConflictBullet(
        sb: SpannableStringBuilder,
        hit: NewAllergyConflict,
        ctx: Context,
    ) {
        val emoji = when (hit.criticality) {
            AllergyCriticality.HIGH -> "🔴"
            AllergyCriticality.LOW -> "🟡"
            AllergyCriticality.UNABLE_TO_ASSESS -> "⚪"
        }
        sb.append("$emoji  ")
        appendBold(sb, hit.existingMedDisplay)
        sb.append("  (ATC ${hit.existingMedAtc})\n")
        appendDetail(sb, ctx.getString(R.string.xchk_match_on, hit.matchedOn))
        sb.append("\n")
    }

    private fun appendDdiBullet(sb: SpannableStringBuilder, hit: DdiHit, ctx: Context) {
        val emoji = when (hit.severity) {
            CrossSeverity.MAJOR -> "🔴"
            CrossSeverity.MODERATE -> "🟠"
            CrossSeverity.MINOR -> "🟡"
            else -> "⚪"
        }
        sb.append("$emoji  ")
        appendBold(sb, hit.candidateDisplay)
        sb.append(" × ${hit.existingMedDisplay}\n")
        appendDetail(sb, ctx.getString(R.string.xchk_severity, hit.severity.name))
        sb.append("\n")
        hit.description?.let {
            appendDetail(sb, "📋 $it")
            sb.append("\n")
        }
        hit.management?.let {
            appendDetail(sb, "💡 $it")
            sb.append("\n")
        }
    }

    private fun appendDdBullet(
        sb: SpannableStringBuilder,
        hit: DrugDiseaseHit,
        ctx: Context,
    ) {
        val emoji = when (hit.severity) {
            CrossSeverity.MAJOR -> "🔴"
            CrossSeverity.MODERATE -> "🟠"
            CrossSeverity.MINOR -> "🟡"
            else -> "⚪"
        }
        sb.append("$emoji  ")
        appendBold(sb, hit.candidateDisplay)
        sb.append(" × ${hit.conditionDisplay}\n")
        appendDetail(sb, ctx.getString(R.string.xchk_severity, hit.severity.name))
        sb.append("\n")
        hit.description?.let {
            appendDetail(sb, "📋 $it")
            sb.append("\n")
        }
        hit.management?.let {
            appendDetail(sb, "💡 $it")
            sb.append("\n")
        }
    }

    private fun appendDetail(sb: SpannableStringBuilder, text: String) {
        val start = sb.length
        sb.append(text)
        sb.setSpan(
            ForegroundColorSpan(COLOR_INFO),
            start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        sb.setSpan(
            RelativeSizeSpan(0.9f),
            start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    private fun appendDisclaimer(sb: SpannableStringBuilder, ctx: Context) {
        val start = sb.length
        sb.append(ctx.getString(R.string.xchk_en_disclaimer))
        sb.setSpan(
            ForegroundColorSpan(COLOR_INFO),
            start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        sb.setSpan(
            RelativeSizeSpan(0.85f),
            start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}
