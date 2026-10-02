/*
 * RED TEST (wave 1) — SD-22 (UC-ALM-009, UC-ALM-010), found again while fact-checking the UML document.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * When an allergy has no code, the app guesses a drug class from the words the person typed
 * (KbCrossCheck.inferAtcFromAllergyName). The guess looks for pieces of words : "ains" inside
 * "grains", "statin" inside "nystatine", "iode" inside "période". A person allergic to sesame
 * grains is then warned against every anti-inflammatory drug, and learns to ignore the red banner.
 * A false alert is not harmless : it trains people to dismiss the true ones.
 *
 * Expected : a keyword matches whole words only (accent- and case-insensitive).
 *
 * The function is private today. The test reaches it by reflection, and accepts the cleaner
 * home the implementer is asked to give it : `object AllergyKeywords { fun inferAtc(name: String): String? }`
 * in package be.heyman.android.jemmapassdemo.kb (pure Kotlin, no Android import).
 */
package be.heyman.android.jemmapassdemo.red

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class Sd22AllergyKeywordFalsePositiveTest {

    private fun inferAtc(name: String): String? {
        try {
            val cls = Class.forName("be.heyman.android.jemmapassdemo.kb.AllergyKeywords")
            val instance = cls.getField("INSTANCE").get(null)
            return cls.getMethod("inferAtc", String::class.java).invoke(instance, name) as String?
        } catch (_: ClassNotFoundException) {
            // legacy location : private method of KbCrossCheck, called on an instance built without its constructor
        }
        val cls = Class.forName("be.heyman.android.jemmapassdemo.kb.KbCrossCheck")
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null)
        val instance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, cls)
        val m = cls.getDeclaredMethod("inferAtcFromAllergyName", String::class.java).apply { isAccessible = true }
        return m.invoke(instance, name) as String?
    }

    private val nsaid = "M01AE01"
    private val statin = "C10AA01"
    private val iodine = "V08AB02"

    @Test
    fun `SD-22 UC-ALM-009 grains and other words that only contain ains are not anti-inflammatory drugs`() {
        for (words in listOf("grains de sésame", "Allergie aux grains", "pains au lait", "certains fruits", "bains de soleil", "mountains pollen")) {
            assertNotEquals("\"$words\" is not an allergy to anti-inflammatory drugs (matched the letters \"ains\" inside a word).", nsaid, inferAtc(words))
        }
    }

    @Test
    fun `SD-22 UC-ALM-010 nystatin and other molecules that end in statin are not statins`() {
        for (words in listOf("nystatine", "Nystatin", "somatostatine", "cilastatin", "pentostatin")) {
            assertNotEquals("\"$words\" is not a cholesterol statin (matched the letters \"statin\" inside another molecule name).", statin, inferAtc(words))
        }
    }

    @Test
    fun `SD-22 UC-ALM-009 words that only contain iode are not iodine contrast`() {
        for (words in listOf("allergie en période de pollen", "épisode d'urticaire au soleil")) {
            assertNotEquals("\"$words\" is not an allergy to iodine contrast (matched the letters \"iode\" inside a word).", iodine, inferAtc(words))
        }
    }

    @Test
    fun `SD-22 UC-ALM-008 real class names are still recognised`() {
        // Guards : green today, must stay green after the fix.
        assertEquals(nsaid, inferAtc("AINS"))
        assertEquals(nsaid, inferAtc("Allergie aux AINS"))
        assertEquals(nsaid, inferAtc("NSAID intolerance"))
        assertEquals(nsaid, inferAtc("ibuprofène"))
        assertEquals(statin, inferAtc("statines"))
        assertEquals(statin, inferAtc("Statin intolerance"))
        assertEquals(statin, inferAtc("atorvastatine"))
        assertEquals(statin, inferAtc("simvastatin"))
        assertEquals(iodine, inferAtc("iode"))
        assertEquals(iodine, inferAtc("Allergie à l'iode"))
        assertEquals(iodine, inferAtc("iodine contrast"))
        assertEquals("J01CA01", inferAtc("Pénicilline"))
    }
}
