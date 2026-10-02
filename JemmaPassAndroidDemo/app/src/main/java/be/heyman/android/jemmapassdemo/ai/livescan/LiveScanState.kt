/*
 * LiveScanState.kt — JEMMA Pass · Live Scan v0.4 refonte · Lot 1
 *
 * State machine de la refonte. On passe de "score-based auto-lock" à
 * "accumulate → identify with Gemma → cross-check SQL → render verdict".
 *
 * 5 états (+ Error) :
 *   1. Accumulating          — Phase A en cours, on collecte OCR + codes KB
 *   2. IdentifyingWithGemma  — Phase B en cours, Gemma vision tourne
 *   3. CrossChecking         — Phase C en cours, SQL Kotlin pur (~500ms)
 *   4. RenderedWithVerdict   — Phase D en streaming, badges + TTS + Gemma stream
 *   5. NoSafeFound           — cas spécial : cascade alternative épuisée
 *   6. Error                 — fatal
 *
 * Annulable à tout moment via repo.reset() — retour à Accumulating.
 *
 * ─── Severity unifié ───
 *
 * On crée notre propre enum `Severity` plutôt que de réutiliser
 * `AllergyCriticality` ou `CrossSeverity` parce qu'on a besoin de
 * comparer des sévérités CROSS-PILLARS (allergy HIGH vs DDI MAJOR vs
 * disease MINOR). Un seul enum + `ordinal` natural ordering →
 * `maxByOrNull { it.severity.ordinal }` gratuit.
 *
 * Les mappers vers les enums du legacy sont en bas de ce fichier.
 *
 * ─── Patient PII isolation ───
 *
 * `CrossCheckReport` peut être passé au LLM via tool JSON (Phase D
 * Gemma explain) sans risque PII : il ne contient que des ATC codes
 * et des display names anonymisés (le nom de la victime n'apparaît
 * jamais).
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.graphics.Bitmap
import be.heyman.android.jemmapassdemo.kb.AllergyCriticality
import be.heyman.android.jemmapassdemo.kb.CrossSeverity
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict

// ──────────────────────────────────────────────────────────────────────
// State machine
// ──────────────────────────────────────────────────────────────────────

sealed class LiveScanState {

    /**
     * Phase A — live OCR streaming. On accumule sans décider.
     *
     * @param nbrOcrWithCodes nombre de frames OCR qui ont produit au moins 1 ATC
     * @param distinctCodes ATC codes uniques vus jusqu'à présent
     * @param lastTextSamples dernières lignes OCR brutes pour preview UI
     * @param triggerThreshold seuil de déclenchement (par défaut 4)
     */
    data class Accumulating(
        val nbrOcrWithCodes: Int,
        val distinctCodes: List<String>,
        val lastTextSamples: List<String>,
        /**
         * 🆕 v4 FIX4 — Liste des candidats ATC découverts pendant l'OCR,
         * triés par fréquence desc. (atc, displayName) pairs pour affichage
         * de chips verts live dans l'overlay (effet "JEMMA découvre").
         */
        val candidateDisplays: List<Pair<String, String>> = emptyList(),
        val triggerThreshold: Int = TRIGGER_THRESHOLD,
    ) : LiveScanState()

    /**
     * Phase B — Gemma multimodal en cours. Latence attendue 8-15s
     * sur Pixel 9 Gemma 4 E4B.
     */
    data class IdentifyingWithGemma(
        val payload: LiveScanPayload,
        val startedAtMs: Long,
        // 🆕 v4.2 UX-B1-STREAM — texte streamé pendant Phase B1 (INN extract).
        // Construit token par token via LiveScanRepository.updateB1Streaming().
        // Affiché dans la zone "Décision Gemma vision" du panneau.
        val b1StreamingText: String = "",
    ) : LiveScanState()

    /**
     * Phase C — Kotlin SQL cross-check en cours. Typiquement <500ms.
     */
    data class CrossChecking(
        val bestCode: String,
        val drugName: String,
    ) : LiveScanState()

    /**
     * Phase D — état final principal. Le verdict est rendu : badges
     * UI visibles, TTS static prononcé (ou en cours), Gemma stream en
     * cours d'arrivée dans `streamingExplanationText`.
     *
     * `safeAlternative` est null tant que la cascade Phase D #2 n'a
     * pas trouvé d'alternative (ou n'a pas été déclenchée car pas
     * de Major).
     */
    data class RenderedWithVerdict(
        val bestCode: String,
        val drugName: String,
        val crossCheckReport: CrossCheckReport,
        val ttsStaticPhrase: String,
        val streamingExplanationText: String = "",
        val explanationDone: Boolean = false,
        val safeAlternative: SafeAlternative? = null,
        // 🆕 FIX7 — Info verbose pour les juges (panneau PatientDetail)
        val lockReason: String = "",
        val ocrFrames: List<String> = emptyList(),
        val kbCandidates: List<Pair<String, String>> = emptyList(),
        val phaseBDurationMs: Long = 0L,
        val phaseCDurationMs: Long = 0L,
        val phaseDDurationMs: Long = 0L,
    ) : LiveScanState()

    /**
     * Cas spécial : Phase C trouve Major, mais la cascade
     * AlternativeFinder a épuisé ses candidats sans en trouver un sûr.
     * UI doit afficher "consulter médecin" en rouge.
     */
    data class NoSafeFound(
        val bestCode: String,
        val crossCheckReport: CrossCheckReport,
        val candidatesTested: Int,
    ) : LiveScanState()

    data class Error(val reason: String) : LiveScanState()

    companion object {
        /**
         * Trigger : (frames OCR avec codes) >= TRIGGER_THRESHOLD
         * OU (codes ATC distincts vus) >= TRIGGER_THRESHOLD.
         *
         * 4 est calibré pour qu'on déclenche après ~3-5s de scan
         * stable (Pixel 9 ~10 fps OCR ÷ 2 si beaucoup de candidates
         * par frame). Trop bas → Phase B sur data partielle.
         * Trop haut → user attend trop avant identification.
         */
        const val TRIGGER_THRESHOLD = 4
    }
}

// ──────────────────────────────────────────────────────────────────────
// Phase A → Phase B handoff payload
// ──────────────────────────────────────────────────────────────────────

/**
 * Bundle envoyé de Phase A vers Phase B. Contient tout ce dont Gemma
 * a besoin pour identifier le drug : la dernière image, les textes
 * OCR collectés, et les candidats ATC déjà résolus par FTS5.
 *
 * `imageBitmap` est le frame au moment exact du trigger threshold.
 * On garde le dernier frame qui a contribué à l'accumulation (le
 * plus stable, généralement).
 */
data class LiveScanPayload(
    val imageBitmap: Bitmap,
    val ocrTexts: List<String>,
    val candidates: List<CandidateAtc>,
)

/**
 * Un candidat ATC pré-résolu par FTS5 pendant Phase A.
 *
 * @param atc le code ATC L5 (ex: "B01AC06")
 * @param displayName nom affichable (ex: "aspirin / dipyridamole")
 * @param matchedText token OCR qui a déclenché le match (ex: "ACETYLSALICYLIQUE")
 * @param matchedLang "latin" ou "cjk" — d'où vient le match FTS5
 * @param frequency nombre de tokens distincts qui ont pointé sur cet ATC
 *   (plus c'est haut, plus le candidat est "confirmé" par le scan)
 */
data class CandidateAtc(
    val atc: String,
    val displayName: String,
    val matchedText: String,
    val matchedLang: String,
    val frequency: Int,
)

// ──────────────────────────────────────────────────────────────────────
// Cross-check report (Phase C output, adapté de KbCrossCheck legacy)
// ──────────────────────────────────────────────────────────────────────

/**
 * Output de Phase C cross-check. Adapté du `CrossCheckResult` legacy
 * de KbCrossCheck en :
 *   • normalisant les sévérités (AllergyCriticality + CrossSeverity → Severity)
 *   • précalculant `overall` = la sévérité maximale cross-pillars
 *     (UC-SAFE-SCAN : jamais NONE quand le check n'a pas (entièrement) tourné —
 *     voir `ScanSafety.displaySeverity` ; un check non fait remonte en MODERATE)
 *   • virant le pillar "duplicate" (n'existe pas dans le legacy)
 *
 * Cet objet est sûr à passer au LLM (zéro PII) : il ne contient que
 * des ATC codes et des display names anonymisés.
 */
data class CrossCheckReport(
    val drugAtc: String,
    val drugName: String,
    val overall: Severity,
    val allergyHits: List<AllergyHit>,
    val ddiHits: List<DdiHit>,
    val conditionHits: List<ConditionHit>,
    /**
     * UC-SAFE-SCAN — what may be said about this report. Only [KbSafetyVerdict.CLEAN]
     * may be rendered / spoken as "no interaction". The default is fail-safe : a report
     * built without a verdict is NOT_CHECKED (or ALERT when it carries hits).
     */
    val verdict: KbSafetyVerdict =
        if (allergyHits.isNotEmpty() || ddiHits.isNotEmpty() || conditionHits.isNotEmpty()) {
            KbSafetyVerdict.ALERT
        } else {
            KbSafetyVerdict.NOT_CHECKED
        },
    /** True only when the drug was recognised and the three pillars were really verified. */
    val fullyChecked: Boolean = false,
) {
    /** "Checked and nothing found". Read this, never `overall == NONE` or empty hit lists alone. */
    val isClean: Boolean
        get() = verdict == KbSafetyVerdict.CLEAN

    val totalHits: Int
        get() = allergyHits.size + ddiHits.size + conditionHits.size
}

/** Sévérité unifiée cross-pillars. ordinal: NONE=0 < MINOR=1 < MODERATE=2 < MAJOR=3. */
enum class Severity { NONE, MINOR, MODERATE, MAJOR }

/** Une hit allergie patient × candidate drug. */
data class AllergyHit(
    val patientAllergyCode: String,
    val patientAllergyName: String,
    val crossReactiveClass: String,
    val severity: Severity,
    val source: String,
)

/** Une hit DDI (drug × drug du patient). */
data class DdiHit(
    val withDrugAtc: String,
    val withDrugName: String,
    val severity: Severity,
    val mechanism: String?,
    val description: String?,
)

/** Une hit drug × disease/condition du patient. */
data class ConditionHit(
    val conditionCode: String,
    val conditionName: String,
    val severity: Severity,
    val source: String,
)

// ──────────────────────────────────────────────────────────────────────
// Alternative search (Phase D #2 output)
// ──────────────────────────────────────────────────────────────────────

/**
 * Alternative drug trouvée par la cascade `AlternativeFinder`.
 * Garanti d'être "safer" : son `checkedReport.overall` est NONE ou MINOR.
 */
data class SafeAlternative(
    val atc: String,
    val displayName: String,
    val rationale: String,
    val checkedReport: CrossCheckReport,
)

// ──────────────────────────────────────────────────────────────────────
// Mappers Legacy → Severity unifié
// ──────────────────────────────────────────────────────────────────────

/**
 * AllergyCriticality (HIGH / LOW / UNABLE_TO_ASSESS) → Severity.
 *
 * Pourquoi HIGH = MAJOR : une allergie connue HIGH (sévère) sur un
 * drug class match → contre-indication absolue.
 *
 * Pourquoi LOW = MODERATE : allergie historique légère → prudence
 * recommandée mais pas blocking.
 *
 * Pourquoi UNABLE_TO_ASSESS = MINOR : on n'a pas pu confirmer la
 * sévérité → trace mais pas d'alerte forte.
 */
fun AllergyCriticality.toSeverity(): Severity = when (this) {
    AllergyCriticality.HIGH -> Severity.MAJOR
    AllergyCriticality.LOW -> Severity.MODERATE
    AllergyCriticality.UNABLE_TO_ASSESS -> Severity.MINOR
}

/**
 * CrossSeverity (MAJOR / MODERATE / MINOR / UNKNOWN / NONE) → Severity.
 *
 * UNKNOWN → MINOR (interaction reportée mais sans sévérité confirmée
 * dans DDInter — on trace).
 */
fun CrossSeverity.toSeverity(): Severity = when (this) {
    CrossSeverity.MAJOR -> Severity.MAJOR
    CrossSeverity.MODERATE -> Severity.MODERATE
    CrossSeverity.MINOR -> Severity.MINOR
    CrossSeverity.UNKNOWN -> Severity.MINOR
    CrossSeverity.NONE -> Severity.NONE
}
