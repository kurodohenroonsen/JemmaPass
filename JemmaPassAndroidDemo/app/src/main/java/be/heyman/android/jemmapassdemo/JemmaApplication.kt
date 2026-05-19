/*
 * JemmaApplication.kt — Application class for JemmaAppDemo.
 *
 * v2.2.0 update : auto-triggers the KnowledgeBaseManager validation on
 * boot if the knowledge_full.db is already on disk. The manager is
 * @Inject-ed by Hilt ; the @AndroidEntryPoint annotation isn't needed
 * on Application classes (HiltAndroidApp is enough), but to access the
 * injected singleton we go through EntryPointAccessors.
 *
 * 🆕 v2.5.1 update — L2 Radar/SOS controller wiring.
 *   The RadarController @Singleton is the façade over the `sos/` + `mesh/`
 *   runtime ported in L1. We force its init by fetching it via the
 *   EntryPoint at boot ; without this, Hilt would only construct it
 *   lazily on first @Inject from a UI surface (which doesn't exist yet
 *   until L3) and we'd have no boot-time signal that the wiring works.
 *   Look for `JEMMA-RADAR · 🆕 RadarController wired (v2.5.1)` in logcat.
 *
 * Boot flow :
 *   1. Application.onCreate()
 *   2. Logs JEMMA-APP boot
 *   3. Triggers kbManager.ensureInitialized() in the background
 *      (no UI blocking ; SettingsFragment will observe state).
 *   4. 🆕 v2.5.1 — Forces RadarController instantiation, which logs
 *      its `init { … }` block to confirm Hilt wiring is healthy.
 */
package be.heyman.android.jemmapassdemo

import android.app.Application
import android.util.Log
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseManager
import be.heyman.android.jemmapassdemo.radar.RadarController
import be.heyman.android.jemmapassdemo.ai.ocr.OcrModuleWarmer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class JemmaApplication : Application() {

    companion object {
        private const val TAG = "JEMMA-APP"
        const val APP_VERSION = "2.5.1-radar-controller"
    }

    /**
     * EntryPoint to fetch Hilt singletons from inside Application.onCreate
     * (where field-level @Inject is not possible on Application).
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface JemmaAppEntryPoint {
        fun knowledgeBaseManager(): KnowledgeBaseManager
        // 🆕 v2.5.1 — RadarController exposed so Application.onCreate
        // can force its instantiation at boot for runtime diagnostics.
        fun radarController(): RadarController
        // 🆕 Force OcrModuleWarmer instantiation at boot to trigger optional module pre-warm
        fun ocrModuleWarmer(): OcrModuleWarmer
    }

    override fun onCreate() {
        super.onCreate()
        val tBoot = System.currentTimeMillis()
        Log.i(TAG, "[t=$tBoot] 🚀 boot · version=$APP_VERSION")

        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                this,
                JemmaAppEntryPoint::class.java,
            )

            // ─── KB validation (existing v2.2.0 behavior) ─────────────
            entryPoint.knowledgeBaseManager().ensureInitialized()
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 KB ensureInitialized triggered")

            // ─── 🆕 Warm up ML Kit OCR optional module ──────────────────────
            val ocrWarmer = entryPoint.ocrModuleWarmer()
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔤 OcrModuleWarmer resolved, prewarm initiated")

            // ─── 🆕 v2.5.1 — Force RadarController init ──────────────
            // Resolving the singleton runs its init { … } block. The
            // controller itself doesn't start any radar / SOS work — it
            // just announces readiness. Actual broadcast/discovery only
            // begins when the L3 native UI calls sosStart() / radarStart().
            val radar = entryPoint.radarController()
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 📡 RadarController resolved · " +
                    "sosActive=${radar.sosIsActive()} · radarActive=${radar.radarIsActive()}",
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ Hilt entrypoint resolution failed : ${e.message}",
            )
        }
    }
}
