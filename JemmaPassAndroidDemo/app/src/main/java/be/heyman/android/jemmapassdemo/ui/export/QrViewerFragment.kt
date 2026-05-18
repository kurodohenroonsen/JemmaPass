/*
 * QrViewerFragment.kt — Lot 14.2 (PHASE 14)
 *
 * Affichage QR du profile actif avec les 3 channels du HTML legacy :
 *   • PRUNED (`_j2:<base64>`) — Channel 1 (depuis Lot 14.1)
 *   • TEXT   (texte multilingue FR/EN/JA) — Channel 2
 *   • FHIR   (R4 Bundle, slideshow multi-frame) — Channel 3
 *
 * Pipeline (refactor Lot 14.2) :
 *
 *   1. loadProfile(id) → JemmaProfileJ
 *   2. hydrate(profile, uiLang) → HydratedProfile (KB displays, atc, etc.)
 *      ⇒ partagé pour les 3 channels (= un seul roundtrip KB)
 *   3. Selon le tab actif :
 *        - PRUNED : JemmaPayloadCodec.encode(rawProfile)
 *        - TEXT   : JemmaTextPayloadBuilder.build(hydrated, lang)
 *        - FHIR   : JemmaFhirBundleBuilder.build(hydrated)
 *   4. Split en frames via JemmaQrFrameSplitter
 *   5. Rendu QR via JemmaQrBitmapEncoder (frame courante)
 *
 * Slideshow auto-play pour FHIR (port de `_renderer.isPlaying` du JS) :
 *   • Handler postDelayed(SLIDESHOW_INTERVAL_MS = 1500ms)
 *   • Wrap-around à la fin (frame N → frame 1)
 *   • Toggle via bouton ▶/⏸
 *   • Auto-démarré quand FHIR tab est sélectionné
 *   • Auto-stoppé sur navigation ou tab switch
 *
 * Copy clipboard (menu_qr_copy) :
 *   • Copie le `rawString` actif (PRUNED string / TEXT string / FHIR JSON)
 *   • Affiche un Toast "📋 Copié — N caractères"
 *   • Utilise ClipboardManager Android — pas de permission requise
 */
package be.heyman.android.jemmapassdemo.ui.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.FragmentQrViewerBinding
import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.kb.JemmaProfileHydrator
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaPayloadCodec
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.qr.JemmaQrBitmapEncoder
import be.heyman.android.jemmapassdemo.qr.JemmaQrFrameSplitter
import be.heyman.android.jemmapassdemo.qr.JemmaTextPayloadBuilder
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class QrViewerFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-QR"

        const val ARG_PROFILE_ID = "profileId"
        const val ARG_FORMAT_KEY = "formatKey"

        const val FORMAT_PRUNED = "pruned"
        const val FORMAT_TEXT = "text"
        const val FORMAT_FHIR = "fhir"

        /** Intervalle slideshow FHIR (ms). Matche `_renderer.interval` du JS. */
        private const val SLIDESHOW_INTERVAL_MS = 1500L
    }

    /** Channel actif. Encapsule l'état (current tab + lang). */
    private enum class Channel { PRUNED, TEXT, FHIR }

    /**
     * 🆕 Lot 14.2c — config QR par channel.
     *
     * Le HTML legacy avait `qrConfig: { errorCorrection: 'M'|'L', margin: 2 }`
     * stocké par channel. On reproduit ici + on ajoute les seuils
     * frame splitter parce que côté Android ZXing core a un decoder
     * moins tolérant que les WebView (= il faut être plus conservatif
     * sur la densité).
     */
    private data class QrConfig(
        /** Seuil au-delà duquel on passe en multi-frame. */
        val maxSingleChars: Int,
        /** Taille de chunk en multi-frame. */
        val frameChunkChars: Int,
        /** Error correction ZXing. M = 15 % redundancy, L = 7 %. */
        val errorCorrection: ErrorCorrectionLevel,
    )

    /** Configs par channel. FHIR est volontairement plus conservatif. */
    private val configByChannel: Map<Channel, QrConfig> = mapOf(
        Channel.PRUNED to QrConfig(
            maxSingleChars = JemmaQrFrameSplitter.QR_MAX_SINGLE,
            frameChunkChars = JemmaQrFrameSplitter.QR_FRAME_CHUNK,
            errorCorrection = ErrorCorrectionLevel.M,
        ),
        Channel.TEXT to QrConfig(
            maxSingleChars = JemmaQrFrameSplitter.QR_MAX_SINGLE,
            frameChunkChars = JemmaQrFrameSplitter.QR_FRAME_CHUNK,
            errorCorrection = ErrorCorrectionLevel.M,
        ),
        Channel.FHIR to QrConfig(
            maxSingleChars = JemmaQrFrameSplitter.QR_MAX_SINGLE_FHIR,
            frameChunkChars = JemmaQrFrameSplitter.QR_FRAME_CHUNK_FHIR,
            errorCorrection = ErrorCorrectionLevel.L,
        ),
    )

    @Inject lateinit var repository: ProfilesRepository
    @Inject lateinit var hydrator: JemmaProfileHydrator

    private var _binding: FragmentQrViewerBinding? = null
    private val binding get() = _binding!!

    private var profileId: String? = null

    /** Profile brut (chargé une fois, partagé entre channels). */
    private var rawProfile: JemmaProfileJ? = null

    /** Profile hydraté (résolu KB une fois, partagé). */
    private var hydratedProfile: HydratedProfile? = null

    /** Channel courant (default = PRUNED). */
    private var currentChannel: Channel = Channel.PRUNED

    /** Langue courante (seulement pertinent quand Channel.TEXT). */
    private var currentLang: JemmaTextPayloadBuilder.Lang = JemmaTextPayloadBuilder.Lang.FR

    /** Frames de la rendition actuelle (channel × lang). */
    private var frames: List<String> = emptyList()

    /** Index de la frame courante (0-based). */
    private var currentFrameIndex: Int = 0

    /** Raw string complet du channel actuel (pour copy clipboard). */
    private var rawPayloadCurrent: String? = null

    /** Bytes du payload actuel (pour le format chip). */
    private var byteSizeCurrent: Int = 0

    /** Format effectif du PRUNED (compressed / legacy). Null pour autres. */
    private var prunedFormatCurrent: JemmaPayloadCodec.Format? = null

    /** Slideshow play/pause state (FHIR only). */
    private var isPlaying: Boolean = false

    private val slideshowHandler = Handler(Looper.getMainLooper())
    private val slideshowRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying || frames.isEmpty()) return
            val next = (currentFrameIndex + 1) % frames.size
            showFrame(next, autoLoop = true)
            slideshowHandler.postDelayed(this, SLIDESHOW_INTERVAL_MS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentQrViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        profileId = arguments?.getString(ARG_PROFILE_ID)
        val initialFormatKey = arguments?.getString(ARG_FORMAT_KEY) ?: FORMAT_PRUNED
        currentChannel = channelFromKey(initialFormatKey)
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 📤 onViewCreated · profileId=$profileId · initialChannel=$currentChannel",
        )

        setupToolbar()
        setupTabs()
        setupLangChips()
        setupFrameNav()
        setupStubButtons()

        val pid = profileId
        if (pid.isNullOrBlank()) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ profileId missing in arguments")
            Toast.makeText(requireContext(), R.string.qr_error_no_profile, Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return
        }

        loadAndHydrate(pid)
    }

    override fun onDestroyView() {
        stopSlideshow()
        super.onDestroyView()
        _binding = null
    }

    override fun onPause() {
        stopSlideshow()
        super.onPause()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Wiring
    // ──────────────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.qrToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.qrToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_qr_copy -> {
                    copyCurrentToClipboard()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTabs() {
        // Sélection initiale.
        val initialTabId = when (currentChannel) {
            Channel.PRUNED -> R.id.qr_tab_pruned
            Channel.TEXT -> R.id.qr_tab_text
            Channel.FHIR -> R.id.qr_tab_fhir
        }
        binding.qrChannelTabs.check(initialTabId)

        binding.qrChannelTabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newChannel = when (checkedId) {
                R.id.qr_tab_pruned -> Channel.PRUNED
                R.id.qr_tab_text -> Channel.TEXT
                R.id.qr_tab_fhir -> Channel.FHIR
                else -> return@addOnButtonCheckedListener
            }
            if (newChannel == currentChannel) return@addOnButtonCheckedListener
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 🔀 channel switch ${currentChannel} → $newChannel",
            )
            currentChannel = newChannel
            stopSlideshow()
            renderActiveChannel()
        }
    }

    private fun setupLangChips() {
        binding.qrLangFr.isChecked = currentLang == JemmaTextPayloadBuilder.Lang.FR
        binding.qrLangEn.isChecked = currentLang == JemmaTextPayloadBuilder.Lang.EN
        binding.qrLangJa.isChecked = currentLang == JemmaTextPayloadBuilder.Lang.JA

        // ChipGroup avec singleSelection=true → un seul check à la fois,
        // mais on doit aussi forcer qu'il y en ait toujours UN coché. Sinon
        // un tap retire la sélection sans la remettre. On gère via guard.
        val chipListener = { newLang: JemmaTextPayloadBuilder.Lang ->
            if (newLang != currentLang) {
                currentLang = newLang
                Log.d(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🌐 lang switch → $newLang",
                )
                if (currentChannel == Channel.TEXT) renderActiveChannel()
            }
        }
        binding.qrLangFr.setOnClickListener {
            if (!binding.qrLangFr.isChecked) binding.qrLangFr.isChecked = true
            chipListener(JemmaTextPayloadBuilder.Lang.FR)
        }
        binding.qrLangEn.setOnClickListener {
            if (!binding.qrLangEn.isChecked) binding.qrLangEn.isChecked = true
            chipListener(JemmaTextPayloadBuilder.Lang.EN)
        }
        binding.qrLangJa.setOnClickListener {
            if (!binding.qrLangJa.isChecked) binding.qrLangJa.isChecked = true
            chipListener(JemmaTextPayloadBuilder.Lang.JA)
        }
    }

    private fun setupFrameNav() {
        binding.qrBtnPrevFrame.setOnClickListener {
            stopSlideshow()
            showFrame(currentFrameIndex - 1)
        }
        binding.qrBtnNextFrame.setOnClickListener {
            stopSlideshow()
            showFrame(currentFrameIndex + 1)
        }
        binding.qrBtnPlayPause.setOnClickListener {
            if (isPlaying) stopSlideshow() else startSlideshow()
        }
    }

    private fun setupStubButtons() {
        binding.qrBtnSave.setOnClickListener {
            val frame = frames.getOrNull(currentFrameIndex) ?: return@setOnClickListener
            val cfg = configByChannel.getValue(currentChannel)
            viewLifecycleOwner.lifecycleScope.launch {
                val bmp = withContext(Dispatchers.Default) {
                    JemmaQrBitmapEncoder.encode(content = frame, sizePx = 800, errorCorrection = cfg.errorCorrection)
                }
                if (bmp != null) {
                    saveBitmapToGallery(bmp)
                } else {
                    Toast.makeText(requireContext(), R.string.qr_save_bitmap_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.qrBtnShare.setOnClickListener {
            val raw = rawProfile ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                be.heyman.android.jemmapassdemo.qr.JemmaPdfExporter.exportPdf(requireContext(), raw, hydrator)
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "jemma_qr_${System.currentTimeMillis()}.png"
        val resolver = requireContext().contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/JemmaPass")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri == null) {
            Toast.makeText(requireContext(), R.string.qr_save_file_failed, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            resolver.openOutputStream(imageUri).use { out ->
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            Toast.makeText(requireContext(), R.string.qr_save_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save QR bitmap", e)
            Toast.makeText(requireContext(), getString(R.string.qr_save_write_failed, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Load + hydrate (once)
    // ──────────────────────────────────────────────────────────────────────

    private fun loadAndHydrate(pid: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val tLoad0 = System.currentTimeMillis()
            val profile = repository.loadProfile(pid)
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 📂 loadProfile($pid) → ${if (profile == null) "null" else "ok"}" +
                    " in ${System.currentTimeMillis() - tLoad0}ms",
            )
            if (profile == null) {
                Toast.makeText(requireContext(), R.string.qr_error_load_failed, Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
                return@launch
            }
            rawProfile = profile

            val uiLang = Locale.getDefault().language.lowercase().take(2).ifBlank { "en" }
            val tHyd0 = System.currentTimeMillis()
            val hydrated = withContext(Dispatchers.IO) { hydrator.hydrate(profile, uiLang = uiLang) }
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 🧪 hydrate ok in ${System.currentTimeMillis() - tHyd0}ms" +
                    " · al=${hydrated.allergies.size} md=${hydrated.medications.size} cn=${hydrated.conditions.size}",
            )
            hydratedProfile = hydrated

            renderActiveChannel()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Render the active channel
    // ──────────────────────────────────────────────────────────────────────

    private fun renderActiveChannel() {
        val raw = rawProfile ?: return
        val hydrated = hydratedProfile ?: return

        // Toggle visibility du lang selector (Text only).
        binding.qrLangChips.isVisible = (currentChannel == Channel.TEXT)

        viewLifecycleOwner.lifecycleScope.launch {
            val (payload, chip) = withContext(Dispatchers.IO) {
                when (currentChannel) {
                    Channel.PRUNED -> buildPruned(raw)
                    Channel.TEXT -> {
                        val targetHydrated = hydrator.hydrate(raw, uiLang = currentLang.isoCode)
                        buildText(targetHydrated, currentLang)
                    }
                    Channel.FHIR -> buildFhir(hydrated)
                }
            }
            if (payload == null) {
                Toast.makeText(
                    requireContext(),
                    R.string.qr_error_bitmap_failed,
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            rawPayloadCurrent = payload

            // 🆕 Lot 14.2c — split avec les seuils du channel courant.
            val cfg = configByChannel.getValue(currentChannel)
            val split = withContext(Dispatchers.Default) {
                JemmaQrFrameSplitter.split(
                    payload = payload,
                    maxSingle = cfg.maxSingleChars,
                    frameChunk = cfg.frameChunkChars,
                )
            }
            frames = split
            currentFrameIndex = 0

            // 🆕 Lot 14.2c — Mettre à jour le chip avec le VRAI frame count
            //    (au lieu de l'estimation pré-split). Important pour FHIR
            //    qui peut avoir 10-20 frames.
            val frameCountLabel = if (split.size == 1) {
                getString(R.string.qr_frame_one)
            } else {
                getString(R.string.qr_frame_many, split.size)
            }
            // Remplace dans le chip le label "N frames" par le vrai count.
            // Chip pattern : "<prefix> · <bytes>B · <frames-label>" → on
            // remplace la dernière partie.
            val chipParts = chip.split(" · ").toMutableList()
            if (chipParts.size >= 3) {
                chipParts[chipParts.size - 1] = frameCountLabel
                binding.qrFormatChip.text = chipParts.joinToString(" · ")
            }

            showFrame(0)

            // Auto-démarre slideshow si FHIR + multi-frame.
            if (currentChannel == Channel.FHIR && frames.size > 1) {
                startSlideshow()
            }
        }
    }

    /** Build PRUNED payload. Returns (payload?, chipText). */
    private fun buildPruned(raw: JemmaProfileJ): Pair<String?, String> {
        val res = JemmaPayloadCodec.encode(raw)
        return when (res) {
            is JemmaPayloadCodec.EncodeResult.Failure -> {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ pruned encode failed : ${res.reason}", res.cause)
                null to "ERR"
            }
            is JemmaPayloadCodec.EncodeResult.Success -> {
                byteSizeCurrent = res.byteSize
                prunedFormatCurrent = res.format
                val chip = getString(
                    R.string.qr_format_chip_pruned,
                    res.byteSize,
                    framesLabelStub(res.byteSize),
                )
                res.payload to chip
            }
        }
    }

    /** Build TEXT payload. Returns (payload?, chipText). */
    private fun buildText(hydrated: HydratedProfile, lang: JemmaTextPayloadBuilder.Lang): Pair<String?, String> {
        val text = JemmaTextPayloadBuilder.build(hydrated, lang)
        val bytes = text.toByteArray(Charsets.UTF_8).size
        byteSizeCurrent = bytes
        prunedFormatCurrent = null
        val chip = getString(
            R.string.qr_format_chip_text,
            lang.flag,
            bytes,
            framesLabelStub(bytes),
        )
        return text to chip
    }

    /** Build FHIR payload. Returns (payload?, chipText). */
    private fun buildFhir(hydrated: HydratedProfile): Pair<String?, String> {
        val json = JemmaFhirBundleBuilder.build(hydrated)
        val bytes = json.toByteArray(Charsets.UTF_8).size
        byteSizeCurrent = bytes
        prunedFormatCurrent = null
        val chip = getString(R.string.qr_format_chip_fhir, bytes, framesLabelStub(bytes))
        return json to chip
    }

    /** Estimation rapide du nombre de frames basée sur le byte count. Le chip
     *  affichera la valeur exacte une fois `frames` calculé via showFrame(). */
    private fun framesLabelStub(bytes: Int): String =
        if (bytes <= JemmaQrFrameSplitter.QR_MAX_SINGLE) {
            getString(R.string.qr_frame_one)
        } else {
            val approxFrames =
                ((bytes + JemmaQrFrameSplitter.QR_FRAME_CHUNK - 11) / (JemmaQrFrameSplitter.QR_FRAME_CHUNK - 10))
                    .coerceAtLeast(2)
            getString(R.string.qr_frame_many, approxFrames)
        }

    // ──────────────────────────────────────────────────────────────────────
    // Frame rendering + slideshow
    // ──────────────────────────────────────────────────────────────────────

    private fun showFrame(index: Int, autoLoop: Boolean = false) {
        if (frames.isEmpty()) return
        val clamped = if (autoLoop) {
            ((index % frames.size) + frames.size) % frames.size
        } else {
            index.coerceIn(0, frames.size - 1)
        }
        currentFrameIndex = clamped

        val multi = frames.size > 1
        binding.qrFrameNav.isVisible = multi
        if (multi) {
            binding.qrFrameCounter.text = "${clamped + 1} / ${frames.size}"
            binding.qrBtnPrevFrame.isEnabled = autoLoop || clamped > 0
            binding.qrBtnNextFrame.isEnabled = autoLoop || clamped < frames.size - 1
            binding.qrBtnPlayPause.isVisible = (currentChannel == Channel.FHIR)
            updatePlayPauseIcon()
        }

        val frame = frames[clamped]
        val cfg = configByChannel.getValue(currentChannel)
        viewLifecycleOwner.lifecycleScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                JemmaQrBitmapEncoder.encode(
                    content = frame,
                    sizePx = 800,
                    errorCorrection = cfg.errorCorrection,
                )
            }
            if (bmp == null) {
                Log.e(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ❌ bitmap encode null for frame ${clamped + 1}/${frames.size}",
                )
                Toast.makeText(requireContext(), R.string.qr_error_bitmap_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            binding.qrImage.setImageBitmap(bmp)
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 🖼️ rendered frame ${clamped + 1}/${frames.size} (${frame.length}b)",
            )
        }
    }

    private fun startSlideshow() {
        if (frames.size <= 1) return
        if (isPlaying) return
        isPlaying = true
        slideshowHandler.removeCallbacks(slideshowRunnable)
        slideshowHandler.postDelayed(slideshowRunnable, SLIDESHOW_INTERVAL_MS)
        updatePlayPauseIcon()
        Log.d(TAG, "[t=${System.currentTimeMillis()}] ▶ slideshow start (${frames.size} frames, ${SLIDESHOW_INTERVAL_MS}ms)")
    }

    private fun stopSlideshow() {
        if (!isPlaying) return
        isPlaying = false
        slideshowHandler.removeCallbacks(slideshowRunnable)
        updatePlayPauseIcon()
        Log.d(TAG, "[t=${System.currentTimeMillis()}] ⏸ slideshow stop")
    }

    private fun updatePlayPauseIcon() {
        _binding?.qrBtnPlayPause?.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Copy to clipboard
    // ──────────────────────────────────────────────────────────────────────

    private fun copyCurrentToClipboard() {
        val payload = rawPayloadCurrent
        if (payload.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.qr_copy_empty, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: throw IllegalStateException("ClipboardManager unavailable")
            val label = "JEMMA ${currentChannel.name}"
            cm.setPrimaryClip(ClipData.newPlainText(label, payload))
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 📋 copied to clipboard · channel=$currentChannel · len=${payload.length}",
            )
            Toast.makeText(
                requireContext(),
                getString(R.string.qr_copy_done, payload.length),
                Toast.LENGTH_SHORT,
            ).show()
        } catch (e: Throwable) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ clipboard failed", e)
            Toast.makeText(requireContext(), R.string.qr_copy_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun channelFromKey(key: String): Channel = when (key.lowercase()) {
        FORMAT_TEXT -> Channel.TEXT
        FORMAT_FHIR -> Channel.FHIR
        else -> Channel.PRUNED
    }

}
