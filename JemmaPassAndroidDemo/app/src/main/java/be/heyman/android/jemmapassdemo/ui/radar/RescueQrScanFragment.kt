/*
 * RescueQrScanFragment.kt — JEMMA Pass · JemmaAppDemo · v2.5.10
 *
 * QR scanner spécifique au mode rescuer. Au scan d'un badge papier
 * d'un patient (`_j 1.2` ou `_j2:`), au lieu de l'enregistrer dans la
 * base profiles (comme QrImportScanFragment), on l'injecte dans le
 * broadcast mesh courant via `RadarController.injectExternalVictim(...)`
 * — le profile scanné apparaît alors dans la liste victims de TOUS les
 * autres rescuers à portée.
 *
 * Cas d'usage typique :
 *   • Pèlerin Henro Haru s'effondre inconsciente, son téléphone est mort
 *   • Kamekichi (rescuer) scanne son badge papier QR `_j 1.2`
 *   • Le mesh re-broadcast Haru → autres rescuers la voient apparaître
 *     avec ses allergies + médicaments + conditions complètes
 *
 * Architecture (verbatim ported from QrImportScanFragment v2.2.16.4) :
 *   1. PreviewView CameraX bind sur lifecycle owner du Fragment
 *   2. ImageAnalysis use-case avec un BarcodeScanner ML Kit (FORMAT_QR_CODE)
 *   3. À chaque frame : barcode list non vide → 1er rawValue
 *      → JemmaPayloadCodec.detectKind(text) → si UNKNOWN, ignore
 *      → si COMPRESSED|LEGACY, freeze la frame, decode, dialog
 *
 * Différence clé vs QrImportScanFragment :
 *   • Au bouton "Diffuser dans le mesh" → appelle
 *     `radar.injectExternalVictim(rawJson)` au lieu de
 *     `profilesRepository.saveProfile(...)`
 *   • navigateUp() retourne vers le RadarFragment (la victime apparaîtra
 *     dans sa liste au prochain tick 1Hz)
 *
 * Permissions : CAMERA est déjà requise + grantée par PermissionsFragment.
 *
 * Log channel : JEMMA-QR-RESCUE
 */
package be.heyman.android.jemmapassdemo.ui.radar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.qr.JemmaPayloadCodec
import be.heyman.android.jemmapassdemo.qr.JemmaQrFrameAssembler
import be.heyman.android.jemmapassdemo.qr.displayName
import be.heyman.android.jemmapassdemo.qr.summaryLine
import be.heyman.android.jemmapassdemo.radar.RadarController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class RescueQrScanFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-QR-RESCUE"
    }

    @Inject lateinit var radar: RadarController

    private lateinit var previewView: PreviewView
    private lateinit var hintTextView: android.widget.TextView

    private lateinit var cameraExecutor: ExecutorService
    private val barcodeScanner: BarcodeScanner by lazy {
        // QR codes only — pas de risque de scanner par accident un EAN/UPC.
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    private val isProcessing = AtomicBoolean(false)
    private var lastProcessedRawValue: String? = null

    /**
     * Multi-frame QR (`JF:i/N|…`, see JemmaQrFrameSplitter) : frames are collected
     * in any order until the set is complete, then the joined text is decoded like
     * a single QR. Main-thread only (ML Kit listeners).
     */
    private val frameAssembler = JemmaQrFrameAssembler()

    /** Last multi-frame payload already shown in a dialog — a looping slideshow must not re-prompt. */
    private var lastJoinedPayload: String? = null

    /** Hint shown before any frame progress, restored when scanning resumes. */
    private var initialHint: CharSequence = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView")
        // Réutilise le même layout que QrImportScanFragment — PreviewView + hint
        return inflater.inflate(R.layout.fragment_qr_import_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewView = view.findViewById(R.id.qr_preview_view)
        hintTextView = view.findViewById(R.id.qr_hint_text)

        // 🆕 v2.5.10 — Adapter le hint pour le contexte rescue
        hintTextView.text = getString(R.string.rescue_qr_hint)
        initialHint = hintTextView.text

        if (!hasCameraPermission()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ camera permission missing — going back")
            Toast.makeText(
                requireContext(),
                getString(R.string.qr_import_perm_missing),
                Toast.LENGTH_LONG,
            ).show()
            findNavController().navigateUp()
            return
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    // ──────────────────────────────────────────────────────────────────────
    // CameraX setup (verbatim from QrImportScanFragment)
    // ──────────────────────────────────────────────────────────────────────

    private fun startCamera() {
        val ctx = requireContext()
        val providerFuture = ProcessCameraProvider.getInstance(ctx)
        providerFuture.addListener(
            {
                val cameraProvider = providerFuture.get()

                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy)
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 📷 camera bound")
                } catch (e: Exception) {
                    Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ camera bind failed", e)
                    Toast.makeText(ctx, getString(R.string.error_camera_unavailable), Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
            },
            ContextCompat.getMainExecutor(ctx),
        )
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: androidx.camera.core.ImageProxy) {
        if (isProcessing.get()) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(mediaImage, rotation)

        barcodeScanner.process(input)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isEmpty()) return@addOnSuccessListener

                val raw = barcodes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                if (raw == lastProcessedRawValue) return@addOnSuccessListener

                // Part of a multi-frame payload : collect it, decode once complete.
                if (JemmaQrFrameAssembler.isFrame(raw)) {
                    lastProcessedRawValue = raw
                    onFrameScanned(raw)
                    return@addOnSuccessListener
                }

                val kind = JemmaPayloadCodec.detectKind(raw)
                if (kind == JemmaPayloadCodec.Format.UNKNOWN) {
                    return@addOnSuccessListener
                }

                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🎯 JEMMA QR detected · kind=$kind · len=${raw.length}",
                )

                if (!isProcessing.compareAndSet(false, true)) {
                    return@addOnSuccessListener
                }
                lastProcessedRawValue = raw

                view?.post {
                    handleDecodedPayload(raw)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ barcode scan failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Decode + confirmation dialog → 🆕 v2.5.10 inject into mesh broadcast
    // ──────────────────────────────────────────────────────────────────────

    /**
     * One `JF:i/N|…` frame was scanned. Shows "🧩 received / total · … missing"
     * in the hint (language-neutral) and hands the joined payload to
     * [handleDecodedPayload] when the last missing frame arrives. A partial set is
     * never decoded.
     */
    private fun onFrameScanned(raw: String) {
        if (!isAdded || view == null || isProcessing.get()) return
        when (val r = frameAssembler.offer(raw)) {
            is JemmaQrFrameAssembler.Result.NotAFrame -> Unit
            is JemmaQrFrameAssembler.Result.Progress -> {
                if (!r.duplicate) {
                    Log.i(
                        TAG,
                        "[t=${System.currentTimeMillis()}] 🧩 frame ${r.received}/${r.total} · missing=${r.missing}" +
                            if (r.restarted) " · new set" else "",
                    )
                }
                val more = if (r.missing.size > 8) " …" else ""
                hintTextView.text = "🧩 ${r.received} / ${r.total}  ·  … ${r.missing.take(8).joinToString(" ")}$more"
            }
            is JemmaQrFrameAssembler.Result.Complete -> {
                hintTextView.text = "🧩 ${r.total} / ${r.total} ✓"
                if (r.payload == lastJoinedPayload) return
                if (!isProcessing.compareAndSet(false, true)) return
                lastJoinedPayload = r.payload
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🧩 ${r.total} frames joined · len=${r.payload.length}")
                handleDecodedPayload(r.payload)
            }
        }
    }

    private fun handleDecodedPayload(rawText: String) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔍 decoding payload (len=${rawText.length})")

        when (val result = JemmaPayloadCodec.decode(rawText)) {
            is JemmaPayloadCodec.DecodeResult.Failure -> {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ decode failed : ${result.reason}", result.cause)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.qr_import_error_title))
                    .setMessage(getString(R.string.qr_import_error_message_template, result.reason))
                    .setPositiveButton(android.R.string.ok) { d, _ ->
                        d.dismiss()
                        resumeScanning()
                    }
                    .setOnCancelListener { resumeScanning() }
                    .show()
            }

            is JemmaPayloadCodec.DecodeResult.Success -> {
                val profile = result.profile
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ✅ decoded · " +
                        "name=${profile.displayName()} · sid=${profile.sid ?: "(none)"} · format=${result.format}",
                )

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.rescue_qr_confirm_title))
                    .setMessage(
                        buildString {
                            append("👤 ${profile.displayName()}\n\n")
                            append(profile.summaryLine(requireContext()))
                            profile.p?.bd?.takeIf { it.isNotBlank() }?.let {
                                val locale = resources.configuration.locales[0]
                                val bornLabel = when (locale.language.lowercase()) {
                                    "ja" -> "生年月日"
                                    "fr" -> "Né(e) le"
                                    else -> "Born on"
                                }
                                append("\n\n📅 $bornLabel $it")
                            }
                            profile.p?.bt?.takeIf { it.isNotBlank() }?.let {
                                val locale = resources.configuration.locales[0]
                                val bloodLabel = when (locale.language.lowercase()) {
                                    "ja" -> "血液型"
                                    "fr" -> "Groupe sanguin"
                                    else -> "Blood type"
                                }
                                append("\n🩸 $bloodLabel $it")
                            }
                            append("\n\n")
                            append(getString(R.string.rescue_qr_confirm_relay_explain))
                            append("\n\n[Format ${result.format}]")
                        },
                    )
                    .setPositiveButton(getString(R.string.rescue_qr_confirm_action)) { _, _ ->
                        // 🆕 v2.5.10 — inject into mesh broadcast (ported HTML
                        // nearbyStartHaruRelay). The rawJson is the decoded
                        // _j 1.2 JSON string ready for JSONObject parsing.
                        val tNow = System.currentTimeMillis()
                        Log.i(TAG, "[t=$tNow] 🪪 injecting external victim · " +
                            "sid=${profile.sid} · ${result.rawJson.length} chars")
                        val res = radar.injectExternalVictim(result.rawJson)
                        Log.i(TAG, "[t=$tNow]   ↳ injectExternalVictim → '$res'")
                        if (res == "ok") {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.rescue_qr_relayed_template, profile.displayName()),
                                Toast.LENGTH_LONG,
                            ).show()
                            findNavController().navigateUp()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.rescue_qr_relay_error_template, res),
                                Toast.LENGTH_LONG,
                            ).show()
                            resumeScanning()
                        }
                    }
                    .setNegativeButton(getString(R.string.qr_import_cancel_action)) { d, _ ->
                        d.dismiss()
                        resumeScanning()
                    }
                    .setOnCancelListener { resumeScanning() }
                    .show()
            }
        }
    }

    /** Reset flags to resume scanning after dialog dismissed. */
    private fun resumeScanning() {
        isProcessing.set(false)
        frameAssembler.reset()
        if (this::hintTextView.isInitialized) hintTextView.text = initialHint
        Log.d(TAG, "[t=${System.currentTimeMillis()}] ▶️ scanning resumed")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (this::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }
}
