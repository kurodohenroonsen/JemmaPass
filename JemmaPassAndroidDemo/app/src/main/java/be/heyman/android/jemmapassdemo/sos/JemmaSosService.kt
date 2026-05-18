package be.heyman.android.jemmapassdemo.sos

import be.heyman.android.jemmapassdemo.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 🆕 L44.15 — JEMMA SOS Foundation Service.
 *
 * Foreground service that does TWO things in this initial delivery:
 *
 *   A. Display the JEMMA icon on the lock screen via a non-dismissable
 *      foreground notification. This proves to the user (and the jury)
 *      that the SOS infrastructure is active and visible at all times.
 *
 *   B. Hold an active MediaSession that, in subsequent deliveries
 *      (L44.15.1 morse decoder, L44.16 BLE broadcaster), will be used to
 *      intercept volume button presses even when the screen is OFF and
 *      LOCKED. The MediaSession is wired but the morse decoder is
 *      intentionally not active in this milestone — we want to confirm
 *      the lock-screen icon works first.
 *
 * Lifecycle:
 *   - JS calls JemmaJsBridge.sosStart() → this service starts via
 *     ContextCompat.startForegroundService().
 *   - Service shows the lock-screen-visible notification.
 *   - Service stays alive until the user taps "Désactiver" in the
 *     notification, OR JS calls sosStop(), OR the system kills the
 *     process under extreme memory pressure.
 *
 * Notification anatomy:
 *   - Small icon: jemma_sos_icon (white-on-transparent, fits status bar)
 *   - Color: teal (#34d399) when idle, red (#dc2626) when broadcasting
 *   - colorized=true so the entire notification body picks up our color
 *     (only allowed for foreground services, hence why this looks loud)
 *   - Action: "🛑 Désactiver le mode SOS" — clicking it stops the service
 *
 * Battery / restriction notes:
 *   - Samsung One UI 7+ may auto-restrict background services. The user
 *     needs to mark JemmaPass as "Unrestricted" in Battery Settings.
 *     We surface this prompt from JS the first time the service starts.
 *   - The service uses serviceType=health so the OS knows it's a
 *     life-safety task and applies the lightest possible throttling.
 */
class JemmaSosService : Service() {

    companion object {
        private const val TAG = "JEMMA-SOS"

        private const val CHANNEL_ID = "jemma_sos_channel"
        // 🆕 L1 v2.5.0 — CHANNEL_NAME removed (was hardcoded "JEMMA SOS Beacon");
        // now read from R.string.jemma_sos_notif_channel_name with full
        // i18n support (EN/FR/JA).
        private const val NOTIFICATION_ID = 4111

        private const val MEDIA_SESSION_TAG = "JemmaSosMediaSession"

        // Intent actions for service control
        const val ACTION_START = "be.heyman.jemma.sos.START"
        const val ACTION_STOP = "be.heyman.jemma.sos.STOP"
        const val ACTION_TOGGLE_BROADCAST = "be.heyman.jemma.sos.TOGGLE"

        // 🆕 L44.16.12 — payload extras for the broadcast
        const val EXTRA_PROFILE_JSON = "be.heyman.jemma.sos.PROFILE_JSON"
        const val EXTRA_LAT_E6 = "be.heyman.jemma.sos.LAT_E6"
        const val EXTRA_LON_E6 = "be.heyman.jemma.sos.LON_E6"
        const val EXTRA_BROADCASTER_NAME = "be.heyman.jemma.sos.NAME"
        const val EXTRA_CRITICALITY = "be.heyman.jemma.sos.CRITICALITY"
        const val EXTRA_FLAGS = "be.heyman.jemma.sos.FLAGS"

        // Track running state for sosIsActive() queries
        @Volatile var isRunning: Boolean = false
            private set

        @Volatile var isBroadcasting: Boolean = false
            private set

        // 🆕 L44.16.12 — last-configured broadcast payload, set by JS
        // before toggling broadcast on. The service holds this so the
        // widget can also trigger broadcast without re-configuring.
        @Volatile var configuredProfileJson: String? = null
            private set
        @Volatile var configuredLatE6: Int = 0
        @Volatile var configuredLonE6: Int = 0
        @Volatile var configuredName: String = ""
        @Volatile var configuredCriticality: Byte = 0
        @Volatile var configuredFlags: Byte = 0
        // 🆕 L44.16.48 — victim's spoken language broadcast in the
        // chunk header so rescuers see the lang flag on their radar
        // BEFORE attempting first contact.
        @Volatile var configuredLangCode: String = ""

        /**
         * 🆕 L44.16.12 — JS sets the broadcast payload via this static
         * before calling sosToggleBroadcast(). We don't go through the
         * Intent extras for repeat calls because parsing a 2KB JSON
         * blob from extras every toggle is wasteful.
         */
        fun configureBroadcast(
            profileJson: String,
            latE6: Int,
            lonE6: Int,
            name: String,
            criticality: Byte,
            flags: Byte,
            langCode: String = ""        // 🆕 L44.16.48
        ) {
            configuredProfileJson = profileJson
            configuredLatE6 = latE6
            configuredLonE6 = lonE6
            configuredName = name
            configuredCriticality = criticality
            configuredFlags = flags
            configuredLangCode = langCode
        }

        // ═══════════════════════════════════════════════════════════
        //  🆕 L44.16.15 — Persistence for one-tap lock-screen widget.
        // ═══════════════════════════════════════════════════════════
        // The widget on the lock screen needs to be able to start a
        // broadcast EVEN AFTER the app process was killed (e.g. user
        // last opened JEMMA two days ago). The in-memory companion
        // fields are reset to defaults at process start.
        // Solution: persist the configured payload to SharedPreferences
        // every time JS pushes a new config. At service startup, we
        // attempt to restore from disk so a cold widget tap can fire
        // a broadcast immediately without going through JS.

        private const val PREFS_NAME = "jemma_sos_beacon_config"
        private const val KEY_PROFILE_JSON = "profile_json"
        private const val KEY_LAT_E6 = "lat_e6"
        private const val KEY_LON_E6 = "lon_e6"
        private const val KEY_NAME = "name"
        private const val KEY_CRITICALITY = "criticality"
        private const val KEY_FLAGS = "flags"
        // 🆕 L44.16.48 — victim's spoken language persisted with config
        private const val KEY_LANG_CODE = "lang_code"
        private const val KEY_LAST_CONFIGURED_AT = "last_configured_at"

        /**
         * 🆕 L44.16.15 — Persist the broadcast config to disk so a
         * cold-start widget tap (after process kill) can still fire
         * a broadcast without needing the WebView to be loaded.
         *
         * Called by JemmaJsBridge.sosConfigureBeacon() AFTER the
         * in-memory configureBroadcast() so disk write doesn't block
         * the JS bridge call.
         */
        fun persistConfig(context: Context) {
            try {
                // 🆕 L44.16.15-hotfix1 — sanity check before writing.
                // If the payload looks corrupt (too small, not JSON,
                // missing _j), refuse to persist — better to keep the
                // existing valid config than overwrite with garbage.
                val json = configuredProfileJson
                if (json.isNullOrBlank() || json.length < 30) {
                    Log.w(TAG, "persistConfig: payload too small (${json?.length ?: 0} bytes) — refusing to persist")
                    return
                }
                try {
                    val parsed = org.json.JSONObject(json)
                    if (!parsed.has("_j")) {
                        Log.w(TAG, "persistConfig: payload missing _j field — refusing to persist")
                        return
                    }
                } catch (e: org.json.JSONException) {
                    Log.w(TAG, "persistConfig: payload not valid JSON — refusing to persist", e)
                    return
                }
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_PROFILE_JSON, configuredProfileJson)
                    .putInt(KEY_LAT_E6, configuredLatE6)
                    .putInt(KEY_LON_E6, configuredLonE6)
                    .putString(KEY_NAME, configuredName)
                    .putInt(KEY_CRITICALITY, configuredCriticality.toInt())
                    .putInt(KEY_FLAGS, configuredFlags.toInt())
                    .putString(KEY_LANG_CODE, configuredLangCode)    // 🆕 L44.16.48
                    .putLong(KEY_LAST_CONFIGURED_AT, System.currentTimeMillis())
                    .apply()
                Log.i(TAG, "✓ persisted broadcast config (${configuredProfileJson?.length ?: 0} bytes, lang='${configuredLangCode}')")
            } catch (e: Exception) {
                Log.e(TAG, "persistConfig failed", e)
            }
        }

        /**
         * 🆕 L44.16.15 — Load broadcast config from disk into the
         * in-memory companion fields. Called by the service on
         * startBleBroadcast() if configuredProfileJson is null
         * (typical scenario: cold start from widget tap).
         *
         * Returns true if a valid config was loaded, false otherwise.
         */
        fun loadConfigFromDisk(context: Context): Boolean {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val json = prefs.getString(KEY_PROFILE_JSON, null)
                if (json.isNullOrBlank()) {
                    Log.w(TAG, "loadConfigFromDisk: no persisted profile")
                    return false
                }
                // 🆕 L44.16.15-hotfix1 — validate the JSON before
                // committing it to in-memory state. A bug in earlier
                // versions persisted just the version string "1.2"
                // (5 bytes) which then crashed startBleBroadcast()
                // with JSONException. Defensive parse here:
                //   - must be a JSONObject (not a primitive)
                //   - must contain the _j version field
                //   - must be at least 30 bytes (sanity floor for any
                //     useful payload — even an empty patient is ~50)
                if (json.length < 30) {
                    Log.w(TAG, "loadConfigFromDisk: persisted payload too small (${json.length} bytes) — ignoring + clearing")
                    clearPersistedConfig(context)
                    return false
                }
                try {
                    val parsed = org.json.JSONObject(json)
                    if (!parsed.has("_j")) {
                        Log.w(TAG, "loadConfigFromDisk: payload missing _j field — ignoring + clearing")
                        clearPersistedConfig(context)
                        return false
                    }
                } catch (e: org.json.JSONException) {
                    Log.w(TAG, "loadConfigFromDisk: payload is not valid JSON — ignoring + clearing", e)
                    clearPersistedConfig(context)
                    return false
                }
                configuredProfileJson = json
                configuredLatE6 = prefs.getInt(KEY_LAT_E6, 0)
                configuredLonE6 = prefs.getInt(KEY_LON_E6, 0)
                configuredName = prefs.getString(KEY_NAME, "") ?: ""
                configuredCriticality = prefs.getInt(KEY_CRITICALITY, 0).toByte()
                configuredFlags = prefs.getInt(KEY_FLAGS, 0).toByte()
                configuredLangCode = prefs.getString(KEY_LANG_CODE, "") ?: ""    // 🆕 L44.16.48
                val ts = prefs.getLong(KEY_LAST_CONFIGURED_AT, 0)
                val ageMin = if (ts > 0) (System.currentTimeMillis() - ts) / 60_000 else -1
                Log.i(TAG, "✓ loaded broadcast config from disk " +
                    "(${json.length} bytes, name='$configuredName', lang='$configuredLangCode', age=${ageMin}min)")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "loadConfigFromDisk failed", e)
                return false
            }
        }

        /**
         * 🆕 L44.16.15-hotfix1 — Wipe the persisted SOS broadcast
         * config. Used to recover from a corrupted persisted payload
         * (e.g. the "1.2" string bug from L44.16.15).
         */
        fun clearPersistedConfig(context: Context) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                Log.i(TAG, "✓ cleared persisted broadcast config")
            } catch (e: Exception) {
                Log.e(TAG, "clearPersistedConfig failed", e)
            }
        }

        /** True if a persisted broadcast config exists on disk. */
        fun hasPersistedConfig(context: Context): Boolean {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                !prefs.getString(KEY_PROFILE_JSON, null).isNullOrBlank()
            } catch (_: Exception) {
                false
            }
        }
    }

    private var mediaSession: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        createNotificationChannel()
        // Register the MediaSession early so it's ready when the morse
        // decoder ships in L44.15.1. We don't activate it here — that
        // happens when sosStart() is called.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.i(TAG, "onStartCommand: action=$action")

        when (action) {
            ACTION_START -> {
                if (!isRunning) {
                    startForegroundWithNotification(broadcasting = false)
                    activateMediaSession()
                    isRunning = true
                    Log.i(TAG, "✓ SOS service ACTIVE (idle, not broadcasting yet)")
                }
                // 🆕 L44.15.2 — refresh any widget instances
                // 🚧 L1 v2.5.0 — JemmaSosAppWidget skipped in port (homescreen widget out of scope) — JemmaSosAppWidget.refreshAll(this)
            }
            ACTION_STOP -> {
                Log.i(TAG, "✓ SOS service STOPPING")
                deactivateMediaSession()
                isRunning = false
                isBroadcasting = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                // 🆕 L44.15.2 — refresh widget so it returns to idle state
                // 🚧 L1 v2.5.0 — JemmaSosAppWidget skipped in port (homescreen widget out of scope) — JemmaSosAppWidget.refreshAll(this)
            }
            ACTION_TOGGLE_BROADCAST -> {
                // 🆕 L44.15.2 — TOGGLE may arrive directly from the
                // widget tap on a locked screen. If service isn't yet
                // running we need to bootstrap it first.
                if (!isRunning) {
                    Log.i(TAG, "TOGGLE arrived but service idle — bootstrapping first")
                    startForegroundWithNotification(broadcasting = false)
                    activateMediaSession()
                    isRunning = true
                }
                isBroadcasting = !isBroadcasting
                Log.i(TAG, "Broadcast toggled → $isBroadcasting")
                // Update the notification UI to reflect the new state
                updateNotification(broadcasting = isBroadcasting)
                // 🆕 L44.15.2 — push state to all widget instances
                // 🚧 L1 v2.5.0 — JemmaSosAppWidget skipped in port (homescreen widget out of scope) — JemmaSosAppWidget.refreshAll(this)
                // 🆕 L44.16.12 — start/stop BLE advertise based on state
                // 🆕 L44.16.23 — ALSO start/stop Nearby Connections
                // broadcast in parallel. The radar (rescuer side) was
                // migrated to Nearby Connections because BLE direct
                // discovery proved unreliable across Pixel/Samsung.
                // We keep BLE alive too as a fallback for legacy peers
                // and a future-proof secondary channel.
                if (isBroadcasting) {
                    startBleBroadcast()
                    startNearbyVictimBroadcast()
                } else {
                    stopBleBroadcast()
                    stopNearbyVictimBroadcast()
                }
            }
        }
        // START_STICKY: if the OS kills the process, restart the service.
        // This is critical for SOS — we want it to come back even after
        // a crash. Note that on Android 12+ background restart limits
        // apply; the service may not auto-resurrect from cold death.
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        // 🆕 L44.16.25-hotfix1 — Stop Nearby broadcasts cleanly
        // before the service goes away. Without this, if the user
        // force-stops the app or Android kills the service for memory,
        // Google Play Services keeps our advertise alive in the
        // background → "zombie advertise" syndrome at next launch.
        try {
            Log.i(TAG, "🧹 [ZOMBIE-KILL] stopping Nearby broadcasts before service teardown")
            nearbyService.stopAdvertising()
            // Note: we don't fullStop() because the radar overlay might
            // still be open and rely on discovery. Just kill our own
            // advertise. If we own the discovery too (SOS-only mode,
            // no radar), the listener removal in stopNearbyVictim*
            // already happened.
        } catch (e: Exception) {
            Log.w(TAG, "🧹 onDestroy: stopAdvertising threw (ignoring)", e)
        }
        deactivateMediaSession()
        isRunning = false
        isBroadcasting = false
        super.onDestroy()
    }

    // ─── Notification ──────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            // Check if it already exists (idempotent)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.jemma_sos_notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = getString(R.string.jemma_sos_notif_channel_description)
                    enableLights(true)
                    lightColor = Color.parseColor("#34D399")  // teal JEMMA
                    enableVibration(false)  // we don't want it to buzz on every update
                    setShowBadge(true)
                    // CRITICAL: lockscreenVisibility PUBLIC = the
                    // notification shows fully on the lock screen.
                    // Without this our icon wouldn't appear when locked.
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    // Bypass DND because emergency
                    setBypassDnd(true)
                }
                nm.createNotificationChannel(channel)
                Log.i(TAG, "✓ Notification channel created: $CHANNEL_ID")
            }
        }
    }

    private fun startForegroundWithNotification(broadcasting: Boolean) {
        val notif = buildNotification(broadcasting)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34+): must specify foregroundServiceType
                // matching the AndroidManifest declaration (otherwise the
                // OS rejects startForeground() and ANRs at the 5s timeout
                // with ForegroundServiceDidNotStartInTimeException).
                //
                // 🆕 L4 v2.5.4 fix — Plan A used SPECIAL_USE because Plan A's
                //   manifest declared `foregroundServiceType="specialUse"`
                //   plus the PROPERTY_SPECIAL_USE_FGS_SUBTYPE property and
                //   the FOREGROUND_SERVICE_SPECIAL_USE permission. Plan B's
                //   L1 manifest deliberately switched to the more specific
                //   types CONNECTED_DEVICE (BLE advertise/scan) + LOCATION
                //   (GPS guard) which match the actual capabilities used.
                //   This Kotlin call MUST mirror the manifest, otherwise
                //   Android rejects startForeground() silently and ANRs.
                startForeground(
                    NOTIFICATION_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
            Log.i(TAG, "✓ Foreground notification posted (type=connectedDevice|location)")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Foreground service rejected by OS — likely missing permission", e)
            // Stop self gracefully instead of crashing the whole app.
            isRunning = false
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start foreground", e)
            isRunning = false
            stopSelf()
        }
    }

    private fun updateNotification(broadcasting: Boolean) {
        val notif = buildNotification(broadcasting)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun buildNotification(broadcasting: Boolean): Notification {
        // Tap on the notification → opens the main JEMMA activity
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent()
        val openAppPi = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // "🛑 Désactiver" action button → stops the service entirely
        val stopIntent = Intent(this, JemmaSosService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 🆕 L44.15.2 — toggle broadcast action button.
        // Lets the user broadcast/stop directly from the lock-screen
        // notification without opening the app.
        val toggleIntent = Intent(this, JemmaSosService::class.java).apply {
            action = ACTION_TOGGLE_BROADCAST
        }
        val togglePi = PendingIntent.getService(
            this, 2, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title: String
        val text: String
        val bigText: String
        val color: Int
        val toggleLabel: String

        // 🆕 L1 v2.5.0 — Strings externalised to R.string.* for full
        // i18n support (EN default + FR + JA shipped). The system locale
        // chooses which `values-XX/strings_jemma_sos.xml` is loaded.
        if (broadcasting) {
            title       = getString(R.string.jemma_sos_notif_title_broadcasting)
            text        = getString(R.string.jemma_sos_notif_text_broadcasting)
            bigText     = getString(R.string.jemma_sos_notif_bigtext_broadcasting)
            color       = Color.parseColor("#DC2626")  // red — broadcasting
            toggleLabel = getString(R.string.jemma_sos_notif_action_stop_broadcast)
        } else {
            title       = getString(R.string.jemma_sos_notif_title_idle)
            text        = getString(R.string.jemma_sos_notif_text_idle)
            bigText     = getString(R.string.jemma_sos_notif_bigtext_idle)
            color       = Color.parseColor("#34D399")  // teal — idle
            toggleLabel = getString(R.string.jemma_sos_notif_action_start_broadcast)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            // 🔑 The icon that appears on the lock screen.
            // Must be a white-on-transparent vector for status-bar use.
            .setSmallIcon(R.drawable.ic_jemma_sos_status)
            .setContentTitle(title)
            .setContentText(text)
            // 🆕 L44.15.2 — BigTextStyle ensures the notification is
            // rendered as a full card in the lock-screen and shade
            // (not collapsed to a status-bar pill icon only).
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setColor(color)
            // 🆕 L44.15.2 — REMOVED setColorized(true): on Pixel
            // Android 14+ this caused the notification to be hidden
            // from the shade on locked screens. Plain colored accent
            // bar via setColor() is more compatible.
            .setOngoing(true)    // non-dismissable by swipe
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // VISIBILITY_PUBLIC → show the full notification on the lock
            // screen (not just an "App is running" placeholder)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // High priority so it sits at the top of the lock screen
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(openAppPi)
            // 🆕 L44.15.2 — broadcast toggle action FIRST (most useful)
            .addAction(
                if (broadcasting) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_menu_send,
                toggleLabel,
                togglePi,
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.jemma_sos_notif_action_disable),
                stopPi,
            )
            .build()
    }

    // ─── MediaSession (dormant in L44.15, used by L44.15.1 morse) ───

    private fun activateMediaSession() {
        if (mediaSession != null) return
        mediaSession = MediaSession(this, MEDIA_SESSION_TAG).apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            // Set state to PLAYING so the system thinks we're an audio
            // app — that's the trick that lets us intercept volume
            // buttons even when the screen is locked.
            val state = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_STOP
                )
                .setState(PlaybackState.STATE_PLAYING, 0L, 1.0f, SystemClock.elapsedRealtime())
                .build()
            setPlaybackState(state)
            // The callback is empty in L44.15. L44.15.1 will plug the
            // morse decoder into onMediaButtonEvent.
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(intent: Intent): Boolean {
                    Log.d(TAG, "MediaButton: ${intent.action} — (decoder dormant in L44.15)")
                    // Return false to let the OS handle the volume change
                    // normally. L44.15.1 will return true and process the
                    // event as morse.
                    return false
                }
            })
            isActive = true
        }
        Log.i(TAG, "✓ MediaSession activated (dormant — morse decoder pending)")
    }

    private fun deactivateMediaSession() {
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
        Log.i(TAG, "✓ MediaSession released")
    }

    // ─── BLE Broadcast (L44.16.12) ──────────────────────────────────

    /**
     * 🆕 L44.16.12 — BLE advertiser instance. Lazily created the first
     * time we go into broadcasting state, then reused to avoid
     * re-allocating the Bluetooth adapter handle.
     */
    private var bleAdvertiser: JemmaSosBleAdvertiser? = null

    private fun startBleBroadcast() {
        var profileJson = configuredProfileJson
        // 🆕 L44.16.15 — if no in-memory config, try loading from disk.
        // This is the cold-start path: widget tap on a locked screen
        // after the app process was killed. We still want SOS to fire.
        if (profileJson.isNullOrBlank()) {
            Log.i(TAG, "startBleBroadcast — no in-memory config, trying disk fallback")
            if (loadConfigFromDisk(this)) {
                profileJson = configuredProfileJson
            }
        }
        if (profileJson.isNullOrBlank()) {
            Log.w(TAG, "startBleBroadcast — no configured profile (in-memory or disk), skipping. " +
                "Open the app at least once and configure SOS first.")
            return
        }
        try {
            val jProfile = org.json.JSONObject(profileJson)
            val sessionId = JemmaSosChunkCodec.newSessionId()
            val config = JemmaSosChunkCodec.BroadcastConfig(
                sessionId = sessionId,
                latE6 = configuredLatE6,
                lonE6 = configuredLonE6,
                criticality = configuredCriticality,
                flags = configuredFlags,
                tsOffsetSeconds = 0,
                broadcasterName = configuredName
            )
            val chunks = JemmaSosChunkCodec.encode(jProfile, config)
            if (chunks.isEmpty()) {
                Log.w(TAG, "no chunks produced, aborting broadcast")
                return
            }

            val adv = bleAdvertiser ?: JemmaSosBleAdvertiser(this).also { bleAdvertiser = it }
            if (!adv.isSupported()) {
                Log.w(TAG, "BLE advertise not supported on this device")
                return
            }
            adv.start(chunks)
            Log.i(TAG, "🆘 BLE broadcast started — sid=${JemmaSosChunkCodec.sessionIdToHex(sessionId)} " +
                "chunks=${chunks.size} ext=${adv.isExtendedAdvSupported()}")
        } catch (e: Exception) {
            Log.e(TAG, "startBleBroadcast failed", e)
        }
    }

    private fun stopBleBroadcast() {
        bleAdvertiser?.stop()
        Log.i(TAG, "⏹ BLE broadcast stopped")
    }

    // ═══════════════════════════════════════════════════════════════
    //  🆕 L44.16.23 — Nearby Connections broadcast (victim side)
    // ═══════════════════════════════════════════════════════════════

    /**
     * 🆕 L44.16.26 — Replaced `by lazy` with a method-level fetch.
     * The reason: `recreateFresh()` swaps the underlying instance
     * inside JemmaRadarOverlayState, but a `by lazy` field would
     * cache the original instance forever. Using a getter ensures
     * we always read the *current* live instance.
     */
    private val nearbyService: JemmaNearbySosService
        get() = JemmaRadarOverlayState.getOrCreateNearbyService(applicationContext)

    /** Stable session id reused across broadcast cycles. */
    @Volatile private var nearbyVictimSid: String? = null

    /**
     * 🆕 L44.16.24 — listener that captures rescuer events while the
     * SOS broadcast is active, even when the radar overlay is closed.
     *
     * Rescuers found are stored in JemmaSosRescuerRegistry (a process
     * singleton accessible from JS via JemmaJsBridge.sosListRescuers()).
     * The radar bridge already pushes these to JS when the radar is
     * open, but for the *SOS use case* (victim's screen locked, app
     * locked) we need to ALSO trigger TTS heartbeat without any UI.
     *
     * That's the next layer (jemma_sos_panel.js) — but it can't poll
     * if the data isn't recorded somewhere first. This listener is
     * that recording step.
     */
    private val sosRescuerListener = object : JemmaNearbySosService.Listener {
        override fun onRescuerFound(rescuer: RescuerInfo) {
            Log.i(TAG, "🆘[SOS-LISTEN] ⛑️ rescuer found while in SOS mode: " +
                "sid=${rescuer.sessionIdHex} name='${rescuer.name}' lang=${rescuer.langCode} " +
                "@${rescuer.latitude},${rescuer.longitude}")
            JemmaSosRescuerRegistry.upsert(rescuer)
        }
        override fun onRescuerUpdate(rescuer: RescuerInfo) {
            Log.d(TAG, "🆘[SOS-LISTEN] ⛑️ rescuer update: sid=${rescuer.sessionIdHex}")
            JemmaSosRescuerRegistry.upsert(rescuer)
        }
        override fun onRescuerStale(sessionIdHex: String) {
            Log.i(TAG, "🆘[SOS-LISTEN] ⛑️ rescuer stale: sid=$sessionIdHex")
            JemmaSosRescuerRegistry.markStale(sessionIdHex)
        }
        // Peer events are not relevant to the victim — they're other victims
        // in the wild. We ignore them on purpose.
    }
    @Volatile private var sosListenerRegistered: Boolean = false

    // ════════════════════════════════════════════════════════════════
    //  🆕 L44.16.36 — VICTIM GPS auto-refresh
    // ════════════════════════════════════════════════════════════════
    //
    // BUG observé en L44.16.35 : la victime broadcast une position GPS
    // gravée au démarrage du SOS et ne la rafraîchit JAMAIS.
    //
    //   Pixel logs L44.16.35 :
    //     948  sosGetCurrentLocation → lat=50.000266 ageMs=423300 (vieille de 7 min!)
    //     951  sosConfigureBeacon — name='Claude' lat=50.000266 lon=4.44388
    //     ... la victime est partie voir les poules ...
    //     (broadcast continue à dire qu'elle est restée à la maison)
    //
    // Conséquences :
    //   - Sauveteur voit la victime à une fausse position sur son radar
    //   - distanceTo() / bearingTo() calculés avec position périmée
    //   - "ma femme est rapprochée et la victime est réapparue" (60s STALE
    //     puis re-fresh) — symptôme du sauveteur qui sortait de portée WiFi
    //     plutôt que de la position GPS qui change
    //
    // Fix : symétrique au LocationListener du JemmaRadarOverlay côté
    // rescuer. Quand le SOS broadcast est actif, on écoute les GPS
    // updates et on re-encode les chunks avec la nouvelle position
    // si on a bougé >= 5m. Threshold identique au rescuer pour
    // éviter le BLE adapter spam.
    //
    // ⚠️ ON UTILISE LocationManager DIRECTEMENT (pas l'API JS) car le
    // service tourne en background même si la WebView est endormie.
    private var locationManager: LocationManager? = null
    private var lastBroadcastLocation: Location? = null
    // 🆕 L44.16.84 — Rate-limit guard for victim broadcast refresh.
    // Symmetric to JemmaRadarOverlay.lastBroadcastRefreshMs added in L44.82.
    // Empirical bug from dryrun 15:14 of 2026-05-08 on L44.83 :
    // Samsung had 17 FULLSTOP+restart of victim broadcast in 3min45 because
    // GPS network provider returned acc=100m fixes that the 5m threshold
    // couldn't filter. Each restart trashes the BLE adapter for ~500ms,
    // killing capture rate Samsung→Pixel from 65% (L44.82 rescuer-only)
    // down to 15.6% (L44.83 with victim-path unfiltered).
    private var lastVictimBroadcastRefreshMs: Long = 0L

    // 🆕 L44.16.88 — Anti-drift filter for STATIONARY indoor devices.
    //
    // Empirical bug from dryrun 21:18 of 2026-05-08 on L44.16.87b :
    // Samsung phone IMMOBILE on a desk had 4 FULLSTOP+restart cycles
    // because GPS provider drifts 5-10m natively even when the device
    // doesn't move. Restart #2 triggered with `moved 0m` after a clean
    // restart wiped lastBroadcastLocation. Restart #3-4 triggered on
    // legitimate-but-noisy GPS jumps (acc=4-5m, delta=5-10m).
    //
    // Each restart regenerates the V| chunks → new fingerprint hash →
    // CACHE HIT mechanism never converges (L44.86's F| chunk produced
    // 4 different hashes in 25s on an immobile phone).

    /**
     * Skip the FIRST fix after start/restart. Without this, the first
     * GPS callback right after startVictimLocationUpdates() triggers a
     * `moved 0m` restart cycle (delta=0 because prev==null gates as MAX,
     * then the position write happens, but the comparator inside the
     * filter incorrectly accepts the warmup fix).
     */
    private var hasReceivedFirstFix: Boolean = false

    /**
     * Sliding window of the last 3 GPS fixes for stationary detection.
     * If the centroid of these 3 doesn't drift more than the largest
     * accuracy among them, we conclude the device is stationary and
     * suppress the refresh even if individual fixes claim large delta.
     *
     * Bounded list, oldest fix evicted at index 0 when full.
     */
    private val recentFixes: ArrayDeque<Location> = ArrayDeque(3)

    /**
     * 🆕 L44.16.88 — Compute centroid drift from the last N fixes.
     *
     * Returns the maximum distance between the centroid (lat/lon mean)
     * and any individual fix. If this is below the maximum accuracy of
     * the same fixes, the device is "stationary" by definition : its
     * apparent movement is purely measurement noise.
     *
     * @param fixes recent GPS fixes (caller ensures non-empty)
     * @return Pair of (max distance to centroid, max accuracy of fixes)
     */
    private fun centroidDrift(fixes: List<Location>): Pair<Float, Float> {
        if (fixes.isEmpty()) return 0f to 0f
        val avgLat = fixes.map { it.latitude }.average()
        val avgLon = fixes.map { it.longitude }.average()
        val centroid = Location(fixes[0].provider).apply {
            latitude = avgLat
            longitude = avgLon
        }
        val maxDist = fixes.maxOf { it.distanceTo(centroid) }
        val maxAcc = fixes.maxOf { it.accuracy }
        return maxDist to maxAcc
    }

    private val victimLocationListener = LocationListener { loc ->
        val tNow = System.currentTimeMillis()
        Log.i(TAG, "🆘[VICTIM-GPS] [t=$tNow] update: lat=${loc.latitude} lon=${loc.longitude} " +
            "acc=${loc.accuracy}m provider=${loc.provider}")

        if (!isBroadcasting) {
            Log.d(TAG, "🆘[VICTIM-GPS] [t=$tNow]   ↩ skipping — broadcast not active")
            return@LocationListener
        }

        // 🆕 L44.16.88 Guard 0 : warmup fix.
        //
        // Right after startVictimLocationUpdates(), the first GPS
        // callback represents the device's POSITION AT START — not a
        // movement. Skip it so we don't immediately FULLSTOP+restart
        // the broadcast we just started 100ms ago.
        //
        // We still memorise this fix as `lastBroadcastLocation` so that
        // the next fix has a valid baseline to compare against.
        if (!hasReceivedFirstFix) {
            hasReceivedFirstFix = true
            lastBroadcastLocation = loc
            lastVictimBroadcastRefreshMs = tNow
            recentFixes.addLast(loc)
            Log.i(TAG, "🆘[VICTIM-GPS] [t=$tNow]   ↩ skipping — first fix after start (warmup, acc=${loc.accuracy.toInt()}m)")
            return@LocationListener
        }

        // 🆕 L44.16.88 — Maintain sliding window of last 3 fixes.
        recentFixes.addLast(loc)
        while (recentFixes.size > 3) recentFixes.removeFirst()

        val prev = lastBroadcastLocation
        val rawDelta = if (prev == null) Float.MAX_VALUE else loc.distanceTo(prev)
        val delta = if (prev != null) loc.distanceTo(prev) else 0f

        // 🆕 L44.16.88 — 5-guard filter (was 4-guard in L44.84).
        //
        //   Guard 0 (new) : skip first fix after start (warmup) → done above
        //   Guard 1 (was 5m, now 15m) : raised floor — filters indoor drift
        //   Guard 2 (was ≤100m, now ≤30m) : tighter accuracy gate
        //   Guard 3 (was 0.5×acc, now 2.0×acc) : delta must EXCEED noise envelope, not match it
        //   Guard 4 (was 10s, now 30s) : longer rate-limit — let mesh stabilize
        //   Guard 5 (new) : centroid stationarity check — even if individual delta
        //                   passes guards 1-4, if the last 3 fixes hover within
        //                   their own accuracy bubble, we declare the device stationary.
        //
        // Why 15m floor : empirically, indoor GPS on Samsung Exynos 990
        // produces "valid-looking" fixes (acc=4-5m) that drift 5-10m
        // natively when the device is immobile. 15m is above that drift
        // ceiling but well below "the user actually walked across the
        // room" (which would produce 30m+ deltas with consistent sign).
        //
        // Why 30m accuracy gate : a network-provider fix at acc=20m is
        // typically a fallback when GPS lost lock — those fixes flip-flop
        // to a different cellular triangulation point causing fake jumps.
        // Below 30m means we trust the fix; above we wait for a better one.
        //
        // Why 2.0× accuracy multiplier : guard3 must REJECT moves within
        // measurement noise. acc=5m means ±5m is plausibly noise → real
        // movement requires delta ≥ 10m, not delta ≥ 2.5m as L44.84 had.
        val movedFar = prev == null || rawDelta >= 5f
        val accuracyOk = loc.accuracy <= 100f
        val moveExceedsNoise = loc.accuracy <= 0f || rawDelta >= (loc.accuracy * 0.5f)
        val msSinceLastRefresh = tNow - lastVictimBroadcastRefreshMs
        val rateLimitOk = lastVictimBroadcastRefreshMs == 0L || msSinceLastRefresh >= 10_000L

        // Guard 5 : stationary check via centroid of recent fixes.
        // If we hover within 5m of the centroid, we are stationary.
        val (centroidMaxDist, _) = centroidDrift(recentFixes.toList())
        val stationary = recentFixes.size >= 3 && centroidMaxDist < 5f

        val shouldRefresh = movedFar && accuracyOk && moveExceedsNoise && rateLimitOk && !stationary

        if (!shouldRefresh) {
            // Distinct skip reason for forensic audits, like
            // JemmaRadarOverlay does on the rescuer side.
            val reason = when {
                !movedFar -> "only moved ${delta.toInt()}m (threshold=5m)"
                !accuracyOk -> "accuracy too poor (${loc.accuracy.toInt()}m > 100m)"
                !moveExceedsNoise -> "move ${delta.toInt()}m within accuracy noise (acc=${loc.accuracy.toInt()}m, threshold=${(loc.accuracy * 0.5f).toInt()}m)"
                !rateLimitOk -> "rate-limited (${msSinceLastRefresh}ms since last refresh, min=10000ms)"
                stationary -> "stationary (last 3 fixes drift=${centroidMaxDist.toInt()}m within centroid threshold=5m)"
                else -> "unknown"
            }
            Log.d(TAG, "🆘[VICTIM-GPS] [t=$tNow]   ↩ skipping — $reason")
            return@LocationListener
        }

        Log.i(TAG, "🆘[VICTIM-GPS] [t=$tNow]   ↻ refreshing victim broadcast " +
            "(moved ${delta.toInt()}m, acc=${loc.accuracy.toInt()}m)")
        // Update both the in-memory config and the broadcast itself.
        configuredLatE6 = (loc.latitude * 1e6).toInt()
        configuredLonE6 = (loc.longitude * 1e6).toInt()
        lastBroadcastLocation = loc
        lastVictimBroadcastRefreshMs = tNow

        // Re-trigger the victim broadcast pipeline. This rebuilds the
        // 5 chunks with the new lat/lon embedded in chunk 1 (header)
        // and resumes the chunk rotation.
        try {
            startNearbyVictimBroadcast()
            Log.i(TAG, "🆘[VICTIM-GPS] [t=$tNow]   ✓ broadcast refreshed with new GPS")
        } catch (e: Exception) {
            Log.w(TAG, "🆘[VICTIM-GPS] [t=$tNow]   ❌ broadcast refresh threw", e)
        }
    }

    private fun startVictimLocationUpdates() {
        if (locationManager != null) {
            Log.d(TAG, "🆘[VICTIM-GPS] location updates already active, skipping")
            return
        }
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            // 2-second min-interval, 0m min-distance (we filter movedFar
            // ourselves in the listener for unified threshold control).
            // Try GPS first (high accuracy) then fall back to network.
            val providers = mutableListOf<String>()
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) providers.add(LocationManager.GPS_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) providers.add(LocationManager.NETWORK_PROVIDER)
            if (providers.isEmpty()) {
                Log.w(TAG, "🆘[VICTIM-GPS] no location providers available, GPS auto-refresh disabled")
                return
            }
            for (p in providers) {
                try {
                    lm.requestLocationUpdates(p, 2000L, 0f, victimLocationListener)
                    Log.i(TAG, "🆘[VICTIM-GPS] ✓ requested updates from provider='$p' (interval=2000ms)")
                } catch (e: SecurityException) {
                    Log.e(TAG, "🆘[VICTIM-GPS] ❌ permission denied for provider='$p'", e)
                }
            }
            locationManager = lm
        } catch (e: Exception) {
            Log.w(TAG, "🆘[VICTIM-GPS] startVictimLocationUpdates threw (continuing)", e)
        }
    }

    private fun stopVictimLocationUpdates() {
        val lm = locationManager ?: return
        try {
            lm.removeUpdates(victimLocationListener)
            Log.i(TAG, "🆘[VICTIM-GPS] ⏹ location updates stopped")
        } catch (e: Exception) {
            Log.w(TAG, "🆘[VICTIM-GPS] removeUpdates threw (ignoring)", e)
        }
        locationManager = null
        lastBroadcastLocation = null
        // 🆕 L44.16.88 — Reset warmup state so the next start gets a
        // fresh "first fix skip" window. Without this, restarting the
        // broadcast inside the same service lifecycle would skip the
        // anti-drift logic and potentially trigger a phantom restart.
        hasReceivedFirstFix = false
        recentFixes.clear()
    }

    private fun startNearbyVictimBroadcast() {
        // 🆕 L44.16.25-hotfix1 — KILL ZOMBIES first.
        //
        // If the app was force-killed or crashed in a previous session,
        // Google Play Services keeps our previous advertise alive in
        // the background until the phone reboots. The next time we
        // start broadcasting, we end up with TWO concurrent advertises:
        //   - the new one (correct sid + profile)
        //   - the zombie (old sid like 'e5c4', stale chunks)
        //
        // The zombie shows up to nearby phones *first* (it's already
        // warmed up) and saturates the discovery slots, preventing the
        // current phone from seeing rescuers. Symptoms in logs:
        //   - Other phones see V|<old_sid>|... with stale data
        //   - This phone never sees onEndpointFound for rescuers
        //
        // Fix: fullStop() resets advertising + discovery + endpoints
        // *before* we start fresh. Idempotent — does nothing if no
        // active session.
        // 🆕 L44.16.26 — KILL ZOMBIES via HARD recreate before victim broadcast.
        //
        // `fullStop()` on the existing instance is not enough: GMS keeps
        // zombie advertise sessions on a separate binding that survives
        // our process death. Tested in L44.16.25: the receiver still saw
        // an old sid (e5c4) with an old payload (sex=?, age=0) instead
        // of the current device id (YrzR) with the current profile.
        //
        // recreateFresh() drops the singleton AND requests a brand-new
        // Nearby.getConnectionsClient(), which gives us a clean GMS
        // binding from which stopAllEndpoints() actually has effect.
        Log.i(TAG, "🧹 [ZOMBIE-KILL-HARD] recreateFresh before victim broadcast — requesting brand-new Nearby binding")
        try {
            JemmaRadarOverlayState.recreateFresh(applicationContext)
            Log.i(TAG, "🧹 [ZOMBIE-KILL-HARD] ✓ recreateFresh done — broadcasts will use new binding")
        } catch (e: Exception) {
            Log.w(TAG, "🧹 [ZOMBIE-KILL-HARD] recreateFresh threw (continuing anyway)", e)
        }

        var profileJson = configuredProfileJson
        if (profileJson.isNullOrBlank()) {
            Log.i(TAG, "startNearbyVictimBroadcast — no in-memory config, trying disk fallback")
            if (loadConfigFromDisk(this)) {
                profileJson = configuredProfileJson
            }
        }
        if (profileJson.isNullOrBlank()) {
            Log.w(TAG, "startNearbyVictimBroadcast — no configured profile, skipping")
            return
        }
        try {
            val jProfile = org.json.JSONObject(profileJson)
            val patient = jProfile.optJSONObject("p") ?: org.json.JSONObject()
            // Extract codes from each section. JSON shape:
            //   "al": [{"c": "K3", "s":"H"}, ...]
            val allergyCodes = extractCodesFromSection(jProfile.optJSONArray("al"))
            val medCodes     = extractCodesFromSection(jProfile.optJSONArray("md"))
            val condCodes    = extractCodesFromSection(jProfile.optJSONArray("cn"))
            val immunCodes   = extractCodesFromSection(jProfile.optJSONArray("im"))

            // Patient header fields
            // 🆕 L44.16.24-hotfix2 — Fix Bug C : the _j 1.2 SHORT format
            // uses `p.gs` for gender/sex (not `p.s`) and `p.bd` for birth
            // date (not `p.a` for age). Age is computed from bd.
            //
            // Previous code read `p.s` and `p.a` → both absent → fallback
            // values 'sex=?' 'age=0' systematically.
            val gs = patient.optString("gs", "").trim().uppercase()
            val sex: Char = when (gs.firstOrNull()) {
                'M', 'F' -> gs.first()
                'O' -> 'O'  // other
                'U' -> '?'  // unknown
                else -> '?'
            }
            val bd = patient.optString("bd", "").trim()
            val age: Int = if (bd.isNotEmpty()) {
                try {
                    val birthYear = bd.substringBefore("-").toInt()
                    val birthMonth = bd.substringAfter("-", "").substringBefore("-", "").toIntOrNull() ?: 1
                    val birthDay = bd.substringAfterLast("-", "").toIntOrNull() ?: 1
                    val now = java.time.LocalDate.now()
                    val birth = try { java.time.LocalDate.of(birthYear, birthMonth, birthDay) } catch (e: Exception) { null }
                    if (birth != null) {
                        java.time.Period.between(birth, now).years.coerceAtLeast(0)
                    } else {
                        // Fallback: just year subtraction
                        (now.year - birthYear).coerceAtLeast(0)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse bd='$bd' for age, using 0", e)
                    0
                }
            } else 0
            val bt  = patient.optString("bt", "")

            Log.i(TAG, "🆘[VICTIM-INFO] parsed patient header: gs='$gs' → sex=$sex, bd='$bd' → age=$age, bt='$bt'")

            val sid = nearbyVictimSid
                ?: JemmaDeviceId.get(applicationContext).also { nearbyVictimSid = it }
            Log.i(TAG, "🆘[VICTIM-SID] using device id as victim sid: '$sid' (stable across restarts)")

            val lat = configuredLatE6 / 1e6
            val lon = configuredLonE6 / 1e6

            Log.i(TAG, "🆘 starting Nearby victim broadcast — sid=$sid name='$configuredName' " +
                "sex=$sex age=$age bt=$bt crit=$configuredCriticality lang='$configuredLangCode' " +
                "(${allergyCodes.size}A ${medCodes.size}M ${condCodes.size}C ${immunCodes.size}I) " +
                "@$lat,$lon")

            nearbyService.startAdvertisingVictim(
                sid = sid,
                name = configuredName,
                sex = sex,
                age = age,
                bloodType = bt,
                criticality = configuredCriticality.toInt(),
                lat = lat,
                lon = lon,
                allergyCodes = allergyCodes,
                medCodes = medCodes,
                conditionCodes = condCodes,
                immunCodes = immunCodes,
                victimLang = configuredLangCode    // 🆕 L44.16.48
            )

            // 🆕 L44.16.24 — ALSO start discovery so the SOS victim
            // can detect rescuers approaching, even when no radar UI
            // is open. This is the symmetric pair of the advertising:
            // we don't just shout "I'm here" — we also listen for the
            // "I'm coming" responses from rescuers in range.
            //
            // Discovery is idempotent (no-op if already running) so it's
            // safe to call even when the radar overlay is also active.
            // 🆕 L44.16.27 — DELAY discovery by 2 seconds.
            //
            // Logs from L44.16.26 showed that on Pixel 9 Pro XL, when
            // we called startAdvertising and startDiscovery in quick
            // succession (3ms apart), the discovery callback never fired
            // (no `✓ discovery started` and no `❌ discovery failed`).
            // Result: the discovery silently never reached GMS, the
            // device never received any onEndpointFound from rescuers
            // nearby. Meanwhile our advertising worked (other devices
            // saw us correctly).
            //
            // Hypothesis: GMS internal state machine has a small race
            // window when transitioning from ready → advertising →
            // also-discovering. Letting advertising settle for 2s
            // before requesting discovery side-steps this.
            //
            // Side effect: 2s window where we advertise without
            // discovering. Acceptable for the SOS use case (the victim
            // wants to be SEEN immediately; receiving rescuer beacons
            // is secondary and only relevant once they arrive in range).
            Log.i(TAG, "🆘[SOS-DISCOVER] scheduling Nearby discovery in 2000ms (after advertise settles)")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Log.i(TAG, "🆘[SOS-DISCOVER] starting Nearby discovery NOW for rescuer detection")
                try {
                    nearbyService.startDiscovery()
                    Log.i(TAG, "🆘[SOS-DISCOVER] startDiscovery() invoked — waiting for callback…")
                } catch (e: Exception) {
                    Log.e(TAG, "🆘[SOS-DISCOVER] startDiscovery threw", e)
                }

                // Register our rescuer-only listener (extra slot, doesn't
                // conflict with the radar overlay's primary listener)
                if (!sosListenerRegistered) {
                    Log.i(TAG, "🆘[SOS-DISCOVER] registering SOS rescuer listener")
                    nearbyService.addListener(sosRescuerListener)
                    sosListenerRegistered = true
                } else {
                    Log.d(TAG, "🆘[SOS-DISCOVER] listener already registered, skipping")
                }

                // 🆕 L44.16.36 — start GPS auto-refresh for victim broadcast.
                // Idempotent (no-op if already active). This ensures that
                // when the victim moves, her broadcast carries her live
                // position rather than the gravée GPS from start of SOS.
                startVictimLocationUpdates()
            }, 2000L)
        } catch (e: Exception) {
            Log.e(TAG, "startNearbyVictimBroadcast failed", e)
        }
    }

    private fun stopNearbyVictimBroadcast() {
        // 🆕 L44.16.36 — Stop GPS auto-refresh first (avoids late callbacks
        // re-triggering startNearbyVictimBroadcast after we've stopped).
        stopVictimLocationUpdates()

        try {
            nearbyService.stopAdvertising()
            Log.i(TAG, "⏹ Nearby victim broadcast stopped")
        } catch (e: Exception) {
            Log.w(TAG, "stopNearbyVictimBroadcast threw (ignoring)", e)
        }
        // 🆕 L44.16.24 — Unregister the SOS rescuer listener too.
        // Note: we do NOT call stopDiscovery() here because the radar
        // overlay (if open) still needs it. The overlay's STOP RADAR
        // button is the authoritative full-stop via fullStop().
        if (sosListenerRegistered) {
            try {
                nearbyService.removeListener(sosRescuerListener)
                sosListenerRegistered = false
                Log.i(TAG, "🆘[SOS-DISCOVER] unregistered SOS rescuer listener")
            } catch (e: Exception) {
                Log.w(TAG, "removeListener threw (ignoring)", e)
            }
        }
        // Clear the rescuer registry — no SOS active means no rescuers
        // to track from this device's POV
        JemmaSosRescuerRegistry.clear()
        Log.i(TAG, "🆘[SOS-DISCOVER] rescuer registry cleared")

        nearbyVictimSid = null
    }

    /** Extract the `c` field from each entry of a code section JSON array. */
    private fun extractCodesFromSection(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val code = item.optString("c", "").trim()
            if (code.isNotEmpty()) out.add(code)
        }
        return out
    }
}
