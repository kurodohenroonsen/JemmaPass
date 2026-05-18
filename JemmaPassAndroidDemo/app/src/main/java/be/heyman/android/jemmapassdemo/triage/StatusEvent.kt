package be.heyman.android.jemmapassdemo.triage

/**
 * 🆕 L44.16.76 — In-memory triage event for a single victim.
 *
 * This is the consumer-facing model used by `JemmaRadarBridge` and
 * the JS UI. It decouples wire format details (`E|...|...`) from
 * the radar UI's event-per-victim view.
 *
 * ── State ownership ─────────────────────────────────────────────
 * `StatusResolver` owns the per-victim "current state" map. Each
 * victim has at most ONE active StatusEvent at any time; new events
 * either overwrite (LWW) or are rejected (asymmetric LWW grace).
 *
 * ── Source tracking ─────────────────────────────────────────────
 * `hopCount = 0` means we received this event directly from the
 * rescuer's phone. `hopCount > 0` means it came through the mesh.
 * The UI shows a "via N hops" breadcrumb badge for hopCount ≥ 1.
 *
 * ── Override flag ───────────────────────────────────────────────
 * `isExplicitOverride = true` is used when a rescuer manually presses
 * the "Cancel deceased" button to revive a victim from DCD. This
 * bypasses the asymmetric LWW grace window — the rescuer made an
 * explicit decision and the protocol must honour it.
 */
data class StatusEvent(
    /** SID of the victim this event applies to. */
    val victimSid: String,
    /** SALT triage code. */
    val status: SaltCode,
    /** SID of the rescuer who took the action. */
    val rescuerSid: String,
    /** UNIX seconds when the event was created at origin. */
    val timestampSec: Long,
    /** Hop count when received (0 = direct, ≥1 = relayed). */
    val hopCount: Int = 0,
    /** True if this event explicitly overrides a DCD lockdown. */
    val isExplicitOverride: Boolean = false,
    /** Per-(source, victim) sequence number — for dedup. */
    val seq: Int = 0
) {
    /**
     * Convert to JSON-compatible map for the JS bridge. Kotlin Maps
     * serialize cleanly via JSONObject without reflection.
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "victimSid" to victimSid,
        "status" to status.code,
        "statusEmoji" to status.emoji,
        "statusColor" to status.colorHex,
        "rescuerSid" to rescuerSid,
        "timestampSec" to timestampSec,
        "hopCount" to hopCount,
        "isExplicitOverride" to isExplicitOverride,
        "seq" to seq
    )
}
