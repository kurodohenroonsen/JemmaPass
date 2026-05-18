/*
 * RadarController.kt — JEMMA Pass · Plan B · L2 v2.5.1
 *
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  Hilt @Singleton facade over the SOS / Mesh runtime ported in    ║
 * ║  L1 v2.5.0. Replaces the @JavascriptInterface methods of Plan A's║
 * ║  JemmaJsBridge with a clean Kotlin API consumed by the L3 native ║
 * ║  UI (RadarFragment / SosBroadcastFragment / PatientDetailFragment║
 * ║  via the SOS/Rescue mode bottom-sheet).                          ║
 * ║                                                                  ║
 * ║  Wiring contract :                                               ║
 * ║    • Forced-init via JemmaApplication.onCreate() through the     ║
 * ║      JemmaAppEntryPoint, so the `init { … }` block logs runtime  ║
 * ║      proof that DI is wired correctly. Look for                  ║
 * ║      `JEMMA-RADAR · 🆕 RadarController wired (v2.5.1)` at boot.  ║
 * ║    • Field-level @Inject from any AndroidEntryPoint Fragment or  ║
 * ║      ViewModel (single-source-of-truth for the SOS/Rescue tab).  ║
 * ║                                                                  ║
 * ║  Method contract (mirrors Plan A's JemmaJsBridge — 21 methods):  ║
 * ║                                                                  ║
 * ║    SOS broadcast lifecycle (victim mode)                         ║
 * ║      sosStart(), sosStop(), sosToggleBroadcast(),                ║
 * ║      sosIsActive(), sosIsBroadcasting(),                         ║
 * ║      sosConfigureBeacon(profileJson, gpsLat, gpsLon, …)          ║
 * ║                                                                  ║
 * ║    Rescuer radar lifecycle                                       ║
 * ║      radarStart(), radarStop(), radarSetMode(RESCUER|SOS),       ║
 * ║      radarIsActive()                                             ║
 * ║                                                                  ║
 * ║    Snapshot queries                                              ║
 * ║      radarGetPeersJson() — list of detected victims              ║
 * ║      sosListRescuersJson() — list of detected rescuers           ║
 * ║      sosFreshLanguagesCsv() — distinct langs of fresh rescuers   ║
 * ║      sosGetCurrentLocationJson() — best-effort GPS fix           ║
 * ║                                                                  ║
 * ║    Triage events (L4+ — already wired through to                 ║
 * ║                   JemmaNearbySosService API)                     ║
 * ║      triagePublishEvent(victim, status, rescuer, override)       ║
 * ║      triageGetAllEventsJson()                                    ║
 * ║      triageGetEventForJson(victimSid)                            ║
 * ║                                                                  ║
 * ║    Cross-relay (L4+)                                             ║
 * ║      nearbyStartHaruRelay(jJsonString) — paper QR scan re-broad  ║
 * ║                                                                  ║
 * ║  All methods return either an explicit Result or a JSON string.  ║
 * ║  Failures are caught and logged with the JEMMA-RADAR tag (which  ║
 * ║  is on the capture_jemma.sh whitelist) and never thrown to the   ║
 * ║  UI layer — this is by design : the UI always gets a stable      ║
 * ║  reply and consults the log for diagnostics.                     ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
package be.heyman.android.jemmapassdemo.radar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import be.heyman.android.jemmapassdemo.sos.JemmaDeviceId
import be.heyman.android.jemmapassdemo.sos.JemmaRadarOverlayState
import be.heyman.android.jemmapassdemo.sos.JemmaSosRescuerRegistry
import be.heyman.android.jemmapassdemo.sos.JemmaSosService
import be.heyman.android.jemmapassdemo.triage.StatusEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadarController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "JEMMA-RADAR"
        const val VERSION = "2.5.1"
    }

    /**
     * Result type for action methods (start/stop/toggle/configure).
     * Wraps SecurityException (typical for foreground-service permission
     * issues on Android 14+) and generic exceptions distinctly so the UI
     * can route to the permission re-prompt screen vs a generic error.
     */
    sealed class Result {
        object Ok : Result()
        object PermissionDenied : Result()
        data class Error(val message: String) : Result()

        /** Wire-format string equivalent to Plan A's JsBridge return values. */
        fun asWireString(): String = when (this) {
            is Ok -> "ok"
            is PermissionDenied -> "permission-denied"
            is Error -> "err:$message"
        }
    }

    /**
     * Matches `JemmaRadarOverlayState.RadarMode` semantics. We expose this
     * as our own enum for the L3 native UI so the bottom-sheet picker
     * doesn't reach into the lower-level state object directly.
     */
    enum class RadarMode { RESCUER, SOS }

    init {
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🆕 RadarController wired (v$VERSION) — " +
                "facade over sos/ + mesh/ runtime",
        )
    }

    // ═════════════════════════════════════════════════════════════════
    //  STATE QUERIES
    // ═════════════════════════════════════════════════════════════════

    /** True if `JemmaSosService` is alive (lock-screen icon visible). */
    fun sosIsActive(): Boolean = JemmaSosService.isRunning

    /** True if the SOS service is in active broadcast mode (red icon). */
    fun sosIsBroadcasting(): Boolean = JemmaSosService.isBroadcasting

    /** True if a rescuer-mode discovery loop is running. */
    fun radarIsActive(): Boolean =
        JemmaRadarOverlayState.sharedNearbyService?.isDiscovering() == true

    /** Current radar mode as set by the bottom-sheet picker. */
    fun radarGetMode(): RadarMode = when (JemmaRadarOverlayState.radarMode) {
        JemmaRadarOverlayState.RadarMode.RESCUER -> RadarMode.RESCUER
        JemmaRadarOverlayState.RadarMode.SOS -> RadarMode.SOS
    }

    // ═════════════════════════════════════════════════════════════════
    //  SOS BROADCAST LIFECYCLE (victim mode)
    // ═════════════════════════════════════════════════════════════════

    /**
     * Boot the foreground SOS service. Equivalent to Plan A's
     * `JemmaJsBridge.sosStart()`. Idempotent — if already running, the
     * service handles the duplicate-START intent internally.
     */
    fun sosStart(): Result {
        val tNow = System.currentTimeMillis()
        Log.i(TAG, "[t=$tNow] 🆘 sosStart")
        return try {
            val intent = Intent(context, JemmaSosService::class.java).apply {
                action = JemmaSosService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "[t=$tNow]   ✓ ACTION_START dispatched")
            Result.Ok
        } catch (e: SecurityException) {
            Log.e(TAG, "[t=$tNow] ❌ sosStart denied (FGS perm)", e)
            Result.PermissionDenied
        } catch (e: Exception) {
            Log.e(TAG, "[t=$tNow] ❌ sosStart failed", e)
            Result.Error(e.message ?: "unknown")
        }
    }

    /** Stop the SOS service entirely. Lock-screen icon disappears. */
    fun sosStop(): Result {
        val tNow = System.currentTimeMillis()
        Log.i(TAG, "[t=$tNow] 🛑 sosStop")
        return try {
            val intent = Intent(context, JemmaSosService::class.java).apply {
                action = JemmaSosService.ACTION_STOP
            }
            context.startService(intent)
            Result.Ok
        } catch (e: Exception) {
            Log.e(TAG, "[t=$tNow] ❌ sosStop failed", e)
            Result.Error(e.message ?: "unknown")
        }
    }

    /**
     * Toggle the broadcast state (idle ⇄ active). The lock-screen
     * notification flips teal ⇄ red. The BLE chunk rotation starts /
     * stops accordingly.
     */
    fun sosToggleBroadcast(): Result {
        val tNow = System.currentTimeMillis()
        Log.i(TAG, "[t=$tNow] 🔄 sosToggleBroadcast")
        return try {
            val intent = Intent(context, JemmaSosService::class.java).apply {
                action = JemmaSosService.ACTION_TOGGLE_BROADCAST
            }
            context.startService(intent)
            Result.Ok
        } catch (e: Exception) {
            Log.e(TAG, "[t=$tNow] ❌ sosToggleBroadcast failed", e)
            Result.Error(e.message ?: "unknown")
        }
    }

    /**
     * Configure which `_j 1.2` profile and which session-wide metadata
     * the foreground service should broadcast when the user toggles
     * broadcast ON. Mirrors `JemmaSosService.configureBroadcast(…)`
     * from Plan A's V2 path : an in-memory call (no Intent) plus
     * `persistConfig` to survive process death (lock-screen widget).
     *
     * @param profileJson `_j 1.2` short-profile JSON as a string
     * @param latE6  victim latitude × 1e6 (e.g. Brussels = 50846900)
     * @param lonE6  victim longitude × 1e6
     * @param broadcasterName  display name embedded in the chunk header (≤ 16 UTF-8 bytes)
     * @param criticality  0..255 — see JemmaSosChunkCodec.CRIT_*
     * @param flags  0..255 — see CapFlags
     * @param langCode  2-char ISO ("fr","ja","en"), empty falls back to system locale
     */
    fun sosConfigureBeacon(
        profileJson: String,
        latE6: Int,
        lonE6: Int,
        broadcasterName: String,
        criticality: Int,
        flags: Int,
        langCode: String,
    ): Result {
        val tNow = System.currentTimeMillis()
        Log.i(
            TAG,
            "[t=$tNow] ⚙ sosConfigureBeacon · name='$broadcasterName' " +
                "lat=${latE6 / 1e6} lon=${lonE6 / 1e6} crit=$criticality flags=$flags " +
                "lang='$langCode' profile=${profileJson.length} bytes",
        )
        return try {
            JemmaSosService.configureBroadcast(
                profileJson = profileJson,
                latE6 = latE6,
                lonE6 = lonE6,
                name = broadcasterName,
                criticality = criticality.coerceIn(0, 255).toByte(),
                flags = flags.coerceIn(0, 255).toByte(),
                langCode = langCode.take(2),
            )
            JemmaSosService.persistConfig(context)
            Log.i(TAG, "[t=$tNow]   ✓ in-memory + SharedPrefs persist OK")
            Result.Ok
        } catch (e: Exception) {
            Log.e(TAG, "[t=$tNow] ❌ sosConfigureBeacon failed", e)
            Result.Error(e.message ?: "unknown")
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  RADAR LIFECYCLE (rescuer mode)
    // ═════════════════════════════════════════════════════════════════

    /**
     * Start rescuer-mode discovery + advertising. Equivalent to Plan A's
     * `JemmaJsBridge.radarStart()`.
     *
     * 🆕 v2.5.6.2 — Previously this method only called `recreateFresh()`
     * and returned Ok, leaving the actual `startDiscovery` and
     * `startAdvertisingRescuer` wiring to be done "by the Fragment ViewModel".
     * In practice no fragment was doing those calls, so the rescuer Pixel
     * was in permanent sleep mode (no scan, no advertise) while the victim
     * Samsung was diffusing into the void.
     *
     * Now we actually call both:
     *   1. `service.startDiscovery()` → discover other devices via Nearby
     *      Connections (P2P_CLUSTER, SERVICE_ID `be.heyman...jemmapass.SOS`)
     *   2. `service.startAdvertisingRescuer(sid, name, langCode, lat, lon)` →
     *      announce ourselves as a rescuer (CapFlags |J=08) so victims see
     *      us on their own mini-radar
     *
     * The UI must hold ACCESS_FINE_LOCATION + BLUETOOTH_SCAN/ADVERTISE/
     * CONNECT before calling this — those are already prompted at boot
     * by `PermissionsFragment`.
     *
     * @param name      Display name for the rescuer beacon (e.g. profile.gn)
     * @param langCode  2-char ISO language code (e.g. "fr", "ja", "en")
     */
    fun radarStart(
        name: String = "JEMMA",
        langCode: String = "en",
    ): Result {
        val tNow = System.currentTimeMillis()
        Log.i(TAG, "[t=$tNow] 📡 radarStart · name='$name' lang='$langCode'")
        if (!hasFineLocation()) {
            Log.w(TAG, "[t=$tNow]   ⚠ ACCESS_FINE_LOCATION not granted")
            return Result.PermissionDenied
        }
        return try {
            // 🆕 v2.5.13 — Reuse the existing JemmaNearbySosService if any.
            // Pre-v2.5.13 we unconditionally called recreateFresh() here,
            // which drops the singleton and wipes peerSessions /
            // rescuerSessions / endpointToSid in the process. Combined
            // with radarStop's now-soft-stop behaviour, this lets the
            // user re-open the radar after STOP and immediately see the
            // peers they had discovered earlier.
            //
            // If no singleton exists (cold start, first time after app
            // launch) we still recreateFresh to get a clean GMS binding.
            val existing = JemmaRadarOverlayState.sharedNearbyService
            val service = if (existing != null) {
                Log.i(TAG, "[t=$tNow]   ♻ reusing existing JemmaNearbySosService (peers cache PRESERVED)")
                existing
            } else {
                // 🆕 L1 v2.5.0 — recreateFresh kills any zombie GMS sessions
                // before standing up a clean Nearby client.
                Log.i(TAG, "[t=$tNow]   🆕 no singleton yet · calling recreateFresh()")
                JemmaRadarOverlayState.recreateFresh(context)
            }
            JemmaRadarOverlayState.radarMode = JemmaRadarOverlayState.RadarMode.RESCUER
            Log.i(TAG, "[t=$tNow]   ✓ NearbySosService instance ready · mode=RESCUER")

            // 🆕 v2.5.6.2 — actually wire the discovery + advertise calls
            val mySid = JemmaDeviceId.get(context)
            val locJson = JSONObject(sosGetCurrentLocationJson())
            val lat = locJson.optDouble("lat").takeUnless { it.isNaN() } ?: 0.0
            val lon = locJson.optDouble("lon").takeUnless { it.isNaN() } ?: 0.0
            Log.i(TAG, "[t=$tNow]   · mySid=$mySid · GPS=($lat,$lon)")

            val discOk = service.startDiscovery()
            Log.i(TAG, "[t=$tNow]   ↳ startDiscovery → $discOk")

            val advOk = service.startAdvertisingRescuer(
                sid = mySid,
                name = name,
                langCode = langCode,
                lat = lat,
                lon = lon,
            )
            Log.i(TAG, "[t=$tNow]   ↳ startAdvertisingRescuer → $advOk")

            if (discOk && advOk) {
                Result.Ok
            } else {
                Log.w(TAG, "[t=$tNow]   ⚠ partial start (disc=$discOk adv=$advOk) — returning Ok anyway")
                Result.Ok
            }
        } catch (e: Exception) {
            Log.e(TAG, "[t=$tNow] ❌ radarStart failed", e)
            Result.Error(e.message ?: "unknown")
        }
    }

    /**
     * Stop rescuer-mode entirely. Calls `JemmaNearbySosService.fullStop()`
     * + resets the GPS cache + alert dedup via `resetOnStopRadar()`.
     */
    fun radarStop(): Result {
        val tNow = System.currentTimeMillis()
        Log.i(TAG, "[t=$tNow] 🛑 radarStop")
        return try {
            // 🆕 v2.5.13 — SOFT STOP : stops the active mesh I/O
            // (stopAdvertising + stopDiscovery) but does NOT call
            // fullStop() — that would also clear peerSessions /
            // rescuerSessions / endpointToSid, wiping the discovered
            // peers from the in-process cache.
            //
            // The user explicitly asked that re-opening the rescuer
            // radar after STOP show the previously-discovered peers
            // again (parity with the SOS broadcast screen which already
            // kept them across re-opens, because sosStop only stops
            // the foreground service without touching the singleton).
            //
            // The Nearby singleton stays alive in the process. At the
            // next radarStart() we just re-issue startAdvertising +
            // startDiscovery on the same instance — caches survive.
            try {
                JemmaRadarOverlayState.sharedNearbyService?.let { svc ->
                    svc.stopAdvertising(wipeRelayed = false)
                    Log.i(TAG, "[t=$tNow]   ✓ stopAdvertising (peers cache PRESERVED)")
                    svc.stopDiscovery()
                    Log.i(TAG, "[t=$tNow]   ✓ stopDiscovery")
                }
            } catch (e: Exception) {
                Log.w(TAG, "[t=$tNow]   ⚠ soft stop threw (continuing)", e)
            }
            // Clear the rescuer identity only (lastRescuerLat/Lon/Name +
            // alertedPeers) because the local user just declared they're
            // no longer rescuing.
            JemmaRadarOverlayState.resetOnStopRadar()
            Log.i(TAG, "[t=$tNow]   ✓ state reset (rescuer identity cleared)")
            Result.Ok
        } catch (e: Exception) {
            Log.e(TAG, "[t=$tNow] ❌ radarStop failed", e)
            Result.Error(e.message ?: "unknown")
        }
    }

    /**
     * Flip the radar mode. Called by the SOS/Rescue bottom-sheet picker
     * (L3 v2.5.2) before opening either the radar overlay (RESCUER mode)
     * or the SOS broadcast screen (SOS mode).
     */
    fun radarSetMode(mode: RadarMode): Result {
        val tNow = System.currentTimeMillis()
        Log.i(TAG, "[t=$tNow] 🔀 radarSetMode → $mode")
        JemmaRadarOverlayState.radarMode = when (mode) {
            RadarMode.RESCUER -> JemmaRadarOverlayState.RadarMode.RESCUER
            RadarMode.SOS -> JemmaRadarOverlayState.RadarMode.SOS
        }
        return Result.Ok
    }

    // ═════════════════════════════════════════════════════════════════
    //  SNAPSHOT QUERIES
    // ═════════════════════════════════════════════════════════════════

    /**
     * JSON array of all currently-discovered victims (rescuer view).
     * Each entry includes sessionId, name, lat/lon, sex, age, blood type,
     * criticality, flags, rssi, complete flag, and code arrays for
     * allergies / medications / conditions / immunizations.
     *
     * Returns "[]" if no service active (radar not started yet) or no
     * peers seen. Never throws.
     */
    fun radarGetPeersJson(): String {
        val service = JemmaRadarOverlayState.sharedNearbyService
        if (service == null) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] radarGetPeers · no active service → []")
            return "[]"
        }
        val peers = service.snapshotPeers()
        val arr = JSONArray()
        for (peer in peers) {
            try {
                arr.put(peer.toJson())
            } catch (e: Exception) {
                Log.w(TAG, "  ⚠ peer.toJson() failed for sid=${peer.sessionIdHex}: ${e.message}")
            }
        }
        return arr.toString()
    }

    /**
     * JSON array of currently-fresh rescuers (SOS / victim view).
     * Reads from the process-singleton `JemmaSosRescuerRegistry`.
     */
    fun sosListRescuersJson(): String {
        val json = JemmaSosRescuerRegistry.toJsonString()
        Log.d(
            TAG,
            "[t=${System.currentTimeMillis()}] sosListRescuers → ${JemmaSosRescuerRegistry.size()} rescuers",
        )
        return json
    }

    /**
     * CSV of distinct languages of fresh rescuers (e.g. "fr,ja"). Used
     * by the SOS-mode TTS heartbeat to cycle through "help me at X
     * meters" announcements in the right language. Empty string if
     * no fresh rescuers.
     */
    fun sosFreshLanguagesCsv(): String {
        val langs = JemmaSosRescuerRegistry.freshLanguages()
        val csv = langs.joinToString(",")
        Log.d(
            TAG,
            "[t=${System.currentTimeMillis()}] sosFreshLanguages → '$csv' (${langs.size} distinct)",
        )
        return csv
    }

    /**
     * Best-effort GPS fix using `LocationManager.getLastKnownLocation()`
     * across GPS / Network / Passive providers. Returns
     * `{"lat": …, "lon": …, "acc": …, "provider": …, "ageMs": …}` or
     * `{}` if no provider returned a fix.
     *
     * Native side has ACCESS_FINE_LOCATION granted at the permission
     * screen. The L3 native UI prefers this over the legacy WebView
     * `navigator.geolocation` path (which had cross-context perm bugs).
     */
    fun sosGetCurrentLocationJson(): String {
        val tNow = System.currentTimeMillis()
        return try {
            if (!hasFineLocation()) {
                Log.w(TAG, "[t=$tNow] sosGetCurrentLocation: FINE_LOCATION not granted")
                return "{}"
            }
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
            var best: Location? = null
            for (p in providers) {
                if (!lm.isProviderEnabled(p)) continue
                val loc = try {
                    lm.getLastKnownLocation(p)
                } catch (e: SecurityException) {
                    Log.w(TAG, "[t=$tNow] sosGetCurrentLocation: SecurityException for $p", e)
                    null
                }
                if (loc != null && (best == null || loc.time > best.time)) {
                    best = loc
                }
            }
            if (best == null) {
                Log.w(TAG, "[t=$tNow] sosGetCurrentLocation: no fix from any provider")
                return "{}"
            }
            val ageMs = tNow - best.time
            val o = JSONObject()
                .put("lat", best.latitude)
                .put("lon", best.longitude)
                .put("acc", best.accuracy.toDouble())
                .put("provider", best.provider ?: "unknown")
                .put("ageMs", ageMs)
            Log.i(
                TAG,
                "[t=$tNow] sosGetCurrentLocation → lat=${best.latitude} lon=${best.longitude} " +
                    "acc=${best.accuracy}m provider=${best.provider} ageMs=$ageMs",
            )
            o.toString()
        } catch (e: Exception) {
            Log.e(TAG, "[t=$tNow] ❌ sosGetCurrentLocation failed", e)
            "{}"
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  TRIAGE EVENTS (forwarded to JemmaNearbySosService)
    // ═════════════════════════════════════════════════════════════════

    /**
     * Publish a SALT triage event for a victim. Called from the radar
     * UI when the user taps a SALT button (WAIT, EVAL, STAB, HELP, EVAC,
     * DCD) on the bottom sheet.
     *
     * @return wire-format E| chunk that was published, or "err:..." on failure
     */
    fun triagePublishEvent(
        victimSid: String,
        statusCode: String,
        rescuerSid: String,
        isExplicitOverride: Boolean,
    ): String {
        val tNow = System.currentTimeMillis()
        Log.i(
            TAG,
            "[t=$tNow] 🩹 triagePublishEvent · victim=$victimSid status=$statusCode " +
                "rescuer=$rescuerSid override=$isExplicitOverride",
        )
        val service = JemmaRadarOverlayState.sharedNearbyService
        if (service == null) {
            Log.w(TAG, "[t=$tNow]   ⚠ no active NearbySosService — radar not started?")
            return "err:no-service"
        }
        return try {
            service.publishTriageEvent(
                victimSid = victimSid,
                statusCode = statusCode,
                rescuerSid = rescuerSid,
                isExplicitOverride = isExplicitOverride,
            ) ?: "err:unknown-salt-code"
        } catch (e: Exception) {
            Log.e(TAG, "[t=$tNow] ❌ triagePublishEvent failed", e)
            "err:${e.message ?: "unknown"}"
        }
    }

    /** JSON snapshot of all current triage events (one per victim). */
    fun triageGetAllEventsJson(): String {
        val service = JemmaRadarOverlayState.sharedNearbyService ?: return "{}"
        val events: Map<String, StatusEvent> = service.getAllTriageEvents()
        val o = JSONObject()
        for ((victim, event) in events) {
            try {
                o.put(victim, JSONObject(event.toMap()))
            } catch (e: Exception) {
                Log.w(TAG, "  ⚠ event.toMap() failed for victim=$victim: ${e.message}")
            }
        }
        return o.toString()
    }

    /** JSON of the single most recent triage event for a victim, or "{}" if none. */
    fun triageGetEventForJson(victimSid: String): String {
        val service = JemmaRadarOverlayState.sharedNearbyService ?: return "{}"
        val event = service.getTriageEventFor(victimSid) ?: return "{}"
        return JSONObject(event.toMap()).toString()
    }

    // ═════════════════════════════════════════════════════════════════
    //  🆕 v2.5.10 — Cross-relay : inject an externally-scanned victim
    //  into the local mesh broadcast (port of Plan A HTML
    //  JemmaJsBridge.nearbyStartHaruRelay, commits L44.16.47 / .48 / .49)
    //
    //  Use case : a rescuer (Kamekichi) scans an unconscious henro
    //  pilgrim's paper QR badge (Haru, _j 1.2). The rescuer's phone
    //  then re-broadcasts Haru's pruned IPS via the mesh, exactly as
    //  if Haru were transmitting it herself. All other rescuers in
    //  the mesh see Haru appear in their victim list with the full
    //  allergies / medications / conditions data needed for triage.
    //
    //  The broadcast uses MIXED rotation (rescuer beacon + victim
    //  chunks) when the rescuer's identity is cached, so Kamekichi
    //  doesn't vanish from the mesh when he scans. Falls back to
    //  solo victim advertise if no rescuer identity is cached yet.
    // ═════════════════════════════════════════════════════════════════

    /**
     * Inject an externally-scanned victim into the local mesh broadcast.
     *
     * @param jJsonString  the decoded `_j 1.2` JSON of the scanned profile
     *                     (typically from JemmaPayloadCodec.decode().rawJson)
     * @return "ok" on success, "err:<reason>" on failure.
     */
    @Suppress("LongMethod")  // ported verbatim from HTML reference for traceability
    fun injectExternalVictim(jJsonString: String): String {
        val tNow = System.currentTimeMillis()
        Log.i(TAG, "[t=$tNow] 🪪 injectExternalVictim — parsing _j 1.2 from QR " +
            "(${jJsonString.length} chars)")
        return try {
            val j = JSONObject(jJsonString)
            val rawSid = j.optString("sid", "")
            val pBlock = j.optJSONObject("p") ?: run {
                Log.e(TAG, "[t=$tNow] 🪪 _j has no `p` block — aborting")
                return "err:no_pblock"
            }
            val victimSid = when {
                rawSid.length == 4 -> rawSid
                rawSid.isNotEmpty() -> {
                    // Stable alphanumeric hash of the longer session ID to keep it 4 chars
                    val hash = Math.abs(rawSid.hashCode())
                    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                    var temp = hash
                    val sb = java.lang.StringBuilder()
                    repeat(4) {
                        sb.append(alphabet[temp % alphabet.length])
                        temp /= alphabet.length
                    }
                    val hashedSid = sb.toString()
                    Log.i(TAG, "[t=$tNow] 🪪 profile sid '$rawSid' is not 4 chars. Generated stable 4-char mesh sid: '$hashedSid'")
                    hashedSid
                }
                else -> {
                    // No sid at all — generate a stable 4-char ID from demographic keys
                    val seed = (pBlock.optString("gn", "") + pBlock.optString("fn", "") + pBlock.optString("bd", ""))
                    val finalSeed = seed.ifBlank { System.currentTimeMillis().toString() }
                    val hash = Math.abs(finalSeed.hashCode())
                    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                    var temp = hash
                    val sb = java.lang.StringBuilder()
                    repeat(4) {
                        sb.append(alphabet[temp % alphabet.length])
                        temp /= alphabet.length
                    }
                    val hashedSid = sb.toString()
                    Log.i(TAG, "[t=$tNow] 🪪 profile has no sid. Generated demographic stable 4-char mesh sid: '$hashedSid'")
                    hashedSid
                }
            }
            val name = pBlock.optString("gn", "Patient").take(64)
            val sex = pBlock.optString("gs", "U").firstOrNull()?.uppercaseChar() ?: 'U'
            val bd = pBlock.optString("bd", "")
            val bt = pBlock.optString("bt", "")
            // Plan A L44.16.48 — pull spoken language so it propagates downstream.
            val victimLang = pBlock.optString("lng", "").lowercase().take(2)
            val age = if (bd.length >= 4) {
                try {
                    val birth = java.time.LocalDate.parse(bd)
                    val now = java.time.LocalDate.now()
                    java.time.Period.between(birth, now).years.coerceAtLeast(0)
                } catch (e: Exception) {
                    Log.w(TAG, "🪪 failed to parse bd='$bd', using age=0", e)
                    0
                }
            } else 0

            // Plan A : criticality=1 (urgent) for any scanned victim — the
            // rescuer just decided to triage them, that's a signal in itself.
            val criticality = 1

            // Extract codes from _j (al/md/cn/im arrays).
            val allergyCodes = extractCodes(j.optJSONArray("al"))
            val medCodes = extractCodes(j.optJSONArray("md"))
            val condCodes = extractCodes(j.optJSONArray("cn"))
            val immunCodes = extractCodes(j.optJSONArray("im"))

            // Use rescuer's CURRENT GPS — that's where the scanned victim is.
            val rescuerLoc = readCurrentGpsFix()
            val (lat, lon) = if (rescuerLoc != null) {
                Pair(rescuerLoc.first, rescuerLoc.second)
            } else {
                Log.w(TAG, "[t=$tNow] 🪪 no GPS fix available — broadcasting with lat=0,lon=0")
                Pair(0.0, 0.0)
            }

            Log.i(TAG, "[t=$tNow] 🪪 starting external relay — sid='$victimSid' name='$name' " +
                "sex=$sex age=$age bt='$bt' crit=$criticality " +
                "(${allergyCodes.size}A ${medCodes.size}M ${condCodes.size}C ${immunCodes.size}I) " +
                "@$lat,$lon")

            val nearbyService = JemmaRadarOverlayState.sharedNearbyService ?: run {
                Log.w(TAG, "[t=$tNow] 🪪 no shared Nearby service — needs the radar to be open first")
                return "err:no_nearby_service"
            }

            // Plan A L44.16.47 — MIXED rotation (rescuer beacon + victim chunks)
            // when rescuer identity is cached. Otherwise solo victim advertise
            // (legacy fallback ; other rescuers lose sight of us until reopen).
            val rescuerName = JemmaRadarOverlayState.lastRescuerName
            val rescuerLang = JemmaRadarOverlayState.lastRescuerLangCode ?: "en"
            val rescuerSid = JemmaRadarOverlayState.lastRescuerSid
            val canMix = !rescuerName.isNullOrBlank() && !rescuerSid.isNullOrBlank()

            val ok = if (canMix) {
                Log.i(TAG, "[t=$tNow] 🪪 🔀 MIXED advertise — rescuer='$rescuerName' " +
                    "(sid=$rescuerSid, lang=$rescuerLang) + victim='$name' " +
                    "(sid=$victimSid, lang='$victimLang')")
                nearbyService.startAdvertisingMixed(
                    rescuerSid = rescuerSid!!,
                    rescuerName = rescuerName!!,
                    rescuerLangCode = rescuerLang,
                    rescuerLat = lat,
                    rescuerLon = lon,
                    victimSid = victimSid,
                    victimName = name,
                    victimSex = sex,
                    victimAge = age,
                    victimBloodType = bt,
                    victimCriticality = criticality,
                    victimLat = lat,
                    victimLon = lon,
                    allergyCodes = allergyCodes,
                    medCodes = medCodes,
                    conditionCodes = condCodes,
                    immunCodes = immunCodes,
                    victimLang = victimLang,
                )
            } else {
                Log.w(TAG, "[t=$tNow] 🪪 ⚠ no rescuer identity cached " +
                    "(name='$rescuerName', sid='$rescuerSid') — falling back to solo " +
                    "victim advertise (other rescuers will lose sight of us until reopen)")
                nearbyService.startAdvertisingVictim(
                    sid = victimSid,
                    name = name,
                    sex = sex,
                    age = age,
                    bloodType = bt,
                    criticality = criticality,
                    lat = lat,
                    lon = lon,
                    allergyCodes = allergyCodes,
                    medCodes = medCodes,
                    conditionCodes = condCodes,
                    immunCodes = immunCodes,
                    victimLang = victimLang,
                )
            }

            if (ok) {
                Log.i(TAG, "[t=$tNow] 🪪 ✓ external relay STARTED for sid='$victimSid' " +
                    "(mode=${if (canMix) "mixed" else "solo"})")

                // 🆕 v2.5.11 — Also surface the scanned victim LOCALLY so the
                // rescuer who just scanned sees them appear in his own list
                // immediately, without waiting for a mesh self-loop (which
                // Nearby never does — own advertisements aren't re-delivered).
                // Without this, the rescuer scans Haru and… nothing happens
                // on his screen for several seconds until another rescuer
                // mesh-relays Haru back. We synthesize the PeerInfo directly
                // from the parsed _j so the local UI dispatcher picks it up
                // on the next 1 Hz tick.
                val synthetic = be.heyman.android.jemmapassdemo.sos.PeerInfo(
                    sessionIdHex      = victimSid,
                    broadcasterName   = name,
                    latitude          = lat,
                    longitude         = lon,
                    sex               = sex,
                    ageDecade         = (age / 10).coerceIn(0, 9),
                    bloodType         = bt,
                    criticality       = criticality,
                    flags             = 0,
                    rssi              = 0,
                    chunksReceived    = 5,
                    chunksTotal       = 5,
                    complete          = true,
                    firstSeenMs       = tNow,
                    lastSeenMs        = tNow,
                    allergyCodes      = allergyCodes.map {
                        be.heyman.android.jemmapassdemo.sos.JemmaSosChunkCodec.CodeEntry(
                            code = it,
                            systemByte = be.heyman.android.jemmapassdemo.sos.JemmaSosChunkCodec.SYS_UNKNOWN,
                        )
                    },
                    medCodes          = medCodes.map {
                        be.heyman.android.jemmapassdemo.sos.JemmaSosChunkCodec.CodeEntry(
                            code = it,
                            systemByte = be.heyman.android.jemmapassdemo.sos.JemmaSosChunkCodec.SYS_UNKNOWN,
                        )
                    },
                    conditionCodes    = condCodes.map {
                        be.heyman.android.jemmapassdemo.sos.JemmaSosChunkCodec.CodeEntry(
                            code = it,
                            systemByte = be.heyman.android.jemmapassdemo.sos.JemmaSosChunkCodec.SYS_UNKNOWN,
                        )
                    },
                    immunCodes        = immunCodes.map {
                        be.heyman.android.jemmapassdemo.sos.JemmaSosChunkCodec.CodeEntry(
                            code = it,
                            systemByte = be.heyman.android.jemmapassdemo.sos.JemmaSosChunkCodec.SYS_UNKNOWN,
                        )
                    },
                    freeTextNote      = "",
                    ageExact          = age,
                    isStale           = false,
                    langCode          = victimLang,
                )
                nearbyService.injectSyntheticPeer(synthetic)
                Log.i(TAG, "[t=$tNow] 🪪 ✓ injected synthetic peer locally so the " +
                    "scanner sees the victim immediately")

                "ok"
            } else {
                Log.e(TAG, "[t=$tNow] 🪪 ✗ startAdvertising returned false")
                "err:advertise_failed"
            }
        } catch (e: Exception) {
            Log.e(TAG, "🪪 injectExternalVictim threw", e)
            "err:${e.message}"
        }
    }

    /** Extract non-blank "c" code strings from an array of `{c: ...}` objects. */
    private fun extractCodes(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.optString("c", "")
                ?.takeIf { it.isNotBlank() }
                ?.let { out.add(it) }
        }
        return out
    }

    /**
     * Best-effort synchronous GPS fix for the QR-relay use case. Tries GPS
     * provider first, falls back to network. Returns null if no fix yet.
     */
    private fun readCurrentGpsFix(): Pair<Double, Double>? {
        if (!hasFineLocation()) return null
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null
            val gpsFix = try {
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } catch (_: SecurityException) { null }
            val fix = gpsFix ?: try {
                lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (_: SecurityException) { null }
            fix?.let { Pair(it.latitude, it.longitude) }
        } catch (e: Exception) {
            Log.w(TAG, "readCurrentGpsFix failed", e)
            null
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  INTERNAL HELPERS
    // ═════════════════════════════════════════════════════════════════

    private fun hasFineLocation(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
}
