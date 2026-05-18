package be.heyman.android.jemmapassdemo.sos
import android.content.Context
import android.util.Log

/**
 * 🆕 L44.16.20 — JemmaRescuerBroadcaster
 *
 * High-level wrapper that lets a rescuer broadcast their presence via
 * BLE. Reuses JemmaSosBleAdvertiser under the hood (same hardware,
 * same UUID, same chunk preamble) — only the chunk_type byte differs
 * so receivers can disambiguate rescuer beacons from SOS broadcasts.
 *
 * Lifecycle:
 *   - Caller invokes start(name, langByte, latE6, lonE6) when the
 *     rescuer taps "⛑️ I am a rescuer" in the radar UI.
 *   - This builds a session_id, encodes 2 chunks (RESCUER + SENTINEL)
 *     via JemmaSosChunkCodec.encodeRescuer(), and hands them to a
 *     dedicated JemmaSosBleAdvertiser instance.
 *   - The advertiser rotates the 2 chunks at CHUNK_INTERVAL_MS (200ms),
 *     so a complete cycle takes ~400ms — much faster than victim's
 *     full SOS broadcast (which can be 4-10 chunks).
 *   - Caller invokes stop() when the rescuer leaves the radar / kills
 *     the broadcast.
 *
 * Coexistence with SOS:
 *   - This broadcaster uses its OWN advertiser instance, separate
 *     from JemmaSosService.bleAdvertiser. Android allows multiple
 *     simultaneous advertising sets (typical limit: 8), so a single
 *     phone can theoretically be both a victim AND a rescuer at once.
 *     In practice that's only useful in chained-rescue scenarios.
 *   - The advertiser uses the same JEMMA_SOS_SERVICE_UUID, so a
 *     single scanner picks up both kinds of beacons in one session.
 *
 * Thread safety:
 *   - All public methods are safe to call from any thread.
 *   - Internal state guarded by @Volatile. start() while already
 *     running is treated as "update parameters" — we stop+restart
 *     the advertiser with the new GPS/name/lang.
 */
class JemmaRescuerBroadcaster(private val context: Context) {

    companion object {
        private const val TAG = "JEMMA-RESCUER-ADV"
    }

    @Volatile private var bleAdvertiser: JemmaSosBleAdvertiser? = null
    @Volatile private var currentSessionId: ByteArray? = null
    @Volatile private var currentName: String = ""
    @Volatile private var currentLang: String = "en"

    /**
     * Start (or update) a rescuer broadcast.
     *
     * @param name      rescuer's given name (max 16 UTF-8 bytes — trimmed)
     * @param langByte  RESCUER_LANG_FR (0), RESCUER_LANG_EN (1), or RESCUER_LANG_JA (2)
     * @param latE6     rescuer's current latitude × 1e6
     * @param lonE6     rescuer's current longitude × 1e6
     * @return  true if the advertiser started successfully
     */
    fun start(name: String, langByte: Byte, latE6: Int, lonE6: Int): Boolean {
        Log.i(TAG, "start: name='$name' lang=${JemmaSosChunkCodec.rescuerLangCode(langByte)} " +
            "lat=${latE6 / 1e6} lon=${lonE6 / 1e6}")

        // If already running, stop the previous advertiser cleanly
        // before restarting with the new params (e.g. user moved or
        // changed name in the modal).
        stop()

        return try {
            val sid = JemmaSosChunkCodec.newSessionId()
            currentSessionId = sid
            currentName = name
            currentLang = JemmaSosChunkCodec.rescuerLangCode(langByte)

            val chunks = JemmaSosChunkCodec.encodeRescuer(name, langByte, latE6, lonE6, sid)
            if (chunks.isEmpty()) {
                Log.w(TAG, "encodeRescuer returned empty list")
                return false
            }

            val adv = JemmaSosBleAdvertiser(context).also { bleAdvertiser = it }
            if (!adv.isSupported()) {
                Log.w(TAG, "BLE advertise not supported on this device")
                return false
            }
            adv.start(chunks)
            Log.i(TAG, "⛑️ rescuer broadcast started — sid=${sid.joinToString("") { "%02x".format(it.toInt() and 0xFF) }}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            false
        }
    }

    /**
     * Stop the current rescuer broadcast. No-op if nothing is running.
     */
    fun stop() {
        val adv = bleAdvertiser ?: return
        try {
            adv.stop()
            Log.i(TAG, "⛑️ rescuer broadcast stopped (was: name='$currentName' lang=$currentLang)")
        } catch (e: Exception) {
            Log.w(TAG, "stop failed (continuing anyway)", e)
        } finally {
            bleAdvertiser = null
            currentSessionId = null
        }
    }

    /** True if a rescuer broadcast is currently active. */
    fun isRunning(): Boolean = bleAdvertiser != null
}
