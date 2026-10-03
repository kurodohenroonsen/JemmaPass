/*
 * RED TEST (sprint 8, contacts pillar) — not to be edited by the implementer
 *
 * Emergency contacts (`p.ct`, FHIR Patient.contact) — docs/analysis/remaining-pillars.md, section 1.
 * Each test is one observable requirement of the finished pillar. Fix the app, not the test.
 *
 * Tests marked "Lock" are green today and must stay green ; the others are expected to fail
 * until the pillar is finished :
 *   UC-CT-001  the pillar is switched on in PillarRegistry
 *   UC-CT-005  a phone number is not declared "mobile" when nobody said so
 *   UC-CT-013  the address of a contact is exported
 *   UC-CT-015 / UC-CT-016  contacts can be read back from the IPS document
 *              (new API, reached by reflection : IpsFhirCodec.contactsOf(bundle: Bundle): List<JContact>)
 *   UC-CT-021  an unknown role code is never printed raw on the text QR
 *   UC-CT-022  every demo persona has a reachable emergency contact
 *
 * All names, numbers and addresses are invented demo data.
 */
package be.heyman.android.jemmapassdemo.pillar8

import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.pillars.IpsRelationshipCatalog
import be.heyman.android.jemmapassdemo.pillars.PillarRegistry
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JContact
import be.heyman.android.jemmapassdemo.qr.JEntryGeneric
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaPersonasSeeder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.qr.JemmaTextPayloadBuilder
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import be.heyman.android.jemmapassdemo.testsupport.TextQrProbe
import be.heyman.android.jemmapassdemo.ui.profile.common.PhoneNumberHelper
import dev.ohs.fhir.model.r4.Bundle
import java.lang.reflect.InvocationTargetException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsPillarTest {

    private val phone1 = "+32 2 000 00 01"
    private val phone2 = "+32 2 000 00 02"
    private val phone3 = "+32 2 000 00 03"

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun profile(vararg contacts: JContact): JemmaProfileJ = JemmaProfileJ(
        j = "1.2", sid = "qa_contacts",
        p = JPatient(gn = "Kurodo", ct = contacts.toList()),
    )

    private fun bundleJson(profile: JemmaProfileJ, uiLang: String = "en"): String =
        JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(profile, uiLang))

    private fun patient(profile: JemmaProfileJ, uiLang: String = "en"): JSONObject {
        val entries = JSONObject(bundleJson(profile, uiLang)).getJSONArray("entry")
        return (0 until entries.length())
            .map { entries.getJSONObject(it).getJSONObject("resource") }
            .single { it.getString("resourceType") == "Patient" }
    }

    /** Patient.contact of the IPS document built from [profile] (empty when the element is absent). */
    private fun fhirContacts(profile: JemmaProfileJ, uiLang: String = "en"): List<JSONObject> {
        val array = patient(profile, uiLang).optJSONArray("contact") ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun objects(parent: JSONObject, key: String): List<JSONObject> {
        val array = parent.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun telecoms(contact: JSONObject, system: String): List<JSONObject> =
        objects(contact, "telecom").filter { it.optString("system") == system }

    /** Every blank string, empty array or empty object under [node], with its path. */
    private fun emptyNodes(path: String, node: Any?, out: MutableList<String>) {
        when (node) {
            is JSONObject -> {
                if (node.length() == 0) {
                    out += "$path is an empty object"
                }
                for (key in node.keys().asSequence().toList()) {
                    emptyNodes("$path.$key", node.get(key), out)
                }
            }
            is JSONArray -> {
                if (node.length() == 0) {
                    out += "$path is an empty array"
                }
                for (i in 0 until node.length()) {
                    emptyNodes("${path}[$i]", node.get(i), out)
                }
            }
            is String -> {
                if (node.isBlank()) {
                    out += "$path is a blank string"
                }
            }
        }
    }

    private fun textQr(
        profile: JemmaProfileJ,
        lang: JemmaTextPayloadBuilder.Lang = JemmaTextPayloadBuilder.Lang.EN,
    ): String = JemmaTextPayloadBuilder.build(ProfileFixtures.hydrated(profile, lang.isoCode), lang)

    /** The lines printed under the contacts title of the text QR, without their bullet. */
    private fun contactLines(text: String): List<String> =
        TextQrProbe.sections(text)[TextQrProbe.CONTACTS]?.lines.orEmpty()

    /**
     * Contacts read back from an IPS document. The reader does not exist yet : it is reached by
     * reflection so that this file compiles before the pillar is written.
     */
    private fun contactsReadBack(profile: JemmaProfileJ): List<JContact> {
        val bundle: Bundle = ProfileFixtures.parse(bundleJson(profile))
        val method = try {
            IpsFhirCodec::class.java.getMethod("contactsOf", Bundle::class.java)
        } catch (e: NoSuchMethodException) {
            throw AssertionError(
                "Emergency contacts cannot be read back from an IPS document. Expected in object " +
                    "be.heyman.android.jemmapassdemo.ips.IpsFhirCodec : " +
                    "fun contactsOf(bundle: Bundle): List<JContact> " +
                    "(one JContact per Patient.contact, in document order ; r = the v3 RoleCode when the " +
                    "relationship is coded, else its text).",
            )
        }
        val result = try {
            method.invoke(IpsFhirCodec, bundle)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
        val list = result as? List<*>
            ?: throw AssertionError("IpsFhirCodec.contactsOf must return List<JContact>, got $result")
        return list.map {
            it as? JContact ?: throw AssertionError("IpsFhirCodec.contactsOf must return List<JContact>, found the element $it")
        }
    }

    // ─── the pillar is switched on ────────────────────────────────────────

    @Test
    fun `UC-CT-001 the contacts pillar is active in the registry`() {
        val pillar = PillarRegistry.get("contacts")
        assertNotNull("PillarRegistry must still know the key 'contacts'", pillar)
        assertTrue(
            "The emergency contacts pillar is still a stub (isActive = false) : the tile opens the information " +
                "page instead of the editing screen.",
            pillar!!.isActive,
        )
        assertTrue("'contacts' must be listed among the active pillars", PillarRegistry.ACTIVE.any { it.key == "contacts" })
        assertFalse("'contacts' must no longer be listed among the stubs", PillarRegistry.PASSIVE.any { it.key == "contacts" })
    }

    // ─── IPS document (Patient.contact) ───────────────────────────────────

    @Test
    fun `UC-CT-003 a contact with name relationship and phone is exported whole - lock`() {
        val contacts = fhirContacts(profile(JContact(n = "Kamekichi", r = "FRND", p = phone1)))
        assertEquals("one contact in the profile, one Patient contact in the document", 1, contacts.size)
        val contact = contacts.single()
        assertEquals("Kamekichi", contact.getJSONObject("name").getString("text"))
        assertEquals(phone1, telecoms(contact, "phone").single().getString("value"))
        assertEquals(1, objects(contact, "relationship").size)
    }

    @Test
    fun `UC-CT-004 a phone number is exported as a phone telecom exactly as typed - lock`() {
        for (number in listOf(phone1, "+81 90 0000 0001", "02 000 00 04")) {
            val contact = fhirContacts(profile(JContact(n = "Kamekichi", p = number))).single()
            val phones = telecoms(contact, "phone")
            assertEquals("the number '$number' must travel as one telecom of system 'phone'", 1, phones.size)
            assertEquals(
                "The number must reach the rescuer exactly as typed : international prefix kept, nothing reformatted.",
                number, phones.single().getString("value"),
            )
        }
    }

    @Test
    fun `UC-CT-005 a phone number is not declared mobile when nobody said so`() {
        val phone = telecoms(fhirContacts(profile(JContact(n = "Kamekichi", p = phone1))).single(), "phone").single()
        assertFalse(
            "The document says \"use\": \"${phone.optString("use")}\" for $phone1, a landline number : the app never " +
                "asked which kind of line it is. A telecom whose use is not known must carry no 'use' element.",
            phone.has("use"),
        )
    }

    @Test
    fun `UC-CT-006 several contacts keep the order of the list - lock`() {
        val contacts = fhirContacts(
            profile(
                JContact(n = "Kamekichi", r = "FRND", p = phone1),
                JContact(n = "Tsuru", r = "NBOR", p = phone2),
                JContact(n = "Usagi", r = "SIB", p = phone3),
            ),
        )
        assertEquals(
            "The order of the list is the order in which people are called : it must not change in the document.",
            listOf("Kamekichi", "Tsuru", "Usagi"),
            contacts.map { it.getJSONObject("name").getString("text") },
        )
        assertEquals(listOf(phone1, phone2, phone3), contacts.map { telecoms(it, "phone").single().getString("value") })
    }

    @Test
    fun `UC-CT-007 a contact without a phone is still exported - lock`() {
        val nameOnly = fhirContacts(profile(JContact(n = "Kamekichi", r = "FRND")))
        assertEquals("a contact known by name only must stay in the document", 1, nameOnly.size)
        assertEquals("Kamekichi", nameOnly.single().getJSONObject("name").getString("text"))
        assertTrue("without phone and e-mail there is no telecom at all", objects(nameOnly.single(), "telecom").isEmpty())

        val byMail = fhirContacts(profile(JContact(n = "Kamekichi", e = "kamekichi@example.org"))).single()
        assertEquals("kamekichi@example.org", telecoms(byMail, "email").single().getString("value"))
        assertTrue("an e-mail must not be exported as a phone", telecoms(byMail, "phone").isEmpty())
    }

    @Test
    fun `UC-CT-008 a contact with a phone only is exported without a name element - lock`() {
        val contacts = fhirContacts(profile(JContact(p = phone1)))
        assertEquals("the phone is the way to reach someone : the contact must stay in the document", 1, contacts.size)
        assertEquals(phone1, telecoms(contacts.single(), "phone").single().getString("value"))
        assertFalse("without a name the contact must have no name element", contacts.single().has("name"))
    }

    @Test
    fun `UC-CT-009 blank fields never produce an empty value in the document - lock`() {
        val cases = listOf(
            JContact(n = "  ", r = " ", p = phone1, e = "", adr = "  "),
            JContact(n = "Kamekichi", r = "", p = " ", e = "", adr = ""),
            JContact(n = "", r = "FRND", p = "", e = "kamekichi@example.org", adr = " "),
            JContact(p = phone1),
            JContact(n = "Kamekichi"),
        )
        for (case in cases) {
            val contacts = fhirContacts(profile(case))
            assertEquals("$case must still be exported", 1, contacts.size)
            val problems = ArrayList<String>()
            emptyNodes("contact", contacts.single(), problems)
            assertTrue(
                "$case produced an empty FHIR value (a hospital validator rejects the document) : $problems",
                problems.isEmpty(),
            )
        }
    }

    @Test
    fun `UC-CT-010 a catalogue relationship is exported as a v3 RoleCode coding with a readable text - lock`() {
        for (code in listOf("FRND", "DAUC", "NBOR", "MTH")) {
            val relationship = objects(fhirContacts(profile(JContact(n = "Kamekichi", r = code, p = phone1))).single(), "relationship").single()
            val coding = objects(relationship, "coding").single()
            assertEquals(IpsRelationshipCatalog.CODE_SYSTEM, coding.getString("system"))
            assertEquals(code, coding.getString("code"))
            assertEquals(
                "Coding display is the official English label of the code",
                IpsRelationshipCatalog.getDisplay(code, "en"), coding.getString("display"),
            )
            val text = relationship.optString("text")
            assertTrue("the relationship '$code' must also carry a text a person can read", text.isNotBlank())
            assertFalse("the text of the relationship must not be the code itself", text == code)
        }
    }

    @Test
    fun `UC-CT-011 the relationship text follows the language of the reader and the coding stays English - lock`() {
        for (lang in listOf("en", "fr", "ja")) {
            val relationship = objects(
                fhirContacts(profile(JContact(n = "Kamekichi", r = "FRND", p = phone1)), lang).single(), "relationship",
            ).single()
            assertEquals("text in '$lang'", IpsRelationshipCatalog.getDisplay("FRND", lang), relationship.getString("text"))
            assertEquals(
                "display in '$lang' stays the official English label",
                IpsRelationshipCatalog.getDisplay("FRND", "en"), objects(relationship, "coding").single().getString("display"),
            )
        }
    }

    @Test
    fun `UC-CT-012 a relationship typed freely stays text only and word for word - lock`() {
        for (words in listOf("friend", "voisine du 3e étage", "幼なじみ")) {
            val relationship = objects(fhirContacts(profile(JContact(n = "Kamekichi", r = words, p = phone1))).single(), "relationship").single()
            assertEquals(words, relationship.getString("text"))
            assertTrue("free text must never be given a code", objects(relationship, "coding").isEmpty())
        }
    }

    @Test
    fun `UC-CT-013 the address of a contact is exported as address text`() {
        val address = "Rue de la Demo 1, 1000 Bruxelles"
        val contact = fhirContacts(profile(JContact(n = "Kamekichi", r = "FRND", p = phone1, adr = address))).single()
        val exported = contact.optJSONObject("address")
        assertNotNull(
            "The address typed for the contact (JContact.adr) is lost in the IPS document. Expected : " +
                "Patient.contact.address.text = \"$address\".",
            exported,
        )
        assertEquals(address, exported!!.optString("text"))
    }

    @Test
    fun `UC-CT-014 a name written in Japanese survives in the document and on the text QR - lock`() {
        val name = "田中 さくら"
        val p = profile(JContact(n = name, r = "DAUC", p = "+81 90 0000 0001"))
        assertEquals(name, fhirContacts(p).single().getJSONObject("name").getString("text"))
        val lines = contactLines(textQr(p, JemmaTextPayloadBuilder.Lang.JA))
        assertEquals(1, lines.size)
        assertTrue("the text QR line must hold the name unchanged, got '${lines.single()}'", lines.single().startsWith(name))
    }

    // ─── round trip `_j` → IPS document → `_j` ────────────────────────────

    @Test
    fun `UC-CT-015 a contact survives the round trip from the profile to the document and back`() {
        val contacts = listOf(
            JContact(n = "Kamekichi", r = "FRND", p = phone1),
            JContact(n = "田中 さくら", r = "DAUC", p = "+81 90 0000 0001", e = "sakura@example.org"),
            JContact(n = "Tsuru", r = "voisine du 3e étage", e = "tsuru@example.org"),
            JContact(p = phone3),
        )
        for (contact in contacts) {
            assertEquals(
                "name, relationship, phone and e-mail must come back unchanged",
                listOf(contact), contactsReadBack(profile(contact)).map { it.copy(adr = null) },
            )
        }
    }

    @Test
    fun `UC-CT-016 several contacts come back from the document in the same order`() {
        val contacts = listOf(
            JContact(n = "Kamekichi", r = "FRND", p = phone1),
            JContact(n = "Tsuru", r = "NBOR", p = phone2),
            JContact(n = "Usagi", r = "SIB", p = phone3),
        )
        assertEquals(contacts, contactsReadBack(profile(*contacts.toTypedArray())).map { it.copy(adr = null) })
    }

    // ─── text QR ──────────────────────────────────────────────────────────

    @Test
    fun `UC-CT-017 the text QR prints name relationship and a phone that can be dialled - lock`() {
        val p = profile(JContact(n = "Kamekichi", r = "FRND", p = phone1, e = "kamekichi@example.org"))
        for (lang in listOf(JemmaTextPayloadBuilder.Lang.EN, JemmaTextPayloadBuilder.Lang.FR, JemmaTextPayloadBuilder.Lang.JA)) {
            val relation = IpsRelationshipCatalog.getDisplay("FRND", lang.isoCode)
            assertEquals(
                "contact line in ${lang.isoCode} : relationship in the reader's language, number exactly as typed",
                listOf("Kamekichi ($relation) $phone1"), contactLines(textQr(p, lang)),
            )
        }
    }

    @Test
    fun `UC-CT-018 the address of a contact is never printed on the text QR - lock`() {
        val p = profile(JContact(n = "Kamekichi", r = "FRND", p = phone1, adr = "Rue de la Demo 1, 1000 Bruxelles"))
        val text = textQr(p)
        assertEquals(1, contactLines(text).size)
        assertFalse(
            "The contact is a third person : her address must not travel on a QR anybody can read.",
            text.contains("Rue de la Demo"),
        )
    }

    @Test
    fun `UC-CT-019 a contact without a phone prints a clean line on the text QR - lock`() {
        val relation = IpsRelationshipCatalog.getDisplay("FRND", "en")
        assertEquals(
            listOf("Kamekichi ($relation)"),
            contactLines(textQr(profile(JContact(n = "Kamekichi", r = "FRND")))),
        )
        assertEquals(
            "without a phone the e-mail is the way to reach the contact",
            listOf("Kamekichi kamekichi@example.org"),
            contactLines(textQr(profile(JContact(n = "Kamekichi", e = "kamekichi@example.org")))),
        )
    }

    @Test
    fun `UC-CT-020 emergency contacts hold rank 6 when the text QR is over budget - lock`() {
        val p = JemmaProfileJ(
            j = "1.2", sid = "qa_contacts_rank",
            p = JPatient(
                gn = "Kurodo",
                adr = "Rue de la Demo 9, 1000 Bruxelles", tel = "+32 2 000 00 09",
                eml = "kurodo@example.org", idn = "ID-DEMO-0001",
                ct = listOf(
                    JContact(n = "Kamekichi", r = "FRND", p = phone1),
                    JContact(n = "Tsuru", r = "NBOR", p = phone2),
                    JContact(n = "Usagi", r = "SIB", p = phone3),
                ),
            ),
            al = listOf(
                JAllergy(displayLabel = "Demo allergen one", s = "H"),
                JAllergy(displayLabel = "Demo allergen two", s = "L"),
            ),
            fs = listOf(
                JEntryGeneric(displayLabel = "Walks with a cane"),
                JEntryGeneric(displayLabel = "Hard of hearing"),
            ),
        )
        val hydrated = ProfileFixtures.hydrated(p)
        val full = JemmaTextPayloadBuilder.build(hydrated, JemmaTextPayloadBuilder.Lang.EN)
        assertEquals("the full text prints the three contacts", 3, contactLines(full).size)

        var contactsWholeWhilePatientLinesCut = false
        var functionalWholeWhileContactsCut = false
        var budget = ProfileFixtures.utf8(full) - 1
        while (budget > 0) {
            val text = JemmaTextPayloadBuilder.build(hydrated, JemmaTextPayloadBuilder.Lang.EN, budget)
            // Below this point only the allergies are left to cut : nothing more to learn about contacts.
            if (!TextQrProbe.isWhole(text, hydrated, TextQrProbe.ALLERGIES)) break
            assertTrue("budget $budget : payload of ${ProfileFixtures.utf8(text)} bytes", ProfileFixtures.utf8(text) <= budget)
            TextQrProbe.assertAccounted("budget $budget", text, hydrated)
            TextQrProbe.assertPriority("budget $budget", text, hydrated)

            val contactsWhole = TextQrProbe.isWhole(text, hydrated, TextQrProbe.CONTACTS)
            val functionalWhole = TextQrProbe.isWhole(text, hydrated, TextQrProbe.FUNCTIONAL)
            if (contactsWhole && !text.contains("ID-DEMO-0001")) contactsWholeWhilePatientLinesCut = true
            if (!contactsWhole) {
                assertFalse(
                    "budget $budget : a contact was dropped while the patient's own address is still printed " +
                        "(contacts are rank 6, the patient address, phone, e-mail and id are rank 8).",
                    text.contains("Rue de la Demo 9"),
                )
                if (functionalWhole) functionalWholeWhileContactsCut = true
            }
            budget--
        }
        assertTrue(
            "Emergency contacts must outlive the patient's address, phone, e-mail and id : no budget was found " +
                "where the three contacts are printed while a patient line is cut.",
            contactsWholeWhilePatientLinesCut,
        )
        assertTrue(
            "Functional status (rank 5) must outlive emergency contacts (rank 6) : no budget was found where a " +
                "contact is cut while the functional status is whole.",
            functionalWholeWhileContactsCut,
        )
    }

    @Test
    fun `UC-CT-021 an unknown role code is never printed raw on the text QR`() {
        // The form sets MEDPROVR when a name starts with "Dr" ; the code is not in the relationship catalogue.
        val p = profile(JContact(n = "Dr Kamekichi", r = "MEDPROVR", p = phone1))
        for (lang in listOf(JemmaTextPayloadBuilder.Lang.EN, JemmaTextPayloadBuilder.Lang.FR, JemmaTextPayloadBuilder.Lang.JA)) {
            val line = contactLines(textQr(p, lang)).single()
            assertFalse(
                "The text QR prints '$line' in ${lang.isoCode} : a rescuer cannot read the code MEDPROVR. Print a " +
                    "word of the reader's language, or no relationship at all.",
                line.contains("MEDPROVR"),
            )
            assertTrue("the name must stay on the line, got '$line'", line.contains("Dr Kamekichi"))
            assertTrue("the phone must stay on the line, got '$line'", line.contains(phone1))
        }
    }

    @Test
    fun `UC-CT-024 the contacts section is printed in each of the text QR languages - lock`() {
        val p = profile(JContact(n = "Kamekichi", r = "FRND", p = phone1))
        for (lang in JemmaTextPayloadBuilder.Lang.values()) {
            val section = TextQrProbe.sections(textQr(p, lang))[TextQrProbe.CONTACTS]
            assertNotNull("${lang.isoCode} : no contacts section on the text QR", section)
            assertTrue("${lang.isoCode} : the contacts section has no title", section!!.title.isNotBlank())
            assertEquals("${lang.isoCode} : one contact, one line", 1, section.lines.size)
            val line = section.lines.single()
            assertTrue("${lang.isoCode} : name missing in '$line'", line.startsWith("Kamekichi"))
            assertTrue("${lang.isoCode} : the number must be printed as typed in '$line'", line.endsWith(phone1))
        }
    }

    @Test
    fun `UC-CT-025 an entirely blank contact is ignored and does not hide the next one - lock`() {
        val p = profile(
            JContact(n = " ", r = "", p = "  ", e = "", adr = ""),
            JContact(n = "Kamekichi", r = "FRND", p = phone1),
        )
        val contacts = fhirContacts(p)
        assertEquals("the blank contact must not be exported, the real one must", 1, contacts.size)
        assertEquals("Kamekichi", contacts.single().getJSONObject("name").getString("text"))
        val lines = contactLines(textQr(p))
        assertEquals("the blank contact must not print an empty line", 1, lines.size)
        assertTrue(lines.single().startsWith("Kamekichi"))

        assertTrue("a profile whose only contact is blank exports no contact", fhirContacts(profile(JContact(n = " ", p = ""))).isEmpty())
    }

    // ─── demo data and input rules ────────────────────────────────────────

    @Test
    fun `UC-CT-022 every demo persona has an emergency contact that can be called`() {
        for (demo in JemmaPersonasSeeder.getDemoProfiles()) {
            val contacts = demo.p?.ct.orEmpty()
            assertTrue(
                "${demo.sid} has no emergency contact. The demo must show the pillar on every persona " +
                    "(an 80 year old person without anyone to call is the case the pillar exists for).",
                contacts.isNotEmpty(),
            )
            assertTrue(
                "${demo.sid} : no seeded contact has both a phone number and a relationship of the catalogue",
                contacts.any { !it.p.isNullOrBlank() && IpsRelationshipCatalog.isValidCode(it.r) },
            )
        }
    }

    @Test
    fun `UC-CT-023 a number too short to be dialled is detected - lock`() {
        assertEquals(PhoneNumberHelper.ValidationCode.TOO_SHORT, PhoneNumberHelper.validate("123", true))
        assertEquals(PhoneNumberHelper.ValidationCode.TOO_SHORT, PhoneNumberHelper.validate("+32 2", true))
        assertEquals(PhoneNumberHelper.ValidationCode.OK, PhoneNumberHelper.validate(phone1, false))
        assertEquals(PhoneNumberHelper.ValidationCode.OK, PhoneNumberHelper.validate("+81 90 0000 0001", false))
    }
}
