/*
 * RED TEST (wave 1) — SD-09, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * Medication × disease : a condition of the profile is matched against the disease names of
 * the knowledge base. A false match is a false alert (alerts stop being read) ; a missed
 * match is a missed contraindication.
 *
 * The pairs of the 2nd and 3rd tests are a clinical decision (which qualified diseases are
 * different diseases, which synonyms are recognised) : to be confirmed by a person before the fix.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.kb.DrugDiseaseTerms
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd09DrugDiseaseMatchingTest {

    /** True when one of the search terms derived from the stored English label matches the KB disease name. */
    private fun anyMatch(storedDisplay: String, disease: String): Boolean =
        DrugDiseaseTerms.candidates(storedDisplay, null, null).any { DrugDiseaseTerms.matches(it, disease) }

    @Test
    fun `SD-09 UC-DDS-010 a term that is only a piece of another word is not a match`() {
        assertFalse(
            "The condition 'coma' matches the rule for 'Glaucoma' because the letters c-o-m-a end that word. " +
                "A term must match whole words only.",
            DrugDiseaseTerms.matches("coma", "Glaucoma"),
        )
        assertFalse(
            "The condition 'heatstroke' matches the rule for 'Stroke' because the word ends with those letters. " +
                "A disease name must match whole words only.",
            DrugDiseaseTerms.matches("heatstroke", "Stroke"),
        )
    }

    @Test
    fun `SD-09 UC-DDS-008 a different disease that shares a word is not a match`() {
        assertFalse(
            "A person with plain 'hypertension' gets the alerts written for 'Intracranial Hypertension', a different disease. " +
                "(The opposite direction, 'essential hypertension' with the rule 'Hypertension', must keep matching.)",
            DrugDiseaseTerms.matches("hypertension", "Intracranial Hypertension"),
        )
        assertFalse(
            "A person with 'diabetes' gets the alerts written for 'Diabetes Insipidus', a different disease. " +
                "('type 2 diabetes mellitus' with the rule 'Diabetes Mellitus' must keep matching.)",
            DrugDiseaseTerms.matches("diabetes", "Diabetes Insipidus"),
        )
    }

    @Test
    fun `SD-09 UC-DDS-013 usual synonyms of a condition are matched`() {
        assertTrue(
            "SNOMED 38341003 'Hypertensive disorder - systemic arterial' is how hypertension is coded, yet it does not match " +
                "the rule 'Hypertension' : the contraindication of an anti-inflammatory drug is missed.",
            anyMatch("Hypertensive disorder, systemic arterial", "Hypertension"),
        )
        assertTrue(
            "'Hepatic cirrhosis' does not match the rule 'Liver Diseases' : hepatic and liver must be interchangeable, " +
                "and cirrhosis is a liver disease.",
            anyMatch("Hepatic cirrhosis", "Liver Diseases"),
        )
        assertTrue(
            "'Chronic renal failure' does not match the rule 'Kidney Diseases' : renal failure is a kidney disease.",
            anyMatch("Chronic renal failure", "Kidney Diseases"),
        )
    }

    @Test
    fun `SD-09 UC-DDS-016 an irregular plural does not block a match`() {
        assertTrue(
            "'psychoses' does not match 'psychosis' : the plural in -oses of a word in -osis must be recognised.",
            DrugDiseaseTerms.matches("psychoses", "psychosis"),
        )
    }
}
