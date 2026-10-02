/*
 * RED TEST (wave 1) — SD-11, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed
 * (certainty "computed" : obtained with a Python port of the builder, never run on the real code).
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * Haru is hard of hearing and walks with a cane. That changes how a rescuer approaches her :
 * it must be in her text QR, at least in French and in Japanese (her own language).
 *
 * The 1800-byte cap and the drop order are pinned by existing tests (QrTextBudgetTest,
 * TextQrAllLanguagesTest, TextQrProbe.assertPriority) : the way to make this pass is a
 * decision for a person (shorter wording, or another priority together with those tests).
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.qr.JemmaPersonasSeeder
import be.heyman.android.jemmapassdemo.qr.JemmaTextPayloadBuilder
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import be.heyman.android.jemmapassdemo.testsupport.TextQrProbe
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd11HaruFunctionalStatusInTextQrTest {

    private fun assertFunctionalStatusPrinted(lang: JemmaTextPayloadBuilder.Lang) {
        val h = ProfileFixtures.hydrated(ProfileFixtures.persona(JemmaPersonasSeeder.SID_HARU).j, uiLang = lang.isoCode)
        assertTrue("the seeded persona Haru must have a functional status (hearing, cane)", h.raw.fs.isNotEmpty())
        val text = JemmaTextPayloadBuilder.build(h, lang)
        val bytes = ProfileFixtures.utf8(text)
        assertTrue("$lang : the text QR is $bytes bytes, over the ${JemmaTextPayloadBuilder.MAX_BYTES}-byte cap of one QR frame", bytes <= JemmaTextPayloadBuilder.MAX_BYTES)
        assertTrue(
            "$lang : Haru's text QR ($bytes bytes) leaves out her functional status. 'Hard of hearing' and 'walks with a cane' " +
                "are the first lines removed when the text is over the cap, before vaccines and laboratory results. " +
                "A rescuer who scans the QR must read all ${h.raw.fs.size} functional status lines.",
            TextQrProbe.isWhole(text, h, TextQrProbe.FUNCTIONAL),
        )
    }

    @Test
    fun `SD-11 UC-I18N-008 the functional status of Haru is in her text QR in French`() {
        assertFunctionalStatusPrinted(JemmaTextPayloadBuilder.Lang.FR)
    }

    @Test
    fun `SD-11 UC-HUM-023 the functional status of Haru is in her text QR in Japanese`() {
        assertFunctionalStatusPrinted(JemmaTextPayloadBuilder.Lang.JA)
    }
}
