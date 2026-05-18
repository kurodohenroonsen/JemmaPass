/*
 * LiveScanRepository.kt — JEMMA Pass · Live Scan v0.4 refonte · Lot 1
 *
 * REFACTOR PROFOND vs v0.3 (Lot 5b) :
 *   • SUPPRIME tout le scoring (computeConfidence, lengthFactor)
 *   • SUPPRIME tout l'auto-lock par seuil de confiance + temporel
 *   • SUPPRIME les states Searching/Locked/Capturing/HandedOff
 *   • AJOUTE l'accumulation OCR + KB candidates SANS DÉCISION
 *   • AJOUTE le trigger threshold (nbrOcrWithCodes ≥ 4 OR distinctCodes ≥ 4)
 *   • AJOUTE la transition vers IdentifyingWithGemma (handoff Phase B)
 *
 * ─── Le job de cette classe ───
 *
 * Phase A only. Pour chaque frame OCR :
 *   1. Extraire les tokens candidats (latin + katakana) via JemmaDrugTextProcessor
 *   2. Pour chaque token nouveau (jamais vu) : lookup FTS5 → collecter les ATC
 *   3. Si la frame a produit au moins un code (nouveau ou existant) :
 *      a. ocrTexts.add(rawText)
 *      b. lastBitmap = currentFrame
 *      c. nbrOcrWithCodes++
 *   4. Si seuil atteint : build LiveScanPayload + state = IdentifyingWithGemma
 *
 * Le Fragment observe le state et déclenche Phase B sur transition.
 *
 * ─── Ce qui A DISPARU vs v0.3 ───
 *
 *   ✗ computeConfidence(query, display, matchedVia) — plus de scoring Kotlin
 *   ✗ lengthFactor, MIN_LOCK_CONFIDENCE, LENGTH_REFERENCE — plus de seuils
 *   ✗ checkLock() — plus de décision dans le repo, c'est Gemma qui identifie
 *   ✗ lockOn() / markCapturing() / markHandedOff() — états supprimés
 *   ✗ drugsByAtc avec seenCount / confidence — remplacé par candidatesByAtc.frequency
 *
 * ─── Ce qui REMPLACE ───
 *
 *   ✓ candidatesByAtc: Map<atc, CandidateAtc> — comptage simple
 *   ✓ queriedTokens: Set<String> — dédup des lookups FTS5
 *   ✓ ocrTexts: List<String> — historique pour le prompt Phase B
 *   ✓ lastBitmap: Bitmap? — image au moment du trigger pour Gemma vision
 *   ✓ markCrossChecking / markRendered / updateStreamingExplanation / etc.
 *     — transitions appelées par CrossCheckOrchestrator (Lot 3) et
 *     GemmaExplainer (Lot 5).
 *
 * ─── 2-pass FTS5 lookup conservé ───
 *
 * On garde le double-query (filtered="Medication" puis null) du Lot 2c
 * parce qu'il rattrape les brand names (Augmentin, Doliprane) dont
 * `category` n'est pas "Medication" dans la KB. C'est orthogonal au
 * scoring qu'on supprime — c'est juste de la recherche FTS5 robuste.
 *
 * Log channel : JEMMA-LIVESCAN-REPO
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.graphics.Bitmap
import android.util.Log
import be.heyman.android.jemmapassdemo.kb.KbSearchResult
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "JEMMA-LIVESCAN-REPO"

@Singleton
class LiveScanRepository @Inject constructor(
    private val kb: KnowledgeBaseService,
    private val processor: JemmaDrugTextProcessor,
) {
    // ─── State flow public ──────────────────────────────────────────

    private val _state = MutableStateFlow<LiveScanState>(
        LiveScanState.Accumulating(
            nbrOcrWithCodes = 0,
            distinctCodes = emptyList(),
            lastTextSamples = emptyList(),
        )
    )
    val state: StateFlow<LiveScanState> = _state.asStateFlow()

    // ─── Internal accumulation buffers (mutex-guarded) ─────────────

    private val mutex = Mutex()
    private val ocrTexts = mutableListOf<String>()
    private val candidatesByAtc = mutableMapOf<String, CandidateAtc>()
    private val queriedTokens = mutableSetOf<String>()
    private var lastBitmap: Bitmap? = null

    /**
     * Scope interne pour les opérations fire-and-forget (reset depuis
     * onDestroyView, etc.). SupervisorJob pour ne pas cancel l'app si
     * un reset throw. Dispatchers.Default car nos ops sont CPU-light
     * (DB queries déjà sur IO via KnowledgeBaseService).
     */
    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ─── Phase A entry point ───────────────────────────────────────

    /**
     * Entry point appelé par JemmaLiveScanFragment pour chaque frame
     * OCR. Le Fragment fournit aussi le Bitmap courant car il peut
     * devenir le frame du trigger.
     *
     * Non-bloquant côté UI : tout le travail FTS5 est suspend, et le
     * Fragment appelle ça depuis une coroutine `lifecycleScope.launch`.
     *
     * @param rawText texte brut sorti de ML Kit TextRecognizer
     * @param currentBitmap frame de la caméra (RGBA Bitmap converti
     *   depuis ImageProxy par le Fragment, à chaque frame qui produit
     *   un rawText non vide)
     * @param lang ISO 639-1 ("fr", "ja", "en") — utilisé pour le routing
     *   FTS5 latin vs cjk
     */
    suspend fun handleOcrFrame(
        rawText: String,
        currentBitmap: Bitmap,
        lang: String,
    ) {
        if (rawText.isBlank()) return

        // Court-circuit : si on n'est pas en phase Accumulating, on
        // ignore les nouvelles frames OCR. Évite que des frames qui
        // arrivent après le trigger viennent polluer l'accumulation.
        if (_state.value !is LiveScanState.Accumulating) return

        val tokens = processor.extractCandidateNames(rawText)
        if (tokens.isEmpty()) {
            Log.v(TAG, "[t=${System.currentTimeMillis()}] · frame had no candidate tokens")
            return
        }

        // Lookup FTS5 pour chaque token nouveau (jamais vu). On
        // dédoublonne via queriedTokens pour ne pas hammer la DB
        // sur les même tokens scannés des dizaines de fois.
        val newTokens = mutex.withLock { tokens.filter { it !in queriedTokens } }
        var foundAnyCode = false

        for (token in newTokens) {
            mutex.withLock { queriedTokens.add(token) }
            val newAtcs = lookupToken(token, lang)
            if (newAtcs.isNotEmpty()) foundAnyCode = true
        }

        // Cette frame "compte" si elle a produit au moins un code
        // — nouveau OU pré-existant (un token déjà queried qui pointe
        // sur un ATC déjà connu = la frame confirme le candidat).
        val frameHasCodes = foundAnyCode || mutex.withLock {
            tokens.any { t ->
                candidatesByAtc.values.any { c -> c.matchedText.equals(t, ignoreCase = true) }
            }
        }

        if (frameHasCodes) {
            mutex.withLock {
                ocrTexts.add(rawText)
                lastBitmap = currentBitmap
            }
            emitAccumulatingState()
            checkTriggerThreshold()
        }
    }

    /**
     * Lookup FTS5 d'un token. Double-query :
     *   • Pass 1 : kb.searchCodes(token, lang, "Medication", 3) — high precision
     *   • Pass 2 (si Pass 1 empty) : kb.searchCodes(token, lang, null, 3) — recall
     *
     * Retourne la liste des NOUVEAUX ATC ajoutés (pour le toggle
     * foundAnyCode du caller). Si l'ATC existe déjà, on incrémente
     * juste sa frequency.
     */
    private suspend fun lookupToken(token: String, lang: String): List<String> {
        val tStart = System.currentTimeMillis()

        val r1 = try {
            kb.searchCodes(query = token, lang = lang, categoryFilter = "Medication", maxResults = 3)
        } catch (e: Exception) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ FTS5 pass1 threw on '$token': ${e.message}")
            return emptyList()
        }

        val r2 = if (r1 is KbSearchResult.Success && r1.hits.isEmpty()) {
            // Pass 2 — drop le filtre Medication pour rattraper brand
            // names dont category != Medication.
            try {
                kb.searchCodes(query = token, lang = lang, categoryFilter = null, maxResults = 3)
            } catch (e: Exception) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ FTS5 pass2 threw on '$token': ${e.message}")
                return emptyList()
            }
        } else r1

        if (r2 !is KbSearchResult.Success) {
            Log.v(TAG, "[t=${System.currentTimeMillis()}] · '$token' lookup result not Success")
            return emptyList()
        }

        val newAtcs = mutableListOf<String>()

        for (hit in r2.hits) {
            val atc = hit.concept.atcCode ?: continue
            mutex.withLock {
                val existing = candidatesByAtc[atc]
                if (existing == null) {
                    candidatesByAtc[atc] = CandidateAtc(
                        atc = atc,
                        displayName = hit.concept.primaryDisplay,
                        matchedText = token,
                        matchedLang = hit.matchedVia.ifBlank { "latin" },
                        frequency = 1,
                    )
                    newAtcs.add(atc)
                    Log.i(
                        TAG,
                        "[t=${System.currentTimeMillis()}] 🎯 +$atc " +
                            "(${hit.concept.primaryDisplay}) via '$token' [${hit.matchedVia}] · " +
                            "${System.currentTimeMillis() - tStart}ms",
                    )
                } else {
                    candidatesByAtc[atc] = existing.copy(frequency = existing.frequency + 1)
                }
            }
        }
        return newAtcs
    }

    // ─── State emission ────────────────────────────────────────────

    /**
     * Émet un nouveau state Accumulating avec les compteurs à jour.
     * Appelé après chaque frame qui contribue à l'accumulation.
     */
    private suspend fun emitAccumulatingState() {
        // 🆕 v4 FIX5 — Guard CRITIQUE : si on n'est plus en Accumulating
        // (par ex. la frame précédente a déclenché checkTriggerThreshold
        // → state = IdentifyingWithGemma), on ne doit PAS écraser ce state
        // avec un nouveau snapshot Accumulating. Sinon le runner observe
        // une oscillation Accumulating → IdentifyingWithGemma → Accumulating
        // → IdentifyingWithGemma qui casse le pipeline.
        //
        // Logs Haru qui montraient le bug FIX4 :
        //   19:01:29.641 🚀 trigger threshold reached · nbrOcrWithCodes=2
        //   19:01:29.758 🚀 trigger threshold reached · nbrOcrWithCodes=3
        //   19:01:29.782 🚀 trigger threshold reached · nbrOcrWithCodes=4
        //   ...
        // Le repo emit Accumulating 6× APRÈS le trigger, parce que les
        // frames OCR continuent à arriver pendant les ~280ms avant que
        // navigateUp() ne tue le Fragment et stoppe la caméra.
        if (_state.value !is LiveScanState.Accumulating) {
            Log.v(
                TAG,
                "[t=${System.currentTimeMillis()}] ⏭️ skip emit accumulating · " +
                    "current=${_state.value::class.simpleName}",
            )
            return
        }

        val snapshot = mutex.withLock {
            // 🆕 v4 FIX4 — Sort candidates by frequency desc + map to (atc, name)
            // pour affichage chips live dans l'overlay.
            val sortedDisplays = candidatesByAtc.values
                .sortedByDescending { it.frequency }
                .map { it.atc to it.displayName }

            LiveScanState.Accumulating(
                nbrOcrWithCodes = ocrTexts.size,
                distinctCodes = candidatesByAtc.keys.toList(),
                lastTextSamples = ocrTexts.takeLast(3),
                candidateDisplays = sortedDisplays,
            )
        }
        // 🆕 FIX5 Double-check après le lock : la transition vers
        // IdentifyingWithGemma peut avoir eu lieu pendant mutex.withLock.
        if (_state.value is LiveScanState.Accumulating) {
            _state.value = snapshot
        } else {
            Log.v(
                TAG,
                "[t=${System.currentTimeMillis()}] ⏭️ state changed during mutex · " +
                    "current=${_state.value::class.simpleName}",
            )
        }
    }

    /**
     * Check si on a atteint le seuil de trigger. Si oui, transition
     * vers IdentifyingWithGemma avec le payload prêt.
     *
     * Deux conditions OR :
     *   • nbrOcrWithCodes ≥ TRIGGER_THRESHOLD (4)
     *     → "On a 4 frames différentes qui ont chacune contribué"
     *     → bonne stabilité visuelle, scan posé
     *   • distinctCodes ≥ TRIGGER_THRESHOLD (4)
     *     → "4 candidats ATC distincts identifiés"
     *     → cas où une seule frame riche en texte produit bcp de hits
     *     → Gemma a assez de matière pour disambiguate
     */
    private suspend fun checkTriggerThreshold() {
        val current = _state.value as? LiveScanState.Accumulating ?: return
        val threshold = current.triggerThreshold

        val shouldTrigger = current.nbrOcrWithCodes >= threshold ||
            current.distinctCodes.size >= threshold

        if (!shouldTrigger) return

        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🚀 trigger threshold reached · " +
                "nbrOcrWithCodes=${current.nbrOcrWithCodes} · " +
                "distinctCodes=${current.distinctCodes.size}",
        )

        val payload = mutex.withLock {
            val bitmap = lastBitmap ?: run {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ no bitmap at trigger — aborting")
                return
            }
            LiveScanPayload(
                imageBitmap = bitmap,
                ocrTexts = ocrTexts.toList(),
                candidates = candidatesByAtc.values.toList(),
            )
        }
        _state.value = LiveScanState.IdentifyingWithGemma(
            payload = payload,
            startedAtMs = System.currentTimeMillis(),
        )
    }

    // ─── State transitions (called by orchestrators in Lot 3+) ─────

    /**
     * 🆕 v4.2 UX-B1-STREAM — append un token de streaming Phase B1 (INN
     * extract) au texte courant. Appelé par INNExtractor via askStreaming
     * onToken callback. Le state reste IdentifyingWithGemma, seul
     * b1StreamingText évolue.
     *
     * No-op si le state actuel n'est pas IdentifyingWithGemma (ex: si
     * Phase B1 finit après que CrossChecking ait démarré côté backend
     * malformé).
     */
    fun updateB1Streaming(token: String) {
        val current = _state.value as? LiveScanState.IdentifyingWithGemma ?: return
        _state.value = current.copy(
            b1StreamingText = current.b1StreamingText + token,
        )
    }

    /**
     * 🆕 v4.2 UX-B1-STREAM — reset b1StreamingText à vide. Appelé par
     * INNExtractor avant chaque scan pour ne pas afficher le résiduel
     * du scan précédent.
     */
    fun resetB1Streaming() {
        val current = _state.value as? LiveScanState.IdentifyingWithGemma ?: return
        if (current.b1StreamingText.isEmpty()) return
        _state.value = current.copy(b1StreamingText = "")
    }

    /**
     * Phase C démarre. Appelé par CrossCheckOrchestrator au début de
     * runPhaseC() pour signaler à l'UI que le SQL cross-check est en
     * cours (utilisé pour afficher un spinner court).
     */
    fun markCrossChecking(bestCode: String, drugName: String) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩺 markCrossChecking · $bestCode ($drugName)")
        _state.value = LiveScanState.CrossChecking(bestCode, drugName)
    }

    /**
     * Phase C terminé, Phase D commence. Appelé par CrossCheckOrchestrator
     * une fois le rapport construit et la phrase TTS préparée. La
     * `streamingExplanationText` démarre vide et sera remplie par les
     * appels successifs à `updateStreamingExplanation` depuis
     * GemmaExplainer.
     */
    fun markRendered(state: LiveScanState.RenderedWithVerdict) {
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🎬 markRendered · " +
                "${state.bestCode} (${state.drugName}) · overall=${state.crossCheckReport.overall}",
        )
        _state.value = state
    }

    /**
     * Append un token Gemma stream au texte d'explication courant.
     * Appelé par GemmaExplainer.onToken pendant Phase D streaming.
     *
     * Si on n'est pas dans le state RenderedWithVerdict (race avec
     * un reset par exemple), on ignore silencieusement.
     */
    fun updateStreamingExplanation(token: String) {
        val current = _state.value as? LiveScanState.RenderedWithVerdict ?: return
        _state.value = current.copy(
            streamingExplanationText = current.streamingExplanationText + token,
        )
    }

    /**
     * Marque la fin du stream Gemma + optionnellement attache une
     * SafeAlternative trouvée pendant Phase D.
     */
    fun markExplanationDone(safeAlt: SafeAlternative? = null) {
        val current = _state.value as? LiveScanState.RenderedWithVerdict ?: return
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✅ markExplanationDone · " +
                "safeAlt=${safeAlt?.atc ?: "none"} · " +
                "streamLen=${current.streamingExplanationText.length}",
        )
        _state.value = current.copy(
            explanationDone = true,
            safeAlternative = safeAlt,
        )
    }

    /**
     * Cas spécial : cascade AlternativeFinder épuisée sans trouver
     * de candidate safe. Appelé par AlternativeFinder ou
     * GemmaExplainer quand `findSafe` retourne CascadeResult(safe=null).
     */
    fun markNoSafeFound(bestCode: String, report: CrossCheckReport, tested: Int) {
        Log.w(
            TAG,
            "[t=${System.currentTimeMillis()}] ⚠️ markNoSafeFound · " +
                "$bestCode · tested=$tested candidates",
        )
        _state.value = LiveScanState.NoSafeFound(bestCode, report, tested)
    }

    fun markError(reason: String) {
        Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ $reason")
        _state.value = LiveScanState.Error(reason)
    }

    // ─── Reset ─────────────────────────────────────────────────────

    /**
     * Reset complet — purge tous les buffers et remet le state à
     * Accumulating vide. Appelé au début de chaque session
     * (onViewCreated du Fragment) et à la fin (onDestroyView).
     */
    suspend fun reset() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔴 reset")
        mutex.withLock {
            ocrTexts.clear()
            candidatesByAtc.clear()
            queriedTokens.clear()
            lastBitmap = null
        }
        _state.value = LiveScanState.Accumulating(
            nbrOcrWithCodes = 0,
            distinctCodes = emptyList(),
            lastTextSamples = emptyList(),
        )
    }

    /**
     * Fire-and-forget reset depuis onDestroyView. Le viewLifecycleScope
     * du Fragment est cancellé avant que `reset()` ne complete
     * (problème déjà connu en Lot 2c). On utilise notre internalScope
     * pour garantir l'exécution.
     */
    fun resetAsync() {
        internalScope.launch { reset() }
    }
}
