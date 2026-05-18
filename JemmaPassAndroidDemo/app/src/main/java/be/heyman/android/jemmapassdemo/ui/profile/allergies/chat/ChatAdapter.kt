/*
 * ChatAdapter.kt — JEMMA Pass · Lot 14.5c30 (PHASE 14)
 *
 * RecyclerView adapter for the allergies chat. THREE view types after
 * the agent refactor :
 *
 *   • VIEW_USER       — user bubble (green, right-aligned, 👤 avatar)
 *   • VIEW_JEMMA      — Jemma bubble (dark, left-aligned, 🤖 avatar)
 *   • VIEW_BG_ACTION  — Material 3-style chip surfacing a @Tool call
 *                       (RUNNING → DONE, with latency badge)
 *
 * The chip is tap-to-expand: tapping the chip toggles the visibility of
 * the `detail` row (full SNOMED candidate list, cross-check details, etc).
 *
 * DiffUtil — careful : Text + BgAction have different id schemes but live
 * in the same RecyclerView list. The sealed [ChatMessage.id] is unique
 * enough across types (Text uses nextId(), BgAction uses idFromActionId()
 * which hashes the actionId string). Content equality is per-subtype.
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R

private const val VIEW_USER = 0
private const val VIEW_JEMMA = 1
private const val VIEW_BG_ACTION = 2

class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int =
        when (val m = getItem(position)) {
            is ChatMessage.Text ->
                if (m.role == ChatRole.USER) VIEW_USER else VIEW_JEMMA
            is ChatMessage.BgAction -> VIEW_BG_ACTION
            is ChatMessage.Reasoning -> VIEW_BG_ACTION // reserved for 14.5c31, falls back to chip view
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_USER -> TextViewHolder(
                inflater.inflate(R.layout.view_chat_msg_user, parent, false)
            )
            VIEW_BG_ACTION -> BgActionViewHolder(
                inflater.inflate(R.layout.view_chat_msg_bg_action, parent, false)
            )
            else -> TextViewHolder(
                inflater.inflate(R.layout.view_chat_msg_jemma, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val m = getItem(position)) {
            is ChatMessage.Text -> (holder as TextViewHolder).bind(m)
            is ChatMessage.BgAction -> (holder as BgActionViewHolder).bind(m)
            is ChatMessage.Reasoning -> { /* TODO lot 14.5c31 */ }
        }
    }

    class TextViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bubble: TextView = view.findViewById(R.id.chat_msg_bubble)

        fun bind(msg: ChatMessage.Text) {
            bubble.text = if (msg.streaming && msg.text.isNotEmpty()) {
                "${msg.text} ▮"
            } else if (msg.streaming) {
                "▮"
            } else {
                msg.text
            }
        }
    }

    class BgActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val chip: LinearLayout = view.findViewById(R.id.bg_action_chip)
        private val emoji: TextView = view.findViewById(R.id.bg_action_emoji)
        private val label: TextView = view.findViewById(R.id.bg_action_label)
        private val separator: TextView = view.findViewById(R.id.bg_action_separator)
        private val value: TextView = view.findViewById(R.id.bg_action_value)
        private val latency: TextView = view.findViewById(R.id.bg_action_latency)
        private val detail: TextView = view.findViewById(R.id.bg_action_detail)

        fun bind(msg: ChatMessage.BgAction) {
            // ── Emoji column: state-aware. RUNNING shows ⏳ overlay, FAILED ❌.
            emoji.text = when (msg.state) {
                AgentActionState.RUNNING -> "${msg.emoji} ⏳"
                AgentActionState.DONE -> msg.emoji
                AgentActionState.FAILED -> "${msg.emoji} ❌"
            }
            // ── Label always shown.
            label.text = msg.label
            // ── Value column shown only when present.
            if (msg.value.isNullOrBlank()) {
                separator.visibility = View.GONE
                value.visibility = View.GONE
            } else {
                separator.visibility = View.VISIBLE
                value.visibility = View.VISIBLE
                value.text = msg.value
            }
            // ── Latency shown only on DONE/FAILED with a valid duration.
            if (msg.latencyMs != null && msg.state != AgentActionState.RUNNING) {
                latency.visibility = View.VISIBLE
                latency.text = "${msg.latencyMs}ms"
            } else {
                latency.visibility = View.GONE
            }
            // ── Detail row: visibility depends on (a) detail non-null, (b) any
            // previous user tap (kept on the chip's tag). DiffUtil reuses the
            // ViewHolder so we always reset properly.
            val hasDetail = !msg.detail.isNullOrBlank()
            val initiallyExpanded = chip.tag as? Boolean ?: false
            if (hasDetail && initiallyExpanded) {
                detail.visibility = View.VISIBLE
                detail.text = msg.detail
            } else {
                detail.visibility = View.GONE
            }
            chip.setOnClickListener {
                if (!hasDetail) return@setOnClickListener
                val isVisible = detail.visibility == View.VISIBLE
                detail.visibility = if (isVisible) View.GONE else View.VISIBLE
                if (!isVisible) detail.text = msg.detail
                chip.tag = !isVisible
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) = old.id == new.id
            override fun areContentsTheSame(old: ChatMessage, new: ChatMessage): Boolean {
                if (old::class != new::class) return false
                return when {
                    old is ChatMessage.Text && new is ChatMessage.Text ->
                        old.text == new.text && old.streaming == new.streaming && old.role == new.role
                    old is ChatMessage.BgAction && new is ChatMessage.BgAction ->
                        old.state == new.state &&
                            old.value == new.value &&
                            old.detail == new.detail &&
                            old.latencyMs == new.latencyMs &&
                            old.label == new.label &&
                            old.emoji == new.emoji
                    old is ChatMessage.Reasoning && new is ChatMessage.Reasoning ->
                        old.title == new.title &&
                            old.toolCallCount == new.toolCallCount &&
                            old.totalLatencyMs == new.totalLatencyMs &&
                            old.expanded == new.expanded
                    else -> false
                }
            }
        }
    }
}
