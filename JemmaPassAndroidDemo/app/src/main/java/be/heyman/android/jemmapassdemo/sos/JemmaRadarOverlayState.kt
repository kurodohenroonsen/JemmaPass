package be.heyman.android.jemmapassdemo.sos

import android.content.Context
import android.util.Log

/**
 * 🆕 L1 v2.5.0 — Extracted from `JemmaRadarOverlay.kt` (Plan A baseline
 * L44.16.89b, ~3128 lines) to allow the SOS/Mesh runtime to compile in
 * Plan B without dragging the entire @Composable WebView-coupled UI.
 *
 * The full `@Composable JemmaRadarOverlay()` and its 5+ UI dependencies
 * (JemmaRadarView, JemmaQrCameraOverlay, JemmaMedScanForVictim, …) stay
 * in Plan A as reference. Plan B will rebuild the radar UI natively
 * (XML + Canvas) in L3 v2.5.2 once the SOS/Rescue mode bottom-sheet is
 * wired.
 *
 * What lives here:
 *   • `lastRescuerLat / lastRescuerLon / lastRescuerLocSetAtMs`
 *     — the critical invariant called out in the handoff. Every
 *     accurate (≤ 100 m) GPS update on the rescuer side mirrors here;
 *     the values reset to `0L` / `0.0` at STOP RADAR.
 *   • `sharedNearbyService` — process-wide singleton wrapper around
 *     `JemmaNearbySosService`. `getOrCreateNearbyService()` is called
 *     by `JemmaSosService` to enter the SOS-listen loop without
 *     standing up a competing Nearby client.
 *   • `recreateFresh()` — hard-cleanup for zombie Nearby advertise
 *     sessions surviving in `com.google.android.gms.persistent`. Drops
 *     the singleton and rebuilds it so a brand-new ConnectionsClient
 *     binding is acquired. Migrates listeners across the swap.
 *   • `radarMode` (RESCUER / SOS) — the bottom-sheet selection from
 *     the new `🚨 SOS/Rescue` tab will write here in L3.
 *   • `alertedPeers` — dedup set so DDI alerts aren't re-spoken every
 *     time the user navigates back to the same fiche.
 *   • `lastVictimRelay` — preserves a Haru-relay broadcast across GPS
 *     updates so a step from the rescuer doesn't overwrite the mixed
 *     advertising rotation back to solo.
 *
 * Logging tag: `JEMMA-NEARBY-LIFECYCLE` — already on the
 * `capture_jemma.sh` whitelist (`JEMMA-NEARBY:V` covers it).
 */
internal object JemmaRadarOverlayState {
    @Volatile var lastRescuerName: String? = null
    @Volatile var lastRescuerLangCode: String? = null

    /**
     * 🆕 L45.0.5 — Cached rescuer GPS position fed by RadarOverlay's
     * LocationListener and read by JsBridge.readCurrentGpsFix() when
     * the rescuer scans a paper QR (Haru's badge).
     *
     * Bug pattern this fixes : Haru appeared 29 m away from the rescuer
     * after a QR scan because JsBridge was using LocationManager.
     * getLastKnownLocation(GPS_PROVIDER) which returned a stale fix
     * while the rescuer broadcaster was using NETWORK_PROVIDER updates.
     *
     * Every accurate (≤ 100 m) GPS update mirrors the position here.
     * JsBridge prefers this cache when set; falls back to legacy
     * LocationManager providers if not. Reset to 0L on STOP RADAR.
     */
    @Volatile var lastRescuerLat: Double = 0.0
    @Volatile var lastRescuerLon: Double = 0.0
    @Volatile var lastRescuerLocSetAtMs: Long = 0L

    /**
     * 🆕 L44.16.68 — Set of (sessionIdHex + alertSignature) pairs that have
     * already been spoken / vibrated for. Prevents the same alert from being
     * re-announced every time the user navigates back to the same peer's
     * fiche during a single radar session.
     *
     * The signature is built from the alert kind + drug ATC + allergy or
     * second-drug code — not from the alert object identity, because each
     * cross-check call produces a fresh JSON.
     *
     * Reset at STOP RADAR (full session reset), survives HIDE → re-open.
     */
    val alertedPeers: java.util.concurrent.ConcurrentHashMap<String, Boolean> =
        java.util.concurrent.ConcurrentHashMap()

    /**
     * 🆕 L44.16.38 — Radar mode.
     *
     * The radar overlay is now shared between two use cases :
     *   - RESCUER  : the user is searching for victims (default behavior).
     *   - SOS      : the user is the victim ; same radar UI but with
     *                a 🆘 pulsing centre, a different header, no TMP
     *                profile save (we don't want the victim to fill
     *                her own contact book), and TTS heartbeat that
     *                cycles through "Help me, I'm Claude!" in the
     *                rescuer's language with the rescuer's first name.
     *
     * Stored here (not as a Composable parameter) so the bottom-sheet
     * `🚨 SOS/Rescue` chooser can flip the mode before showing the
     * overlay (Plan B L3 v2.5.2).
     */
    enum class RadarMode { RESCUER, SOS }
    @Volatile var radarMode: RadarMode = RadarMode.RESCUER

    // 🆕 L44.16.23 — Single Nearby Connections service replaces the
    // previous dual scanner + broadcaster. The service handles both
    // advertising (rescuer beacon, victim chunks) AND discovery
    // (peer + rescuer reception) under one Nearby session.
    @Volatile var sharedNearbyService: JemmaNearbySosService? = null

    /** Stable session id reused across HIDE → re-open. */
    @Volatile var lastRescuerSid: String? = null

    /**
     * 🆕 L44.16.49 — Cache of the last "Haru relay" (victim re-broadcast
     * triggered by a paper QR scan) so that GPS updates of the rescuer
     * can re-emit the MIXED advertising (rescuer beacon + victim chunks)
     * instead of falling back to a plain solo-rescuer broadcast.
     */
    data class VictimRelaySnapshot(
        val sid: String,
        val name: String,
        val sex: Char,
        val age: Int,
        val bloodType: String,
        val criticality: Int,
        val allergyCodes: List<String>,
        val medCodes: List<String>,
        val conditionCodes: List<String>,
        val immunCodes: List<String>,
        val langCode: String
    )
    @Volatile var lastVictimRelay: VictimRelaySnapshot? = null

    /**
     * 🆕 L44.16.50 — Distinguishes "first overlay mount" (zombie kill
     * needed) from "remount after HIDE" (overlay was just dismissed
     * 5s ago, sessions are still alive, do NOT recreate).
     */
    @Volatile var wasHidden: Boolean = false

    fun getOrCreateNearbyService(context: Context): JemmaNearbySosService {
        sharedNearbyService?.let { return it }
        synchronized(this) {
            sharedNearbyService?.let { return it }
            val tNow = System.currentTimeMillis()
            Log.i("JEMMA-NEARBY-LIFECYCLE",
                "[t=$tNow] 🆕 getOrCreateNearbyService — first instance for this process")
            val s = JemmaNearbySosService(context.applicationContext)
            sharedNearbyService = s
            return s
        }
    }

    /**
     * 🆕 L44.16.26 — HARD CLEANUP for zombie advertise sessions.
     *
     * Background:
     *   Google Play Services (com.google.android.gms.persistent) keeps
     *   our previous Nearby advertises alive in the background EVEN AFTER
     *   our app process is killed. These "zombies" survive until the
     *   phone reboots, and they pollute discovery slots.
     *
     *   `fullStop()` on our existing connectionsClient instance does NOT
     *   tear down these zombies because GMS holds them on a separate
     *   binding tied to the previous session.
     *
     * Solution:
     *   Drop the singleton entirely, force a new
     *   `Nearby.getConnectionsClient()` call by creating a brand-new
     *   JemmaNearbySosService. The new client gets a fresh binding to
     *   GMS, which gives us access to the *active* session and lets us
     *   tear down zombies via `stopAllEndpoints()` on the new client.
     *
     * Calling pattern:
     *   - At app boot (StartActivity.onCreate) → kill any leftover
     *   - Before every SOS broadcast start → fresh start guarantee
     *   - Before every radar overlay rescuer broadcast start → ditto
     *
     * NOT idempotent — each call really does drop and recreate.
     */
    fun recreateFresh(context: Context): JemmaNearbySosService {
        synchronized(this) {
            val tNow = System.currentTimeMillis()
            Log.i("JEMMA-NEARBY-LIFECYCLE",
                "[t=$tNow] 🔄 recreateFresh — dropping current Nearby instance to force GMS binding refresh")

            // 🆕 L44.16.32 — SAVE listeners from the outgoing instance
            // BEFORE dropping it, so we can migrate them to the new one.
            val savedExtraListeners = sharedNearbyService?.getExtraListenersSnapshot() ?: emptyList()
            val savedPrimaryListener = sharedNearbyService?.getPrimaryListener()
            Log.i("JEMMA-NEARBY-LIFECYCLE",
                "[t=$tNow]   📋 saved ${savedExtraListeners.size} extra listener(s) + primary=${savedPrimaryListener != null} for migration")

            // First, full stop on the existing instance (if any) to clear
            // its in-process state. This is a best-effort; the actual
            // zombie-killing leverage comes from the binding refresh.
            sharedNearbyService?.let { old ->
                try {
                    old.fullStop()
                    Log.i("JEMMA-NEARBY-LIFECYCLE",
                        "[t=$tNow]   ✓ fullStop() on outgoing instance done")
                } catch (e: Exception) {
                    Log.w("JEMMA-NEARBY-LIFECYCLE",
                        "[t=$tNow]   ⚠ fullStop() on outgoing threw (ignoring)", e)
                }
            }
            sharedNearbyService = null
            // Reset the rescuer sid as well — caller must re-fetch from
            // JemmaDeviceId.get() (which is stable, so it's harmless).
            lastRescuerSid = null
            // 🆕 L44.16.68 — fresh radar session = fresh alerts. Clear the
            // dedup set so re-discovered peers re-announce their alerts.
            alertedPeers.clear()

            // Build a brand-new instance. The internal
            // `Nearby.getConnectionsClient(context)` call inside the
            // constructor returns a fresh binding to GMS.
            val fresh = JemmaNearbySosService(context.applicationContext)
            sharedNearbyService = fresh

            // 🆕 L44.16.32 — Migrate the saved listeners onto the new
            // instance. This guarantees that downstream observers keep
            // receiving events without needing to know that a recreate
            // happened underneath them.
            for (l in savedExtraListeners) {
                fresh.addListener(l)
            }
            if (savedPrimaryListener != null) {
                fresh.setListener(savedPrimaryListener)
            }
            Log.i("JEMMA-NEARBY-LIFECYCLE",
                "[t=$tNow]   ✓ created fresh JemmaNearbySosService instance, migrated ${savedExtraListeners.size} extra listener(s) + primary=${savedPrimaryListener != null}")
            return fresh
        }
    }

    /**
     * 🆕 L1 v2.5.0 — Reset all rescuer-side state to "stopped" defaults.
     * Called by `RadarFragment` from L3 v2.5.2 on STOP RADAR. Keeps the
     * `sharedNearbyService` singleton alive so the fullStop()/teardown
     * happens through `recreateFresh()` if needed; this method only
     * clears the rescuer-position cache + alert dedup.
     */
    fun resetOnStopRadar() {
        val tNow = System.currentTimeMillis()
        lastRescuerLat = 0.0
        lastRescuerLon = 0.0
        lastRescuerLocSetAtMs = 0L
        lastRescuerName = null
        lastRescuerLangCode = null
        alertedPeers.clear()
        Log.i("JEMMA-NEARBY-LIFECYCLE",
            "[t=$tNow] 🛑 resetOnStopRadar — cleared rescuer GPS cache + alert dedup")
    }
}
