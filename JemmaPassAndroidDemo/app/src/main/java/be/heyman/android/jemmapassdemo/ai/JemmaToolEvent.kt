/*
 * JemmaToolEvent.kt — JEMMA Pass · JemmaAppDemo · v2.6.0
 *
 * Sealed events emitted by the [JemmaTools] @Tool surface when Gemma 4
 * decides to invoke an action that has a side-effect on the device UI
 * (alert banners, toasts, focus changes, narration). The events are
 * fanned out to interested fragments via a [kotlinx.coroutines.flow.SharedFlow]
 * exposed by [JemmaTools.events].
 *
 * Why a sealed Event stream rather than direct callbacks ?
 *   1. The same @Tool surface is shared across many UI surfaces (Patient
 *      Detail, Forge wizard, Chat panel). Each surface subscribes only
 *      to the events it cares about — no need to plumb N callbacks per
 *      tool.
 *   2. SharedFlow gives us automatic lifecycle-aware buffering. A
 *      fragment that hasn't yet attached its observer doesn't miss the
 *      event ; a fragment that's gone won't receive ghosts.
 *   3. The events are testable in isolation : feed Gemma a prompt, observe
 *      the resulting event stream, assert on it.
 *
 * Convention : every event carries a millisecond [timestampMs] so consumers
 * can compute latency from the call site (useful for the hackathon demo
 * "Gemma decided in 187 ms" badges).
 *
 * Status of the UI bridge tools (triggerRedAlert / triggerToast / etc.) :
 *   • At v2.6.0 (this delivery) the events are emitted but no UI surface
 *     observes them yet. The next delivery (2.6.1 — Killer Demo) attaches
 *     PatientDetailFragment to the [JemmaTools.events] flow and renders
 *     red alert banners + toasts. Smoke-test path : Gemma calls
 *     `triggerRedAlert("Pénicilline détectée", "...")` → event observed
 *     in logcat under tag `JEMMA-TOOLS-EVT`.
 */
package be.heyman.android.jemmapassdemo.ai

/**
 * Severity for [JemmaToolEvent.Toast] — the higher the more urgent. Maps
 * roughly to Material 3 SnackbarHostState semantics (NEUTRAL → default,
 * WARNING → tonal, CRITICAL → red).
 */
enum class ToastSeverity { INFO, WARNING, CRITICAL }

sealed class JemmaToolEvent {
    abstract val timestampMs: Long

    /**
     * Full-screen blocking red alert. Emitted by Gemma when it detects a
     * critical clinical condition (Major DDI, severe allergy match,
     * contraindicated drug).
     *
     * Consumers (fragments) typically render a Material 3 AlertDialog
     * with red header + a "Dismiss" + "Speak it" actions (the latter
     * triggers a TTS in the rescuer's language).
     */
    data class RedAlert(
        val title: String,
        val body: String,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : JemmaToolEvent()

    /**
     * Non-blocking toast. Emitted by Gemma for soft findings ("Resolved
     * Augmentin → ATC J01CR02") that don't need to interrupt the user.
     */
    data class Toast(
        val message: String,
        val severity: ToastSeverity = ToastSeverity.INFO,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : JemmaToolEvent()

    /**
     * Status frame for the step-by-step pipeline visualization. Emitted
     * not from Gemma but from the controller (e.g.
     * `MedScanForVictimController`) that orchestrates a multi-step
     * pipeline. Lets the UI render a live progress timeline like :
     *
     *   1. 📷 DocumentScanner    [done — 1.2s]
     *   2. 🧠 OCR (Latin + JA)  [running…]
     *   3. 💡 Gemma extract     [pending]
     *   4. 📚 KB resolve         [pending]
     *   5. 🚨 Cross-check        [pending]
     *
     * The fragment maintains a list of [PipelineStep] indexed by [stepKey]
     * and updates the matching row when an event arrives.
     */
    data class PipelineStep(
        val stepKey: String,
        val label: String,
        val state: PipelineStepState,
        val detail: String? = null,
        val durationMs: Long? = null,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : JemmaToolEvent()

    /**
     * Free-form narration. The chat panel (livraison 2.6.6) listens to
     * this and bubbles it ; PatientDetailFragment ignores it.
     */
    data class Narration(
        val text: String,
        val lang: String = "en",
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : JemmaToolEvent()
}

/** Lifecycle of a single pipeline step in the UI timeline. */
enum class PipelineStepState { PENDING, RUNNING, OK, WARN, FAIL }
