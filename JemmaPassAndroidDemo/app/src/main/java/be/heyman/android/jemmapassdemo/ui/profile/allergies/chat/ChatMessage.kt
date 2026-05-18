/*
 * ChatMessage.kt — JEMMA Pass · Lot 14.5c30 (PHASE 14)
 *
 * Sealed model for the allergies chat. THREE message types, each
 * rendered by a different view holder :
 *
 *   • ChatMessageText        — classic dialogue bubble (USER or JEMMA)
 *   • ChatMessageBgAction    — Material 3 chip showing a @Tool invocation
 *                              live (spinner during RUNNING, check + latency
 *                              ms once DONE). The hackathon demo signature.
 *   • ChatMessageReasoning   — collapsable parent bubble grouping the tool
 *                              calls of a single Gemma turn. Tap to expand.
 *
 * Why a sealed class :
 *   The previous lot used a single data class with a `streaming` flag —
 *   fine for 2 types but breaks down at 3+ with different fields. Sealed
 *   keeps the type-safety + lets the adapter `when()` exhaustively over
 *   it. Each subtype carries only its own fields.
 *
 * View type integer mapping is defined in [ChatAdapter] for performance
 * (RecyclerView pools by viewType).
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies.chat

import java.util.concurrent.atomic.AtomicLong

enum class ChatRole { USER, JEMMA }

sealed class ChatMessage {
    abstract val id: Long

    /** Classic text bubble: user spoke or Jemma replied via playTTS. */
    data class Text(
        override val id: Long,
        val role: ChatRole,
        val text: String,
        val streaming: Boolean = false,
    ) : ChatMessage()

    /**
     * A bg-action chip — one per @Tool invocation. The agent emits two
     * events per call (RUNNING then DONE); the fragment translates them
     * into a single chip that animates state in place.
     *
     * actionId is the stable id from the agent's BgAction event — it's
     * what we use to update the same chip when RUNNING transitions to
     * DONE. The ChatMessage [id] is derived from actionId (hash) so
     * DiffUtil treats it as the same item.
     */
    data class BgAction(
        override val id: Long,
        val actionId: String,
        val emoji: String,
        val label: String,
        val value: String?,
        val state: AgentActionState,
        val detail: String?,
        val latencyMs: Long?,
    ) : ChatMessage()

    /**
     * Future use (Lot 14.5c31): collapsable parent grouping the tool calls
     * of a single Gemma turn. Carries the list of child action ids so the
     * adapter can hide/show them on tap.
     */
    data class Reasoning(
        override val id: Long,
        val title: String,
        val toolCallCount: Int,
        val totalLatencyMs: Long,
        val expanded: Boolean = false,
        val childActionIds: List<String> = emptyList(),
    ) : ChatMessage()

    companion object {
        private val counter = AtomicLong(0L)
        fun nextId(): Long = System.currentTimeMillis() * 1000 + (counter.incrementAndGet() % 1000)

        /** Derive a stable ChatMessage id from a BgAction.actionId string. */
        fun idFromActionId(actionId: String): Long {
            // Hash to Long, stay positive. Collisions are vanishingly rare for our
            // session scale (< 200 actions per chat).
            return actionId.hashCode().toLong() and 0xFFFFFFFFL
        }
    }
}
