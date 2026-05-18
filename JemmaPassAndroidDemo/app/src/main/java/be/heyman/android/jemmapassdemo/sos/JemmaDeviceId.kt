package be.heyman.android.jemmapassdemo.sos
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.security.SecureRandom

/**
 * 🆕 L44.16.25 — JemmaDeviceId
 *
 * Process-singleton that exposes a stable 4-character device identifier,
 * generated once at first install and persisted in SharedPreferences.
 *
 * Why?
 *   The Nearby session ids (`sid`) currently rotate every time the SOS
 *   broadcast or rescuer beacon restarts (e.g. on app restart, on
 *   STOP/START radar, etc.). This means the *same* phone appears as a
 *   *different* peer to nearby devices each time, which is bad for:
 *
 *     - Debug: hard to recognize "Kurodo's Pixel" across log sessions
 *       because its sid is `e5c4` one minute, `7a91` the next, `f18e`
 *       after a restart.
 *     - Dedup: the receiver's TMP profile pipeline keys on sid → if sid
 *       changes, it creates a new "tmp_ble" profile every time.
 *     - User trust: in a real disaster, a rescuer scanning peers should
 *       see the *same identifier* every time so they can recognize a
 *       returning victim ("oh, that's the same person from this morning").
 *
 * Solution: one stable 4-char id per install, generated from a wide
 * alphabet to keep collision chance negligible. Stored in
 * SharedPreferences ("jemma_device") under the key "device_id".
 *
 * ## Alphabet
 *
 * 80 characters total, designed for maximum entropy *while remaining
 * safe inside the Nearby endpointName envelope*:
 *
 *   - A–Z  (26)
 *   - a–z  (26)
 *   - 0–9  (10)
 *   - safe symbols: ! @ # $ % ^ & * ( ) _ - + = . , ; : ?  (18)
 *
 * **Excluded** (would break the V|sid|N|TOT|payload format or cause
 * encoding issues):
 *   - `|`  (chunk separator in JemmaNearbyEndpointCodec)
 *   - `\`  (escape char, parser ambiguity)
 *   - `'` `"`  (JSON quoting hazard when emitted into JS)
 *   - ` `  (whitespace, can cause UTF-8 endpointName trim quirks)
 *   - `<` `>`  (would force HTML escaping in the radar UI)
 *   - `/` `~` `\``  (path / shell / template literal hazards)
 *   - control chars (`\n`, `\t`, ...)
 *   - non-ASCII (smileys/emoji can balloon UTF-8 byte count)
 *
 * 80⁴ = 40,960,000 combinations → P(collision among 100 phones) ≈ 0.012%.
 * Acceptable for a peer-discovery tool.
 *
 * ## Length
 *
 * 4 characters fits comfortably in:
 *   - Nearby endpointName payload (max 131 chars; we use ~35–80)
 *   - Radar UI badges (small font, single line)
 *   - Logcat lines (no wrap)
 *
 * ## Usage
 *
 *   val myId = JemmaDeviceId.get(context)         // → e.g. "K7@9"
 *   val myId = JemmaDeviceId.get(context)         // → "K7@9" again (stable)
 *
 * Thread-safety: SharedPreferences is thread-safe for read/write.
 * The first-call generate-and-persist is wrapped in synchronized()
 * to avoid two concurrent boot threads writing two different ids.
 */
object JemmaDeviceId {
    private const val TAG = "JEMMA-DEVICE-ID"
    private const val PREFS_NAME = "jemma_device"
    private const val KEY_DEVICE_ID = "device_id"
    private const val ID_LENGTH = 4

    /**
     * Alphabet used for ID generation. Must NOT contain any character
     * that would break the Nearby endpointName encoding (notably `|`)
     * or the JSON wire format.
     */
    private val ALPHABET = (
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +     // 26 uppercase
        "abcdefghijklmnopqrstuvwxyz" +     // 26 lowercase
        "0123456789" +                      // 10 digits
        "!@#\$%^&*()_-+=.,;:?"              // 18 safe symbols
    ).toCharArray()

    init {
        // Sanity check at class load — catch any inadvertent change to
        // the alphabet that would reintroduce a forbidden character.
        val forbidden = "|\\\"'<>/~` \n\r\t".toSet()
        require(ALPHABET.toSet().intersect(forbidden).isEmpty()) {
            "ALPHABET contains forbidden char(s): ${ALPHABET.toSet().intersect(forbidden)}"
        }
        Log.d(TAG, "alphabet OK: ${ALPHABET.size} chars, ${ALPHABET.size.toLong()
            .let { it * it * it * it }} combinations")
    }

    @Volatile private var cached: String? = null
    private val genLock = Any()

    /**
     * Get this device's stable 4-char ID, generating + persisting it
     * on first call.
     *
     * Cached in-process after first successful read.
     */
    fun get(context: Context): String {
        cached?.let { return it }

        synchronized(genLock) {
            // Re-check after acquiring lock (another thread might have just won)
            cached?.let { return it }

            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var id = prefs.getString(KEY_DEVICE_ID, null)

            if (id == null || !isValid(id)) {
                if (id != null) {
                    Log.w(TAG, "stored device id '$id' is invalid (length or chars), regenerating")
                }
                id = generate()
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
                Log.i(TAG, "🆕 generated new device id='$id' and persisted to SharedPreferences")
            } else {
                Log.i(TAG, "✓ loaded device id='$id' from SharedPreferences")
            }

            cached = id
            return id
        }
    }

    /**
     * Generate a fresh random 4-char ID using SecureRandom. NOT cached
     * by itself — only `get()` caches.
     */
    private fun generate(): String {
        val rng = SecureRandom()
        val sb = StringBuilder(ID_LENGTH)
        repeat(ID_LENGTH) {
            sb.append(ALPHABET[rng.nextInt(ALPHABET.size)])
        }
        return sb.toString()
    }

    /**
     * Validate an ID stored in SharedPreferences against current rules.
     * Used at boot to detect corrupted prefs (length mismatch, foreign
     * chars from older formats, etc.) and trigger regeneration.
     */
    private fun isValid(id: String): Boolean {
        if (id.length != ID_LENGTH) return false
        val alphabetSet = ALPHABET.toSet()
        return id.all { it in alphabetSet }
    }

    /**
     * For debugging/dev only — wipe the persisted ID and regenerate
     * a new one on next get(). Useful when investigating dedup or
     * peer-recognition issues.
     *
     * NOT exposed to the JS bridge — too easy to misuse.
     */
    fun resetForDevOnly(context: Context) {
        synchronized(genLock) {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val old = prefs.getString(KEY_DEVICE_ID, null)
            prefs.edit().remove(KEY_DEVICE_ID).apply()
            cached = null
            Log.w(TAG, "⚠ resetForDevOnly: cleared device id (was '$old')")
        }
    }
}
