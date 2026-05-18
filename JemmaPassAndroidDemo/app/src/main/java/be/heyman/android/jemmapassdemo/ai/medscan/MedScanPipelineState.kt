/*
 * MedScanPipelineState.kt — JEMMA Pass · JemmaAppDemo · v2.6.2a
 *
 * State model for the rescuer med-scan killer demo. The pipeline goes
 * through 5 sequential steps each of which can be PENDING → RUNNING →
 * OK / WARN / FAIL.
 *
 * 🆕 v2.6.2a — NEW step GEMMA_REASON inserted between OCR_TEXT and
 *   IDENTIFY_DRUG. The flow is now :
 *
 *     📷 SCAN_BLISTER → 🔤 OCR_TEXT → 🧠 GEMMA_REASON → 🔎 IDENTIFY_DRUG → 🚨 CROSS_CHECK
 *
 *   GEMMA_REASON runs Gemma 4 E4B phase 1a (text-only) to propose 5
 *   search candidates from the OCR text. IDENTIFY_DRUG then runs the
 *   deterministic KB search (phase 1b) over those candidates.
 *
 * The state is held by [MedScanController] and observed by the host
 * Fragment via a StateFlow. The fragment is dumb : it diffs the
 * incoming state against the rendered state and repaints what changed.
 */
package be.heyman.android.jemmapassdemo.ai.medscan

import be.heyman.android.jemmapassdemo.kb.CrossCheckResult

/** Lifecycle of a single pipeline step in the UI timeline. */
enum class StepLifecycle { PENDING, RUNNING, OK, WARN, FAIL }

/** Canonical step keys — order matters (timeline is rendered in order). */
enum class StepKey(val emoji: String) {
    SCAN_BLISTER("📷"),
    OCR_TEXT("🔤"),
    GEMMA_REASON("🧠"),   // 🆕 v2.6.2a — Gemma phase 1a
    IDENTIFY_DRUG("🔎"),
    CROSS_CHECK("🚨"),
}

/**
 * One row of the pipeline timeline.
 */
data class StepRow(
    val key: StepKey,
    val labelResId: Int,
    val state: StepLifecycle,
    val detail: String? = null,
    val durationMs: Long? = null,
)

/**
 * Top-level verdict surfaced under the timeline once the cross-check
 * settled. Drives the color of the bottom banner.
 */
sealed class Verdict {

    object None : Verdict()

    data class Major(
        val candidateDisplay: String,
        val title: String,
        val body: String,
        val crossCheck: CrossCheckResult,
    ) : Verdict()

    data class Moderate(
        val candidateDisplay: String,
        val title: String,
        val body: String,
        val crossCheck: CrossCheckResult,
    ) : Verdict()

    data class Minor(
        val candidateDisplay: String,
        val title: String,
        val body: String,
        val crossCheck: CrossCheckResult,
    ) : Verdict()

    data class Clean(
        val candidateDisplay: String,
        val crossCheck: CrossCheckResult,
    ) : Verdict()

    data class NotIdentified(
        val ocrText: String,
        val reason: String,
    ) : Verdict()

    data class Failed(
        val stage: StepKey,
        val reason: String,
    ) : Verdict()

    /**
     * 🆕 v2.6.2b — agentic verdict. Gemma ran as an agent with the
     * search + interactions tools and produced a free-form text verdict
     * (already in the victim's language). Whether it's safe, dangerous,
     * or unsure is encoded in the prose (which the rescuer reads and
     * TTS speaks). No structured severity — Gemma already synthesized
     * a stress-readable message.
     *
     * The UI may scan [text] for keywords like "DO NOT ADMINISTER" /
     * "NE PAS DONNER" / "投与しないでください" to pick a banner colour,
     * but that's a presentation concern, not a correctness one.
     */
    data class AgentText(
        val victimDisplayName: String,
        val text: String,
    ) : Verdict()
}

/**
 * Full pipeline state, observed by the Fragment.
 */
data class MedScanPipelineState(
    val isActive: Boolean = false,
    val steps: List<StepRow> = emptyList(),
    val verdict: Verdict = Verdict.None,
    val totalDurationMs: Long = 0L,
) {
    companion object {
        val IDLE = MedScanPipelineState()
    }
}
