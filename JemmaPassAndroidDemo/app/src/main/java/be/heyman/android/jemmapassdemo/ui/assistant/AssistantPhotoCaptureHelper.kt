/*
 * AssistantPhotoCaptureHelper.kt — Lot 14.5b1 (PHASE 14)
 *
 * Pipeline de capture d'image pour l'Assistant Jemma.
 *
 * 🆕 Lot 14.5b1 — UX restauré : on offre maintenant un AlertDialog
 * Material explicite avec deux entrées claires :
 *
 *   📷 Scanner un document (caméra)
 *      → ML Kit Document Scanner. Edge detection + perspective
 *        correction + multi-page natif. Idéal pour scanner une boîte
 *        de médicament ou une fiche de traitement papier sur place.
 *
 *   🖼️ Choisir depuis la galerie
 *      → ActivityResultContracts.PickMultipleVisualMedia. Multi-select
 *        natif Android (jusqu'à 20 images). Utile quand l'utilisateur
 *        a déjà ses photos en galerie (typique sur la fiche papier
 *        scannée avec Google Lens ou simple photo prise hier).
 *
 * Le Document Scanner avait théoriquement un bouton "import galerie"
 * interne via setGalleryImportAllowed(true), mais sur certains
 * téléphones (notamment Pixel récents avec Photo Picker Android 13+)
 * ce bouton n'apparaît pas visiblement. Kudoro a remonté qu'il ne le
 * voyait plus → on rajoute le chemin galerie séparément pour
 * couverture 100 percent.
 *
 * Pattern Fragment hôte (callback toujours en List<Uri>) :
 *
 *     class MedicationsEditFragment : Fragment() {
 *         private lateinit var photoHelper: AssistantPhotoCaptureHelper
 *
 *         override fun onCreate(savedInstanceState: Bundle?) {
 *             super.onCreate(savedInstanceState)
 *             photoHelper = AssistantPhotoCaptureHelper.register(this) { uris ->
 *                 ...
 *             }
 *         }
 *
 *         fun onAssistantPhotoModePicked() {
 *             photoHelper.showSourceChooser()
 *         }
 *     }
 */
package be.heyman.android.jemmapassdemo.ui.assistant

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import be.heyman.android.jemmapassdemo.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class AssistantPhotoCaptureHelper private constructor(
    private val fragment: Fragment,
    private val onImagesReady: (List<Uri>) -> Unit,
) {

    companion object {
        private const val TAG = "JEMMA-ASSISTANT-PHOTO"

        /** Limite haute du scanner + galerie (large : couvre fiche multi-pages). */
        private const val MAX_PAGES = 20

        /**
         * Enregistre les launchers + la helper. À appeler en onCreate()
         * du Fragment hôte (avant onCreateView).
         */
        fun register(fragment: Fragment, onImagesReady: (List<Uri>) -> Unit): AssistantPhotoCaptureHelper {
            val helper = AssistantPhotoCaptureHelper(fragment, onImagesReady)
            helper.registerLaunchers()
            return helper
        }
    }

    private lateinit var docScanLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var pickMultipleMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>

    private fun registerLaunchers() {
        // 1️⃣ Document Scanner pour le mode caméra.
        docScanLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val uris = scanResult?.pages?.mapNotNull { it.imageUri } ?: emptyList()
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 📄 doc scan OK · pages=${uris.size}",
                )
                if (uris.isNotEmpty()) {
                    for ((i, uri) in uris.withIndex()) {
                        Log.i(TAG, "[t=${System.currentTimeMillis()}]   page ${i + 1}: $uri")
                    }
                    onImagesReady(uris)
                }
            } else {
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🚫 doc scan cancelled · resultCode=${result.resultCode}",
                )
            }
        }

        // 2️⃣ Galerie multi-pick natif Android 13+ (Photo Picker système).
        pickMultipleMediaLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(MAX_PAGES)
        ) { uris ->
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 🖼️ gallery multi-pick · count=${uris.size}",
            )
            if (uris.isNotEmpty()) {
                for ((i, uri) in uris.withIndex()) {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}]   pick ${i + 1}: $uri")
                }
                onImagesReady(uris)
            }
        }
    }

    /**
     * Affiche un Material AlertDialog avec deux entrées claires :
     * Caméra (Document Scanner) ou Galerie (multi-pick).
     */
    fun showSourceChooser() {
        val ctx = fragment.requireContext()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] showing photo source chooser")

        val items = arrayOf(
            ctx.getString(R.string.assistant_photo_source_camera),
            ctx.getString(R.string.assistant_photo_source_gallery),
        )

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.assistant_photo_source_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> launchDocumentScanner()
                    1 -> launchGalleryMultiPick()
                }
            }
            .setNegativeButton(R.string.assistant_photo_source_cancel, null)
            .show()
    }

    private fun launchDocumentScanner() {
        val ctx = fragment.requireContext()
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] launching document scanner · maxPages=$MAX_PAGES",
        )

        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setPageLimit(MAX_PAGES)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(fragment.requireActivity())
            .addOnSuccessListener { intentSender ->
                val request = IntentSenderRequest.Builder(intentSender).build()
                docScanLauncher.launch(request)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ getStartScanIntent failed", e)
                Toast.makeText(ctx, R.string.assistant_photo_capture_failed, Toast.LENGTH_LONG).show()
            }
    }

    private fun launchGalleryMultiPick() {
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] launching gallery multi-pick · max=$MAX_PAGES",
        )
        pickMultipleMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
}
