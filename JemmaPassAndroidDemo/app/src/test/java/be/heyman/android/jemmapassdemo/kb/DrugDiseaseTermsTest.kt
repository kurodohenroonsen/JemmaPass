/*
 * DrugDiseaseTermsTest.kt — device QA cycle 18: drug × disease returned 0 hits for
 * Kamekichi (ibuprofen / warfarin / bisoprolol × hypertension, AF, angina) because the
 * LIKE ran on the *localised* label ("Hypertension essentielle") one way only.
 */
package be.heyman.android.jemmapassdemo.kb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrugDiseaseTermsTest {

    @Test
    fun englishTermsComeFirstAndLocalisedLast() {
        val t = DrugDiseaseTerms.candidates("Essential hypertension", "Essential hypertension (disorder)", "Hypertension essentielle")
        assertEquals("essential hypertension", t.first())
        assertTrue("SNOMED semantic tag dropped", t.none { it.contains("(disorder)") })
        assertEquals("hypertension essentielle", t.last())
    }

    @Test
    fun kidneyAndRenalAreInterchangeable() {
        val t = DrugDiseaseTerms.candidates("Chronic kidney disease stage 3", null, null)
        assertTrue(t.contains("chronic kidney disease stage 3"))
        assertTrue(t.contains("chronic renal disease stage 3"))
    }

    @Test
    fun matchingWorksBothWays() {
        assertTrue(DrugDiseaseTerms.matches("essential hypertension", "Hypertension"))
        assertTrue(DrugDiseaseTerms.matches("heart failure", "Congestive Heart Failure"))
        assertFalse("too short disease names never match by containment", DrugDiseaseTerms.matches("pain in the chest", "Pain"))
        assertFalse(DrugDiseaseTerms.matches("asthma", "Hypertension"))
    }

    @Test
    fun blanksAndDuplicatesAreDropped() {
        assertEquals(listOf("angina pectoris"), DrugDiseaseTerms.candidates("Angina pectoris", " angina pectoris ", ""))
        assertTrue(DrugDiseaseTerms.candidates(null, null, "—").isEmpty())
    }

    @Test
    fun pluralsDoNotBlockAMatch() {
        // cycle 19: fexofenadine (R06AX26) × "Kidney Diseases" vs Haru's CKD stage 3
        assertTrue(DrugDiseaseTerms.matches("chronic kidney disease stage 3", "Kidney Diseases"))
        assertTrue(DrugDiseaseTerms.matches("liver disease", "Liver Diseases"))
        assertEquals("kidney disease", DrugDiseaseTerms.singular("Kidney Diseases"))
        assertTrue(DrugDiseaseTerms.matches("type 2 diabetes mellitus", "Diabetes Mellitus"))
        assertFalse(DrugDiseaseTerms.matches("essential hypertension", "Intracranial Hypertension"))
    }
}
