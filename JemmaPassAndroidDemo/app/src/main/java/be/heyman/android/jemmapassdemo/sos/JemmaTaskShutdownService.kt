/*
 * JemmaTaskShutdownService.kt — JEMMA Pass · JemmaAppDemo · v2.6.2a.3
 *
 * Pourquoi ce service.
 *   `JemmaNearbySosService` n'est PAS un `android.app.Service`, c'est une
 *   classe Kotlin instanciée par Hilt. Quand l'utilisateur swipe l'app
 *   depuis la task list (= "Remove task"), Android ne nous appelle nulle
 *   part : Application.onCreate ne fire pas onDestroy, MainActivity peut
 *   être finished mais le process est gardé en vie tant qu'il y a des
 *   threads ou des binding GMS actifs. Résultat : les broadcasts Nearby
 *   Connections continuent à émettre alors que l'utilisateur croit avoir
 *   fermé l'app.
 *
 *   Solution Android idiomatique : un mini `Service` qui s'attache au
 *   task de l'Activity, et override `onTaskRemoved`. C'est le SEUL hook
 *   fiable que le framework appelle systématiquement quand l'utilisateur
 *   swipe l'app, peu importe l'état du process.
 *
 *   Dans ce hook, on appelle `RadarController.sosStop()` et `radarStop()`
 *   pour fermer proprement le Nearby Connections client (qui à son tour
 *   appelle GMS `stopAllEndpoints / stopAdvertising / stopDiscovery`),
 *   PUIS on `stopSelf()` pour laisser le process mourir normalement.
 *
 * Comment l'activer.
 *   1. Déclarer le service dans AndroidManifest.xml :
 *
 *        <service
 *            android:name=".sos.JemmaTaskShutdownService"
 *            android:exported="false"
 *            android:stopWithTask="false" />
 *
 *      `stopWithTask=false` est ESSENTIEL — sinon Android arrête le
 *      service AVANT d'appeler onTaskRemoved, et notre hook ne fire pas.
 *
 *   2. `MainActivity.onCreate` (déjà patché dans la même livraison)
 *      démarre le service via `startService(Intent(this, JemmaTaskShutdownService::class.java))`.
 *      On le lance avec un startService (pas bindService) pour qu'il
 *      reste attaché au task même si MainActivity est paused/stopped.
 *
 *   3. Le service est complètement passif tant que onTaskRemoved n'est
 *      pas appelé. Pas de notification, pas de foreground service,
 *      aucun coût RAM. Il existe juste pour que `onTaskRemoved` soit
 *      câblé.
 *
 * Log channel : JEMMA-SHUTDOWN
 */
package be.heyman.android.jemmapassdemo.sos

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import be.heyman.android.jemmapassdemo.radar.RadarController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

private const val TAG = "JEMMA-SHUTDOWN"

class JemmaTaskShutdownService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ShutdownEntryPoint {
        fun radarController(): RadarController
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🆕 JemmaTaskShutdownService onCreate · " +
            "ready to handle task removal")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] · onStartCommand · " +
            "intent=$intent flags=$flags startId=$startId")
        // We're a passive shutdown hook — START_STICKY ensures Android
        // restarts us if it kills the service while the app is alive
        // (rare but possible under memory pressure). The next task
        // removal will then still be heard.
        return START_STICKY
    }

    /**
     * Called when the user swipes the app from the recent tasks list.
     * THIS is the hook that lets us clean up Nearby Connections
     * advertising/discovery BEFORE the process is killed.
     *
     * Important : we have ~5 seconds of grace time here before the
     * system forcibly kills the process. That's plenty for the GMS
     * stop IPC calls (each ~50-200ms), but we still do them on the
     * main thread for predictability — the bookkeeping in
     * JemmaNearbySosService.fullStop is non-blocking (fire-and-forget
     * IPC).
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val t = System.currentTimeMillis()
        Log.i(TAG, "[t=$t] 🛑 onTaskRemoved — app swiped from recents · " +
            "rootIntent=$rootIntent")
        try {
            val ep = EntryPointAccessors.fromApplication(
                applicationContext,
                ShutdownEntryPoint::class.java,
            )
            val radar = ep.radarController()
            val wasSosActive = radar.sosIsActive()
            val wasRadarActive = radar.radarIsActive()
            Log.i(TAG, "[t=${System.currentTimeMillis()}]   state at swipe : " +
                "sosActive=$wasSosActive · radarActive=$wasRadarActive")
            if (wasSosActive) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}]   ⏹ stopping SOS")
                radar.sosStop()
            }
            if (wasRadarActive) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}]   ⏹ stopping radar")
                radar.radarStop()
            }
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ shutdown clean · " +
                "elapsed=${System.currentTimeMillis() - t}ms")
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ shutdown threw : ${e.message}", e)
        } finally {
            // Stop the service itself — we've done our job.
            stopSelf()
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💤 JemmaTaskShutdownService onDestroy")
        super.onDestroy()
    }

    companion object {
        /**
         * Helper to start the service from MainActivity.onCreate.
         * Calls startService — NOT bindService — so the service stays
         * attached to the task lifecycle, not the activity lifecycle.
         */
        fun ensureStarted(context: Context) {
            val intent = Intent(context, JemmaTaskShutdownService::class.java)
            try {
                context.startService(intent)
                Log.d(TAG, "[t=${System.currentTimeMillis()}] · ensureStarted · service intent fired")
            } catch (e: Exception) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] · ensureStarted threw : ${e.message}")
            }
        }
    }
}
