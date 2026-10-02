/*
 * RED TEST (wave 1) — SD-07, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * A profile id read from a scanned QR becomes a file name. It must not be the name of another
 * profile's Bundle file (`<id>.fhir.json`) nor a name the profile list ignores (`meta.json`).
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.profiles.ProfileFiles
import org.junit.Assert.assertNull
import org.junit.Test

class Sd07ProfileIdLayoutCollisionTest {

    @Test
    fun `SD-07 UC-IMP-009 an id ending in fhir is rejected because it would overwrite the document of another profile`() {
        for (bad in listOf("demo_haru.fhir", "x.fhir", "demo_haru.FHIR", "x.Fhir", " demo_haru.fhir\n")) {
            assertNull(
                "The id '${bad.trim()}' is accepted. Its profile would be written to '${bad.trim()}.json', which is the " +
                    "IPS document file of another profile (id + '.fhir.json', also on a case-insensitive storage) : " +
                    "that person's record is damaged by a simple scan, and the imported profile never shows in the " +
                    "list. Such an id must be rejected so that a new id is generated.",
                ProfileFiles.safeIdOrNull(bad),
            )
        }
    }

    @Test
    fun `SD-07 UC-MPR-014 the id meta is rejected because the profile list ignores that file`() {
        for (bad in listOf("meta", "META", "Meta", " meta ")) {
            assertNull(
                "The id '${bad.trim()}' is accepted. Its profile would be written to 'meta.json', a file name the profile " +
                    "list skips : the import answers 'saved' and the person never appears. Such an id must be " +
                    "rejected so that a new id is generated.",
                ProfileFiles.safeIdOrNull(bad),
            )
        }
    }
}
