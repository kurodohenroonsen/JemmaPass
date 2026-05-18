/*
 * ExtractedMedication.kt — Lot 14.5c (PHASE 14)
 *
 * Représente une entrée détectée par le pipeline OCR + Gemma + KB,
 * prête à être présentée dans AssistantMultiPreviewFragment.
 *
 * Pour le handoff entre AssistantPipelineFragment (qui produit) et
 * AssistantMultiPreviewFragment (qui consomme), on utilise un singleton
 * `AssistantHandoff` — la sérialisation en Bundle d'objets imbriqués
 * `ResolvedConcept` serait possible avec Moshi mais c'est complexité
 * inutile : le flow est synchrone et les deux fragments vivent dans la
 * même Activity. Singleton statique = OK.
 *
 * Le handoff est CLEARED après consommation pour éviter les fuites
 * mémoire et les confusions sur navigation back-and-forth.
 */
package be.heyman.android.jemmapassdemo.ai.assistant

import be.heyman.android.jemmapassdemo.kb.KbConcept
import be.heyman.android.jemmapassdemo.kb.ResolvedConcept
import be.heyman.android.jemmapassdemo.qr.JMedication

/**
 * Un médicament détecté par la pipeline. Cumule les infos de chaque
 * étape pour traceability + affichage utilisateur.
 *
 * 🆕 Lot 14.5c3 :
 *   • `gemmaSkipped` : la page OCR existe mais Gemma n'a rien identifié.
 *     Apparaît quand même dans le preview avec un style "❌ Non identifié"
 *     pour que l'user voie qu'on a raté + puisse ajouter manuellement.
 *   • `doseHints` : doses possibles trouvées dans la KB pour cet ATC
 *     (e.g. ["20 mg"] pour bilastine, ["1000 mg", "500 mg"] pour
 *     paracetamol). Le premier est pré-rempli dans la JMedication.
 *
 * @property pageIndex 0-based, l'index de la page OCR source
 * @property ocrSnippet bloc OCR brut (utile pour debug + display "ce qu'on a lu")
 * @property gemmaCandidates ce que Gemma a proposé (3-5 candidats INN)
 * @property topCandidate gemmaCandidates.firstOrNull()
 * @property kbResolution résultat KB — Exact / Prefix / Contains / NotFound
 * @property doseHints doses possibles depuis la KB (e.g. "20 mg")
 * @property gemmaSkipped true si Gemma a renvoyé c=[] pour cette page
 * @property selected coché par défaut si KB résolu, décoché si skip ou not found
 */
data class ExtractedMedication(
    val pageIndex: Int,
    val ocrSnippet: String,
    val gemmaCandidates: List<String>,
    val topCandidate: String?,
    val kbResolution: ResolvedConcept,
    val doseHints: List<String> = emptyList(),
    val gemmaSkipped: Boolean = false,
    var selected: Boolean = true,
    val visionVerified: Boolean = false,
    val visionReason: String? = null,
    val imageUri: String? = null,
) {

    /** Nom à afficher dans la row preview. */
    fun displayName(): String {
        if (gemmaSkipped) {
            val lang = java.util.Locale.getDefault().language
            return when (lang) {
                "fr" -> "(non identifié)"
                "ja" -> "(未特定)"
                else -> "(not identified)"
            }
        }
        val kb = kbConcept()
        if (kb != null) return kb.primaryDisplay
        if (!topCandidate.isNullOrBlank()) return topCandidate
        return ocrSnippet.lines().firstOrNull()?.take(60) ?: "?"
    }

    /** Premier dose hint (le plus commun dans la KB) ou null. */
    val firstDose: String? get() = doseHints.firstOrNull()

    /** ATC code si résolu, sinon null. */
    fun atcCode(): String? = kbConcept()?.atcCode

    /** Le KbConcept underlying si résolu, sinon null. */
    fun kbConcept(): KbConcept? = when (val r = kbResolution) {
        is ResolvedConcept.Exact -> r.value
        is ResolvedConcept.Prefix -> r.value
        is ResolvedConcept.Contains -> r.value
        ResolvedConcept.NotFound -> null
    }

    /** True si la résolution KB a abouti (peu importe le tier). */
    val isResolved: Boolean
        get() = kbResolution !is ResolvedConcept.NotFound

    /**
     * 🆕 Lot 14.5c3 — Parse le firstDose en (value, unit) pour persistence.
     * Exemple : "20 mg" → ("20", "mg"). Null si pas de dose ou parse fail.
     */
    fun doseAsValueUnit(): Pair<String, String>? {
        val d = firstDose ?: return null
        val m = doseValueUnitRegex.find(d) ?: return null
        return m.groupValues[1] to m.groupValues[2].lowercase()
    }

    /**
     * Convertit en JMedication pour persistence dans le profil JEMMA.
     * 🆕 Lot 14.5c3 — Remplit `v` (value) + `u` (unit) si une dose hint
     * a été trouvée dans la KB. Le champ `t` (timing) reste null (déduit
     * en aval par disambiguation multimodale au Lot 14.6 future).
     */
    fun toJMedication(): JMedication {
        val kb = kbConcept()
        val (v, u) = doseAsValueUnit() ?: (null to null)
        return JMedication(
            c = kb?.atcCode ?: kb?.code,
            t = null,
            r = null,
            v = v,
            u = u,
            rs = null,
            rc = null,
        )
    }

    companion object {
        private val doseValueUnitRegex = Regex(
            """(\d+(?:\.\d+)?)\s*(mg|ml|mcg|µg|g|iu|ui)\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}

/**
 * Singleton transient pour passer la liste d'extractions entre
 * AssistantPipelineFragment (set après pipeline OK) et
 * AssistantMultiPreviewFragment (consume au onViewCreated).
 *
 * 🆕 Lot 14.5c2 — Transporte aussi le `targetProfileId` de fin en fin.
 * MedicationsEditFragment le set juste avant de naviguer vers le
 * pipeline ; MultiPreview le consomme au save batch. Évite le bug du
 * Lot 14.5c1 où le save retombait sur `profilesRepo.currentProfileId`
 * (= profil actif global) au lieu du profil que l'utilisateur édite
 * réellement.
 *
 * Pourquoi pas un ViewModel partagé ? L'overhead de NavGraphViewModel
 * + serializer chaque ResolvedConcept en Parcelable serait disproportionné
 * pour ce flow synchrone à 2-3 écrans. Singleton statique + clear =
 * pragmatique et suffisant pour la phase hackathon.
 *
 * ⚠ Limitation : si le process est killé pendant le flow (peu probable
 * sur navigation immédiate), le handoff est perdu et le preview tombe
 * sur les 3 mocks de fallback + le save sur le current profile.
 * Acceptable pour un POC hackathon.
 */
object AssistantHandoff {
    @Volatile
    private var pending: List<ExtractedMedication>? = null

    /**
     * 🆕 Lot 14.5c2 — profil cible explicite pour le save batch.
     * Set par MedicationsEditFragment juste avant de naviguer vers
     * AssistantPipelineFragment. Consumé par AssistantMultiPreviewFragment
     * au moment du save. Null = pas de cible explicite → MultiPreview
     * tombe en fallback sur `profilesRepo.currentProfileId`.
     */
    @Volatile
    private var targetProfileId: String? = null

    fun set(items: List<ExtractedMedication>) {
        pending = items
    }

    /** Récupère ET clear (consommation one-shot). */
    fun consume(): List<ExtractedMedication> {
        val r = pending ?: emptyList()
        pending = null
        return r
    }

    /** Peek sans clear (pour debug / log). */
    fun peek(): List<ExtractedMedication>? = pending

    /** 🆕 Lot 14.5c2 — set le profil cible (avant navigate vers pipeline). */
    fun setTargetProfile(profileId: String?) {
        targetProfileId = profileId
    }

    /**
     * 🆕 Lot 14.5c2 — récupère le profil cible.
     * Volontairement NON-clearant car le pipeline → preview peut faire un
     * roundtrip (user back puis re-tape Save). Cleared explicitement par
     * MedicationsEditFragment au prochain démarrage de pipeline.
     */
    fun getTargetProfile(): String? = targetProfileId

    /** 🆕 Lot 14.5c2 — reset complet (items + targetProfileId). */
    fun clearAll() {
        pending = null
        targetProfileId = null
    }
}
