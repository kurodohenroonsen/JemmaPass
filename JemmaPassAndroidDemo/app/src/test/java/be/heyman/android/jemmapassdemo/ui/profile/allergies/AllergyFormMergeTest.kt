/*
 * AllergyFormMergeTest.kt — UC-ALG-004 (qa/usecases/01-pillar-editing.md) : an allergy
 * with several reactions keeps all of them through load → edit → save.
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies

import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JReaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AllergyFormMergeTest {

    private val urticaria = JReaction("126485001", "Urticaria", "http://snomed.info/sct", "moderate")
    private val anaphylaxis = JReaction("39579001", "Anaphylaxis", "http://snomed.info/sct", "severe")
    private val angioedema = JReaction("41291007", "Angioedema", "http://snomed.info/sct", "severe")
    private val dictated = JReaction(manifestationCode = null, manifestationDisplay = "gonflement des lèvres")

    private val penicillin = JAllergy(
        c = "91936005", s = "L", st = "A", d = "depuis l'enfance", m = "IgE",
        displayLabel = "Pénicilline", codeSystem = "http://snomed.info/sct",
        type = "allergy", category = "medication", onset = "1985",
        reactions = listOf(urticaria, anaphylaxis, angioedema),
    )

    /** What AllergiesEditFragment rebuilds from the form result : at most one reaction. */
    private fun formResult(base: JAllergy, shown: JReaction?, criticality: String? = base.s) =
        base.copy(s = criticality, reactions = listOfNotNull(shown))

    @Test
    fun `UC-ALG-004 changing the criticality keeps the three reactions`() {
        val shown = AllergyFormMerge.displayedReaction(penicillin.reactions)
        val saved = AllergyFormMerge.apply(penicillin, formResult(penicillin, shown, criticality = "H"))
        assertEquals("H", saved.s)
        assertEquals(listOf(urticaria, anaphylaxis, angioedema), saved.reactions)
        assertEquals(penicillin.copy(s = "H"), saved)
    }

    @Test
    fun `UC-ALG-004 editing the displayed reaction replaces it in place`() {
        val edited = urticaria.copy(severity = "severe")
        val saved = AllergyFormMerge.apply(penicillin, formResult(penicillin, edited))
        assertEquals(listOf(edited, anaphylaxis, angioedema), saved.reactions)
    }

    @Test
    fun `UC-ALG-004 clearing the displayed reaction removes only that one`() {
        val saved = AllergyFormMerge.apply(penicillin, formResult(penicillin, null))
        assertEquals(listOf(anaphylaxis, angioedema), saved.reactions)
        // The next one becomes the displayed one : every reaction stays reachable.
        assertEquals(anaphylaxis, AllergyFormMerge.displayedReaction(saved.reactions))
    }

    @Test
    fun `UC-ALG-004 a reaction without a code is not displayed and is never dropped`() {
        val stored = penicillin.copy(reactions = listOf(dictated, anaphylaxis))
        assertEquals(1, AllergyFormMerge.displayedReactionIndex(stored.reactions))
        assertEquals(1, AllergyFormMerge.hiddenReactionCount(stored.reactions))

        val untouched = AllergyFormMerge.apply(stored, formResult(stored, anaphylaxis))
        assertEquals(listOf(dictated, anaphylaxis), untouched.reactions)

        val cleared = AllergyFormMerge.apply(stored, formResult(stored, null))
        assertEquals(listOf(dictated), cleared.reactions)
    }

    @Test
    fun `UC-ALG-004 only uncoded reactions - a picked reaction is appended`() {
        val stored = penicillin.copy(reactions = listOf(dictated))
        assertEquals(-1, AllergyFormMerge.displayedReactionIndex(stored.reactions))
        assertNull(AllergyFormMerge.displayedReaction(stored.reactions))
        assertEquals(1, AllergyFormMerge.hiddenReactionCount(stored.reactions))

        assertEquals(listOf(dictated), AllergyFormMerge.apply(stored, formResult(stored, null)).reactions)
        assertEquals(
            listOf(dictated, urticaria),
            AllergyFormMerge.apply(stored, formResult(stored, urticaria)).reactions,
        )
    }

    @Test
    fun `UC-ALG-004 hidden reaction count`() {
        assertEquals(0, AllergyFormMerge.hiddenReactionCount(emptyList()))
        assertEquals(0, AllergyFormMerge.hiddenReactionCount(listOf(urticaria)))
        assertEquals(2, AllergyFormMerge.hiddenReactionCount(penicillin.reactions))
    }

    @Test
    fun `UC-ALG-001 creation and single-reaction entries behave as before`() {
        val created = formResult(penicillin, urticaria)
        assertSame(created, AllergyFormMerge.apply(null, created))

        val single = penicillin.copy(reactions = listOf(urticaria))
        assertEquals(listOf(anaphylaxis), AllergyFormMerge.apply(single, formResult(single, anaphylaxis)).reactions)
        assertEquals(emptyList<JReaction>(), AllergyFormMerge.apply(single, formResult(single, null)).reactions)

        val none = penicillin.copy(reactions = emptyList())
        assertEquals(listOf(urticaria), AllergyFormMerge.apply(none, formResult(none, urticaria)).reactions)
    }

    @Test
    fun `UC-ALG-004 fields edited by the form come from the form`() {
        val form = JAllergy(
            c = "762952008", s = "H", st = "R", d = null, m = null, displayLabel = "Arachide",
            codeSystem = "http://snomed.info/sct", type = "intolerance", category = "food", onset = null,
            reactions = listOf(urticaria),
        )
        val saved = AllergyFormMerge.apply(penicillin, form)
        assertEquals(form.copy(reactions = penicillin.reactions), saved)
    }
}
