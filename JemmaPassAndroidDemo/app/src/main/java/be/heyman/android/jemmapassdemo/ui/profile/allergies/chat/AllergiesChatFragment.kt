/*
 * AllergiesChatFragment.kt — JEMMA Pass · Lot 14.5c30 (PHASE 14)
 *
 * 🎯 LOT 14.5c30 — Refonte "Agent piloté par @Tool" pour le concours
 *    Gemma 4 Good Hackathon (Special Tech Track LiteRT · $10k +
 *    Impact Track Health & Sciences · $10k).
 *
 * Avant (lots 14.5c16 → 14.5c28) : Gemma 4 reçoit un long system prompt
 * lui demandant de produire un JSON entre `---DRAFT---` délimiteurs.
 * Gemma 4 E4B ignore ce format → extraction regex échoue → frustration.
 *
 * Maintenant (lot 14.5c30) : on délègue tout à la fonction calling native
 * de Gemma 4. À l'entrée du chat, on configure une session avec deux
 * ToolSet :
 *   • [AllergiesAgentTools] (16 @Tool spécifiques à la collecte d'allergie)
 *   • [JemmaTools] (12 @Tool partagés : red alert, toast, dateTime, etc.)
 *
 * Quand l'user parle, Gemma raisonne, appelle ses @Tool dans l'ordre
 * qu'elle juge bon, et chaque tool call émet un événement sur un
 * SharedFlow. Le fragment écoute, traduit chaque événement en un
 * [ChatMessage.BgAction] chip rendu inline dans la liste de chat. Le
 * spectateur de la démo voit Gemma raisonner en temps réel, avec des
 * badges de latence par tool call ("Gemma a décidé en 187 ms").
 *
 * Aucun parsing texte. Aucun regex sur la sortie. Le tool calling fait
 * tout le travail structuré.
 *
 * Pipeline (un tour conversationnel) :
 *
 *   STT.onResults("je suis allergique à la pénicilline")
 *     ↓
 *   submitUserMessage(text)
 *     → append Text(USER, text)
 *     → gemma.ask(text)  // no systemInstruction — taskSystemPrompt déjà set
 *         ↓
 *         Gemma 4 réfléchit, appelle des @Tool :
 *           setSubstance("Pénicilline")           → BgAction chip
 *           setCategory("medication")             → BgAction chip
 *           setSeverity("High")                   → BgAction chip
 *           setManifestation("Urticaire")         → BgAction chip
 *           searchSnomedAllergyIntolerance(...)   → BgAction chip
 *           confirmSnomedCode("91936005", ...)    → BgAction chip
 *           playTTS("Pénicilline allergie sévère, c'est noté.")
 *           saveAllergyDraft()                    → BgAction chip + popBackStack
 *
 *   Le fragment voit chaque BgAction event et le rend.
 *   Le fragment voit Narration event et feed le TTS streaming.
 *   Le fragment voit SaveCommit event et déclenche le save bundle.
 *
 * Pas de DRAFT_DELIMITER, pas d'extraction, pas de regex.
 *
 * Lookups KB automatiques (kickoff*) : conservés tels quels mais
 * désormais écrivent dans [AllergiesAgentTools.setResolvedSubstanceCode]
 * au lieu du draft local. Le draft local du fragment est synchronisé
 * via les DraftFieldUpdated events.
 *
 * Log channel : JEMMA-CHAT-ALLERGIES-AGENT (renommé pour ce lot).
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.JemmaTools
import be.heyman.android.jemmapassdemo.ai.gemma.GemmaSession
import be.heyman.android.jemmapassdemo.ai.stt.JemmaSttService
import be.heyman.android.jemmapassdemo.ai.tts.JemmaTtsService
import be.heyman.android.jemmapassdemo.databinding.FragmentAllergiesChatBinding
import be.heyman.android.jemmapassdemo.kb.AllergyReactionItem
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseManager
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.getAllergyIntoleranceList
import be.heyman.android.jemmapassdemo.kb.getAllergyReactionList
import be.heyman.android.jemmapassdemo.kb.resolveAllergyFhirCategory
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import be.heyman.android.jemmapassdemo.ui.profile.allergies.AllergyFormBottomSheet
import be.heyman.android.jemmapassdemo.ui.profile.allergies.AllergyFormMode
import com.google.ai.edge.litertlm.tool
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AllergiesChatFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-CHAT-ALLERG-AGT"
        const val ARG_PROFILE_ID = "profileId"

        // Catégories KB considérées comme "vrai" allergène (utilisé par
        // kickoffSubstanceLookup Tier 1 pour filtrer les hits searchCodes).
        private val ALLERGEN_CATEGORIES = listOf("Chemical_Allergen", "Protein_Allergen")
    }

    private var _binding: FragmentAllergiesChatBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var tts: JemmaTtsService
    @Inject lateinit var stt: JemmaSttService
    @Inject lateinit var gemma: GemmaSession
    @Inject lateinit var kbService: KnowledgeBaseService
    @Inject lateinit var kbManager: KnowledgeBaseManager
    @Inject lateinit var profilesRepo: ProfilesRepository
    @Inject lateinit var translationsRepo: be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
    @Inject lateinit var allergiesAgentTools: AllergiesAgentTools
    @Inject lateinit var jemmaTools: JemmaTools
    // 🆕 Lot 14.5c31 — Gemma 4 multimodal audio capture pipeline.
    // Replaces SpeechRecognizer as the primary STT route. The latter
    // remains injected above as fallback.
    @Inject lateinit var audioRecorder: be.heyman.android.jemmapassdemo.ai.audio.JemmaAudioRecorder

    private val adapter = ChatAdapter()
    private val messages = mutableListOf<ChatMessage>()

    private var sttActive = false
    private var ttsStream: JemmaTtsService.StreamSession? = null

    /**
     * Lifecycle of the mic button. LOADING = Gemma session is initializing
     * or greeting TTS is still playing — taps are blocked, visual is ⏳.
     * IDLE = ready for the user to tap, visual is 🎤. RECORDING = STT is
     * active, tap to stop, visual is 🛑.
     */
    private enum class MicState { LOADING, IDLE, RECORDING }
    private var micState: MicState = MicState.LOADING

    /**
     * 🆕 Lot 14.5c31 — once Gemma audio inference has failed at least
     * once in this session (e.g. audioBackend init issue, unsupported
     * model section), we drop back to SpeechRecognizer for all
     * subsequent mic taps in this fragment. Reset only on fragment
     * destroy / new chat session.
     */
    private var gemmaAudioBroken: Boolean = false

    /**
     * 🆕 Lot 14.5c31 — true while AudioRecord is actively capturing PCM
     * to be sent to Gemma 4 audio. Distinguishes the new Gemma-audio
     * path from the legacy SpeechRecognizer path (which has its own
     * `sttActive` flag below).
     */
    private var gemmaAudioActive: Boolean = false

    /**
     * Local mirror of the agent's draft state. Updated on every
     * DraftFieldUpdated event so renderIpsCard() can paint the mini-card.
     * The single source of truth is [AllergiesAgentTools.snapshot()].
     */
    private var draft: IpsAllergyDraft = IpsAllergyDraft()

    // ──────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAllergiesChatBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💬 onCreateView · profileId=${arguments?.getString(ARG_PROFILE_ID)}")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tts.init() // 🆕 Lot 14.5c16 — Init TTS engine for agent replies.
        binding.allergiesChatToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.allergiesChatToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_allergies_chat_save -> { onSaveTap(); true }
                else -> false
            }
        }
        binding.allergiesChatRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.allergiesChatRecycler.adapter = adapter

        binding.allergiesChatMicButton.setOnClickListener { onMicTap() }
        binding.allergiesChatStopTtsButton.setOnClickListener { onStopTtsTap() }

        renderIpsCard()

        // ── Agent setup ──
        // bind the agent ToolSet to this conversation (resets state +
        // captures profileId for cross-checks). The fragment will collect
        // its events below.
        val profileId = arguments?.getString(ARG_PROFILE_ID)
        allergiesAgentTools.bind(profileId = profileId, lang = currentLang())
        // JemmaTools needs the loaded profile (not just the id) to power
        // its getFocusProfile* tools. Load async + bind when ready.
        if (profileId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val p = profilesRepo.loadProfile(profileId)
                    jemmaTools.bind(p)
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎯 jemmaTools bound to profile $profileId (${p?.p?.gn ?: "—"})")
                } catch (e: Throwable) {
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ jemmaTools bind failed: ${e.message}")
                    jemmaTools.bind(null)
                }
            }
        } else {
            jemmaTools.bind(null)
        }

        // Collect agent events → translate into ChatMessages.
        viewLifecycleOwner.lifecycleScope.launch {
            allergiesAgentTools.events.collect { event ->
                handleAgentEvent(event)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            jemmaTools.events.collect { event ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔔 JemmaToolEvent · $event")
                // For lot 14.5c30 we don't surface JemmaTools events in the
                // allergies chat (they target PatientDetail). Future lots may
                // route Toast / RedAlert to bg-action chips here.
            }
        }

        // ── Configure Gemma session for this task ──
        // Tools available throughout the chat: the agent's 16 + Jemma's 12.
        // Each ask() call below will use these by default.
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                gemma.configureForTask(
                    taskId = "allergies-chat-agent",
                    systemPrompt = buildAgentSystemPrompt(currentLang()),
                    tools = listOf(tool(allergiesAgentTools), tool(jemmaTools)),
                    supportImage = false,
                )
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎯 Gemma session configured · agent mode")
            } catch (e: Throwable) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ configureForTask failed", e)
            }
        }

        // 🆕 Lot 14.5c30 — mic en état LOADING (⏳) tant que Gemma s'initialise
        // ET que le greeting TTS n'est pas terminé. Le tap est ignoré pendant
        // cette phase. Au callback de fin du greeting, on passe IDLE puis on
        // démarre STT (qui passera RECORDING).
        micState = MicState.LOADING
        updateMicUi()

        // ── Init TTS + greeting + auto-mic ──
        tts.init {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔊 TTS ready · greeting then auto-mic")
            val greeting = getString(R.string.allergies_chat_greeting)
            appendTextMessage(ChatRole.JEMMA, greeting, streaming = false)
            tts.speakOneShot(greeting, ttsLangTag()) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏁 greeting TTS done · auto-tap mic via onMicTap (c32)")
                if (_binding == null || !isAdded) return@speakOneShot
                // 🆕 Lot 14.5c32 — route through onMicTap() instead of
                // calling startSttIfPossible() direct. Previous code
                // bypassed the Gemma-audio-first router, so the auto-mic
                // path always went to SpeechRecognizer.
                micState = MicState.IDLE
                updateMicUi()
                onMicTap()
            }
        }
    }

    override fun onDestroyView() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔻 onDestroyView · cleanup")
        try { stt.cancel() } catch (_: Throwable) {}
        // 🆕 Lot 14.5c31 — cancel any in-flight AudioRecord too. The
        // @Singleton recorder survives the fragment, so leaving the mic
        // capturing would leak it into the next session.
        try { audioRecorder.cancel() } catch (_: Throwable) {}
        try { ttsStream?.cancel() } catch (_: Throwable) {}
        ttsStream = null
        try { tts.stopAll() } catch (_: Throwable) {}
        // Agent cleanup
        try { allergiesAgentTools.unbind() } catch (_: Throwable) {}
        try { jemmaTools.unbind() } catch (_: Throwable) {}
        try { gemma.clearTaskConfig() } catch (_: Throwable) {}
        super.onDestroyView()
        _binding = null
    }

    // ──────────────────────────────────────────────────────────────────
    // Agent event handling
    // ──────────────────────────────────────────────────────────────────

    /**
     * Translate an [AllergiesAgentEvent] into one or more [ChatMessage].
     * BgAction events become chips. DraftFieldUpdated triggers KB lookups
     * and re-renders the mini-card. Narration feeds the TTS streamer.
     * SaveCommit / CancelAndExit terminate the chat.
     */
    private fun handleAgentEvent(event: AllergiesAgentEvent) {
        when (event) {
            is AllergiesAgentEvent.BgAction -> {
                val msgId = ChatMessage.idFromActionId(event.actionId)
                val msg = ChatMessage.BgAction(
                    id = msgId,
                    actionId = event.actionId,
                    emoji = event.emoji,
                    label = event.label,
                    value = event.value,
                    state = event.state,
                    detail = event.detail,
                    latencyMs = event.latencyMs,
                )
                upsertMessage(msg)
            }

            is AllergiesAgentEvent.DraftFieldUpdated -> {
                // Mirror the field into the local draft for the mini-card.
                draft = when (event.field) {
                    "substance" -> draft.copy(
                        substance = event.value,
                        substanceCode = null,
                        substanceDisplay = null,
                        substanceLookup = SubstanceLookupState.IDLE,
                    )
                    "category" -> draft.copy(category = event.value)
                    "severity" -> draft.copy(severity = event.value)
                    "status" -> draft.copy(status = event.value)
                    "manifestation" -> draft.copy(
                        manifestation = event.value,
                        manifestationCode = null,
                        manifestationDisplay = null,
                        manifestationLookup = SubstanceLookupState.IDLE,
                    )
                    "onsetDate" -> draft.copy(onsetDate = event.value)
                    else -> draft
                }
                renderIpsCard()

                // Automatic KB lookups on substance / manifestation capture.
                // The agent will see the result if it queries getProfileSummary
                // or by calling search* tools explicitly.
                if (event.field == "substance" && !event.value.isNullOrBlank()) {
                    kickoffSubstanceLookup(event.value)
                }
                if (event.field == "manifestation" && !event.value.isNullOrBlank()) {
                    kickoffManifestationLookup(event.value)
                }
            }

            is AllergiesAgentEvent.Narration -> {
                // Pipe Gemma's vocalization into the TTS streamer.
                if (ttsStream == null) {
                    val sess = tts.startStream(ttsLangTag())
                    ttsStream = sess
                    _binding?.allergiesChatStopTtsButton?.isVisible = true
                }
                ttsStream?.feed(event.text + " ")
                // Also surface the spoken text as a Jemma chat bubble so the
                // user can re-read it if TTS is muted/scrolling.
                appendTextMessage(ChatRole.JEMMA, event.text, streaming = false)
            }

            is AllergiesAgentEvent.SaveCommit -> {
                // The agent decided to commit. Build the bundle using the
                // current agent snapshot + the mirrored draft (for the human
                // dates already in draft). Same path as the manual save.
                doSaveAndPop()
            }

            is AllergiesAgentEvent.CancelAndExit -> {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛑 agent cancelled · ${event.reason}")
                try { stt.cancel() } catch (_: Throwable) {}
                try { ttsStream?.cancel() } catch (_: Throwable) {}
                try { tts.stopAll() } catch (_: Throwable) {}
                if (isAdded) findNavController().popBackStack()
            }

            is AllergiesAgentEvent.CrossCheckCompleted -> {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩺 cross-check done · matches=${event.matchCount}")
                // No-op visually beyond the chip already rendered. Gemma can
                // follow up with triggerRedAlert if the count > 0.
            }
        }
    }

    /**
     * Upsert a ChatMessage into the adapter list. If a message with the
     * same id already exists (typical for BgAction RUNNING → DONE updates
     * of the same actionId), replace it in place; otherwise append.
     */
    private fun upsertMessage(msg: ChatMessage) {
        val existingIdx = messages.indexOfFirst { it.id == msg.id }
        if (existingIdx >= 0) {
            messages[existingIdx] = msg
        } else {
            messages.add(msg)
        }
        adapter.submitList(messages.toList())
        _binding?.allergiesChatRecycler?.scrollToPosition(messages.size - 1)
    }

    // ──────────────────────────────────────────────────────────────────
    // Mic / STT (inchangé du lot précédent)
    // ──────────────────────────────────────────────────────────────────

    private fun onMicTap() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 mic tap · state=$micState · " +
            "gemmaAudioBroken=$gemmaAudioBroken · gemmaAudioActive=$gemmaAudioActive · sttActive=$sttActive")
        when (micState) {
            MicState.LOADING -> {
                // Silently ignore. Visual already shows ⏳ so the user gets
                // feedback that the chat isn't ready yet.
                Log.d(TAG, "[t=${System.currentTimeMillis()}] ⏳ mic tap during LOADING — ignored")
                return
            }
            MicState.RECORDING -> {
                // Stop whichever path is active. The two are mutually
                // exclusive — only one can be active at a time.
                if (gemmaAudioActive) {
                    stopGemmaAudioAndSubmit()
                } else if (sttActive) {
                    stt.stop()
                }
            }
            MicState.IDLE -> {
                // 🆕 Lot 14.5c31 — Gemma 4 audio primary, SpeechRecognizer
                // fallback. We try Gemma audio first; if it fails mid-session
                // (gemmaAudioBroken=true after a thrown ask), all subsequent
                // taps go to SpeechRecognizer until the fragment is recreated.
                if (gemmaAudioBroken) {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔁 Gemma audio broken — fallback to SpeechRecognizer")
                    startSttIfPossible()
                } else {
                    startGemmaAudioCapture()
                }
            }
        }
    }

    /**
     * 🆕 Lot 14.5c31 — Gemma 4 multimodal STT path.
     *
     * Starts AudioRecord at 16 kHz mono 16-bit PCM, drives the waveform
     * from `calculatePeakAmplitude` of each buffer, and on user tap-stop
     * sends the PCM byte array to Gemma via `Content.AudioBytes(pcm)`.
     * Gemma transcribes AND function-calls in a single inference.
     *
     * Auto-stops after JemmaAudioRecorder.DEFAULT_MAX_DURATION_MS (30s)
     * to bound inference cost.
     */
    private fun startGemmaAudioCapture() {
        val b = _binding ?: return
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ RECORD_AUDIO not granted (Gemma audio path)")
            appendTextMessage(
                ChatRole.JEMMA,
                getString(R.string.allergies_chat_err_no_mic_permission),
                streaming = false,
            )
            return
        }
        // Cancel any in-flight TTS so we don't talk over the user.
        try { ttsStream?.cancel() } catch (_: Throwable) {}
        ttsStream = null

        gemmaAudioActive = true
        micState = MicState.RECORDING
        updateMicUi()
        b.allergiesChatTranscriptLive.text = ""
        b.allergiesChatWaveform.visibility = View.VISIBLE
        b.allergiesChatTranscriptLive.visibility = View.GONE
        b.allergiesChatWaveform.start()

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎙 startGemmaAudioCapture · waveform on")

        audioRecorder.start(
            onAmplitudeChanged = { amp ->
                // Background thread → post to UI.
                _binding?.allergiesChatWaveform?.post {
                    _binding?.allergiesChatWaveform?.setAmplitude(amp)
                }
            },
            onMaxDurationReached = { pcm ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ⏱ Gemma audio auto-stop · ${pcm.size} bytes")
                onGemmaAudioCaptured(pcm)
            },
        )
    }

    /** User-initiated stop of Gemma audio capture. Drains and submits. */
    private fun stopGemmaAudioAndSubmit() {
        val pcm = audioRecorder.stop()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛑 stopGemmaAudioAndSubmit · ${pcm.size} bytes")
        onGemmaAudioCaptured(pcm)
    }

    /** Common path: clear waveform UI, wrap PCM in WAV, push to Gemma. */
    private fun onGemmaAudioCaptured(pcm: ByteArray) {
        gemmaAudioActive = false
        val b = _binding ?: return
        b.allergiesChatWaveform.stop()
        b.allergiesChatWaveform.visibility = View.GONE
        b.allergiesChatTranscriptLive.visibility = View.VISIBLE
        b.allergiesChatTranscriptLive.text = ""
        if (pcm.size < 4_000) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ Gemma audio too short (${pcm.size} B) — discarding")
            micState = MicState.IDLE
            updateMicUi()
            return
        }
        // 🆕 Lot 14.5c32 — wrap raw PCM in 44-byte RIFF/WAVE header.
        // Gemma 4 / litert-lm decodes via miniaudio which needs a file
        // format header. Raw PCM → `miniaudio decoder error -10`.
        // Port of Gallery's ChatMessageAudioClip.genByteArrayForWav.
        val wav = audioRecorder.pcmToWav(pcm)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎼 pcm→wav · pcmBytes=${pcm.size} · wavBytes=${wav.size} (header=44 + pcm)")
        submitUserMessage(text = "", audioPcm = wav)
    }

    private fun startSttIfPossible() {
        val b = _binding ?: return
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ RECORD_AUDIO not granted")
            appendTextMessage(
                ChatRole.JEMMA,
                getString(R.string.allergies_chat_err_no_mic_permission),
                streaming = false,
            )
            micState = MicState.IDLE
            updateMicUi()
            return
        }
        if (!stt.isSupported()) {
            appendTextMessage(
                ChatRole.JEMMA,
                getString(R.string.allergies_chat_err_stt_unsupported),
                streaming = false,
            )
            micState = MicState.IDLE
            updateMicUi()
            return
        }
        try { ttsStream?.cancel() } catch (_: Throwable) {}
        ttsStream = null

        sttActive = true
        micState = MicState.RECORDING
        updateMicUi()
        b.allergiesChatTranscriptLive.text = ""
        // 🆕 Lot 14.5c30 v6 — show animated waveform while recording.
        b.allergiesChatWaveform.visibility = View.VISIBLE
        b.allergiesChatTranscriptLive.visibility = View.GONE
        b.allergiesChatWaveform.start()

        stt.start(sttLangTag(), object : JemmaSttService.SttListener {
            override fun onReady() {
                Log.d(TAG, "[t=${System.currentTimeMillis()}] 🟢 STT ready")
            }
            override fun onPartial(text: String) {
                _binding?.allergiesChatTranscriptLive?.text = text
            }
            override fun onRms(rmsDb: Float) {
                _binding?.allergiesChatWaveform?.setRmsDb(rmsDb)
            }
            override fun onFinal(text: String) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ STT final segment · \"$text\"")
            }
            override fun onEnd(fullText: String) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏁 STT end · full=\"$fullText\"")
                sttActive = false
                micState = MicState.IDLE
                val b2 = _binding ?: return
                updateMicUi()
                b2.allergiesChatTranscriptLive.text = ""
                b2.allergiesChatWaveform.stop()
                b2.allergiesChatWaveform.visibility = View.GONE
                b2.allergiesChatTranscriptLive.visibility = View.VISIBLE
                if (fullText.isNotBlank() && isAdded) {
                    submitUserMessage(fullText)
                }
            }
            override fun onError(code: Int, msg: String) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ❌ STT error $code · $msg")
                sttActive = false
                micState = MicState.IDLE
                val b2 = _binding ?: return
                updateMicUi()
                b2.allergiesChatTranscriptLive.text = ""
                b2.allergiesChatWaveform.stop()
                b2.allergiesChatWaveform.visibility = View.GONE
                b2.allergiesChatTranscriptLive.visibility = View.VISIBLE
                if (code != android.speech.SpeechRecognizer.ERROR_NO_MATCH &&
                    code != android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                    code != android.speech.SpeechRecognizer.ERROR_CLIENT &&
                    isAdded
                ) {
                    appendTextMessage(
                        ChatRole.JEMMA,
                        getString(R.string.allergies_chat_err_stt_generic, msg),
                        streaming = false,
                    )
                }
            }
        })
    }

    private fun updateMicUi() {
        val b = _binding ?: return
        b.allergiesChatMicButton.text = when (micState) {
            MicState.LOADING -> "⏳"
            MicState.IDLE -> "🎤"
            MicState.RECORDING -> "🛑"
        }
        b.allergiesChatMicButton.alpha = if (micState == MicState.LOADING) 0.45f else 1.0f
    }

    // ──────────────────────────────────────────────────────────────────
    // Submit user message → Gemma agent
    // ──────────────────────────────────────────────────────────────────

    /**
     * Submit a turn to Gemma. Either as plain text (STT fallback path)
     * OR with an audio clip (Gemma 4 multimodal STT path, primary).
     *
     * When `audioPcm != null` and `text` is blank, we ask Gemma to
     * transcribe AND extract IPS fields in a single inference — no
     * Android SpeechRecognizer involved. The transcript appears in the
     * USER bubble after Gemma returns (it would be empty during recording
     * — that's the trade-off vs SpeechRecognizer's onPartial stream).
     *
     * When `audioPcm == null`, this is the text/SpeechRecognizer path
     * preserved as fallback if Gemma audio init fails.
     */
    private fun submitUserMessage(text: String, audioPcm: ByteArray? = null) {
        val displayText = if (audioPcm != null && text.isBlank()) {
            getString(R.string.allergies_chat_audio_message, audioPcm.size / 32_000)
        } else text
        appendTextMessage(ChatRole.USER, displayText, streaming = false)

        // 🆕 Lot 14.5c30 v5 — Kotlin-side FR date extractor.
        // Gemma sometimes hallucinates / truncates the date under CPU
        // sampler fallback ("2024" → "204") and doesn't call setOnsetDate.
        // We parse the user's input directly to extract any FR date and
        // apply it BEFORE inference, so Gemma sees onsetDate already filled
        // in the state preamble and won't need to touch it.
        //
        // For audio-only turns, we have no text to parse — skip this and
        // trust Gemma's audio STT to extract the date.
        if (text.isNotBlank()) {
            val isoDate = extractFrDateAsIso(text)
            if (isoDate != null) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 📅 FR date extracted from STT · '$isoDate'")
                allergiesAgentTools.applyResolvedOnsetDate(isoDate)
            }
        }

        // Build a per-turn user prompt that includes the current agent
        // state so Gemma knows where she is in the collection. The
        // session reset (per ask()) wipes conversation history but the
        // taskSystemPrompt persists; we still inject state in the user
        // prompt to keep Gemma grounded.
        val snapshot = allergiesAgentTools.snapshot()
        val statePreamble = buildStatePreamble(snapshot)
        val userTurnPrompt = if (audioPcm != null && text.isBlank()) {
            // 🆕 Lot 14.5c31 — audio-only turn. Ask Gemma to transcribe
            // and extract in one shot via the same @Tool agent.
            val langName = when (currentLang()) {
                "en" -> "English"
                "ja" -> "Japanese"
                else -> "French"
            }
            "$statePreamble\n\nUser audio message (PCM 16-bit mono 16 kHz · ${audioPcm.size / 32_000} s):\n" +
                "Listen to the attached audio. Translate the patient's intent to English to populate the tool arguments. " +
                "Call setSubstance (mandatory), and IF mentioned, call setSeverity and setManifestation. " +
                "Remember your playTTS must be spoken in $langName. " +
                "Call saveAllergyDraft as soon as setSubstance has been called."
        } else if (statePreamble.isNotBlank()) {
            "$statePreamble\n\nUser message: $text"
        } else {
            text
        }

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📤 submitUserMessage · " +
            "text='${text.take(60)}' · audio=${audioPcm?.size ?: 0} bytes")
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📤 turn-state preamble :\n$statePreamble")

        // 🆕 Lot 14.5c30 v2 — UX feedback during Gemma inference.
        // Lock the mic to LOADING (⏳, alpha 45%) and show a static
        // "thinking" bubble so the user has visible proof that Jemma
        // is working. Both are reverted in the finally block.
        micState = MicState.LOADING
        updateMicUi()
        val thinkingMsgId = appendThinkingMessage(
            if (audioPcm != null) getString(R.string.allergies_chat_jemma_listening)
            else pickThinkingPhrase(snapshot)
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val tStart = System.currentTimeMillis()
            try {
                val response = gemma.ask(
                    prompt = userTurnPrompt,
                    audioClip = audioPcm,
                )
                val elapsed = System.currentTimeMillis() - tStart
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ gemma.ask done · ${elapsed}ms · responseLen=${response.length}")
                // 🆕 Lot 14.5c32 — full diagnostic dump of Gemma's reply.
                // When responseLen=0 it usually means Gemma went tool-only
                // (good). But it can also mean she generated nothing (bad)
                // OR generated a free-form refusal we never see. Dump the
                // raw bytes too in case there are control tokens / FC
                // markers we'd otherwise lose.
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 📥 ask · RAW REPLY (len=${response.length}) :\n'''\n$response\n'''")
                if (response.isNotEmpty()) {
                    val bytes = response.toByteArray(Charsets.UTF_8)
                    val hex = bytes.take(120).joinToString(" ") { "%02x".format(it) }
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 📥 ask · first 120 bytes HEX : $hex")
                }
                // Also dump the agent state AFTER the call so we can
                // correlate "0 tool calls" vs "tool calls fired but draft
                // unchanged" vs "draft updated".
                val postSnapshot = allergiesAgentTools.snapshot()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 📊 post-ask draft · substance='${postSnapshot.substance}' · severity='${postSnapshot.severity}' · manifestation='${postSnapshot.manifestation}' · filled=${postSnapshot.filledCount}/${postSnapshot.mandatoryFieldCount}")

                val trimmed = response.trim()
                if (trimmed.isNotEmpty() && trimmed.length < 600) {
                    appendTextMessage(ChatRole.JEMMA, trimmed, streaming = false)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ gemma.ask threw", e)
                val msg = if (audioPcm != null) {
                    getString(R.string.allergies_chat_error_audio_processing, e.message ?: "error")
                } else {
                    getString(R.string.allergies_chat_error_generic, e.message ?: "error")
                }
                appendTextMessage(ChatRole.JEMMA, msg, streaming = false)
                if (audioPcm != null) gemmaAudioBroken = true
            } finally {
                removeMessageById(thinkingMsgId)
                if (micState == MicState.LOADING) {
                    micState = MicState.IDLE
                    updateMicUi()
                }
            }
        }
    }

    /**
     * Pick a thinking phrase that hints at WHAT Gemma is about to do,
     * based on the agent's current state.
     */
    private fun pickThinkingPhrase(snapshot: AllergiesAgentDraft): String {
        val filled = snapshot.filledCount
        return when {
            filled == 0 -> getString(R.string.allergies_chat_jemma_thinking_analyze)
            filled == 1 -> getString(R.string.allergies_chat_jemma_thinking_snomed)
            else -> getString(R.string.allergies_chat_jemma_thinking_default)
        }
    }

    private fun appendThinkingMessage(text: String): Long {
        val id = ChatMessage.nextId()
        val msg = ChatMessage.Text(id = id, role = ChatRole.JEMMA, text = text, streaming = true)
        messages.add(msg)
        adapter.submitList(messages.toList())
        _binding?.allergiesChatRecycler?.scrollToPosition(messages.size - 1)
        return id
    }

    private fun removeMessageById(id: Long) {
        val idx = messages.indexOfFirst { m ->
            when (m) {
                is ChatMessage.Text -> m.id == id
                is ChatMessage.BgAction -> m.id == id
                is ChatMessage.Reasoning -> m.id == id
            }
        }
        if (idx >= 0) {
            messages.removeAt(idx)
            adapter.submitList(messages.toList())
        }
    }

    /**
     * Build the system prompt that defines Gemma's role + tells her which
     * tools to use and when. Kept short — the @Tool descriptions carry
     * the per-method semantics. Gemma 4 has built-in function calling
     * intelligence, we don't need to over-prompt.
     */
    private fun buildAgentSystemPrompt(lang: String): String {
        val langName = when (lang) {
            "en" -> "English"
            "ja" -> "Japanese"
            else -> "French"
        }
        return """
You are Jemma, a medical assistant collecting ONE allergy or intolerance from a patient via voice.
The user's transcribed audio input will be in $langName.

⚡ FUNDAMENTAL RULE: When the user says "I am allergic to X", "I have an allergy to X", "I am intolerant to X", "I cannot tolerate X", you MUST interpret this as declaring an allergy/intolerance. Words like "allergy", "allergic", "intolerant", "intolerance" are ALL treated as synonyms here.

📋 ONLY MANDATORY FIELD: the SUBSTANCE.
The other fields (severity, manifestation) are OPTIONAL — capture them IF AND ONLY IF the user spontaneously mentioned them. NEVER ask for them if missing. As soon as the substance is captured, save immediately.

🛠 @Tool TOOLS — you ONLY use these 4:
• setSubstance(name) : MANDATORY. Capitalized singular English INN or common name: 'Penicillin', 'Fish' (NOT 'Fishes'), 'Peanut'.
• setSeverity(severity) : OPTIONAL. Map to "High", "Low", or "Unknown" based on user description:
    - severe / strong / anaphylactic → setSeverity("High")
    - light / mild / slightly → setSeverity("Low")
    - I don't know → setSeverity("Unknown")
• setManifestation(text) : OPTIONAL. If the user described the reaction (hives, edema, itching, shock, etc.).
• playTTS(text) : vocalize a short acknowledgment (1-2 sentences) in $langName. No markdown.
• saveAllergyDraft : CALL THIS as soon as setSubstance is called successfully. Do not ask for confirmation.
• cancelAndExit(reason) : if the user says "cancel", "stop", or "abort".

🔁 WORKFLOW (VERY SHORT) per turn:
1. Extract the substance + (if present) severity and manifestation.
2. Call the corresponding set* tools.
3. Call playTTS for a brief acknowledgment in $langName.
4. Call saveAllergyDraft. END OF TURN.

Full example:
   User: "I am allergic to penicillin it is severe and I get hives"
   →  setSubstance("Penicillin")
      setSeverity("High")
      setManifestation("Hives")
      playTTS("I have noted your severe allergy to penicillin causing hives.")
      saveAllergyDraft()

Short example:
   User: "I am allergic to peanuts"
   →  setSubstance("Peanut")
      playTTS("I have noted your allergy to peanuts.")
      saveAllergyDraft()

❗ NEVER REPLY WITH FREE TEXT — your only outputs are tool calls.
❗ DO NOT ask for severity or manifestation if the user didn't mention them.
❗ Always reply in $langName inside playTTS.
""".trimIndent()
    }

    /**
     * Per-turn state injection : reminds Gemma what she has already
     * captured, so she knows what fields are still missing. Necessary
     * because gemma.ask() resets the conversation each turn.
     */
    private fun buildStatePreamble(s: AllergiesAgentDraft): String {
        val lines = mutableListOf<String>()
        lines.add("Current allergy draft state:")
        lines.add("• substance (mandatory) : ${s.substance ?: "(missing)"}")
        lines.add("• severity (optional)   : ${s.severity ?: "(unspecified)"}")
        lines.add("• manifestation (opt.)  : ${s.manifestation ?: "(unspecified)"}")
        if (!s.category.isNullOrBlank()) {
            lines.add("• category (auto KB, do not ask) : ${s.category}")
        }
        if (!s.substanceCode.isNullOrBlank()) {
            lines.add("• SNOMED substance : ${s.substanceCode} (${s.substanceDisplay ?: ""})")
        }
        if (!s.manifestationCode.isNullOrBlank()) {
            lines.add("• SNOMED manif     : ${s.manifestationCode} (${s.manifestationDisplay ?: ""})")
        }
        if (s.substance.isNullOrBlank()) {
            lines.add("ACTION : substance is missing. Ask ONLY for the substance.")
        } else {
            lines.add("ACTION : substance is captured. Call playTTS for a short acknowledgment THEN saveAllergyDraft. Ask for NOTHING else.")
        }
        return lines.joinToString("\n")
    }

    private fun onStopTtsTap() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 stop-tts tap")
        try { ttsStream?.cancel() } catch (_: Throwable) {}
        ttsStream = null
        try { tts.stopAll() } catch (_: Throwable) {}
        _binding?.allergiesChatStopTtsButton?.isVisible = false
    }

    // ──────────────────────────────────────────────────────────────────
    // Adapter helpers
    // ──────────────────────────────────────────────────────────────────

    private fun appendTextMessage(role: ChatRole, text: String, streaming: Boolean) {
        val id = ChatMessage.nextId()
        appendTextMessageWithId(id, role, text, streaming)
    }

    private fun appendTextMessageWithId(id: Long, role: ChatRole, text: String, streaming: Boolean) {
        val msg = ChatMessage.Text(id = id, role = role, text = text, streaming = streaming)
        messages.add(msg)
        adapter.submitList(messages.toList())
        _binding?.allergiesChatRecycler?.scrollToPosition(messages.size - 1)
    }

    private fun ttsLangTag(): String = Locale.getDefault().toLanguageTag()

    private fun sttLangTag(): String = ttsLangTag()

    private fun currentLang(): String = Locale.getDefault().language.lowercase().take(2)

    // ──────────────────────────────────────────────────────────────────
    // IPS mini-card render
    // ──────────────────────────────────────────────────────────────────

    private fun renderIpsCard() {
        val b = _binding ?: return
        b.allergiesChatIpsProgress.text = getString(
            R.string.allergies_chat_ips_progress, draft.filledCount,
        )
        renderIpsSubstanceLine(b.allergiesChatIpsSubstance)
        renderIpsLine(
            b.allergiesChatIpsCategory,
            getString(R.string.allergies_chat_ips_category_label),
            draft.category?.let { translateCategory(it) },
        )
        renderIpsLine(
            b.allergiesChatIpsSeverity,
            getString(R.string.allergies_chat_ips_severity_label),
            draft.severity?.let { translateSeverity(it) },
        )
        renderIpsLine(
            b.allergiesChatIpsStatus,
            getString(R.string.allergies_chat_ips_status_label),
            draft.status?.let { translateStatus(it) },
        )
        renderIpsLine(
            b.allergiesChatIpsManifestation,
            getString(R.string.allergies_chat_ips_manifestation_label),
            draft.manifestation,
        )
        // 🆕 c34 — date row removed from voice UI. Date is set only in
        // the manual form, not during voice collection.

        val canSave = draft.isMinimallyComplete
        val saveItem = b.allergiesChatToolbar.menu.findItem(R.id.action_allergies_chat_save)
        saveItem?.let {
            it.isEnabled = canSave
            it.icon?.alpha = if (canSave) 255 else 90
        }
    }

    private fun renderIpsLine(tv: android.widget.TextView, label: String, value: String?) {
        tv.text = if (!value.isNullOrBlank()) {
            getString(R.string.allergies_chat_ips_line_filled, label, value)
        } else {
            getString(R.string.allergies_chat_ips_line_missing, label)
        }
    }

    private fun translateCategory(cat: String): String = when (cat) {
        "medication" -> getString(R.string.allergies_chat_cat_medication)
        "food" -> getString(R.string.allergies_chat_cat_food)
        "environment" -> getString(R.string.allergies_chat_cat_environment)
        "biologic" -> getString(R.string.allergies_chat_cat_biologic)
        else -> cat
    }

    private fun translateSeverity(sev: String): String = when (sev) {
        "High" -> getString(R.string.allergies_chat_sev_high)
        "Low" -> getString(R.string.allergies_chat_sev_low)
        "Unknown" -> getString(R.string.allergies_chat_sev_unknown)
        else -> sev
    }

    private fun translateStatus(st: String): String = when (st) {
        "Active" -> getString(R.string.allergies_chat_status_active)
        "Inactive" -> getString(R.string.allergies_chat_status_inactive)
        "Resolved" -> getString(R.string.allergies_chat_status_resolved)
        else -> st
    }

    private fun renderIpsSubstanceLine(tv: android.widget.TextView) {
        val label = getString(R.string.allergies_chat_ips_substance_label)
        val sub = draft.substance
        if (sub.isNullOrBlank()) {
            tv.text = getString(R.string.allergies_chat_ips_line_missing, label)
            return
        }
        tv.text = when (draft.substanceLookup) {
            SubstanceLookupState.FOUND -> getString(
                R.string.allergies_chat_ips_substance_resolved,
                label,
                draft.substanceDisplay ?: sub,
                draft.substanceCode ?: "?",
            )
            SubstanceLookupState.PENDING -> getString(
                R.string.allergies_chat_ips_substance_pending, label, sub,
            )
            SubstanceLookupState.NOT_FOUND_WITH_CANDIDATES,
            SubstanceLookupState.NOT_FOUND_EMPTY -> getString(
                R.string.allergies_chat_ips_substance_unresolved, label, sub,
            )
            SubstanceLookupState.IDLE -> getString(
                R.string.allergies_chat_ips_line_filled, label, sub,
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Save commit (manuel or agent-driven via SaveCommit event)
    // ──────────────────────────────────────────────────────────────────

    private fun onSaveTap() {
        if (!draft.isMinimallyComplete) {
            Toast.makeText(
                requireContext(),
                R.string.allergies_chat_save_toast_empty,
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        doSaveAndPop()
    }

    private fun doSaveAndPop() {
        try { stt.cancel() } catch (_: Throwable) {}
        try { ttsStream?.cancel() } catch (_: Throwable) {}
        try { tts.stopAll() } catch (_: Throwable) {}
        sttActive = false
        updateMicUi()
        _binding?.allergiesChatStopTtsButton?.isVisible = false

        // Pull the freshest agent snapshot for SNOMED codes (the local mirror
        // may lag the agent if the KB lookup just completed).
        val agentSnap = allergiesAgentTools.snapshot()
        val resolvedSubstanceCode = agentSnap.substanceCode ?: draft.substanceCode
        val resolvedSubstanceDisplay = agentSnap.substanceDisplay ?: draft.substanceDisplay ?: draft.substance
        val resolvedSubstanceSystem = agentSnap.substanceSystem ?: draft.substanceSystem
        val resolvedManifCode = agentSnap.manifestationCode ?: draft.manifestationCode
        val resolvedManifDisplay = agentSnap.manifestationDisplay ?: draft.manifestationDisplay ?: draft.manifestation
        val resolvedManifSystem = agentSnap.manifestationSystem ?: draft.manifestationSystem

        // Mapping codes 1-lettre attendus par AllergiesEditFragment.
        val sCode = when (draft.severity) {
            "High" -> "H"
            "Low" -> "L"
            else -> "U"
        }
        val stCode = when (draft.status?.lowercase()) {
            "active" -> "A"
            "resolved" -> "R"
            "inactive" -> "I"
            else -> "A"
        }

        val bundle = Bundle().apply {
            putString(AllergyFormBottomSheet.ARG_MODE, AllergyFormMode.CREATE.name)
            putInt(AllergyFormBottomSheet.ARG_INDEX, -1)
            putString(AllergyFormBottomSheet.ARG_CODE, resolvedSubstanceCode)
            putString(AllergyFormBottomSheet.ARG_DISPLAY, resolvedSubstanceDisplay)
            putString(AllergyFormBottomSheet.ARG_SEVERITY, sCode)
            putString(AllergyFormBottomSheet.ARG_STATUS, stCode)
            putString(AllergyFormBottomSheet.ARG_MECHANISM, null)
            putString(AllergyFormBottomSheet.ARG_NOTES, draft.manifestation)
            putString(AllergyFormBottomSheet.ARG_TYPE, "allergy")
            putString(AllergyFormBottomSheet.ARG_CATEGORY, draft.category)
            putString(AllergyFormBottomSheet.ARG_ONSET, draft.onsetDate)
            putString(AllergyFormBottomSheet.ARG_CODE_SYSTEM, resolvedSubstanceSystem)
            putString(AllergyFormBottomSheet.ARG_REACTION_MANIF_CODE, resolvedManifCode)
            putString(AllergyFormBottomSheet.ARG_REACTION_MANIF_DISPLAY, resolvedManifDisplay)
            putString(AllergyFormBottomSheet.ARG_REACTION_MANIF_SYSTEM, resolvedManifSystem)
            putString(AllergyFormBottomSheet.ARG_REACTION_SEVERITY, sCode)
        }

        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 💾 save commit · " +
                "code=$resolvedSubstanceCode · display='$resolvedSubstanceDisplay' · " +
                "severity=$sCode · status=$stCode · category=${draft.category} · " +
                "onset=${draft.onsetDate} · manifestation='${draft.manifestation}' " +
                "(code=$resolvedManifCode)",
        )

        parentFragmentManager.setFragmentResult(
            AllergyFormBottomSheet.RESULT_KEY,
            bundle,
        )

        Toast.makeText(
            requireContext(),
            R.string.allergies_chat_save_toast_done,
            Toast.LENGTH_SHORT,
        ).show()

        if (isAdded) findNavController().popBackStack()
    }

    // ──────────────────────────────────────────────────────────────────
    // KB lookups (conservés du lot 14.5c27, écrivent dans l'agent)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Background KB lookup triggered automatically when the agent captures
     * a substance (via DraftFieldUpdated event). Updates BOTH the local
     * draft (for the mini-card UI) AND the agent (so saveAllergyDraft
     * sees the resolved SNOMED code).
     *
     * Three tiers tried in cascade :
     *   Tier 0 : IPS-curated allergy-intolerance valueset (292 codes)
     *   Tier 1 : resolveAllergy (Chemical/Protein allergen categories)
     *   Tier 2 : searchCodes filtered on allergen categories → top 5 cands.
     */
    private fun kickoffSubstanceLookup(substance: String) {
        draft = draft.copy(substanceLookup = SubstanceLookupState.PENDING)
        renderIpsCard()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔎 KB lookup kickoff (substance) · '$substance'")

        viewLifecycleOwner.lifecycleScope.launch {
            val lang = currentLang()
            // ── TIER 0 : IPS-curated valueset ──
            val ipsList = try {
                val list = kbService.getAllergyIntoleranceList(kbManager, lang)
                if (lang == "en") {
                    list.map { item ->
                        val tr = translationsRepo.get(item.code, "en")
                        if (!tr.isNullOrBlank()) item.copy(display = tr) else item
                    }
                } else list
            } catch (e: Throwable) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ getAllergyIntoleranceList err: ${e.message}")
                emptyList()
            }
            val ipsMatch = findIpsMatch(substance, ipsList)
            if (ipsMatch != null) {
                if (!isAdded) return@launch
                applySubstanceResolution(ipsMatch.code, ipsMatch.display, ipsMatch.system)
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ✅ Tier 0 IPS match '$substance' → " +
                        "code=${ipsMatch.code} · display='${ipsMatch.display}'",
                )
                return@launch
            }

            // ── TIER 1 : resolveAllergy (allergen categories only) ──
            val direct = try { kbService.resolveAllergy(substance) } catch (_: Throwable) { null }
            val directConcept = direct?.concept?.takeIf { it.category in ALLERGEN_CATEGORIES }

            val noAccents = stripAccents(substance)
            val retryConcept = if (directConcept == null && noAccents != substance) {
                val r = try { kbService.resolveAllergy(noAccents) } catch (_: Throwable) { null }
                r?.concept?.takeIf { it.category in ALLERGEN_CATEGORIES }
            } else null

            val hit = directConcept ?: retryConcept
            if (hit != null) {
                if (!isAdded) return@launch
                val snomedCode = hit.snomedCode ?: hit.code
                applySubstanceResolution(snomedCode, hit.primaryDisplay, hit.system)
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ✅ Tier 1 resolveAllergy '$substance' → " +
                        "code=$snomedCode · display='${hit.primaryDisplay}'",
                )
                return@launch
            }

            // ── TIER 2 : searchCodes for candidates ──
            val candidates = mutableListOf<SubstanceCandidate>()
            for (q in listOfNotNull(substance, if (noAccents != substance) noAccents else null)) {
                if (candidates.size >= 5) break
                for (cat in ALLERGEN_CATEGORIES) {
                    if (candidates.size >= 5) break
                    val r = try {
                        kbService.searchCodes(q, lang = "en", categoryFilter = cat, maxResults = 3)
                    } catch (_: Throwable) { null }
                    if (r is be.heyman.android.jemmapassdemo.kb.KbSearchResult.Success) {
                        for (h in r.hits) {
                            val sn = h.concept.snomedCode ?: h.concept.code
                            if (candidates.any { it.code == sn }) continue
                            candidates.add(
                                SubstanceCandidate(
                                    code = sn,
                                    display = h.display.ifBlank { h.concept.primaryDisplay },
                                    system = h.concept.system,
                                )
                            )
                            if (candidates.size >= 5) break
                        }
                    }
                }
            }

            if (!isAdded) return@launch
            if (candidates.isNotEmpty()) {
                draft = draft.copy(
                    substanceCode = null,
                    substanceDisplay = null,
                    substanceSystem = null,
                    substanceLookup = SubstanceLookupState.NOT_FOUND_WITH_CANDIDATES,
                    substanceCandidates = candidates,
                )
                allergiesAgentTools.setResolvedSubstanceCode(null, null, null)
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ❓ KB no exact · ${candidates.size} candidates",
                )
            } else {
                draft = draft.copy(
                    substanceCode = null,
                    substanceDisplay = null,
                    substanceSystem = null,
                    substanceLookup = SubstanceLookupState.NOT_FOUND_EMPTY,
                    substanceCandidates = emptyList(),
                )
                allergiesAgentTools.setResolvedSubstanceCode(null, null, null)
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ❌ KB miss complet pour '$substance'")
            }
            renderIpsCard()
        }
    }

    private fun applySubstanceResolution(code: String, display: String, system: String) {
        draft = draft.copy(
            substanceCode = code,
            substanceDisplay = display,
            substanceSystem = system,
            substanceLookup = SubstanceLookupState.FOUND,
            substanceCandidates = emptyList(),
        )
        allergiesAgentTools.setResolvedSubstanceCode(code, display, system)
        renderIpsCard()
        // 🆕 Lot 14.5c30 v5 — auto-resolve FHIR category from KB.
        // Form-side `batchResolveAllergyFhirCategory` knows penicillin →
        // 'medication', peanut → 'food', etc., based on SNOMED hierarchy.
        // We piggyback the same logic to short-circuit Gemma's setCategory
        // call (which can mis-classify with CPU sampler fallback).
        viewLifecycleOwner.lifecycleScope.launch {
            val fhir = try {
                kbService.resolveAllergyFhirCategory(kbManager, code)
            } catch (e: Throwable) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ resolveAllergyFhirCategory err: ${e.message}")
                null
            }
            if (fhir != null && isAdded) {
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🏷️ KB → FHIR category for $code = '$fhir' · applying",
                )
                allergiesAgentTools.applyKbResolvedCategory(fhir)
            }
        }
    }

    private fun kickoffManifestationLookup(manifestation: String) {
        draft = draft.copy(manifestationLookup = SubstanceLookupState.PENDING)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔥 manifestation lookup kickoff · '$manifestation'")

        viewLifecycleOwner.lifecycleScope.launch {
            val lang = currentLang()
            val list = try {
                val rawList = kbService.getAllergyReactionList(kbManager, lang)
                if (lang == "en") {
                    rawList.map { item ->
                        val tr = translationsRepo.get(item.code, "en")
                        if (!tr.isNullOrBlank()) item.copy(display = tr) else item
                    }
                } else rawList
            } catch (e: Throwable) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ getAllergyReactionList err: ${e.message}")
                emptyList()
            }
            val match = findIpsMatch(manifestation, list, isManifestation = true)
            if (!isAdded) return@launch
            if (match != null) {
                draft = draft.copy(
                    manifestationCode = match.code,
                    manifestationDisplay = match.display,
                    manifestationSystem = match.system,
                    manifestationLookup = SubstanceLookupState.FOUND,
                )
                allergiesAgentTools.setResolvedManifestationCode(match.code, match.display, match.system)
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ✅ manifestation IPS match '$manifestation' → " +
                        "code=${match.code} · display='${match.display}'",
                )
            } else {
                draft = draft.copy(
                    manifestationCode = null,
                    manifestationDisplay = null,
                    manifestationSystem = null,
                    manifestationLookup = SubstanceLookupState.NOT_FOUND_EMPTY,
                )
                allergiesAgentTools.setResolvedManifestationCode(null, null, null)
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ❌ manifestation no IPS match for '$manifestation'")
            }
            renderIpsCard()
        }
    }

    private fun findIpsMatch(
        query: String,
        list: List<AllergyReactionItem>,
        isManifestation: Boolean = false,
    ): AllergyReactionItem? {
        if (list.isEmpty()) return null
        val q = normalizeForIpsMatch(query, isManifestation = isManifestation)
        if (q.isBlank()) return null
        // 🆕 Lot 14.5c30 v3 — singular form for FR plural ("poissons" → "poisson").
        // Strip trailing 's' iff len > 3 to avoid butchering short words.
        val qSing = singularizeFr(q)

        // 🆕 Lot 14.5c30 v4 — scoring-based match.
        // Problem case: user says "poissons" → singular "poisson" → matches
        // "Huiles de poisson" (code=735341005) because "poisson" is a word
        // inside that display, even though it's a derivative (fish oil) and
        // not the substance itself. Score every candidate and return the best.
        var best: AllergyReactionItem? = null
        var bestScore = Int.MIN_VALUE
        val derivativeWords = listOf(
            "huile", "graisse", "extrait", "concentre", "fume", "residu",
            "dechet", "produit derive",
        )
        for (item in list) {
            val n = normalizeForIpsMatch(item.display, isManifestation = isManifestation)
            if (n.isBlank()) continue
            val nSing = singularizeFr(n)
            var score = 0

            // Exact match — strongest signal.
            if (n == q || n == qSing || nSing == q || nSing == qSing) {
                score += 1000
            }
            // Word-boundary match of query inside display.
            if (Regex("""\b${Regex.escape(qSing)}\b""").containsMatchIn(nSing)) {
                score += 200
            } else if (Regex("""\b${Regex.escape(q)}\b""").containsMatchIn(n)) {
                score += 150
            } else if (Regex("""\b${Regex.escape(nSing)}\b""").containsMatchIn(qSing) && nSing.length >= 4) {
                // display word is inside query (less common but valid)
                score += 80
            }

            // Skip rows that scored nothing positive — no match at all.
            if (score == 0) continue

            // Penalize derivatives ("Huiles de poisson" when user said "poisson")
            // unless the query itself is about the derivative.
            val nForKw = stripAccents(item.display).lowercase()
            val isDerivative = derivativeWords.any { kw ->
                nForKw.contains(kw) && !qSing.contains(kw)
            }
            if (isDerivative) score -= 500

            // Prefer shorter, more direct displays (closer to the substance).
            score -= n.length / 4

            // Prefer rows where the query appears at the START of the
            // normalized display (e.g. "poisson" matches "poisson" better
            // than "produits de poisson").
            if (nSing.startsWith(qSing) || n.startsWith(q)) {
                score += 100
            }

            if (score > bestScore) {
                bestScore = score
                best = item
            }
        }
        // Reject very weak matches — better to fall back to Tier 1 SNOMED
        // search than return a misleading IPS hit.
        return if (bestScore >= 50) best else null
    }

    /** Strip trailing -s for FR singular form. Safe heuristic: only strip
     *  if the resulting word is still 3+ chars (avoids "as" → "a", "es" → "e"). */
    private fun singularizeFr(s: String): String =
        if (s.length > 3 && s.endsWith("s")) s.dropLast(1) else s

    /**
     * 🆕 Lot 14.5c30 v5 — extract a FR date phrase from user STT input
     * and return ISO YYYY-MM-DD, or null if no valid date found. Patterns
     * handled (case-insensitive, accents stripped) :
     *
     *   • "le 20 janvier 2026" / "20 janvier 2026" / "20 jan 2026"
     *   • "le 1er mars 2024" / "le premier mars 2024"
     *   • "mars 2024" → defaults to day=01
     *   • "20/01/2026" / "20-01-2026" / "20.01.2026"
     *   • "2024-03-01" (already ISO) → passed through
     *
     * Year must be 1900..2100. Day defaults to 01 if month-only.
     */
    private fun extractFrDateAsIso(text: String): String? {
        if (text.isBlank()) return null
        val months = mapOf(
            "janvier" to 1, "janv" to 1, "jan" to 1,
            "fevrier" to 2, "fev" to 2, "fevr" to 2,
            "mars" to 3, "mar" to 3,
            "avril" to 4, "avr" to 4,
            "mai" to 5,
            "juin" to 6,
            "juillet" to 7, "juil" to 7, "jul" to 7,
            "aout" to 8, "aou" to 8,
            "septembre" to 9, "sept" to 9, "sep" to 9,
            "octobre" to 10, "oct" to 10,
            "novembre" to 11, "nov" to 11,
            "decembre" to 12, "dec" to 12,
        )
        val t = stripAccents(text).lowercase()
        fun yearOk(y: Int) = y in 1900..2100
        fun dayOk(d: Int) = d in 1..31

        // Pattern 0 : already ISO "2024-03-01"
        Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""").find(t)?.let { m ->
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val d = m.groupValues[3].toInt()
            if (yearOk(y) && mo in 1..12 && dayOk(d)) {
                return "%04d-%02d-%02d".format(y, mo, d)
            }
        }

        // Pattern 1 : "20 janvier 2026" / "le 20 janvier 2026" / "le 1er mars 2024" / "premier mars 2024"
        val frMonthAlt = months.keys.joinToString("|")
        Regex(
            """(?:le\s+)?(\d{1,2}|1er|1\s*er|premier)\s+($frMonthAlt)\s+(\d{4})""",
        ).find(t)?.let { m ->
            val dRaw = m.groupValues[1]
            val d = when {
                dRaw.startsWith("1er") || dRaw.startsWith("1 er") -> 1
                dRaw == "premier" -> 1
                else -> dRaw.toIntOrNull() ?: return@let
            }
            val mo = months[m.groupValues[2]] ?: return@let
            val y = m.groupValues[3].toInt()
            if (yearOk(y) && dayOk(d)) return "%04d-%02d-%02d".format(y, mo, d)
        }

        // Pattern 2 : "20/01/2026" / "20-01-2026" / "20.01.2026"
        Regex("""\b(\d{1,2})[\/\-\.](\d{1,2})[\/\-\.](\d{4})\b""").find(t)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].toInt()
            if (yearOk(y) && mo in 1..12 && dayOk(d)) {
                return "%04d-%02d-%02d".format(y, mo, d)
            }
        }

        // Pattern 3 : "mars 2024" (no day → default to 01)
        Regex("""\b($frMonthAlt)\s+(\d{4})\b""").find(t)?.let { m ->
            val mo = months[m.groupValues[1]] ?: return@let
            val y = m.groupValues[2].toInt()
            if (yearOk(y)) return "%04d-%02d-01".format(y, mo)
        }

        return null
    }

    private fun normalizeForIpsMatch(s: String, isManifestation: Boolean = false): String {
        var v = stripAccents(s).lowercase().trim()
        if (!isManifestation) {
            v = v.replace(Regex("""^(allergie|intolerance)\s+(a\s+l'|a\s+la\s+|aux\s+|a\s+l\s+|au\s+|a\s+)"""), "")
            v = v.replace(Regex("""^(allergie|intolerance)\s+"""), "")
        }
        if (isManifestation) {
            v = v.replace(Regex("""^(une\s+|un\s+|le\s+|la\s+|l'|les\s+|des\s+|du\s+|de\s+la\s+|de\s+)"""), "")
        }
        v = v.replace(Regex("""\s+"""), " ").trim()
        return v
    }

    private fun stripAccents(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
}
