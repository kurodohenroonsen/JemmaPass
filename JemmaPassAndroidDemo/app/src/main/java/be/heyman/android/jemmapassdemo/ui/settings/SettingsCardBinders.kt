/*
 * SettingsCardBinders.kt — helpers that know how to bind a single layout
 * (view_model_card_v2 / view_kb_card / view_preflight_row) to a piece of
 * state. Keeps SettingsFragment under control by extracting per-card UI
 * code, and makes it trivial to reuse the same logic for E2B + E4B.
 */
package be.heyman.android.jemmapassdemo.ui.settings

import android.text.format.Formatter
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.ViewKbCardBinding
import be.heyman.android.jemmapassdemo.databinding.ViewModelCardV2Binding
import be.heyman.android.jemmapassdemo.databinding.ViewPreflightRowBinding
import be.heyman.android.jemmapassdemo.downloads.JemmaDownloadFormat
import be.heyman.android.jemmapassdemo.kb.KbState
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType

// ──────────────────────────────────────────────────────────────────────────
// PreflightRowBinder
// ──────────────────────────────────────────────────────────────────────────

class PreflightRowBinder(
    private val binding: ViewPreflightRowBinding,
    private val staticLabel: String,
) {
    init {
        binding.preflightLabel.text = staticLabel
    }

    fun bind(row: PreflightRow) {
        val ctx = binding.root.context
        binding.preflightDetail.text = row.detail
        when (row.status) {
            PreflightStatus.OK -> {
                binding.preflightStatusIcon.setImageResource(android.R.drawable.checkbox_on_background)
                binding.preflightStatusIcon.imageTintList =
                    ContextCompat.getColorStateList(ctx, R.color.severity_low)
            }
            PreflightStatus.KO -> {
                binding.preflightStatusIcon.setImageResource(android.R.drawable.ic_delete)
                binding.preflightStatusIcon.imageTintList =
                    ContextCompat.getColorStateList(ctx, R.color.severity_high)
            }
            PreflightStatus.IN_PROGRESS -> {
                binding.preflightStatusIcon.setImageResource(android.R.drawable.ic_popup_sync)
                binding.preflightStatusIcon.imageTintList =
                    ContextCompat.getColorStateList(ctx, R.color.jemma_primary)
            }
            PreflightStatus.PENDING -> {
                binding.preflightStatusIcon.setImageResource(android.R.drawable.ic_menu_recent_history)
                binding.preflightStatusIcon.imageTintList =
                    ContextCompat.getColorStateList(ctx, R.color.jemma_text_muted)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// ModelCardBinder (used for E2B and E4B Gemma cards)
// ──────────────────────────────────────────────────────────────────────────

class ModelCardBinder(
    private val binding: ViewModelCardV2Binding,
    private val model: Model,
    private val onPrimaryTap: () -> Unit,
    private val onDeleteTap: () -> Unit,
    private val getActiveId: () -> String?,
) {
    private var expanded: Boolean = false

    init {
        // Static fields populated once.
        binding.modelCardEmoji.text = "🧠"
        binding.modelCardName.text = model.displayName
        binding.modelCardCapabilities.text = "text · vision · audio"
        binding.modelCardSize.text =
            Formatter.formatFileSize(binding.root.context, model.sizeInBytes)
        binding.modelCardDescription.text = model.info
        binding.modelCardSource.text = model.url
        binding.modelCardBackend.text = "GPU"
        binding.modelCardFormat.text = ".litertlm"
        binding.modelCardPrefill.text = "—"
        binding.modelCardDecode.text = "—"

        // Header tap : toggle expanded state.
        binding.modelCardHeader.setOnClickListener {
            expanded = !expanded
            binding.modelCardExpandedSection.isVisible = expanded
            binding.modelCardChevron.rotation = if (expanded) 180f else 0f
        }
        binding.modelCardBtnPrimary.setOnClickListener { onPrimaryTap() }
        binding.modelCardBtnDelete.setOnClickListener { onDeleteTap() }
    }

    /**
     * Re-render based on current download status. Called every time
     * coordinator.statuses emits.
     */
    fun bind(status: ModelDownloadStatus?) {
        val ctx = binding.root.context
        val st = status?.status ?: ModelDownloadStatusType.NOT_DOWNLOADED
        val isActive = getActiveId() == model.name

        // ── Status pill ──
        when {
            isActive -> {
                binding.modelCardStatusPill.setText(R.string.dl_pill_active)
                binding.modelCardStatusPill.setBackgroundResource(R.drawable.bg_pill_count)
            }
            st == ModelDownloadStatusType.SUCCEEDED -> {
                binding.modelCardStatusPill.setText(R.string.dl_pill_succeeded)
                binding.modelCardStatusPill.setBackgroundResource(R.drawable.bg_pill_count)
            }
            st == ModelDownloadStatusType.IN_PROGRESS -> {
                binding.modelCardStatusPill.setText(R.string.dl_pill_downloading)
                binding.modelCardStatusPill.setBackgroundResource(R.drawable.bg_pill_locked)
            }
            st == ModelDownloadStatusType.PARTIALLY_DOWNLOADED -> {
                binding.modelCardStatusPill.setText(R.string.dl_pill_partial)
                binding.modelCardStatusPill.setBackgroundResource(R.drawable.bg_pill_locked)
            }
            st == ModelDownloadStatusType.FAILED -> {
                binding.modelCardStatusPill.setText(R.string.dl_pill_failed)
                binding.modelCardStatusPill.setBackgroundResource(R.drawable.bg_warning_chip)
            }
            else -> {
                binding.modelCardStatusPill.setText(R.string.dl_pill_not_downloaded)
                binding.modelCardStatusPill.setBackgroundResource(R.drawable.bg_pill_locked)
            }
        }

        // ── Progress section ──
        if (st == ModelDownloadStatusType.IN_PROGRESS && status != null) {
            binding.modelCardProgressSection.isVisible = true
            val pct = if (status.totalBytes > 0L) {
                ((status.receivedBytes * 100) / status.totalBytes).toInt().coerceIn(0, 100)
            } else 0
            binding.modelCardProgress.progress = pct
            binding.modelCardProgressLabel.text = buildString {
                append(JemmaDownloadFormat.bytesRatio(ctx, status.receivedBytes, status.totalBytes))
                if (status.bytesPerSecond > 0L) {
                    append(" · ")
                    append(JemmaDownloadFormat.rate(ctx, status.bytesPerSecond))
                }
                if (status.remainingMs > 0L) {
                    append(" · ")
                    append(JemmaDownloadFormat.eta(ctx, status.remainingMs))
                }
            }
        } else {
            binding.modelCardProgressSection.isVisible = false
        }

        // ── Action button ──
        when {
            isActive -> {
                binding.modelCardBtnPrimary.setText(R.string.dl_btn_already_active)
                binding.modelCardBtnPrimary.isEnabled = false
                binding.modelCardBtnDelete.isVisible = true
            }
            st == ModelDownloadStatusType.SUCCEEDED -> {
                binding.modelCardBtnPrimary.setText(R.string.dl_btn_activate)
                binding.modelCardBtnPrimary.isEnabled = true
                binding.modelCardBtnDelete.isVisible = true
            }
            st == ModelDownloadStatusType.IN_PROGRESS -> {
                binding.modelCardBtnPrimary.setText(R.string.dl_btn_pause)
                binding.modelCardBtnPrimary.isEnabled = true
                binding.modelCardBtnDelete.isVisible = false
            }
            st == ModelDownloadStatusType.PARTIALLY_DOWNLOADED -> {
                binding.modelCardBtnPrimary.setText(R.string.dl_btn_resume)
                binding.modelCardBtnPrimary.isEnabled = true
                binding.modelCardBtnDelete.isVisible = true
            }
            st == ModelDownloadStatusType.FAILED -> {
                binding.modelCardBtnPrimary.setText(R.string.dl_btn_retry)
                binding.modelCardBtnPrimary.isEnabled = true
                binding.modelCardBtnDelete.isVisible = false
            }
            else -> {
                binding.modelCardBtnPrimary.setText(R.string.dl_btn_download)
                binding.modelCardBtnPrimary.isEnabled = true
                binding.modelCardBtnDelete.isVisible = false
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// KbCardBinder — similar shape but with KB-specific stats
// ──────────────────────────────────────────────────────────────────────────

class KbCardBinder(
    private val binding: ViewKbCardBinding,
    private val model: Model,
    private val onPrimaryTap: () -> Unit,
    private val onDeleteTap: () -> Unit,
) {
    private var expanded: Boolean = false

    init {
        binding.kbCardName.text = model.displayName
        binding.kbCardSubtitle.text = "ATC · RxNorm · SNOMED · DDinter · FTS5"
        binding.kbCardSize.text =
            Formatter.formatFileSize(binding.root.context, model.sizeInBytes)
        binding.kbCardSource.text = model.url
        binding.kbCardStatCodes.text = "—"
        binding.kbCardStatLangs.text = "—"
        binding.kbCardStatDdi.text = "—"
        binding.kbCardStatFts.text = "—"

        binding.kbCardHeader.setOnClickListener {
            expanded = !expanded
            binding.kbCardExpandedSection.isVisible = expanded
            binding.kbCardChevron.rotation = if (expanded) 180f else 0f
        }
        binding.kbCardBtnPrimary.setOnClickListener { onPrimaryTap() }
        binding.kbCardBtnDelete.setOnClickListener { onDeleteTap() }
    }

    /** Update the download-related parts (pill + progress + button label). */
    fun bindDownloadStatus(status: ModelDownloadStatus?) {
        val ctx = binding.root.context
        val st = status?.status ?: ModelDownloadStatusType.NOT_DOWNLOADED

        if (st == ModelDownloadStatusType.IN_PROGRESS && status != null) {
            binding.kbCardProgressSection.isVisible = true
            val pct = if (status.totalBytes > 0L) {
                ((status.receivedBytes * 100) / status.totalBytes).toInt().coerceIn(0, 100)
            } else 0
            binding.kbCardProgress.progress = pct
            binding.kbCardProgressLabel.text = buildString {
                append(JemmaDownloadFormat.bytesRatio(ctx, status.receivedBytes, status.totalBytes))
                if (status.bytesPerSecond > 0L) {
                    append(" · ")
                    append(JemmaDownloadFormat.rate(ctx, status.bytesPerSecond))
                }
                if (status.remainingMs > 0L) {
                    append(" · ")
                    append(JemmaDownloadFormat.eta(ctx, status.remainingMs))
                }
            }
        } else {
            binding.kbCardProgressSection.isVisible = false
        }

        // Pill + button text (only when KB is NOT in Ready state ; Ready
        // overrides via bindKbState below).
        when (st) {
            ModelDownloadStatusType.NOT_DOWNLOADED -> {
                binding.kbCardStatusPill.setText(R.string.dl_pill_not_downloaded)
                binding.kbCardStatusPill.setBackgroundResource(R.drawable.bg_pill_locked)
                binding.kbCardBtnPrimary.setText(R.string.dl_btn_download)
                binding.kbCardBtnDelete.isVisible = false
            }
            ModelDownloadStatusType.PARTIALLY_DOWNLOADED -> {
                binding.kbCardStatusPill.setText(R.string.dl_pill_partial)
                binding.kbCardStatusPill.setBackgroundResource(R.drawable.bg_pill_locked)
                binding.kbCardBtnPrimary.setText(R.string.dl_btn_resume)
                binding.kbCardBtnDelete.isVisible = true
            }
            ModelDownloadStatusType.IN_PROGRESS -> {
                binding.kbCardStatusPill.setText(R.string.dl_pill_downloading)
                binding.kbCardStatusPill.setBackgroundResource(R.drawable.bg_pill_locked)
                binding.kbCardBtnPrimary.setText(R.string.dl_btn_pause)
                binding.kbCardBtnDelete.isVisible = false
            }
            ModelDownloadStatusType.FAILED -> {
                binding.kbCardStatusPill.setText(R.string.dl_pill_failed)
                binding.kbCardStatusPill.setBackgroundResource(R.drawable.bg_warning_chip)
                binding.kbCardBtnPrimary.setText(R.string.dl_btn_retry)
                binding.kbCardBtnDelete.isVisible = false
            }
            else -> {
                // SUCCEEDED / UNZIPPING : will be overridden by KB state binder.
            }
        }
    }

    /**
     * Update the KB-state-specific UI : pill, expanded stats, "Reload" button
     * label when Ready. This trumps the download-status binding when the
     * file is on disk.
     */
    fun bindKbState(state: KbState) {
        when (state) {
            is KbState.Ready -> {
                binding.kbCardStatusPill.setText(R.string.dl_pill_succeeded)
                binding.kbCardStatusPill.setBackgroundResource(R.drawable.bg_pill_count)
                binding.kbCardStatCodes.text = formatLong(state.totalCodes)
                binding.kbCardStatLangs.text = state.totalLanguages.toString()
                binding.kbCardStatDdi.text = formatLong(state.totalDdiPairs)
                binding.kbCardStatFts.text = if (state.fts5Ok) "✓" else "✗"
                binding.kbCardBtnPrimary.setText(R.string.kb_card_btn_reload)
                binding.kbCardBtnPrimary.isEnabled = true
                binding.kbCardBtnDelete.isVisible = true
            }
            is KbState.Validating -> {
                binding.kbCardStatusPill.setText(R.string.dl_pill_downloading)
                binding.kbCardStatusPill.setBackgroundResource(R.drawable.bg_pill_locked)
                binding.kbCardStatCodes.text = "…"
                binding.kbCardStatLangs.text = "…"
                binding.kbCardStatDdi.text = "…"
                binding.kbCardStatFts.text = "…"
            }
            is KbState.Failed -> {
                binding.kbCardStatusPill.setText(R.string.dl_pill_failed)
                binding.kbCardStatusPill.setBackgroundResource(R.drawable.bg_warning_chip)
            }
            is KbState.NotPresent -> { /* leave as set by bindDownloadStatus */ }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────

private fun formatLong(n: Long): String {
    return when {
        n >= 1_000_000L -> "${"%.1f".format(n / 1_000_000.0)} M"
        n >= 1_000L -> "${(n / 1_000)} K"
        else -> n.toString()
    }
}
