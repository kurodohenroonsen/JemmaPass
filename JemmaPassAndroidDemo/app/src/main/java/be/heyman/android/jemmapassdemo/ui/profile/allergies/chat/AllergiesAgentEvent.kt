/*
 * AllergiesAgentEvent.kt — JEMMA Pass · Lot 14.5c30 (PHASE 14)
 *
 * Sealed events emitted by [AllergiesAgentTools] when Gemma 4 decides to
 * call a @Tool. Each event corresponds to a tool invocation OR an
 * async-completion notification (KB lookup result, save commit, etc).
 *
 * The [AllergiesChatFragment] subscribes to a [kotlinx.coroutines.flow.SharedFlow]
 * of these events and translates each one into a [ChatMessageBgAction] —
 * a Material 3 chip rendered inline in the chat list. Result: the user
 * sees EVERY tool call Gemma performs, in real time, with latency
 * badges. This is the hackathon demo signature.
 *
 * Why a sealed event stream (vs direct fragment callbacks) :
 *   1. The @Tool methods are invoked from LiteRT-LM's native thread.
 *      We cannot touch UI from there. SharedFlow trampolines back to
 *      the main thread via collect().
 *   2. Multiple consumers can subscribe (PatientDetail, future SOS UI)
 *      without plumbing N callbacks through ToolSet.
 *   3. Testable in isolation : feed Gemma a prompt, assert the event
 *      stream.
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies.chat

/**
 * Lifecycle state of a single @Tool invocation (for the UI chip).
 */
enum class AgentActionState { RUNNING, DONE, FAILED }

/**
 * Lightweight candidate descriptor when Gemma can't pinpoint the SNOMED
 * code exactly. Returned by searchSnomedAllergyIntolerance / searchSnomedReaction.
 */
data class AgentCandidate(
    val code: String,
    val display: String,
    val system: String = "http://snomed.info/sct",
)

sealed class AllergiesAgentEvent {
    abstract val timestampMs: Long

    /**
     * A @Tool was invoked by Gemma. The fragment appends a bg-action chip
     * in [AgentActionState.RUNNING] state. Most synchronous setters
     * transition to DONE immediately (next event with same actionId).
     *
     * @param actionId   stable id used to update the chip in place when
     *                   the action transitions RUNNING→DONE/FAILED.
     * @param toolName   name of the @Tool method invoked (used for log)
     * @param emoji      emoji rendered in the chip's left slot
     * @param label      localized short label (e.g. "Substance")
     * @param value      localized value captured (e.g. "Pénicilline"),
     *                   null while RUNNING with no value yet.
     * @param state      current lifecycle state
     * @param detail     optional expandable detail (full KB row, etc.)
     * @param latencyMs  ms between RUNNING and DONE (null while RUNNING)
     */
    data class BgAction(
        val actionId: String,
        val toolName: String,
        val emoji: String,
        val label: String,
        val value: String? = null,
        val state: AgentActionState = AgentActionState.RUNNING,
        val detail: String? = null,
        val latencyMs: Long? = null,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : AllergiesAgentEvent()

    /**
     * Draft state has changed. Emitted by every set* tool AFTER its
     * BgAction. The fragment updates [IpsAllergyDraft] and re-renders
     * the mini-card. No new chip created — the chip is the BgAction.
     */
    data class DraftFieldUpdated(
        val field: String,
        val value: String?,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : AllergiesAgentEvent()

    /**
     * Gemma requested vocal output via playTTS(). Fragment streams the
     * text into the TTS session and (optionally) appends a JEMMA bubble
     * containing the same text for the visual chat log.
     */
    data class Narration(
        val text: String,
        val lang: String,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : AllergiesAgentEvent()

    /**
     * Gemma decided the allergy draft is complete and validated. Fragment
     * commits the bundle (same path as the manual form save) and pops
     * back to the allergies list.
     *
     * The fragment is responsible for resolving any remaining KB lookups
     * (substance/manifestation) before the actual save — the agent state
     * carries those.
     */
    data class SaveCommit(
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : AllergiesAgentEvent()

    /**
     * Gemma decided to abort the data collection (e.g. user said "annule").
     * Fragment pops back without saving.
     */
    data class CancelAndExit(
        val reason: String,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : AllergiesAgentEvent()

    /**
     * Cross-check tool ran against the focus profile. The result is
     * narrated by Gemma in a follow-up tool call (it may then call
     * triggerRedAlert if matches found).
     */
    data class CrossCheckCompleted(
        val matchCount: Int,
        val details: String,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : AllergiesAgentEvent()
}
