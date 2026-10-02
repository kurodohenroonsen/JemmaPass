/*
 * AllergyFormMerge.kt — pure merge of the allergy form into the stored entry (UC-ALG-004).
 *
 * The form shows and edits ONE reaction. An imported or dictated allergy can carry
 * several (urticaria + anaphylaxis) : the ones the form does not show must survive
 * load → edit → save. No Android dependency : covered by AllergyFormMergeTest.
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies

import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JReaction

object AllergyFormMerge {

    /**
     * Index of the reaction the form displays : the first one that has a manifestation
     * code (the form cannot render a reaction without a code). -1 when there is none.
     */
    fun displayedReactionIndex(reactions: List<JReaction>): Int =
        reactions.indexOfFirst { !it.manifestationCode.isNullOrBlank() }

    /** The reaction the form displays, or null. */
    fun displayedReaction(reactions: List<JReaction>): JReaction? =
        reactions.getOrNull(displayedReactionIndex(reactions))

    /** Number of stored reactions the form does not show. */
    fun hiddenReactionCount(reactions: List<JReaction>): Int =
        if (displayedReactionIndex(reactions) >= 0) reactions.size - 1 else reactions.size

    /**
     * Rebuilds the reaction list after an edit.
     *   - [edited] replaces the displayed reaction, at the same position ;
     *   - [edited] == null (cleared in the form) removes the displayed reaction only ;
     *   - when nothing was displayed, [edited] is appended ;
     *   - every other reaction is kept, in order.
     */
    fun mergeReactions(existing: List<JReaction>, edited: JReaction?): List<JReaction> {
        val shown = displayedReactionIndex(existing)
        if (shown < 0) return if (edited != null) existing + edited else existing
        val out = existing.toMutableList()
        if (edited != null) out[shown] = edited else out.removeAt(shown)
        return out
    }

    /**
     * @param existing the stored entry being edited (null when creating)
     * @param edited   the entry rebuilt from the form, with 0 or 1 reaction
     */
    fun apply(existing: JAllergy?, edited: JAllergy): JAllergy {
        if (existing == null) return edited
        return edited.copy(
            reactions = mergeReactions(existing.reactions, edited.reactions.firstOrNull()),
        )
    }
}
