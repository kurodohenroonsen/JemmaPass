/*
 * IpsAllergyDraft.kt — JEMMA Pass · Lot 14.5c24 (PHASE 14)
 *
 * Snapshot incrémental de l'AllergyIntolerance FHIR R4 / IPS en cours
 * de capture par l'assistante vocale Jemma.
 *
 * 🆕 Lot 14.5c24 — Résolution KB INLINE pendant le chat :
 *   - substanceCode/Display/System : code SNOMED canonique résolu via
 *     KnowledgeBaseService.resolveAllergy dès la capture de la substance
 *   - substanceLookup : état de la résolution (Pending/Found/NotFound...)
 *   - substanceCandidates : si pas trouvé en exact, shortlist d'allergènes
 *     proposée à l'user via Gemma pour qu'il choisisse.
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies.chat

enum class SubstanceLookupState {
    IDLE,
    PENDING,
    FOUND,
    NOT_FOUND_WITH_CANDIDATES,
    NOT_FOUND_EMPTY,
}

data class SubstanceCandidate(
    val code: String,
    val display: String,
    val system: String,
)

data class IpsAllergyDraft(
    val substance: String? = null,
    val category: String? = null,
    val severity: String? = null,
    val status: String? = null,
    val manifestation: String? = null,
    val onsetDate: String? = null,

    // 🆕 Lot 14.5c24 — Résolution KB pendant le chat.
    val substanceCode: String? = null,
    val substanceDisplay: String? = null,
    val substanceSystem: String? = null,
    val substanceLookup: SubstanceLookupState = SubstanceLookupState.IDLE,
    val substanceCandidates: List<SubstanceCandidate> = emptyList(),

    // 🆕 Lot 14.5c27 — Résolution KB de la manifestation (réaction observée).
    // Match contre allergy-reaction-snomed-ct-ips-free-set (29 codes IPS).
    val manifestationCode: String? = null,
    val manifestationDisplay: String? = null,
    val manifestationSystem: String? = null,
    val manifestationLookup: SubstanceLookupState = SubstanceLookupState.IDLE,
) {
    val filledCount: Int get() = listOfNotNull(
        substance, category, severity, status, manifestation, onsetDate
    ).size

    val isMinimallyComplete: Boolean get() = !substance.isNullOrBlank()

    val isSubstanceResolved: Boolean get() =
        substanceLookup == SubstanceLookupState.FOUND && !substanceCode.isNullOrBlank()

    val isManifestationResolved: Boolean get() =
        manifestationLookup == SubstanceLookupState.FOUND && !manifestationCode.isNullOrBlank()
}
