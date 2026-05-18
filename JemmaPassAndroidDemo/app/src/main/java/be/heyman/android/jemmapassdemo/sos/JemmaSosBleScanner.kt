package be.heyman.android.jemmapassdemo.sos
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🆕 L44.16.12 — JEMMA SOS BLE Scanner.
 *
 * Scans for BLE advertises matching the JEMMA_SOS service UUID,
 * reassembles chunks by session_id, and emits "peer complete"
 * events when all expected chunks have been received (or after a
 * timeout).
 *
 * Architecture:
 *   - One scan callback fires for every BLE advertise heard
 *     matching our service UUID filter.
 *   - The service data byte array IS the chunk (already wrapped
 *     with our preamble).
 *   - We accumulate per-session state (Map<sid_hex, PeerSession>)
 *     and emit a callback when sentinel arrives or all chunks
 *     are present.
 *   - Peers that haven't sent a chunk in 30s are evicted as "lost".
 *
 * The Scanner runs as long as start() ↔ stop() bracketed by the
 * caller. Designed to live inside a Foreground Service.
 *
 * ─── Permissions required ──────────────────────────────────────
 *  - BLUETOOTH_SCAN (Android 12+)
 *  - ACCESS_FINE_LOCATION (Android 6+, required for BLE scan)
 *  - BLUETOOTH (legacy, Android ≤ 11)
 *  - BLUETOOTH_ADMIN (legacy, Android ≤ 11)
 *
 * Caller is responsible for checking permissions.
 */
class JemmaSosBleScanner(private val context: Context) {

    companion object {
        private const val TAG = "JEMMA-SOS-SCAN"

        // How long a session can be silent before we declare it "lost".
        private const val PEER_TIMEOUT_MS = 30_000L

        // Periodic eviction cadence
        private const val EVICTION_INTERVAL_MS = 5_000L
    }

    interface Listener {
        /** A new session_id was first heard (header chunk arrived). */
        fun onPeerFound(peer: PeerInfo)
        /** Header or any code chunk updated for an existing peer. */
        fun onPeerUpdate(peer: PeerInfo)
        /** Sentinel chunk received OR all expected chunks present. */
        fun onPeerComplete(peer: PeerInfo)
        /** Peer hasn't been heard in PEER_TIMEOUT_MS. */
        fun onPeerLost(sessionIdHex: String)

        // 🆕 L44.16.20 — Rescuer beacon callbacks (separate from
        // SOS victim peers above). Rescuer beacons carry only a name,
        // language, and GPS — no medical data. The JS layer uses
        // these callbacks to drive the TTS announcement engine.
        fun onRescuerFound(rescuer: RescuerInfo) {}
        fun onRescuerUpdate(rescuer: RescuerInfo) {}
        fun onRescuerLost(sessionIdHex: String) {}
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    @Volatile private var isScanning: Boolean = false
    @Volatile private var listener: Listener? = null

    // Per-session reassembly state
    private val sessions = mutableMapOf<String, PeerSession>()

    // 🆕 L44.16.20 — Separate map for rescuer beacons. Rescuer
    // sessions have their own session_id space (not subset of peer
    // sessions) so we keep them distinct here.
    private val rescuerSessions = mutableMapOf<String, RescuerSession>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val evictionRunnable = object : Runnable {
        override fun run() {
            if (!isScanning) return
            evictStalePeers()
            mainHandler.postDelayed(this, EVICTION_INTERVAL_MS)
        }
    }

    fun setListener(l: Listener?) { this.listener = l }

    /** True if scanning is supported on this hardware. */
    fun isSupported(): Boolean {
        if (bluetoothAdapter == null) return false
        if (!bluetoothAdapter.isEnabled) return false
        if (scanner == null) return false
        return true
    }

    /** Snapshot copy of all known peers (complete or partial). */
    @Synchronized
    fun snapshotPeers(): List<PeerInfo> {
        return sessions.values.map { it.toPeerInfo() }
    }

    /**
     * 🆕 L44.16.22-hotfix5 — Snapshot of currently-tracked rescuer
     * beacons. Used by JemmaRadarOverlay on (re-)open to immediately
     * populate its UI with previously-captured rescuers when the
     * scanner singleton was already running (e.g. after HIDE).
     */
    @Synchronized
    fun snapshotRescuers(): List<RescuerInfo> {
        return rescuerSessions.values.map { it.toRescuerInfo() }
    }

    fun start() {
        if (!hasScanPermission()) {
            Log.w(TAG, "missing BLUETOOTH_SCAN/FINE_LOCATION permission, cannot start")
            return
        }
        if (!isSupported()) {
            Log.w(TAG, "BLE scanning not supported")
            return
        }
        if (isScanning) {
            Log.i(TAG, "already scanning")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(JemmaSosBleAdvertiser.JEMMA_SOS_PARCEL_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            mainHandler.post(evictionRunnable)
            Log.i(TAG, "▶ scanning started (service=${JemmaSosBleAdvertiser.JEMMA_SOS_SERVICE_UUID})")
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException starting scan: ${se.message}")
        }
    }

    fun stop() {
        if (!isScanning) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException stopping scan: ${se.message}")
        }
        isScanning = false
        mainHandler.removeCallbacks(evictionRunnable)
        synchronized(this) {
            sessions.clear()
            rescuerSessions.clear()  // 🆕 L44.16.20
        }
        Log.i(TAG, "⏹ scanning stopped")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) handleResult(r)
        }
        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed: errorCode=$errorCode")
        }
    }

    private fun handleResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val serviceData = record.serviceData ?: return
        val chunkBytes = serviceData[JemmaSosBleAdvertiser.JEMMA_SOS_PARCEL_UUID] ?: return

        // 🆕 L44.16.22-hotfix3 — log EVERY received chunk so we can
        // see in logcat whether BLE packets are reaching us at all.
        Log.d(TAG, "📡 BLE packet received: ${chunkBytes.size} bytes, rssi=${result.rssi}")

        val parsed = JemmaSosChunkCodec.decode(chunkBytes) ?: run {
            Log.w(TAG, "⚠ ignored malformed chunk (${chunkBytes.size} bytes)")
            return
        }

        val sidHex = JemmaSosChunkCodec.sessionIdToHex(parsed.sessionId)
        val rssi = result.rssi
        Log.d(TAG, "  → decoded: type=${parsed::class.simpleName} sid=${sidHex.take(8)} idx=${parsed.chunkIndex}/${parsed.chunkTotal}")

        // 🆕 L44.16.20 — Route rescuer chunks to their own pipeline.
        // A rescuer beacon's session_id is independent from victim
        // session_ids; they live in rescuerSessions, not sessions.
        if (parsed is JemmaSosChunkCodec.ParsedChunk.Rescuer) {
            Log.i(TAG, "  ⛑️ ROUTING to rescuer pipeline: name='${parsed.rescuerName}' lang=${parsed.langCode}")
            handleRescuerChunk(parsed, sidHex, rssi)
            return
        }

        val (created, completed, peerSnap) = synchronized(this) {
            val existed = sessions.containsKey(sidHex)
            val sess = sessions.getOrPut(sidHex) {
                PeerSession(
                    sessionIdHex = sidHex,
                    chunkTotal = parsed.chunkTotal,
                    firstSeenMs = System.currentTimeMillis()
                )
            }
            sess.lastSeenMs = System.currentTimeMillis()
            sess.rssi = rssi

            sess.applyChunk(parsed)

            val justCompleted = !sess.isComplete && sess.checkComplete(parsed)
            if (justCompleted) sess.isComplete = true

            Triple(!existed, justCompleted, sess.toPeerInfo())
        }

        if (created) {
            Log.i(TAG, "🆕 peer found: $sidHex name='${peerSnap.broadcasterName}' lat=${peerSnap.latitude} lon=${peerSnap.longitude}")
            listener?.onPeerFound(peerSnap)
        }

        if (completed) {
            Log.i(TAG, "✅ peer complete: $sidHex (${peerSnap.allergyCodes.size} allergies, " +
                "${peerSnap.medCodes.size} meds, ${peerSnap.conditionCodes.size} conditions)")
            listener?.onPeerComplete(peerSnap)
        } else {
            listener?.onPeerUpdate(peerSnap)
        }
    }

    /**
     * 🆕 L44.16.20 — Process a rescuer beacon chunk.
     *
     * Rescuer beacons are simple: 1 chunk with name+lang+gps. We
     * don't need to wait for a complete cycle — every successful
     * decode is immediately a usable update.
     *
     * Emits onRescuerFound on first contact for a session_id, then
     * onRescuerUpdate on every subsequent ping (RSSI / position
     * changes that drive the TTS announcement engine on victim side).
     */
    private fun handleRescuerChunk(
        parsed: JemmaSosChunkCodec.ParsedChunk.Rescuer,
        sidHex: String,
        rssi: Int
    ) {
        val (created, snap) = synchronized(this) {
            val existed = rescuerSessions.containsKey(sidHex)
            val sess = rescuerSessions.getOrPut(sidHex) {
                RescuerSession(
                    sessionIdHex = sidHex,
                    firstSeenMs = System.currentTimeMillis(),
                    name = parsed.rescuerName,
                    langCode = parsed.langCode,
                    latE6 = parsed.latE6,
                    lonE6 = parsed.lonE6,
                    rssi = rssi
                )
            }
            // Update mutable fields (the rescuer might have moved
            // since their last broadcast cycle).
            sess.name = parsed.rescuerName
            sess.langCode = parsed.langCode
            sess.latE6 = parsed.latE6
            sess.lonE6 = parsed.lonE6
            sess.rssi = rssi
            sess.lastSeenMs = System.currentTimeMillis()
            Pair(!existed, sess.toRescuerInfo())
        }
        if (created) {
            Log.i(TAG, "🩹 rescuer found: $sidHex name='${snap.name}' lang=${snap.langCode} " +
                "lat=${snap.latitude} lon=${snap.longitude}")
            listener?.onRescuerFound(snap)
        } else {
            listener?.onRescuerUpdate(snap)
        }
    }

    private fun evictStalePeers() {
        val now = System.currentTimeMillis()
        val evicted = mutableListOf<String>()
        synchronized(this) {
            val it = sessions.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (now - entry.value.lastSeenMs > PEER_TIMEOUT_MS) {
                    evicted.add(entry.key)
                    it.remove()
                }
            }
        }
        for (sid in evicted) {
            Log.i(TAG, "💨 peer lost: $sid")
            listener?.onPeerLost(sid)
        }

        // 🆕 L44.16.20 — Same eviction logic for rescuer beacons.
        val rescuerEvicted = mutableListOf<String>()
        synchronized(this) {
            val it = rescuerSessions.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (now - entry.value.lastSeenMs > PEER_TIMEOUT_MS) {
                    rescuerEvicted.add(entry.key)
                    it.remove()
                }
            }
        }
        for (sid in rescuerEvicted) {
            Log.i(TAG, "💨 rescuer lost: $sid")
            listener?.onRescuerLost(sid)
        }
    }

    private fun hasScanPermission(): Boolean {
        val finePermOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!finePermOk && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}

/**
 * Per-session reassembly state, mutable. Held in JemmaSosBleScanner.
 */
internal class PeerSession(
    val sessionIdHex: String,
    var chunkTotal: Int,
    val firstSeenMs: Long
) {
    var lastSeenMs: Long = firstSeenMs
    var rssi: Int = 0
    var isComplete: Boolean = false

    // Header fields
    var broadcasterName: String = ""
    var latE6: Int = 0
    var lonE6: Int = 0
    var sex: Char = 'U'
    var ageDecade: Int = 0
    var bloodType: String = ""
    var criticality: Byte = 0
    var flags: Byte = 0
    var hasHeader: Boolean = false

    // Code lists
    val allergies = mutableListOf<JemmaSosChunkCodec.CodeEntry>()
    val meds = mutableListOf<JemmaSosChunkCodec.CodeEntry>()
    val conditions = mutableListOf<JemmaSosChunkCodec.CodeEntry>()
    val immunizations = mutableListOf<JemmaSosChunkCodec.CodeEntry>()
    var freeTextNote: String = ""

    // Track which chunk indices have been seen, to detect "all received"
    val receivedIndices = mutableSetOf<Int>()
    var sentinelSeen: Boolean = false

    fun applyChunk(parsed: JemmaSosChunkCodec.ParsedChunk) {
        receivedIndices.add(parsed.chunkIndex)
        if (parsed.chunkTotal > chunkTotal) chunkTotal = parsed.chunkTotal

        when (parsed) {
            is JemmaSosChunkCodec.ParsedChunk.Header -> {
                broadcasterName = parsed.broadcasterName
                latE6 = parsed.latE6
                lonE6 = parsed.lonE6
                sex = parsed.sex
                ageDecade = parsed.ageDecade
                bloodType = parsed.bloodType
                criticality = parsed.criticality
                flags = parsed.flags
                hasHeader = true
            }
            is JemmaSosChunkCodec.ParsedChunk.Codes -> {
                val target: MutableList<JemmaSosChunkCodec.CodeEntry> = when (parsed.sectionType) {
                    JemmaSosChunkCodec.CHUNK_ALLERGIES -> allergies
                    JemmaSosChunkCodec.CHUNK_MEDS -> meds
                    JemmaSosChunkCodec.CHUNK_CONDITIONS -> conditions
                    JemmaSosChunkCodec.CHUNK_IMMUN -> immunizations
                    else -> return
                }
                // Replace if same chunk arrives again (idempotent)
                // We could dedup more carefully but for simplicity we
                // just append and trust the broadcaster's format.
                for (entry in parsed.entries) {
                    if (target.none { it.code == entry.code && it.systemByte == entry.systemByte }) {
                        target.add(entry)
                    }
                }
            }
            is JemmaSosChunkCodec.ParsedChunk.TextNote -> {
                freeTextNote = parsed.note
            }
            is JemmaSosChunkCodec.ParsedChunk.Sentinel -> {
                sentinelSeen = true
            }
            // 🆕 L44.16.22-hotfix2 — Rescuer chunks are routed in
            // handleResult() BEFORE reaching applyChunk(), so this
            // branch should never execute. We add it explicitly to
            // satisfy Kotlin's exhaustive-when check on the sealed
            // class (ParsedChunk.Rescuer was added in L44.16.20).
            is JemmaSosChunkCodec.ParsedChunk.Rescuer -> {
                // unreachable — see handleResult() routing
            }
        }
    }

    fun checkComplete(latest: JemmaSosChunkCodec.ParsedChunk): Boolean {
        // Two completion criteria, either is sufficient:
        //   (a) sentinel chunk received
        //   (b) all expected chunk indices [0..total-1] received
        if (sentinelSeen) return true
        if (chunkTotal > 0 && receivedIndices.size >= chunkTotal) return true
        return false
    }

    fun toPeerInfo(): PeerInfo = PeerInfo(
        sessionIdHex = sessionIdHex,
        broadcasterName = broadcasterName,
        latitude = if (hasHeader) latE6 / 1_000_000.0 else null,
        longitude = if (hasHeader) lonE6 / 1_000_000.0 else null,
        sex = sex,
        ageDecade = ageDecade,
        bloodType = bloodType,
        criticality = criticality.toInt() and 0xFF,
        flags = flags.toInt() and 0xFF,
        rssi = rssi,
        chunksReceived = receivedIndices.size,
        chunksTotal = chunkTotal,
        complete = isComplete,
        firstSeenMs = firstSeenMs,
        lastSeenMs = lastSeenMs,
        allergyCodes = allergies.toList(),
        medCodes = meds.toList(),
        conditionCodes = conditions.toList(),
        immunCodes = immunizations.toList(),
        freeTextNote = freeTextNote
    )
}

/**
 * Snapshot of a peer's reassembled state, safe to serialize and pass
 * to JS / UI thread.
 */
data class PeerInfo(
    val sessionIdHex: String,
    val broadcasterName: String,
    val latitude: Double?,
    val longitude: Double?,
    val sex: Char,
    val ageDecade: Int,
    val bloodType: String,
    val criticality: Int,
    val flags: Int,
    val rssi: Int,
    val chunksReceived: Int,
    val chunksTotal: Int,
    val complete: Boolean,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val allergyCodes: List<JemmaSosChunkCodec.CodeEntry>,
    val medCodes: List<JemmaSosChunkCodec.CodeEntry>,
    val conditionCodes: List<JemmaSosChunkCodec.CodeEntry>,
    val immunCodes: List<JemmaSosChunkCodec.CodeEntry>,
    val freeTextNote: String,
    // 🆕 L44.16.23 — exact age (Nearby endpoint encodes the full int,
    // not bucketed in decades like the BLE codec did). Use ageExact
    // when displaying. Falls back to ageDecade*10 if BLE-sourced.
    val ageExact: Int = ageDecade * 10,
    // 🆕 L44.16.23 — true if the peer hasn't been seen recently
    // (Nearby's onEndpointLost or stale timer). UI keeps showing the
    // peer with last-known data but greyed out / fade.
    val isStale: Boolean = false,
    // 🆕 L44.16.48 — Victim's spoken language (2-char ISO code, e.g.
    // "fr", "ja", "en"). Empty string if not known (BLE-sourced peer
    // or pre-L44.16.48 sender). Lets a rescuer's UI show a language
    // flag/badge on the victim card so they know to expect French
    // before approaching.
    val langCode: String = ""
) {
    /**
     * Serialize to a JSON object for postMessage to JS land.
     * The JS side will use JemmaKbBridge.resolveByCode() to turn
     * codes into local-language labels.
     */
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("sessionId", sessionIdHex)
        o.put("name", broadcasterName)
        o.put("latitude", latitude ?: JSONObject.NULL)
        o.put("longitude", longitude ?: JSONObject.NULL)
        o.put("sex", sex.toString())
        o.put("ageDecade", ageDecade)
        o.put("bloodType", bloodType)
        o.put("criticality", criticality)
        o.put("flags", flags)
        o.put("rssi", rssi)
        o.put("chunksReceived", chunksReceived)
        o.put("chunksTotal", chunksTotal)
        o.put("complete", complete)
        o.put("firstSeenMs", firstSeenMs)
        o.put("lastSeenMs", lastSeenMs)
        o.put("allergies", codesToJsonArray(allergyCodes))
        o.put("medications", codesToJsonArray(medCodes))
        o.put("conditions", codesToJsonArray(conditionCodes))
        o.put("immunizations", codesToJsonArray(immunCodes))
        o.put("note", freeTextNote)
        // 🆕 L44.16.48 — surface langCode for JS UI (radar victim card)
        if (langCode.isNotBlank()) o.put("langCode", langCode)
        return o
    }

    private fun codesToJsonArray(codes: List<JemmaSosChunkCodec.CodeEntry>): JSONArray {
        val arr = JSONArray()
        for (c in codes) {
            val o = JSONObject()
            o.put("code", c.code)
            o.put("system", c.systemName)
            if (c.severity != ' ') o.put("severity", c.severity.toString())
            if (c.status != ' ')   o.put("status", c.status.toString())
            if (c.route != ' ')    o.put("route", c.route.toString())
            arr.put(o)
        }
        return arr
    }
}


/**
 * 🆕 L44.16.20 — RescuerInfo
 *
 * Compact data carrier emitted on every rescuer beacon update. Mirrors
 * the lightweight payload sent by JemmaRescuerBroadcaster — name, lang,
 * GPS, plus runtime fields like RSSI for distance estimation.
 *
 * RescuerInfo is NOT a PeerInfo subset — they're two distinct concepts.
 * A peer is a victim broadcasting their medical SOS profile; a rescuer
 * is a helper announcing their presence. The TTS announcement engine
 * on the victim's phone listens specifically for RescuerInfo events.
 */
data class RescuerInfo(
    val sessionIdHex: String,
    val name: String,
    val langCode: String,            // "fr" / "en" / "ja"
    val latitude: Double?,           // null if unknown (lat=0,lon=0)
    val longitude: Double?,
    val rssi: Int,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    // 🆕 L44.16.23 — staleness flag for UI fade
    val isStale: Boolean = false
) {
    fun toJson(): org.json.JSONObject {
        val o = org.json.JSONObject()
        o.put("sessionId", sessionIdHex)
        o.put("name", name)
        // 🆕 L44.16.24 — emit BOTH "lang" (legacy BLE callers) AND
        // "langCode" (new — used by jemma_radar_panel.js TTS engine
        // and by jemma_sos_panel.js for the rescuer list display).
        // Without "langCode" the JS side saw `undefined` and the TTS
        // announcement defaulted to FR for everyone.
        o.put("lang", langCode)
        o.put("langCode", langCode)
        o.put("latitude", latitude ?: org.json.JSONObject.NULL)
        o.put("longitude", longitude ?: org.json.JSONObject.NULL)
        o.put("rssi", rssi)
        o.put("firstSeenMs", firstSeenMs)
        o.put("lastSeenMs", lastSeenMs)
        // 🆕 L44.16.24 — expose isStale to JS so the SOS panel can
        // grey out stale rescuer cards in its list UI.
        o.put("isStale", isStale)
        return o
    }
}

/**
 * 🆕 L44.16.20 — Internal session tracker for a rescuer beacon.
 * Mirrors PeerSession but with the rescuer-specific fields.
 */
internal class RescuerSession(
    val sessionIdHex: String,
    val firstSeenMs: Long,
    var name: String,
    var langCode: String,
    var latE6: Int,
    var lonE6: Int,
    var rssi: Int = 0,
    var lastSeenMs: Long = firstSeenMs
) {
    fun toRescuerInfo(): RescuerInfo {
        val lat = if (latE6 == 0 && lonE6 == 0) null else latE6 / 1e6
        val lon = if (latE6 == 0 && lonE6 == 0) null else lonE6 / 1e6
        return RescuerInfo(
            sessionIdHex = sessionIdHex,
            name = name,
            langCode = langCode,
            latitude = lat,
            longitude = lon,
            rssi = rssi,
            firstSeenMs = firstSeenMs,
            lastSeenMs = lastSeenMs
        )
    }
}
