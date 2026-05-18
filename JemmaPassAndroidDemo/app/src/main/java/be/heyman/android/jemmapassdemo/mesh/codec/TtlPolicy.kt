package be.heyman.android.jemmapassdemo.mesh.codec

/**
 * 🆕 L44.16.75 — Per-chunk-type TTL defaults.
 *
 * ── Council Round 1 verdict (claudeai blind-spot finding) ───────
 * The dossier originally treated TTL as a single global value (TTL=3,
 * ~300m). In reality, event TTL and victim TTL want different values :
 *
 *   • Victim chunks are 5-chunk rotation, large, benefit from wide
 *     propagation → TTL=3 (3 hops max ≈ 300m)
 *   • Rescuer chunks (rescuers move; their state should refresh from
 *     origin, not echo through stale relays) → TTL=2
 *   • Event HELP/STAB/EVAC : safety-critical, should propagate → TTL=3
 *   • Event DCD : safety-critical too — late-arriving rescuers (PPJ at
 *     2 hops) need to know the victim is deceased so they don't waste
 *     time on resuscitation. Originally TTL=1 ("anti-stale-race") but
 *     the LWW-asymmetric grace window from L44.16.76 already handles
 *     the stale-DCD-outraces-fresh-STAB risk at the StatusResolver
 *     layer. Bumped to TTL=3 in L44.16.82 (audit dryrun 2026-05-08
 *     11h56 confirmed empirically: Samsung dropped DCD with
 *     "use-locally-only event TTL exhausted").
 *   • Event WAIT : low-priority, reduces mesh saturation → TTL=1
 *
 * One line of config, big behavioral difference.
 *
 * ── Usage ────────────────────────────────────────────────────────
 *   TtlPolicy.initial(ChunkType.VICTIM_DIRECT)  // → 3 (when first relayed)
 *   TtlPolicy.initial("E", "DCD")               // → 3 (since L44.16.82)
 *   TtlPolicy.decrement(currentTtl)             // returns new ttl, or 0 if expired
 */
object TtlPolicy {
    /** Default TTL for victim profile relays. 3 hops ≈ 300m radius. */
    const val TTL_VICTIM = 3

    /** Default TTL for rescuer beacon relays. 2 hops ≈ 200m. */
    const val TTL_RESCUER = 2

    /** Default TTL for HELP/STAB/EVAC/DCD events. Safety-critical. */
    const val TTL_EVENT_CRITICAL = 3

    /** Default TTL for EVAL events. Less urgent. */
    const val TTL_EVENT_NORMAL = 2

    /**
     * Default TTL for WAIT events. Low priority — reduces mesh saturation.
     * (Note: was previously also used for DCD; DCD bumped to CRITICAL
     * in L44.16.82, see class doc.)
     */
    const val TTL_EVENT_LOW = 1

    /**
     * Compute the initial TTL for a given chunk type and (optionally)
     * SALT status code (only meaningful for EVENT chunks).
     *
     * @param type the chunk type being relayed
     * @param saltCode optional SALT 4-char code (WAIT, EVAL, STAB, HELP, EVAC, DCD)
     * @return TTL value to embed in the relayed chunk
     */
    fun initial(type: ChunkType, saltCode: String? = null): Int {
        return when (type) {
            ChunkType.VICTIM_DIRECT,
            ChunkType.VICTIM_RELAYED -> TTL_VICTIM

            ChunkType.RESCUER_DIRECT,
            ChunkType.RESCUER_RELAYED -> TTL_RESCUER

            ChunkType.EVENT -> when (saltCode?.uppercase()) {
                // 🆕 L44.16.82 — DCD joins HELP/STAB/EVAC at TTL=3.
                // The original TTL=1 broke the 3-phone demo: late-arriving
                // PPJ at 2 hops never saw declared deaths.
                "HELP", "STAB", "EVAC", "DCD" -> TTL_EVENT_CRITICAL
                "EVAL" -> TTL_EVENT_NORMAL
                "WAIT" -> TTL_EVENT_LOW
                null -> TTL_EVENT_NORMAL  // fallback if status unknown
                else -> TTL_EVENT_NORMAL
            }

            // 🆕 L44.16.87b — Fingerprint chunks have no TTL semantic.
            //
            // F| chunks are pure local-state advertisements : a peer
            // computes them from its own cached profile and broadcasts
            // them as a "this is what I have" signal. They are never
            // relayed (RelayDecider drops them, see below) so the TTL
            // value here is effectively unused.
            //
            // We return TTL=1 as a safe sentinel : if some future code
            // path accidentally tries to relay a F| chunk, the TTL=1
            // means it dies on first hop without flooding the mesh.
            ChunkType.FINGERPRINT -> 1
        }
    }

    /**
     * Decrement TTL by 1. Returns the new value, or 0 if already at 0
     * (signals "do not relay further").
     *
     * Caller is responsible for checking `decrement(...) > 0` before
     * forwarding.
     */
    fun decrement(currentTtl: Int): Int {
        return (currentTtl - 1).coerceAtLeast(0)
    }
}
