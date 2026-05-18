/*
 * AssistantPatientPipelineFragment.kt — JEMMA Pass · Lot 14.5c35
 *
 * Patient extraction pipeline. Reuses the assistant nav graph entry
 * pattern (image URIs joined by '|' in args) but is dedicated to a
 * single-entity extraction (one JPatient), not a list of items.
 *
 * Pipeline (4 visible steps) :
 *   1. Photo · OK if the image loaded
 *   2. OCR (ML Kit text recognition, JA model multi-lingual)
 *   3. MRZ try-parse (deterministic) — pre-seeds the draft if it works
 *   4. Gemma 4 multimodal extraction via @Tools on PatientAgentTools
 *
 * After Gemma completes, we set the PatientHandoff to the draft and
 * navigate forward to PatientEditFragment with arg consumeHandoff=true,
 * which makes the form pre-fill from the draft instead of from disk.
 *
 * Works for ANY document containing patient info :
 *   - CNI / passport (MRZ helps)
 *   - business card (no MRZ, Gemma only)
 *   - hospital admission slip
 *   - insurance card
 *   - prescription header
 *
 * Log tag : JEMMA-PATIENT-PIPE.
 */
package be.heyman.android.jemmapassdemo.ui.assistant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.gemma.GemmaSession
import be.heyman.android.jemmapassdemo.ai.patient.MrzParser
import be.heyman.android.jemmapassdemo.ai.patient.MrzResult
import be.heyman.android.jemmapassdemo.ai.patient.PatientAgentTools
import be.heyman.android.jemmapassdemo.ai.patient.PatientDraft
import be.heyman.android.jemmapassdemo.ai.patient.PatientHandoff
import com.google.ai.edge.litertlm.tool
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class AssistantPatientPipelineFragment :
    Fragment(R.layout.fragment_assistant_patient_pipeline) {

    companion object {
        private const val TAG = "JEMMA-PATIENT-PIPE"
        const val ARG_IMAGE_URIS = "imageUris"
        const val ARG_PROFILE_ID = "profileId"
    }

    @Inject lateinit var textRecognizer: TextRecognizer
    @Inject lateinit var gemma: GemmaSession
    @Inject lateinit var agentTools: PatientAgentTools

    private var argProfileId: String? = null
    private var firstUri: Uri? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        argProfileId = arguments?.getString(ARG_PROFILE_ID)
        val urisStr = arguments?.getString(ARG_IMAGE_URIS)
        val uris: List<Uri> = urisStr
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?.map(Uri::parse)
            ?: emptyList()

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🤖 patient pipeline open · " +
            "profileId=$argProfileId · pages=${uris.size}")

        // Toolbar back.
        view.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            R.id.patient_pipeline_toolbar
        )?.setNavigationOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 back tap")
            findNavController().navigateUp()
        }

        // 5 steps initial state — c37 added step 0 for Gemma init.
        renderStepInitial(view, R.id.patient_step_0, R.string.assistant_patient_step_0)
        renderStepInitial(view, R.id.patient_step_1, R.string.assistant_patient_step_1)
        renderStepInitial(view, R.id.patient_step_2, R.string.assistant_patient_step_2)
        renderStepInitial(view, R.id.patient_step_3, R.string.assistant_patient_step_3)
        renderStepInitial(view, R.id.patient_step_4, R.string.assistant_patient_step_4)

        view.findViewById<View>(R.id.patient_pipeline_btn_cancel)
            ?.setOnClickListener { findNavController().navigateUp() }

        view.findViewById<View>(R.id.patient_pipeline_btn_open_form)
            ?.setOnClickListener {
                val draft = agentTools.snapshot()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 open form · filled=${draft.filledCount}")
                PatientHandoff.set(draft)
                val args = bundleOf(
                    "profileId" to argProfileId,
                    "consumeHandoff" to true,
                )
                findNavController().navigate(
                    R.id.action_patient_pipeline_to_perso,
                    args,
                )
            }

        if (uris.isEmpty()) {
            view.findViewById<View>(R.id.patient_pipeline_empty_state)?.isVisible = true
            view.findViewById<ImageView>(R.id.patient_pipeline_preview)?.isVisible = false
            Toast.makeText(requireContext(), R.string.assistant_patient_no_image, Toast.LENGTH_LONG).show()
            return
        }

        firstUri = uris[0]
        // 🆕 c37 — populate the thumbnails strip with ALL images (not just
        // the first one). The strip is a horizontal scroll view so the
        // user actually SEES every photo they captured. The first ImageView
        // (id=patient_pipeline_preview) is reused; additional ones are
        // appended programmatically.
        loadAllPreviews(view, uris)
        agentTools.reset()

        viewLifecycleOwner.lifecycleScope.launch {
            // ── Step 0 : Gemma init (c37) ───────────────────────────────
            // First-run init takes ~12s. Without this visible step the
            // user thinks the app froze. We also push the actual init
            // call to Dispatchers.IO so it doesn't block the main thread
            // (the previous version triggered an ANR on cold start).
            markStepRunning(view, R.id.patient_step_0,
                R.string.assistant_patient_step_0_running)
            val tInitStart = System.currentTimeMillis()
            try {
                withContext(Dispatchers.IO) {
                    gemma.configureForTask(
                        taskId = "patient-vision-pipeline",
                        systemPrompt = SYSTEM_PROMPT,
                        tools = listOf(tool(agentTools)),
                        supportImage = true,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ configureForTask failed", e)
                markStepError(view, R.id.patient_step_0, e.message ?: "init failed")
                return@launch
            }
            val initDt = (System.currentTimeMillis() - tInitStart) / 1000.0
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ Gemma init done in ${initDt}s")
            markStepDone(view, R.id.patient_step_0,
                getString(R.string.assistant_patient_step_0_done_fmt, initDt))

            runPipeline(view, uris)
        }
    }

    /**
     * 🆕 c37 — Load every image into the horizontal thumbnails strip.
     * The first ImageView (patient_pipeline_preview) is reused for the
     * first URI; additional URIs get fresh ImageViews appended to the
     * LinearLayout. Also shows a "n photos" count caption when N > 1.
     */
    private fun loadAllPreviews(view: View, uris: List<Uri>) {
        val strip = view.findViewById<android.widget.LinearLayout>(R.id.patient_pipeline_thumbs_strip)
        val firstIv = view.findViewById<ImageView>(R.id.patient_pipeline_preview)
        val ctx = requireContext()

        // First image into the existing ImageView.
        uris.firstOrNull()?.let { loadBitmapInto(firstIv, it) }

        // Additional images — programmatic ImageViews after the first.
        if (uris.size > 1 && strip != null) {
            val density = ctx.resources.displayMetrics.density
            val sizePx = (240 * density).toInt()
            val gapPx = (4 * density).toInt()
            for (i in 1 until uris.size) {
                val iv = ImageView(ctx).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(sizePx, sizePx).apply {
                        marginStart = gapPx
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = getString(R.string.assistant_patient_preview_desc)
                }
                loadBitmapInto(iv, uris[i])
                strip.addView(iv)
            }
        }

        // Count caption.
        if (uris.size > 1) {
            view.findViewById<TextView>(R.id.patient_pipeline_strip_count)?.apply {
                text = getString(R.string.assistant_patient_strip_count_fmt, uris.size)
                isVisible = true
            }
        }
    }

    private fun loadBitmapInto(iv: ImageView?, uri: Uri) {
        if (iv == null) return
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                iv.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ preview load failed for $uri", e)
        }
    }

    /**
     * Sequential pipeline. We do everything off the main thread for
     * the heavy bits (OCR, Gemma) — UI calls go back via withContext.
     *
     * 🆕 c37 — Now accepts a List<Uri>. Each image is OCR'd in turn and
     * the concatenated text (with markers) is fed to MRZ.tryParse and
     * to Gemma. The PRIMARY image (first one) is what Gemma sees in its
     * vision context, but Gemma reads the combined OCR text from all
     * pages, so a CNI back+front captured as two photos works correctly.
     */
    private suspend fun runPipeline(view: View, uris: List<Uri>) {
        val nImages = uris.size

        // ── Step 1 : Photo(s) loaded ───────────────────────────────────
        markStepDone(view, R.id.patient_step_1,
            if (nImages == 1) getString(R.string.assistant_patient_step_1_done)
            else getString(R.string.assistant_patient_step_1_done_fmt, nImages))

        // ── Step 2 : OCR every image, concatenate ──────────────────────
        markStepRunning(view, R.id.patient_step_2,
            R.string.assistant_patient_step_2_running)
        val ocrCombined: String = try {
            val sb = StringBuilder()
            uris.forEachIndexed { i, u ->
                if (nImages > 1) {
                    markStepRunningWithBody(view, R.id.patient_step_2,
                        getString(R.string.assistant_patient_step_2_running_fmt,
                            i + 1, nImages))
                    sb.append("=== IMAGE ${i + 1} / $nImages ===\n")
                }
                val partial = runOcr(u)
                sb.append(partial).append("\n")
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ OCR failed", e)
            markStepError(view, R.id.patient_step_2, e.message ?: "OCR error")
            return
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 OCR combined len=${ocrCombined.length} across $nImages image(s) :\n$ocrCombined")
        markStepDone(view, R.id.patient_step_2,
            if (nImages == 1) getString(R.string.assistant_patient_step_2_done_fmt, ocrCombined.length)
            else getString(R.string.assistant_patient_step_2_done_multi_fmt, ocrCombined.length, nImages))

        // ── Step 3 : MRZ try-parse (deterministic, runs on full OCR) ──
        markStepRunning(view, R.id.patient_step_3,
            R.string.assistant_patient_step_3_running)
        val mrz: MrzResult? = MrzParser.tryParse(ocrCombined)
        if (mrz != null) {
            agentTools.preSeed(
                PatientDraft(
                    familyName = mrz.familyName?.lowercase()?.replaceFirstChar { it.uppercase() },
                    givenName = mrz.givenNames?.lowercase()?.replaceFirstChar { it.uppercase() },
                    birthDate = mrz.birthDateIso,
                    gender = mrz.sex,
                    nationality = mrz.nationalityIso2,
                    identifierValue = mrz.documentNumber,
                    identifierSystem = if (mrz.documentType == "TD3") "PASSPORT" else "BE-NRN",
                )
            )
            markStepDone(view, R.id.patient_step_3,
                getString(R.string.assistant_patient_step_3_done_fmt,
                    mrz.documentType, mrz.familyName ?: "?"))
        } else {
            markStepWaitingWithBody(view, R.id.patient_step_3,
                getString(R.string.assistant_patient_step_3_skipped))
        }

        // ── Step 4 : Gemma multimodal extraction via @Tools ────────────
        markStepRunning(view, R.id.patient_step_4,
            R.string.assistant_patient_step_4_running)
        val primaryUri = uris[0]
        val bitmap: Bitmap? = withContext(Dispatchers.IO) {
            runCatching {
                requireContext().contentResolver.openInputStream(primaryUri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
        }
        if (bitmap == null) {
            markStepError(view, R.id.patient_step_4, "Bitmap decode failed")
            return
        }

        val tGemmaStart = System.currentTimeMillis()
        try {
            gemma.ask(
                prompt = buildUserPrompt(ocrCombined, mrz),
                image = bitmap,
                systemInstruction = SYSTEM_PROMPT,
                tools = listOf(tool(agentTools)),
            )
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ Gemma extraction failed", e)
            markStepError(view, R.id.patient_step_4, e.message ?: "Gemma error")
            return
        }
        val dt = System.currentTimeMillis() - tGemmaStart
        val draft = agentTools.snapshot()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ Gemma extraction done in ${dt}ms · " +
            "filled=${draft.filledCount} · " +
            "given='${draft.givenName}' family='${draft.familyName}' birth='${draft.birthDate}' " +
            "sex='${draft.gender}' nat='${draft.nationality}'")

        markStepDone(view, R.id.patient_step_4,
            getString(R.string.assistant_patient_step_4_done_fmt, draft.filledCount))

        // Auto-handoff : set the draft + enable the button.
        PatientHandoff.set(draft)
        view.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.patient_pipeline_btn_open_form
        )?.isEnabled = draft.isMinimallyComplete

        // 🆕 Lot 14.5c36 — Auto-navigate back to the form ~1.5s after the
        // extraction completes (only if the draft is minimally complete).
        // The user sees "X fields extracted ✓" for a moment, gets a Toast,
        // then is taken back to the form with consumeHandoff=true.
        // (Re-added in c38 — accidentally dropped during the c37 multi-image
        // refactor.)
        if (draft.isMinimallyComplete) {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ⏳ auto-navigate to form in 1500ms · filled=${draft.filledCount}")
            Toast.makeText(requireContext(),
                R.string.assistant_patient_auto_return_toast,
                Toast.LENGTH_SHORT).show()
            kotlinx.coroutines.delay(1500)
            if (!isAdded || view.findViewById<View>(R.id.patient_pipeline_btn_open_form) == null) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ fragment detached during auto-nav delay")
                return
            }
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🚀 auto-navigating to form (consumeHandoff=true)")
            val navArgs = bundleOf(
                "profileId" to (arguments?.getString("profileId") ?: ""),
                "consumeHandoff" to true,
                "pillarKey" to "patient",
            )
            findNavController().navigate(
                R.id.action_patient_pipeline_to_perso, navArgs
            )
        } else {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ draft NOT minimally complete · filled=${draft.filledCount} · user must tap 'Ouvrir le formulaire' manually")
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  OCR
    // ──────────────────────────────────────────────────────────────────

    private suspend fun runOcr(uri: Uri): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromFilePath(requireContext(), uri)
            textRecognizer.process(image)
                .addOnSuccessListener { result ->
                    val sb = StringBuilder()
                    result.textBlocks.forEach { block ->
                        block.lines.forEach { line ->
                            sb.append(line.text).append('\n')
                        }
                        sb.append('\n')
                    }
                    cont.resume(sb.toString())
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    // ──────────────────────────────────────────────────────────────────
    //  Step renderers
    // ──────────────────────────────────────────────────────────────────

    private fun renderStepInitial(parent: View, includeId: Int, titleRes: Int) {
        val root = parent.findViewById<View>(includeId) ?: return
        root.findViewById<TextView>(R.id.step_title)?.setText(titleRes)
        root.findViewById<TextView>(R.id.step_icon)?.text = "◯"
        root.findViewById<TextView>(R.id.step_status)
            ?.setText(R.string.assistant_step_status_waiting)
        root.findViewById<ProgressBar>(R.id.step_spinner)?.isVisible = false
        root.findViewById<TextView>(R.id.step_body)?.isVisible = false
    }

    private fun markStepRunning(parent: View, includeId: Int, statusRes: Int) {
        val root = parent.findViewById<View>(includeId) ?: return
        root.findViewById<TextView>(R.id.step_icon)?.text = "⏳"
        root.findViewById<TextView>(R.id.step_status)
            ?.setText(R.string.assistant_step_status_running)
        root.findViewById<ProgressBar>(R.id.step_spinner)?.isVisible = true
        val body = root.findViewById<TextView>(R.id.step_body)
        body?.isVisible = true
        body?.setText(statusRes)
    }

    /** 🆕 c37 — Same as markStepRunning but takes a String body (used for
     *  the per-image OCR progress "OCR · image 2 / 3…"). */
    private fun markStepRunningWithBody(parent: View, includeId: Int, body: String) {
        val root = parent.findViewById<View>(includeId) ?: return
        root.findViewById<TextView>(R.id.step_icon)?.text = "⏳"
        root.findViewById<TextView>(R.id.step_status)
            ?.setText(R.string.assistant_step_status_running)
        root.findViewById<ProgressBar>(R.id.step_spinner)?.isVisible = true
        val bodyView = root.findViewById<TextView>(R.id.step_body)
        bodyView?.isVisible = true
        bodyView?.text = body
    }

    private fun markStepDone(parent: View, includeId: Int, body: String) {
        val root = parent.findViewById<View>(includeId) ?: return
        root.findViewById<TextView>(R.id.step_icon)?.text = "✅"
        root.findViewById<TextView>(R.id.step_status)
            ?.setText(R.string.assistant_step_status_done)
        root.findViewById<ProgressBar>(R.id.step_spinner)?.isVisible = false
        val bodyView = root.findViewById<TextView>(R.id.step_body)
        bodyView?.isVisible = true
        bodyView?.text = body
    }

    private fun markStepError(parent: View, includeId: Int, body: String) {
        val root = parent.findViewById<View>(includeId) ?: return
        root.findViewById<TextView>(R.id.step_icon)?.text = "❌"
        root.findViewById<TextView>(R.id.step_status)
            ?.setText(R.string.assistant_step_status_error)
        root.findViewById<ProgressBar>(R.id.step_spinner)?.isVisible = false
        val bodyView = root.findViewById<TextView>(R.id.step_body)
        bodyView?.isVisible = true
        bodyView?.text = body
    }

    private fun markStepWaitingWithBody(parent: View, includeId: Int, body: String) {
        val root = parent.findViewById<View>(includeId) ?: return
        root.findViewById<TextView>(R.id.step_icon)?.text = "◯"
        root.findViewById<TextView>(R.id.step_status)
            ?.setText(R.string.assistant_step_status_waiting)
        root.findViewById<ProgressBar>(R.id.step_spinner)?.isVisible = false
        val bodyView = root.findViewById<TextView>(R.id.step_body)
        bodyView?.isVisible = true
        bodyView?.text = body
    }

    // ──────────────────────────────────────────────────────────────────
    //  Gemma prompts
    // ──────────────────────────────────────────────────────────────────

    private fun buildUserPrompt(ocrText: String, mrz: MrzResult?): String {
        val sb = StringBuilder()
        sb.append("Here is a photo of a document containing patient identity information. ")
        sb.append("This could be an ID card, a passport, a business card, a hospital admission slip, ")
        sb.append("a health insurance card, or any other document. Extract the PATIENT's information ")
        sb.append("by calling the available @Tool methods.\n\n")

        if (mrz != null) {
            sb.append("⚡ An MRZ zone (${mrz.documentType}) has already been parsed deterministically:\n")
            sb.append("- Family name: ${mrz.familyName ?: "(?)"} ✅ already set\n")
            sb.append("- Given names: ${mrz.givenNames ?: "(?)"} ✅ already set\n")
            sb.append("- Birth date: ${mrz.birthDateIso ?: "(?)"} ✅ already set\n")
            sb.append("- Sex: ${mrz.sex ?: "(?)"} ✅ already set\n")
            sb.append("- Nationality: ${mrz.nationalityIso2 ?: "(?)"} ✅ already set\n")
            sb.append("Do NOT call the @Tool methods for these fields again — focus on ")
            sb.append("the STILL missing fields: address, phone, email, bloodType.\n\n")
        }

        sb.append("=== EXTRACTED OCR TEXT FROM THE PHOTO ===\n")
        sb.append(ocrText)
        sb.append("\n=== END OCR ===\n\n")

        sb.append("The image itself is attached — use it to resolve OCR ambiguities ")
        sb.append("(misread characters, accents, etc.).\n\n")
        sb.append("Call finalizePatientDraft at the end.")
        return sb.toString()
    }

    private val SYSTEM_PROMPT = """
You are a patient data extraction agent for the JEMMA Pass health system.

Your job: from a document photo + OCR text, extract the patient's IPS information using the available @Tool methods.

📋 STRICT RULES:
1. NEVER write free text. Your only outputs are @Tool calls.
2. NEVER INVENT data. If a field is not visible on the photo or in the OCR, DO NOT call it at all — leave it empty.
3. Use the attached image to DISAMBIGUATE the OCR (e.g., ML Kit often confuses O/0, I/1, accents). If the photo says "François" but the OCR says "Francois", use the photo.
4. ALWAYS end with finalizePatientDraft().

🛠 AVAILABLE @Tool TOOLS:
• setGivenName(name) — first name(s) (1st + 2nd names if present, separated by space)
• setFamilyName(name) — family name
• setBirthDate(isoDate) — birth date STRICT YYYY-MM-DD (convert any format)
• setGender(M|F|O|U) — ONE single letter
• setNationality(iso2) — ISO 3166-1 alpha-2 code (BE, FR, JP, DE, NL, IT, ES, PT, GB, US, JP, CN, etc.)
• setBloodType(A+|A-|B+|B-|AB+|AB-|O+|O-|Unknown) — only if explicitly written
• setAddress(line, city, postalCode, country) — postal address, country in ISO 3166-1 alpha-2
• setPhone(phone) — phone number as printed (keep the + and spaces)
• setEmail(email) — lowercased email
• setIdentifier(value, system) — system: BE-NRN | PASSPORT | JP-MyNumber | FR-NSS | OTHER
• finalizePatientDraft() — call ONCE at the end

🌍 SUPPORTED DOCUMENTS:
- Identity card (European CNI, Belgian eID, etc.)
- Passport
- Professional business card (extract name, phone, email, employer if present in address)
- Hospital admission slip
- Health insurance card
- Prescription with patient header
- Any document with readable patient data

Think silently, then chain the @Tool calls in the order the fields appear on the document.
""".trimIndent()
}
