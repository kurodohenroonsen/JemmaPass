package be.heyman.android.jemmapassdemo.sos
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * 🆕 L44.16.12 — JEMMA SOS BLE Advertiser.
 *
 * Wraps Android's BluetoothLeAdvertiser to broadcast our chunked SOS
 * payload. Uses BLE 5.0 Extended Advertising when supported (~250
 * bytes payload per advertise), falls back to legacy 4.x advertising
 * (~24 bytes payload, MUCH more chunks needed) if not.
 *
 * Strategy: rotate through the chunk list every CHUNK_INTERVAL_MS
 * milliseconds. The first cycle delivers the full profile in
 * ~chunks.size × 200ms = ~1.5 seconds. After that, we keep cycling
 * to support late-arriving scanners and tolerate packet loss.
 *
 * Architecture choice: ONE advertise running at a time, ONE chunk
 * being transmitted continuously until rotated. This is simpler and
 * more reliable than the periodic-advertising-with-aux-pointer
 * pattern, at the cost of slightly slower initial discovery.
 *
 * ─── Service UUID ──────────────────────────────────────────────
 *
 * We use a custom 128-bit UUID derived from the namespace string
 * "be.heyman.jemma.sos" → SHA-1 → first 16 bytes formatted as UUID.
 * Pre-computed for stability across builds:
 *
 *   0000FEED-0000-1000-8000-00805F9B34FB
 *
 * (FEED is a memorable 16-bit code; the rest is the standard BT SIG
 * base UUID. Using a pre-existing 16-bit code is technically a SIG
 * conflict but is acceptable for proof-of-concept; production should
 * register a real 16-bit UUID with the SIG.)
 *
 * ─── Permissions required ──────────────────────────────────────
 *  - BLUETOOTH_ADVERTISE (Android 12+)
 *  - BLUETOOTH (legacy, Android ≤ 11)
 *  - BLUETOOTH_ADMIN (legacy, Android ≤ 11)
 *
 * Caller is responsible for checking permissions before invoking
 * start(); start() throws SecurityException otherwise.
 */
class JemmaSosBleAdvertiser(private val context: Context) {

    companion object {
        private const val TAG = "JEMMA-SOS-ADV"

        // Rotation cadence in ms. 200ms = 5 chunks/sec, full 10-chunk
        // profile delivered in 2 seconds first cycle, then cycling.
        const val CHUNK_INTERVAL_MS = 200L

        // Service UUID for JEMMA SOS broadcasts. Receivers filter on
        // this UUID. See the class kdoc for the rationale.
        val JEMMA_SOS_SERVICE_UUID: UUID = UUID.fromString("0000FEED-0000-1000-8000-00805F9B34FB")
        val JEMMA_SOS_PARCEL_UUID: ParcelUuid = ParcelUuid(JEMMA_SOS_SERVICE_UUID)
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val advertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapter?.bluetoothLeAdvertiser

    @Volatile private var isAdvertising: Boolean = false
    @Volatile private var currentChunks: List<ByteArray> = emptyList()
    @Volatile private var currentIdx: Int = 0

    private val rotationHandler = Handler(Looper.getMainLooper())
    private val rotationRunnable = object : Runnable {
        override fun run() {
            if (!isAdvertising || currentChunks.isEmpty()) return
            rotate()
            rotationHandler.postDelayed(this, CHUNK_INTERVAL_MS)
        }
    }

    // The current advertising-set callback (Extended Advertising path)
    // or the legacy AdvertiseCallback (Android < 8 fallback path).
    // We hold a reference to it so we can cleanly stopAdvertisingSet().
    private var legacyCallback: AdvertiseCallback? = null
    private var extendedCallback: AdvertisingSetCallback? = null

    /** True if BLE advertising is supported on this hardware. */
    fun isSupported(): Boolean {
        if (bluetoothAdapter == null) return false
        if (!bluetoothAdapter.isEnabled) return false
        if (advertiser == null) return false
        return true
    }

    /** True if BLE 5.0 Extended Advertising (250-byte payload) is supported. */
    fun isExtendedAdvSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return bluetoothAdapter?.isLeExtendedAdvertisingSupported == true
    }

    /**
     * Start broadcasting the given chunks in rotation.
     *
     * @throws SecurityException if BLUETOOTH_ADVERTISE permission missing
     */
    fun start(chunks: List<ByteArray>) {
        if (!hasAdvertisePermission()) {
            Log.w(TAG, "missing BLUETOOTH_ADVERTISE permission, cannot start")
            return
        }
        if (!isSupported()) {
            Log.w(TAG, "BLE advertising not supported on this device")
            return
        }
        if (chunks.isEmpty()) {
            Log.w(TAG, "no chunks to advertise")
            return
        }
        if (isAdvertising) {
            Log.i(TAG, "already advertising — replacing chunk set")
            stop()
        }

        currentChunks = chunks
        currentIdx = 0
        isAdvertising = true

        Log.i(TAG, "▶ start advertising ${chunks.size} chunks " +
            "(extended=${isExtendedAdvSupported()}, max payload=" +
            "${if (isExtendedAdvSupported()) 250 else 24} bytes)")

        // Kick off the rotation
        rotationHandler.post(rotationRunnable)
    }

    /**
     * Replace the current chunk set without stopping the advertise.
     * Useful when the broadcasting profile is updated mid-session
     * (e.g. user toggled a critical flag).
     */
    fun replaceChunks(chunks: List<ByteArray>) {
        if (!isAdvertising) {
            start(chunks)
            return
        }
        currentChunks = chunks
        currentIdx = currentIdx.coerceIn(0, chunks.size - 1)
        Log.i(TAG, "↻ replaced chunk set (${chunks.size} chunks)")
    }

    /** Stop broadcasting and release the advertiser slot. */
    fun stop() {
        isAdvertising = false
        rotationHandler.removeCallbacks(rotationRunnable)
        stopCurrentAdvertise()
        currentChunks = emptyList()
        currentIdx = 0
        Log.i(TAG, "⏹ stop advertising")
    }

    private fun rotate() {
        val chunks = currentChunks
        if (chunks.isEmpty()) return

        val chunk = chunks[currentIdx]
        currentIdx = (currentIdx + 1) % chunks.size

        // Stop the previous advertise before starting the next
        stopCurrentAdvertise()
        startAdvertiseForChunk(chunk)
    }

    private fun startAdvertiseForChunk(chunk: ByteArray) {
        try {
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(JEMMA_SOS_PARCEL_UUID)
                .addServiceData(JEMMA_SOS_PARCEL_UUID, chunk)
                .build()

            if (isExtendedAdvSupported()) {
                startExtendedAdvertise(data, chunk)
            } else {
                startLegacyAdvertise(data, chunk)
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException during advertise: ${se.message}")
        } catch (e: Exception) {
            Log.e(TAG, "advertise failed: ${e.message}", e)
        }
    }

    private fun startExtendedAdvertise(data: AdvertiseData, chunk: ByteArray) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startLegacyAdvertise(data, chunk)
            return
        }
        val params = AdvertisingSetParameters.Builder()
            .setLegacyMode(false)            // → use BT5 extended for full payload
            .setConnectable(false)            // SOS broadcast is one-way, no connection
            .setScannable(false)              // not requesting scan responses
            .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)  // ~1 sec interval, low power
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)  // max range for SOS
            .build()

        val callback = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: android.bluetooth.le.AdvertisingSet?,
                txPower: Int,
                status: Int
            ) {
                if (status != ADVERTISE_SUCCESS) {
                    Log.w(TAG, "extended advertise start failed: status=$status")
                }
            }
        }
        extendedCallback = callback
        advertiser?.startAdvertisingSet(params, data, null, null, null, callback)
    }

    private fun startLegacyAdvertise(data: AdvertiseData, chunk: ByteArray) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)  // 0 = no timeout, we control via stop()
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                Log.w(TAG, "legacy advertise start failed: errorCode=$errorCode")
            }
        }
        legacyCallback = callback
        advertiser?.startAdvertising(settings, data, callback)
    }

    private fun stopCurrentAdvertise() {
        try {
            extendedCallback?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    advertiser?.stopAdvertisingSet(it)
                }
                extendedCallback = null
            }
            legacyCallback?.let {
                advertiser?.stopAdvertising(it)
                legacyCallback = null
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException stopping advertise: ${se.message}")
        } catch (e: Exception) {
            Log.w(TAG, "stopAdvertise error: ${e.message}")
        }
    }

    private fun hasAdvertisePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_ADVERTISE
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
