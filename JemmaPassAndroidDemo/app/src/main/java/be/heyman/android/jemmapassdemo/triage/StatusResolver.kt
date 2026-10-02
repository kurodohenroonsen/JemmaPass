package be.heyman.android.jemmapassdemo.triage

/**
 * 🆕 L44.16.76 — Asymmetric LWW (Last-Write-Wins) for SALT triage.
 *
 * ── Council Round 2 verdict (5/8 explicit ii) ───────────────────
 *
 * The council split on DCD policy in Round 1. Round 2 converged on
 * **asymmetric LWW with 30s grace + UI 2-tap + override flag** :
 *
 *   • Most transitions : pure LWW with deterministic tie-break
 *   • Promote TO DCD : always allowed (UI 2-tap confirms intent)
 *   • Demote FROM DCD : 30s grace window OR explicit override flag
 *
 * ── Why asymmetric ──────────────────────────────────────────────
 *
 * The killer scenario : a stale DCD event from 4 minutes ago propagates
 * slowly through 2-hop relay, arrives AFTER a fresh STAB event. Pure
 * LWW (later timestamp wins) would mark Haru as deceased even though
 * she's just been stabilized. With asymmetric LWW + 30s grace, the
 * stale DCD is rejected because it's not "fresh enough" to overwrite
 * the recent non-DCD state.
 *
 * The grace window of 30s matches the worst-case 3-hop propagation
 * latency in our 1.75s-rotation mesh.
 *
 * ── Override flag ───────────────────────────────────────────────
 *
 * Real-world false positives on DCD do happen (weak pulse missed,
 * hypothermia mimicking death). The rescuer must have a way to
 * correct this immediately. The "Cancel deceased" UI button sets
 * `isExplicitOverride=true`, which bypasses the grace window.
 *
 * ── Tie-break rule ──────────────────────────────────────────────
 *
 * When two events have IDENTICAL timestamps (rare but possible), we
 * use lexicographic comparison of the rescuerSid as a deterministic
 * tie-break. This guarantees ALL phones converge to the same answer.
 *
 * ── Thread safety ───────────────────────────────────────────────
 *
 * `StatusResolver` is NOT thread-safe. Callers should serialize
 * access via Mutex or single-threaded executor. The radar bridge
 * uses a Mutex around the resolver instance.
 */
class StatusResolver {

    /** Per-victim current best-known status. */
    private val current = mutableMapOf<String, StatusEvent>()
    private val events = mutableMapOf<String, MutableList<StatusEvent>>()

    companion object {
        /**
         * Grace window for DCD demotion. Empirically matches worst-case
         * 3-hop mesh propagation latency at 1.75s/rotation.
         */
        const val DCD_GRACE_SEC = 30L
        const val MAX_EVENTS_PER_VICTIM = 64

        /**
         * Pure decision function — returns true if `incoming` should
         * overwrite `existing`. No state mutation. Useful for tests.
         *
         * Decision flow :
         *   1. Same status code → pure LWW (timestamp + tie-break)
         *   2. Promote to DCD (non-DCD → DCD) → pure LWW
         *   3. Demote from DCD (DCD → non-DCD) → grace OR override
         *   4. Other transitions (non-DCD → non-DCD different code) → pure LWW
         */
        fun shouldOverwrite(
            existing: StatusEvent,
            incoming: StatusEvent
        ): Boolean {
            // Case 1 : same status — pure LWW with deterministic tie-break
            if (existing.status == incoming.status) {
                return when {
                    incoming.timestampSec != existing.timestampSec ->
                        incoming.timestampSec > existing.timestampSec
                    else -> incoming.rescuerSid > existing.rescuerSid
                }
            }

            // Case 2 : promote to DCD — pure LWW (UI handles 2-tap confirm)
            if (existing.status != SaltCode.DCD && incoming.status == SaltCode.DCD) {
                return incoming.timestampSec > existing.timestampSec
            }

            // Case 3 : demote from DCD — asymmetric grace
            if (existing.status == SaltCode.DCD && incoming.status != SaltCode.DCD) {
                if (incoming.isExplicitOverride) return true
                return incoming.timestampSec > existing.timestampSec + DCD_GRACE_SEC
            }

            // Case 4 : other transitions (non-DCD → non-DCD different code)
            return when {
                incoming.timestampSec != existing.timestampSec ->
                    incoming.timestampSec > existing.timestampSec
                else -> incoming.rescuerSid > existing.rescuerSid
            }
        }
    }

    private fun resolve(list: List<StatusEvent>): StatusEvent? {
        val dcds = list.filter { it.status == SaltCode.DCD }
        val unsuppressed = list.filterNot { e ->
            e.status != SaltCode.DCD && !e.isExplicitOverride && dcds.any { d ->
                e.timestampSec >= d.timestampSec && e.timestampSec <= d.timestampSec + DCD_GRACE_SEC
            }
        }
        return unsuppressed.maxWithOrNull(
            compareBy<StatusEvent> { it.timestampSec }
                .thenBy { it.rescuerSid }
                .thenBy { it.status.ordinal }
        )
    }

    /**
     * Apply an incoming event. If it should overwrite the current state,
     * update internal map and return the new event. Otherwise return the
     * existing event unchanged.
     *
     * @return the resolved current event for this victim
     */
    fun apply(incoming: StatusEvent): StatusEvent {
        val list = events.getOrPut(incoming.victimSid) { mutableListOf() }
        val isDuplicate = list.any {
            it.rescuerSid == incoming.rescuerSid &&
                it.status == incoming.status &&
                it.timestampSec == incoming.timestampSec &&
                it.isExplicitOverride == incoming.isExplicitOverride
        }
        if (!isDuplicate) {
            list.add(incoming)
            if (list.size > MAX_EVENTS_PER_VICTIM) {
                val dcds = list.filter { it.status == SaltCode.DCD }
                val nonDcds = list.filter { it.status != SaltCode.DCD }
                val trimmed = (dcds + nonDcds.takeLast(MAX_EVENTS_PER_VICTIM - dcds.size.coerceAtMost(MAX_EVENTS_PER_VICTIM / 2)))
                    .distinct()
                    .sortedBy { it.timestampSec }
                list.clear()
                list.addAll(trimmed)
            }
        }
        val resolved = resolve(list) ?: incoming
        current[incoming.victimSid] = resolved
        return resolved
    }

    /** Return the current event for a victim, or null if none. */
    fun get(victimSid: String): StatusEvent? = current[victimSid]

    /** Return all current events (one per victim). */
    fun all(): Map<String, StatusEvent> = current.toMap()

    /** Clear all state. For tests and "wipe" operations. */
    fun clear() {
        current.clear()
        events.clear()
    }
}
