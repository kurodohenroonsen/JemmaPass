/*
 * JemmaCompass.kt — JEMMA Pass · Plan B · v2.5.6.3
 *
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  Heading sensor wireup for the radar overlay. Reads the device's ║
 * ║  TYPE_ROTATION_VECTOR sensor (a fused gyro+accel+magnetometer    ║
 * ║  signal) and exposes the current azimuth in degrees (0..360),    ║
 * ║  where 0 = magnetic north. RadarOverlayView consumes this value  ║
 * ║  to rotate the N/S/E/W cardinal markers in real time as the      ║
 * ║  user spins the phone.                                           ║
 * ║                                                                  ║
 * ║  Previously the rescuer's selfHeadingDeg was hardcoded to `null` ║
 * ║  with a TODO marker ("L7 — compass sensor wireup"). That marker  ║
 * ║  has been outstanding since v2.5.5, which is why turning the     ║
 * ║  phone never moved the cardinal markers. Closed by v2.5.6.3.     ║
 * ║                                                                  ║
 * ║  Lifecycle :                                                     ║
 * ║    • Fragment.onResume() → compass.start()                       ║
 * ║    • Fragment.onPause()  → compass.stop()                        ║
 * ║    • The polling tick reads `compass.currentHeadingDeg` each     ║
 * ║      frame and passes it into the Snapshot.                      ║
 * ║                                                                  ║
 * ║  Sensor choice :                                                 ║
 * ║    TYPE_ROTATION_VECTOR is the fused sensor — most accurate.     ║
 * ║    Fallback to TYPE_GAME_ROTATION_VECTOR (no magneto, less drift ║
 * ║    indoors) if rotation_vector isn't available.                  ║
 * ║                                                                  ║
 * ║  Thread safety :                                                 ║
 * ║    onSensorChanged runs on the main thread by default (we use    ║
 * ║    the no-Handler overload). currentHeadingDeg is marked         ║
 * ║    @Volatile so any consumer thread can read it freely.          ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
package be.heyman.android.jemmapassdemo.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class JemmaCompass(context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "JEMMA-COMPASS"
        /** Skip pushing updates if the heading didn't move enough (saves UI work). */
        private const val DEG_EPSILON = 0.5f
    }

    private val sensorManager: SensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    /**
     * Current heading in degrees clockwise from magnetic north, range [0, 360).
     * Null until first sensor event arrives (typically < 100 ms after start).
     */
    @Volatile
    var currentHeadingDeg: Float? = null
        private set

    private var started = false
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var lastPushedDeg: Float = -1000f
    private var sampleCount = 0L

    fun start() {
        if (started) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] (already started — no-op)")
            return
        }
        val sensor = rotationVectorSensor
        if (sensor == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ no rotation vector sensor on this device — compass disabled")
            return
        }
        started = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ▶ start · sensor=${sensor.name} type=${sensor.type} ok=$started"
        )
    }

    fun stop() {
        if (!started) return
        sensorManager.unregisterListener(this)
        started = false
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛑 stop · samplesProcessed=$sampleCount")
        sampleCount = 0L
        lastPushedDeg = -1000f
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR &&
            event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR
        ) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // orientationAngles[0] = azimuth in radians, -π..+π
        // Convert to degrees in [0, 360)
        var deg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        if (deg < 0f) deg += 360f
        if (deg >= 360f) deg -= 360f

        sampleCount++

        // Push only when the heading moved enough (avoid pixel-jitter redraws)
        if (kotlin.math.abs(deg - lastPushedDeg) >= DEG_EPSILON || lastPushedDeg < -100f) {
            lastPushedDeg = deg
            currentHeadingDeg = deg
            // Light log every 100 samples to confirm activity in capture
            if (sampleCount % 100 == 0L) {
                Log.d(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🧭 heading=${"%.1f".format(deg)}° (samples=$sampleCount)"
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(
            TAG,
            "[t=${System.currentTimeMillis()}] onAccuracyChanged · sensor=${sensor?.name} accuracy=$accuracy"
        )
    }
}
