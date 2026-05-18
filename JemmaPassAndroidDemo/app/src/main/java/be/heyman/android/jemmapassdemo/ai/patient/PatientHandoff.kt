/*
 * PatientHandoff.kt — JEMMA Pass · Lot 14.5c35
 *
 * In-memory bridge between AssistantPatientPipelineFragment (which
 * extracts a PatientDraft via Gemma+OCR) and PatientEditFragment
 * (which pre-fills the form for user review).
 *
 * Why a singleton object rather than nav-args Bundle :
 * the draft has many optional fields — a Bundle would either bloat the
 * nav args or force serialization. A volatile static holder is simpler
 * and matches the pattern used for medications (AssistantHandoff).
 *
 * Lifecycle :
 *   1. Pipeline fragment calls set(draft) on success
 *   2. Pipeline fragment navigates to PatientEditFragment with arg
 *      handoff=true
 *   3. PatientEditFragment.onViewCreated reads handoff=true → calls
 *      PatientHandoff.consume() to get + clear the draft
 *   4. PatientEditFragment.populateForm(draft.toJPatient())
 *
 * If the user navigates away before consuming, the draft hangs around.
 * That's OK — the next set() overwrites it. We also clear it on
 * consume() to avoid stale data on a 2nd entry.
 */
package be.heyman.android.jemmapassdemo.ai.patient

import android.util.Log

private const val TAG = "JEMMA-PATIENT-HANDOFF"

object PatientHandoff {

    @Volatile
    private var pending: PatientDraft? = null

    fun set(draft: PatientDraft) {
        pending = draft
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🤝 handoff SET · " +
            "filled=${draft.filledCount} · givenName='${draft.givenName}' family='${draft.familyName}'")
    }

    fun consume(): PatientDraft? {
        val taken = pending
        pending = null
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🤝 handoff CONSUMED · " +
            "wasPresent=${taken != null}")
        return taken
    }

    fun peek(): PatientDraft? = pending

    fun clear() {
        pending = null
    }
}
