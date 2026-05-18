/*
 * OcrModule.kt — JEMMA Pass · JemmaAppDemo · v2.6.1.3
 *
 * Hilt module providing a single, app-wide instance of ML Kit's
 * [TextRecognizer] tuned for Japanese (which also reads kanji, hiragana,
 * katakana AND romanji/latin on the same image).
 *
 * Why a global singleton :
 *   • Construction is non-trivial (resolves the GMS binding, allocates
 *     thread pools, hooks the optional module manager). Creating one per
 *     scan wastes 50–200 ms each time.
 *   • The same recognizer will be reused by the future
 *     PillarEncodingAssistant (paper badge / blister / Rx photo OCR
 *     during pillar encoding), the QR fallback flow, and any other
 *     surface needing OCR. One source of truth.
 *   • The associated *optional model* (~10 MB binary for JA) is
 *     downloaded by ML Kit lazily from Play Services the first time
 *     `process()` is called — and the first call THROWS an exception
 *     `Waiting for the text optional module to be downloaded`. By
 *     keeping a single recognizer + an `OcrModuleWarmer` triggered at
 *     boot, we pre-fetch the binary before the user taps SCAN, so the
 *     first real scan succeeds.
 *
 * Usage anywhere in the app :
 *   ```kotlin
 *   @Inject lateinit var textRecognizer: TextRecognizer
 *   ```
 *
 * To trigger pre-warm from outside MedScanController (e.g. the
 * Application class or the rescue boot screen), just inject
 * `OcrModuleWarmer` — its `init {}` fires `ModuleInstallClient` once.
 *
 * Log channel : JEMMA-OCR
 */
package be.heyman.android.jemmapassdemo.ai.ocr

import android.content.Context
import android.util.Log
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "JEMMA-OCR"

@Module
@InstallIn(SingletonComponent::class)
object OcrModule {

    /**
     * Single app-wide [TextRecognizer]. The Japanese recognizer is
     * multilingual : kanji, hiragana, katakana, AND romanji/latin all
     * decode on the same image with no extra cost. So one recognizer
     * covers blisters from Japan, Belgium, France, anywhere.
     */
    @Provides
    @Singleton
    fun provideTextRecognizer(): TextRecognizer {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🆕 creating singleton TextRecognizer (JA + multilingual latin)")
        return TextRecognition.getClient(
            JapaneseTextRecognizerOptions.Builder().build()
        )
    }
}

/**
 * Pre-warms the ML Kit text-recognition optional module on first
 * injection. ML Kit lazily downloads the ~10 MB JA model from Google
 * Play Services the first time `process()` is called — and that first
 * call FAILS with `MlKitException: Waiting for the text optional module
 * to be downloaded`. By installing the module via [ModuleInstall] up
 * front, we make sure the user's first real scan succeeds.
 *
 * Inject this from any surface that runs early in the app lifecycle —
 * the Application class, the rescue hub fragment, or just `MedScanController`.
 * Hilt instantiates it lazily on first inject, after which the init
 * block runs once and never again.
 *
 * The install is fire-and-forget : if it fails (no Wi-Fi, no Play
 * Services), the retry-with-backoff in `MedScanController.runOcr` will
 * give it more chances. If it succeeds, the user never even notices.
 */
@Singleton
class OcrModuleWarmer @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val textRecognizer: TextRecognizer,
) {
    init {
        prewarm()
    }

    /** Triggers a one-shot module download via Google Play Services. */
    fun prewarm() {
        val tStart = System.currentTimeMillis()
        Log.i(TAG, "[t=$tStart] 📡 prewarm · requesting ModuleInstall for text recognizer")
        try {
            val client = ModuleInstall.getClient(appContext)
            val request = ModuleInstallRequest.newBuilder()
                .addApi(textRecognizer)
                .build()
            client.installModules(request)
                .addOnSuccessListener { response ->
                    val alreadyInstalled = response.areModulesAlreadyInstalled()
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ prewarm OK · " +
                        "alreadyInstalled=$alreadyInstalled · took=${System.currentTimeMillis() - tStart}ms")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ prewarm failure (retry on first scan will recover) · " +
                        "class=${e::class.java.simpleName} · msg=${e.message}", e)
                }
        } catch (e: Throwable) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ prewarm threw synchronously · " +
                "class=${e::class.java.simpleName} · msg=${e.message}", e)
        }
    }
}
