/*
 * PatientChatFragment.kt — JEMMA Pass · Lot 14.5c40 (PHASE 14)
 *
 * 🆕 Mode vocal MINIMAL pour le pilier Patient (FHIR IPS).
 *
 * Diff de design vs AllergiesChatFragment (1460 lignes) :
 *   • Pas de Gemma audio (PCM → multimodal). On reste sur
 *     SpeechRecognizer system pour la fiabilité — la démo dure 3 min.
 *   • Pas de BgAction chips. Le user voit juste un bubble USER puis
 *     un bubble JEMMA confirmant la capture.
 *   • Pas de boucle multi-tours. Un seul tour : prompt initial via TTS,
 *     l'user dit son nom + sa date de naissance, Gemma extrait, on
 *     popBackStack vers PerSo avec handoff.
 *
 * Flow :
 *
 *   1. onViewCreated
 *      → configureForTask(patient-voice-minimal, PatientAgentTools)
 *      → micState = LOADING (⏳)
 *      → tts.init { speakOneShot(greeting) { startStt() } }
 *
 *   2. STT.onFinal(text)
 *      → bubble USER
 *      → gemma.ask(text) (Gemma appelle setGivenName + setBirthDate)
 *      → bubble JEMMA "Noté : Claude, 4 avril 1979"
 *      → tts.speakOneShot(confirm) { popBack + handoff }
 *
 *   3. PatientHandoff.set(draft) puis nav back via
 *      action_patient_chat_to_perso (popUpTo dest_perso inclusive).
 *      Le PerSo recréé lit consumeHandoff=true et préremplit le form.
 *
 * Tag log : JEMMA-CHAT-PATIENT-VOICE.
 */
package be.heyman.android.jemmapassdemo.ui.profile.perso.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.gemma.GemmaSession
import be.heyman.android.jemmapassdemo.ai.patient.PatientAgentTools
import be.heyman.android.jemmapassdemo.ai.patient.PatientHandoff
import be.heyman.android.jemmapassdemo.ai.stt.JemmaSttService
import be.heyman.android.jemmapassdemo.ai.tts.JemmaTtsService
import be.heyman.android.jemmapassdemo.databinding.FragmentPatientChatBinding
import be.heyman.android.jemmapassdemo.qr.JemmaTranslations
import be.heyman.android.jemmapassdemo.qr.JemmaTextPayloadBuilder
import be.heyman.android.jemmapassdemo.ui.profile.allergies.chat.ChatAdapter
import be.heyman.android.jemmapassdemo.ui.profile.allergies.chat.ChatMessage
import be.heyman.android.jemmapassdemo.ui.profile.allergies.chat.ChatRole
import com.google.ai.edge.litertlm.tool
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PatientChatFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-CHAT-PATIENT-VOICE"
        const val ARG_PROFILE_ID = "profileId"
        private val MSG_ID_SEQ = AtomicLong(1L)
        private fun nextId() = MSG_ID_SEQ.getAndIncrement()
    }

    private var _binding: FragmentPatientChatBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var tts: JemmaTtsService
    @Inject lateinit var stt: JemmaSttService
    @Inject lateinit var gemma: GemmaSession
    @Inject lateinit var patientAgentTools: PatientAgentTools

    private val adapter = ChatAdapter()
    private val messages = mutableListOf<ChatMessage>()

    private enum class MicState { LOADING, IDLE, RECORDING, THINKING, DONE }
    private var micState: MicState = MicState.LOADING

    private var sttActive = false
    private var hasNavigatedBack = false

    // ──────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPatientChatBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎤 onCreateView · profileId=${arguments?.getString(ARG_PROFILE_ID)}")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.patientChatToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.patientChatRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.patientChatRecycler.adapter = adapter

        binding.patientChatMicButton.setOnClickListener { onMicTap() }

        renderDraftCard()

        // ── Agent setup ──
        patientAgentTools.reset()

        // ── Configure Gemma session ──
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                gemma.configureForTask(
                    taskId = "patient-voice-minimal",
                    systemPrompt = buildVoiceSystemPrompt(),
                    tools = listOf(tool(patientAgentTools)),
                    supportImage = false,
                )
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎯 Gemma session configured · patient voice mode")
            } catch (e: Throwable) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ configureForTask failed", e)
            }
        }

        micState = MicState.LOADING
        updateMicUi()

        // ── TTS init + greeting + auto-mic ──
        tts.init {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔊 TTS ready · greeting then auto-mic")
            val greeting = getString(R.string.patient_chat_greeting)
            appendTextMessage(ChatRole.JEMMA, greeting)
            tts.speakOneShot(greeting, ttsLangTag()) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏁 greeting TTS done · auto-tap mic")
                if (_binding == null || !isAdded) return@speakOneShot
                micState = MicState.IDLE
                updateMicUi()
                onMicTap()
            }
        }
    }

    override fun onDestroyView() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔻 onDestroyView · cleanup")
        try { stt.cancel() } catch (_: Throwable) {}
        try { tts.stopAll() } catch (_: Throwable) {}
        _binding = null
        super.onDestroyView()
    }

    // ──────────────────────────────────────────────────────────────────
    // Mic state
    // ──────────────────────────────────────────────────────────────────

    private fun onMicTap() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 mic tap · state=$micState")
        when (micState) {
            MicState.LOADING, MicState.THINKING, MicState.DONE -> {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ⏸ ignored (busy)")
            }
            MicState.IDLE -> startSttIfPossible()
            MicState.RECORDING -> stt.stop()
        }
    }

    private fun startSttIfPossible() {
        val micGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!micGranted) {
            Toast.makeText(requireContext(),
                getString(R.string.patient_chat_mic_perm_needed), Toast.LENGTH_LONG).show()
            return
        }
        if (!stt.isSupported()) {
            Toast.makeText(requireContext(),
                getString(R.string.patient_chat_stt_unsupported), Toast.LENGTH_LONG).show()
            return
        }
        sttActive = true
        micState = MicState.RECORDING
        updateMicUi()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎙 STT.start · lang=${sttLangTag()}")
        stt.start(sttLangTag(), object : JemmaSttService.SttListener {
            override fun onReady() {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🟢 STT ready")
            }
            override fun onPartial(text: String) {
                if (_binding == null) return
                binding.patientChatTranscriptLive.text = text
            }
            override fun onFinal(text: String) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏁 STT final · text='$text'")
            }
            override fun onEnd(fullText: String) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛑 STT end · fullText='$fullText'")
                if (_binding == null || !isAdded) return
                sttActive = false
                binding.patientChatTranscriptLive.text = ""
                if (fullText.isBlank()) {
                    micState = MicState.IDLE
                    updateMicUi()
                    return
                }
                onUserUtterance(fullText)
            }
            override fun onError(code: Int, msg: String) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ❌ STT error · code=$code · msg=$msg")
                if (_binding == null || !isAdded) return
                sttActive = false
                micState = MicState.IDLE
                updateMicUi()
                Toast.makeText(requireContext(),
                    getString(R.string.patient_chat_stt_error_fmt, msg), Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateMicUi() {
        if (_binding == null) return
        val (label, hintRes) = when (micState) {
            MicState.LOADING -> "⏳" to R.string.patient_chat_status_loading
            MicState.IDLE -> "🎤" to R.string.patient_chat_status_idle
            MicState.RECORDING -> "🛑" to R.string.patient_chat_status_recording
            MicState.THINKING -> "🤔" to R.string.patient_chat_status_thinking
            MicState.DONE -> "✅" to R.string.patient_chat_status_done
        }
        binding.patientChatMicButton.text = label
        binding.patientChatStatusLabel.setText(hintRes)
    }

    // ──────────────────────────────────────────────────────────────────
    // User utterance → Gemma → handoff
    // ──────────────────────────────────────────────────────────────────

    private fun onUserUtterance(text: String) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💬 user utterance · '$text'")
        appendTextMessage(ChatRole.USER, text)

        micState = MicState.THINKING
        updateMicUi()

        viewLifecycleOwner.lifecycleScope.launch {
            val t0 = System.currentTimeMillis()
            try {
                gemma.ask(
                    prompt = text,
                    audioClip = null,
                    onPartial = null,
                )
                val dtMs = System.currentTimeMillis() - t0
                val draft = patientAgentTools.snapshot()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ Gemma done in ${dtMs}ms · " +
                    "given='${draft.givenName}' family='${draft.familyName}' birth='${draft.birthDate}' " +
                    "gender='${draft.gender}'")

                if (_binding == null || !isAdded) return@launch

                val gn = draft.givenName.orEmpty()
                val bd = draft.birthDate.orEmpty()
                val gs = draft.gender.orEmpty()
                if (gn.isBlank() && bd.isBlank()) {
                    val sorry = getString(R.string.patient_chat_extract_failed)
                    appendTextMessage(ChatRole.JEMMA, sorry)
                    tts.speakOneShot(sorry, ttsLangTag()) {
                        if (_binding == null || !isAdded) return@speakOneShot
                        micState = MicState.IDLE
                        updateMicUi()
                    }
                    return@launch
                }

                // 🆕 c42 — Two-string strategy :
                //   • visualConfirm : bubble shown in chat, keeps emoji + ISO date
                //     for clarity ("Noté ! Claude · homme · né le 1979-04-04 ✅")
                //   • ttsConfirm    : what the TTS engine actually SPEAKS, with
                //     human-readable French date ("quatre avril 1979"), no emoji,
                //     no hyphens, no parenthetical "(e)" — the TTS would
                //     otherwise read "tiret zéro quatre tiret zéro quatre" and
                //     "parenthèse e parenthèse", which sounds awful in demo.
                val visualConfirm = buildVisualConfirm(gn, gs, bd)
                val ttsConfirm = buildTtsConfirm(gn, gs, bd)
                appendTextMessage(ChatRole.JEMMA, visualConfirm)
                micState = MicState.DONE
                updateMicUi()

                tts.speakOneShot(ttsConfirm, ttsLangTag()) {
                    if (_binding == null || !isAdded) return@speakOneShot
                    pushHandoffAndPop()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ Gemma ask failed", e)
                if (_binding == null || !isAdded) return@launch
                micState = MicState.IDLE
                updateMicUi()
                Toast.makeText(requireContext(),
                    getString(R.string.patient_chat_extract_error_fmt, e.message ?: "?"),
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pushHandoffAndPop() {
        if (hasNavigatedBack) return
        hasNavigatedBack = true
        val draft = patientAgentTools.snapshot()
        PatientHandoff.set(draft)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🚀 navigating back to PerSo · " +
            "given='${draft.givenName}' birth='${draft.birthDate}'")
        try {
            findNavController().navigate(
                R.id.action_patient_chat_to_perso,
                bundleOf(
                    "profileId" to (arguments?.getString(ARG_PROFILE_ID) ?: ""),
                    "consumeHandoff" to true,
                    "pillarKey" to "patient",
                ),
            )
        } catch (e: Throwable) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ navigation failed · falling back to navigateUp", e)
            try { findNavController().navigateUp() } catch (_: Throwable) {}
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Chat rendering helpers
    // ──────────────────────────────────────────────────────────────────

    private fun renderDraftCard() {
        if (_binding == null) return
        val draft = patientAgentTools.snapshot()
        binding.patientChatDraftGivenName.text = getString(
            R.string.patient_chat_draft_given_name_fmt,
            if (draft.givenName.isNullOrBlank()) "—" else draft.givenName,
        )
        binding.patientChatDraftBirthDate.text = getString(
            R.string.patient_chat_draft_birth_date_fmt,
            if (draft.birthDate.isNullOrBlank()) "—" else draft.birthDate,
        )
        // 🆕 c42 — 3rd row for gender
        binding.patientChatDraftGender.text = getString(
            R.string.patient_chat_draft_gender_fmt,
            genderToHuman(draft.gender),
        )
    }

    private fun appendTextMessage(role: ChatRole, text: String) {
        val msg = ChatMessage.Text(id = nextId(), role = role, text = text, streaming = false)
        messages.add(msg)
        adapter.submitList(messages.toList())
        if (_binding != null) {
            binding.patientChatRecycler.scrollToPosition(messages.size - 1)
        }
        // 🆕 c40 — re-render draft card after each turn (so user sees fields fill up).
        renderDraftCard()
    }

    // ──────────────────────────────────────────────────────────────────
    // Locale helpers
    // ──────────────────────────────────────────────────────────────────

    private fun currentLang(): String {
        val tag = Locale.getDefault().language.lowercase()
        val supported = JemmaTextPayloadBuilder.Lang.values().map { it.isoCode.lowercase() }
        return if (tag in supported) tag else "en"
    }

    private fun sttLangTag(): String {
        return Locale.getDefault().toLanguageTag()
    }

    private fun ttsLangTag(): String = sttLangTag()

    // ──────────────────────────────────────────────────────────────────
    // System prompt
    // ──────────────────────────────────────────────────────────────────

    private fun buildVoiceSystemPrompt(): String {
        val langName = Locale(currentLang()).getDisplayLanguage(Locale.ENGLISH)
        return """
You are a voice agent for extracting patient identity data for JEMMA Pass.

The user will speak their first name, gender, and date of birth aloud, in any order.
The user's transcribed audio input will be in $langName.

📋 STRICT RULES:
1. NEVER write free text in your response — your only outputs are @Tool calls.
2. NEVER INVENT data. If a field is not clear in the audio, do not call it.
3. ⚠ DO NOT INFER gender from the first name (Marie ≠ F automatically, Claude can be M or F). Call setGender ONLY if the user explicitly states it (man/woman/male/female/boy/girl/mr/mrs).
4. ALWAYS end with finalizePatientDraft().

🛠 @Tool PRIORITY TOOLS:
• setGivenName(name) — first name(s)
• setFamilyName(name) — family name (if the user says it)
• setBirthDate(isoDate) — date of birth, YYYY-MM-DD format
• setGender(letter) — gender, ONE single letter: M (man/male/boy/mr), F (woman/female/girl/mrs), O (other/non-binary)
• finalizePatientDraft() — ONCE at the end

🎯 EXAMPLES (transcribed audio):

User: "My name is Claude, I am a man, born on April 4, 1979."
  → setGivenName("Claude")
  → setGender("M")
  → setBirthDate("1979-04-04")
  → finalizePatientDraft()

User: "Marie Dupont, woman, March 15, 1985"
  → setGivenName("Marie")
  → setFamilyName("Dupont")
  → setGender("F")
  → setBirthDate("1985-03-15")
  → finalizePatientDraft()

User: "My last name is Tanaka, first name Haru, I am a woman, June 5, 1944"
  → setGivenName("Haru")
  → setFamilyName("Tanaka")
  → setGender("F")
  → setBirthDate("1944-06-05")
  → finalizePatientDraft()

User: "My name is Claude, born on April 4, 1979." (gender NOT specified)
  → setGivenName("Claude")
  → setBirthDate("1979-04-04")
  → finalizePatientDraft()
  (NO call to setGender — the user did not say it explicitly)

Think silently, then chain the @Tool calls.
""".trimIndent()
    }

    // ──────────────────────────────────────────────────────────────────
    // 🆕 c42 · Confirmation builders (visual vs TTS)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Build the chat bubble version. Keeps emoji + ISO date for visual
     * clarity. Example :
     *   "Noté ! Claude · homme · né le 1979-04-04 ✅"
     *   "Noté ! Marie · femme · née le 1985-03-15 ✅"
     *   "Noté ! Claude · né le 1979-04-04 ✅"  (gender unknown)
     */
    private fun buildVisualConfirm(givenName: String, gender: String, birthDate: String): String {
        val lang = currentLang()
        val noted = JemmaTranslations.getChatString("noted_visual", lang)
        val sb = StringBuilder(noted)
        sb.append(if (givenName.isNotBlank()) givenName else "—")
        val genderHuman = genderToHumanOrNull(gender, lang)
        if (genderHuman != null) sb.append(" · ").append(genderHuman)
        if (birthDate.isNotBlank()) {
            sb.append(" · ")
            val bornStr = when (lang) {
                "fr" -> {
                    val bornVerb = if (gender.uppercase() == "F") "née" else "né"
                    "$bornVerb le $birthDate"
                }
                "ja" -> "$birthDate 生まれ"
                "es" -> "nacido(a) el $birthDate"
                "de" -> "geboren am $birthDate"
                "it" -> "nato(a) il $birthDate"
                "pt" -> "nascido(a) em $birthDate"
                "nl" -> "geboren op $birthDate"
                else -> "born on $birthDate"
            }
            sb.append(bornStr)
        }
        sb.append(" ✅")
        return sb.toString()
    }

    /**
     * Build the version the TTS engine SPEAKS. Critical : NO emoji, NO ISO
     * date with hyphens (TTS reads "tiret zéro quatre"), NO "(e)"
     * parenthetical, NO bullet · separators. Plain natural-language.
     */
    private fun buildTtsConfirm(givenName: String, gender: String, birthDate: String): String {
        val lang = currentLang()
        val nameStr = if (givenName.isNotBlank()) {
            givenName
        } else {
            JemmaTranslations.getChatString("unknown_person", lang)
        }
        
        val greeting = JemmaTranslations.getChatString("noted_tts", lang)
        val sb = StringBuilder(greeting)
        sb.append(nameStr)
        
        val genderHuman = genderToHumanOrNull(gender, lang)
        if (genderHuman != null) sb.append(", ").append(genderHuman)
        
        if (birthDate.isNotBlank()) {
            val spokenDate = isoDateToSpoken(birthDate, lang)
            val bornStr = when (lang) {
                "fr" -> {
                    val bornVerb = if (gender.uppercase() == "F") "née" else "né"
                    ", $bornVerb le $spokenDate"
                }
                "ja" -> ", $spokenDate 生まれ"
                "es" -> ", nacido(a) el $spokenDate"
                "de" -> ", geboren am $spokenDate"
                "it" -> ", nato(a) il $spokenDate"
                "pt" -> ", nascido(a) em $spokenDate"
                "nl" -> ", geboren op $spokenDate"
                else -> ", born on $spokenDate"
            }
            sb.append(bornStr)
        }
        sb.append(".")
        return sb.toString()
    }

    /** Returns "homme" / "femme" / "autre" / "man" / "woman" / "other" / null if unknown. */
    private fun genderToHumanOrNull(gender: String?, lang: String): String? {
        return JemmaTranslations.genderToHuman(gender, lang)
    }

    /** Used by renderDraftCard — never null. */
    private fun genderToHuman(gender: String?): String {
        return genderToHumanOrNull(gender, currentLang()) ?: "—"
    }

    /**
     * Convert "1979-04-04" → "4 avril 1979" or "April 4, 1979" (TTS-friendly).
     * Used ONLY for the TTS string — the chat bubble keeps the ISO form
     * because it's structured and clearer for reading.
     */
    private fun isoDateToSpoken(iso: String, lang: String): String {
        return try {
            val sdfInput = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val date = sdfInput.parse(iso) ?: return iso
            
            val locale = java.util.Locale(lang)
            val sdfOutput = if (lang == "ja") {
                java.text.SimpleDateFormat("yyyy年M月d日", locale)
            } else {
                java.text.SimpleDateFormat("d MMMM yyyy", locale)
            }
            sdfOutput.format(date)
        } catch (e: Exception) {
            iso
        }
    }
}
