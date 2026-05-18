package be.heyman.android.jemmapassdemo.sos
import android.util.Log

/**
 * 🆕 L44.16.24 — JemmaSosRescuerRegistry
 *
 * Process-singleton that holds the rescuers discovered by the SOS
 * service while the victim is broadcasting. Read by the JS bridge
 * (`JemmaJsBridge.sosListRescuers()`) which the JS UI polls every
 * few seconds to:
 *
 *   1. Display the list of rescuers under the "🆘 Mode SOS" panel
 *      (image 1 of the screenshots — currently has no rescuer list).
 *
 *   2. Drive the heartbeat TTS announcement loop (the victim should
 *      cycle through the unique languages of detected rescuers and
 *      shout "Au secours, je suis à X mètres" / "助けてください、…").
 *
 * Why a singleton instead of a per-instance field?
 *   - JemmaSosService is bound to the Service lifecycle, but we need
 *     stable rescuer state across rotation/restart of the Service.
 *   - JemmaJsBridge runs in the WebView, which is in the Activity.
 *     Both must reach the same data.
 *
 * Thread safety: read/write under synchronized(...) blocks. Snapshot
 * methods return defensive copies to avoid mutation surprises.
 */
object JemmaSosRescuerRegistry {
    private const val TAG = "JEMMA-SOS-REG"

    // sid → RescuerInfo, mutable in place so updates are reflected
    private val byId = LinkedHashMap<String, RescuerInfo>()

    @Synchronized
    fun upsert(rescuer: RescuerInfo) {
        val existed = byId.containsKey(rescuer.sessionIdHex)
        byId[rescuer.sessionIdHex] = rescuer
        if (!existed) {
            Log.i(TAG, "+ rescuer registered: sid=${rescuer.sessionIdHex} name='${rescuer.name}' " +
                "lang=${rescuer.langCode} (total=${byId.size})")
        }
    }

    @Synchronized
    fun markStale(sid: String) {
        val r = byId[sid] ?: return
        if (!r.isStale) {
            byId[sid] = r.copy(isStale = true)
            Log.i(TAG, "↺ rescuer marked stale: sid=$sid (still in registry, count=${byId.size})")
        }
    }

    @Synchronized
    fun clear() {
        val n = byId.size
        byId.clear()
        if (n > 0) Log.i(TAG, "🗑 cleared registry ($n rescuers)")
    }

    /**
     * Return a snapshot of all rescuers — fresh first, stale last.
     * Each entry is sorted by `lastSeenMs` desc within its group.
     */
    @Synchronized
    fun snapshot(): List<RescuerInfo> {
        return byId.values.sortedWith(
            compareBy<RescuerInfo> { it.isStale }
                .thenByDescending { it.lastSeenMs }
        )
    }

    /**
     * The set of distinct languages of currently-fresh rescuers.
     * Used by the heartbeat TTS to know which languages to cycle
     * through ("au secours" → "助けてください" → "help").
     */
    @Synchronized
    fun freshLanguages(): Set<String> {
        return byId.values
            .asSequence()
            .filter { !it.isStale }
            .map { it.langCode }
            .filter { it.isNotBlank() }
            .toSet()
    }

    @Synchronized
    fun size(): Int = byId.size

    /** Serialize to JSON array string for JS consumption. */
    @Synchronized
    fun toJsonString(): String {
        val arr = org.json.JSONArray()
        for (r in snapshot()) {
            arr.put(r.toJson())
        }
        return arr.toString()
    }
}
