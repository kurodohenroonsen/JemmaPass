/*
 * QrImportScanFragment.kt — v2.2.16.4 (was 2.3.0a)
 *
 * Scan QR live via CameraX + ML Kit BarcodeScanning. Filtre les QR
 * sur le format JEMMA (`_j2:` ou `{"_j":"1.2",...`), décode via
 * JemmaPayloadCodec, et expose le profile décodé via un AlertDialog
 * de confirmation.
 *
 * v2.2.16.4 changes :
 *   • Persistence wired : tap on "Importer" now actually saves the
 *     profile via [ProfilesRepository.saveProfile] (was a TODO log only).
 *   • @AndroidEntryPoint added so Hilt can inject the repository.
 *   • The save runs on viewLifecycleOwner.lifecycleScope (IO dispatcher
 *     internal to the repository), then navigates up.
 *   • If the save throws, error dialog ; otherwise navigate to detail.
 *
 * Architecture :
 *   1. PreviewView CameraX bind sur lifecycle owner du Fragment
 *   2. ImageAnalysis use-case avec un BarcodeScanner ML Kit (FORMAT_QR_CODE)
 *   3. À chaque frame : barcode list non vide → prendre rawValue du 1er
 *      → JemmaPayloadCodec.detectKind(text) → si UNKNOWN, ignorer
 *      → si COMPRESSED|LEGACY, freezer la frame, decode, dialog
 *
 * Le freeze évite qu'on rescan plusieurs fois le même QR pendant qu'on
 * affiche le dialog (sinon dialog stack-up infini, mauvaise UX).
 *
 * Permissions : CAMERA est déjà requise et grantée par le
 * PermissionsFragment (livraison 2.0.0). Si pour une raison X elle est
 * révoquée, on affiche un Toast et on revient en arrière.
 */
package be.heyman.android.jemmapassdemo.ui.profiles.import_qr

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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import be.heyman.android.jemmapassdemo.qr.JemmaPayloadCodec
import be.heyman.android.jemmapassdemo.qr.JemmaQrFrameAssembler
import be.heyman.android.jemmapassdemo.qr.displayName
import be.heyman.android.jemmapassdemo.qr.summaryLine
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
import kotlinx.coroutines.launch

@AndroidEntryPoint
class QrImportScanFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-QR"
    }

    @Inject lateinit var profilesRepository: ProfilesRepository

    private lateinit var previewView: PreviewView
    private lateinit var hintTextView: android.widget.TextView

    private lateinit var cameraExecutor: ExecutorService
    private val barcodeScanner: BarcodeScanner by lazy {
        // On filtre uniquement sur QR codes — on ne veut pas scanner des
        // EAN/UPC d'un emballage de médicament par accident.
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    /**
     * Bool atomique pour ignorer les frames pendant qu'on traite un QR
     * (decode + dialog en cours). Évite les dialogs stackés.
     */
    private val isProcessing = AtomicBoolean(false)

    /**
     * Memo du dernier rawValue traité, pour éviter de rescanner le même QR
     * en boucle si l'utilisateur ferme le dialog en restant pointé dessus.
     * Réinitialisé quand l'utilisateur tape "Annuler" / "Importer".
     */
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
        return inflater.inflate(R.layout.fragment_qr_import_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewView = view.findViewById(R.id.qr_preview_view)
        hintTextView = view.findViewById(R.id.qr_hint_text)
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

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ──────────────────────────────────────────────────────────────────────
    // CameraX setup
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
    // Decode + confirmation dialog
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
                        "name=${profile.displayName()} · format=${result.format}",
                )

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.qr_import_confirm_title))
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
                            append("\n\n[Format ${result.format}]")
                        },
                    )
                    .setPositiveButton(getString(R.string.qr_import_confirm_action)) { _, _ ->
                        // v2.2.16.5 : SaveResult differentiates "imported" from "updated"
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                val saveResult = profilesRepository.saveProfile(
                                    profile = profile,
                                    sourceFormat = result.format.name,
                                )
                                Log.i(
                                    TAG,
                                    "[t=${System.currentTimeMillis()}] 💾 saved profile id=${saveResult.id} name=${profile.displayName()} alreadyExisted=${saveResult.alreadyExisted}"
                                )
                                val toastMsg = if (saveResult.alreadyExisted) {
                                    getString(R.string.qr_import_updated_template, profile.displayName())
                                } else {
                                    getString(R.string.qr_import_done_template, profile.displayName())
                                }
                                Toast.makeText(
                                    requireContext(),
                                    toastMsg,
                                    Toast.LENGTH_LONG,
                                ).show()
                                // Navigate back to the list — the StateFlow will
                                // emit the new/updated profile and the list will refresh.
                                findNavController().navigateUp()
                            } catch (e: Exception) {
                                Log.e(
                                    TAG,
                                    "[t=${System.currentTimeMillis()}] ❌ saveProfile threw : ${e.message}",
                                    e,
                                )
                                Toast.makeText(
                                    requireContext(),
                                    "Erreur d'enregistrement : ${e.message}",
                                    Toast.LENGTH_LONG,
                                ).show()
                                resumeScanning()
                            }
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

    /**
     * Reset les flags pour reprendre le scan après un dialog fermé.
     * `lastProcessedRawValue` est gardé tel quel : si l'utilisateur a
     * dit "Annuler" sur un QR précis, on évite de re-prompter immédiatement
     * pour le même QR sans qu'il bouge la caméra.
     */
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
