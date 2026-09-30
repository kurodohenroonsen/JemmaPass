/*
 * IpsResultCodecTest.kt — JVM round trips for the sprint-3 pillar :
 * Observation (results) ⇄ IpsResult, `_j.rs` projection, Bundle wiring.
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import dev.ohs.fhir.model.r4.Composition
import dev.ohs.fhir.model.r4.Observation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpsResultCodecTest {

    private val patientUrn = "urn:uuid:00000000-0000-0000-0000-000000000001"

    private val potassium = IpsResult(
        id = "rs-1", code = "2823-3", system = IpsCodeSystems.LOINC, display = "Potassium",
        date = "2026-02-10", status = IpsResultStatus.FINAL, category = IpsResultCategory.LABORATORY,
        value = "4.1", unit = "mmol/L", interpretation = IpsResultInterpretation.NORMAL,
        refLow = "3.5", refHigh = "5.1", performer = "Central lab", note = "Fasting sample",
    )

    private val bloodGroup = IpsResult(
        id = "rs-2", code = "882-1", system = IpsCodeSystems.LOINC, display = "ABO and Rh blood group",
        date = "2015-09-01", valueCode = "278149003", valueCodeSystem = IpsCodeSystems.SNOMED,
        valueDisplay = "Blood group O Rh(D) positive",
    )

    private val chestXray = IpsResult(
        id = "rs-3", code = null, system = IpsCodeSystems.LOINC, text = "Chest X-ray", date = "2025-12",
        category = IpsResultCategory.IMAGING, valueText = "Mild cardiomegaly, no pleural effusion", performer = "Radiology",
    )

    private fun roundTrip(rs: IpsResult): IpsResult {
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(rs, patientUrn).build())
        val parsed = IpsFhirCodec.json.decodeFromString(json)
        assertTrue(parsed is Observation)
        return IpsFhirCodec.fromFhir(parsed as Observation)
    }

    // ── Observation round trips ──────────────────────────────────────────

    @Test
    fun numericResultFullRoundTrip() {
        assertEquals(potassium, roundTrip(potassium))
    }

    @Test
    fun codedAndTextualResultsRoundTrip() {
        assertEquals(bloodGroup, roundTrip(bloodGroup))
        assertEquals(chestXray, roundTrip(chestXray))
    }

    @Test
    fun numericJsonCarriesIpsEssentials() {
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(potassium, patientUrn).build())
        val o = JSONObject(json)
        assertEquals("Observation", o.getString("resourceType"))
        assertEquals("final", o.getString("status"))
        assertEquals("2026-02-10", o.getString("effectiveDateTime"))
        assertEquals(IpsFhirCodec.PROFILE_OBSERVATION_RESULTS_LABORATORY_UV_IPS, o.getJSONObject("meta").getJSONArray("profile").getString(0))
        assertEquals(patientUrn, o.getJSONObject("subject").getString("reference"))

        val category = o.getJSONArray("category").getJSONObject(0).getJSONArray("coding").getJSONObject(0)
        assertEquals("laboratory", category.getString("code"))
        assertEquals(IpsFhirCodec.SYSTEM_OBSERVATION_CATEGORY, category.getString("system"))

        val code = o.getJSONObject("code").getJSONArray("coding").getJSONObject(0)
        assertEquals("2823-3", code.getString("code"))
        assertEquals(IpsCodeSystems.LOINC, code.getString("system"))

        val q = o.getJSONObject("valueQuantity")
        assertEquals(4.1, q.getDouble("value"), 1e-9)
        assertEquals("mmol/L", q.getString("unit"))
        assertEquals("mmol/L", q.getString("code"))
        assertEquals(IpsFhirCodec.SYSTEM_UCUM, q.getString("system"))

        val ip = o.getJSONArray("interpretation").getJSONObject(0).getJSONArray("coding").getJSONObject(0)
        assertEquals("N", ip.getString("code"))
        assertEquals(IpsFhirCodec.SYSTEM_OBSERVATION_INTERPRETATION, ip.getString("system"))

        val rr = o.getJSONArray("referenceRange").getJSONObject(0)
        assertEquals(3.5, rr.getJSONObject("low").getDouble("value"), 1e-9)
        assertEquals(5.1, rr.getJSONObject("high").getDouble("value"), 1e-9)
        assertEquals("mmol/L", rr.getJSONObject("high").getString("code"))
        assertEquals("Central lab", o.getJSONArray("performer").getJSONObject(0).getString("display"))
        assertEquals("Fasting sample", o.getJSONArray("note").getJSONObject(0).getString("text"))
    }

    @Test
    fun codedAndTextualJsonUseTheRightValueChoice() {
        val coded = JSONObject(IpsFhirCodec.encode(IpsFhirCodec.toFhir(bloodGroup, patientUrn).build()))
        val vcc = coded.getJSONObject("valueCodeableConcept").getJSONArray("coding").getJSONObject(0)
        assertEquals("278149003", vcc.getString("code"))
        assertEquals(IpsCodeSystems.SNOMED, vcc.getString("system"))
        assertFalse(coded.has("valueQuantity"))
        assertFalse(coded.has("referenceRange"))

        val textual = JSONObject(IpsFhirCodec.encode(IpsFhirCodec.toFhir(chestXray, patientUrn).build()))
        assertEquals("Mild cardiomegaly, no pleural effusion", textual.getString("valueString"))
        assertEquals("Chest X-ray", textual.getJSONObject("code").getString("text"))
        assertFalse(textual.getJSONObject("code").has("coding"))
        assertEquals("imaging", textual.getJSONArray("category").getJSONObject(0).getJSONArray("coding").getJSONObject(0).getString("code"))
        assertEquals(IpsFhirCodec.PROFILE_OBSERVATION_RESULTS_RADIOLOGY_UV_IPS, textual.getJSONObject("meta").getJSONArray("profile").getString(0))
        assertEquals("2025-12", textual.getString("effectiveDateTime"))
    }

    @Test
    fun integralAndCommaDecimalsSurviveTheDoubleJsonEncoding() {
        val glucose = potassium.copy(id = "rs-g", code = "2345-7", display = "Glucose", value = "120", unit = "mg/dL", refLow = "70", refHigh = "110")
        val back = roundTrip(glucose)
        assertEquals("120", back.value)
        assertEquals("70", back.refLow)
        assertEquals("110", back.refHigh)

        val comma = potassium.copy(id = "rs-c", value = "4,10", refLow = "3,50", refHigh = null)
        val backComma = roundTrip(comma)
        assertEquals("4.1", backComma.value)
        assertEquals("3.5", backComma.refLow)
        assertNull(backComma.refHigh)
    }

    @Test
    fun statusInterpretationAndCategoryNormalise() {
        assertEquals("final", IpsResultStatus.normalize(null))
        assertEquals("final", IpsResultStatus.normalize("bogus"))
        assertEquals("preliminary", IpsResultStatus.normalize(" Preliminary "))
        assertEquals("laboratory", IpsResultCategory.normalize(null))
        assertEquals("imaging", IpsResultCategory.normalize("IMAGING"))
        assertNull(IpsResultInterpretation.normalize(""))
        assertNull(IpsResultInterpretation.normalize("weird"))
        assertEquals("HH", IpsResultInterpretation.normalize("hh"))

        val weird = roundTrip(potassium.copy(status = "bogus", interpretation = "??", category = "nope"))
        assertEquals(IpsResultStatus.FINAL, weird.status)
        assertNull(weird.interpretation)
        assertEquals(IpsResultCategory.LABORATORY, weird.category)
    }

    @Test
    fun nonNumericTypedValueFallsBackToValueString() {
        val messy = potassium.copy(id = "rs-m", value = "positive", unit = null, refLow = null, refHigh = null)
        val json = JSONObject(IpsFhirCodec.encode(IpsFhirCodec.toFhir(messy, patientUrn).build()))
        assertEquals("positive", json.getString("valueString"))
        assertFalse(json.has("valueQuantity"))
        val back = roundTrip(messy)
        assertNull(back.value)
        assertEquals("positive", back.valueText)
    }

    // ── `_j.rs` projection ───────────────────────────────────────────────

    @Test
    fun projectionContract() {
        val e = potassium.toJEntry()
        assertEquals("2823-3", e.c)
        assertEquals("Potassium", e.displayLabel)
        assertEquals("2026-02-10", e.date)
        assertEquals("4.1", e.value)
        assertEquals("mmol/L", e.unit)
        assertEquals("N", e.interpretation)
        assertEquals("3.5-5.1", e.referenceRange)
        assertEquals("Fasting sample", e.d)
        assertNull("LOINC is the default system", e.codeSystem)
        assertNull("final is the default status", e.status)
        assertNull("laboratory is the default category", e.category)
        assertNull(e.valueCode)

        val coded = bloodGroup.toJEntry()
        assertEquals("278149003", coded.valueCode)
        assertEquals("Blood group O Rh(D) positive", coded.value)
        assertNull(coded.unit)

        val imaging = chestXray.toJEntry()
        assertNull(imaging.c)
        assertEquals("Chest X-ray", imaging.displayLabel)
        assertEquals("imaging", imaging.category)
        assertEquals("Mild cardiomegaly, no pleural effusion", imaging.value)

        val onlyHigh = potassium.copy(refLow = null).toJEntry()
        assertEquals("≤5.1", onlyHigh.referenceRange)
    }

    @Test
    fun projectionRebuildsTheThreeValueKinds() {
        val j = JemmaProfileJ(j = "1.2", sid = "x", rs = listOf(potassium.toJEntry(), bloodGroup.toJEntry(), chestXray.toJEntry()))
        val a = IpsNativePillars.fromJEntries(j.im, j.pr, j.dv, j.rs)
        val b = IpsNativePillars.fromJEntries(j.im, j.pr, j.dv, j.rs)
        assertEquals("deterministic ids", a, b)
        assertEquals(3, a.results.size)

        val k = a.results[0]
        assertEquals("2823-3", k.code)
        assertEquals("4.1", k.value)
        assertEquals("mmol/L", k.unit)
        assertEquals("3.5", k.refLow)
        assertEquals("5.1", k.refHigh)
        assertEquals("N", k.interpretation)
        assertEquals("Fasting sample", k.note)
        assertTrue(k.isNumeric)

        val g = a.results[1]
        assertTrue(g.isCoded)
        assertEquals("278149003", g.valueCode)
        assertEquals("Blood group O Rh(D) positive", g.valueDisplay)

        val x = a.results[2]
        assertNull(x.code)
        assertEquals("Chest X-ray", x.text)
        assertEquals(IpsResultCategory.IMAGING, x.category)
        assertEquals("Mild cardiomegaly, no pleural effusion", x.valueText)
        assertFalse(x.isNumeric)
    }

    @Test
    fun referenceRangeParsing() {
        assertEquals("3.5" to "5.1", IpsResult.parseReferenceRange("3.5-5.1"))
        assertEquals("3,5" to "5,1", IpsResult.parseReferenceRange(" 3,5 - 5,1 "))
        assertEquals("60" to null, IpsResult.parseReferenceRange("≥60"))
        assertEquals("60" to null, IpsResult.parseReferenceRange(">=60"))
        assertEquals(null to "100", IpsResult.parseReferenceRange("≤100"))
        assertEquals(null to null, IpsResult.parseReferenceRange("n/a"))
        assertEquals(null to null, IpsResult.parseReferenceRange(null))
    }

    @Test
    fun decimalHelpers() {
        assertEquals("5.4", IpsDecimal.normalize(" 5,4 "))
        assertEquals("120", IpsDecimal.normalize("+120"))
        assertEquals("-0.5", IpsDecimal.normalize("-0.5"))
        assertNull(IpsDecimal.normalize("12a"))
        assertNull(IpsDecimal.normalize(""))
        assertEquals("120", IpsDecimal.trimZeros("120.0"))
        assertEquals("5.4", IpsDecimal.trimZeros("5.40"))
        assertEquals("0", IpsDecimal.trimZeros("0.000"))
        assertEquals("7", IpsDecimal.trimZeros("7"))
    }

    // ── Bundle wiring ────────────────────────────────────────────────────

    private fun hydratedStub(sid: String): HydratedProfile = HydratedProfile(
        raw = JemmaProfileJ(j = "1.2", sid = sid, p = JPatient(gn = "Haru", fn = "Tanaka", gs = "F", bd = "1946-02-08")),
        uiLang = "en", allergies = emptyList(), medications = emptyList(), conditions = emptyList(),
        ddiAlerts = emptyList(), allergyAlerts = emptyList(), drugDiseaseAlerts = emptyList(), hydrationMs = 0L,
    )

    private fun sectionRefs(comp: Composition, loinc: String): List<String> =
        comp.section.filter { s -> s.code?.coding?.any { it.code?.value == loinc } == true }
            .flatMap { s -> s.entry.mapNotNull { it.reference?.value } }

    @Test
    fun bundleEmbedsResultsWithTheResultsSection() {
        val native = IpsNativePillars(results = listOf(potassium, bloodGroup, chestXray))
        val json = JemmaFhirBundleBuilder.build(hydratedStub("demo_test"), native)
        val bundle = IpsFhirCodec.parseBundle(json)
        assertNotNull(bundle); bundle!!

        val back = IpsFhirCodec.nativeOf(bundle)
        assertEquals(native.results, back.results)
        assertTrue(back.immunizations.isEmpty() && back.procedures.isEmpty() && back.devices.isEmpty())

        val comp = IpsFhirCodec.resourcesOf(bundle).filterIsInstance<Composition>().single()
        val refs = sectionRefs(comp, IpsFhirCodec.LOINC_SECTION_RESULTS)
        assertEquals(
            listOf("rs-1", "rs-2", "rs-3").map { IpsFhirCodec.resultUrn("demo_test", it) },
            refs,
        )
        val fullUrls = bundle.entry.mapNotNull { it.fullUrl?.value }.toSet()
        assertTrue(fullUrls.containsAll(refs))
        assertTrue(sectionRefs(comp, IpsFhirCodec.LOINC_SECTION_IMMUNIZATIONS).isEmpty())
        // 1 Composition + 1 Patient + 3 Observation
        assertEquals(5, bundle.entry.size)

        // An unchanged profile rebuilds to the same document (deterministic URNs, no timestamps in ids).
        val again = IpsFhirCodec.parseBundle(JemmaFhirBundleBuilder.build(hydratedStub("demo_test"), native))!!
        assertEquals(bundle.entry.map { it.fullUrl?.value }, again.entry.map { it.fullUrl?.value })
    }

    @Test
    fun otherObservationKindsAreLeftToTheirOwnPillars() {
        val vital = IpsFhirCodec.toFhir(potassium.copy(id = "vs-1"), patientUrn).build()
        // Same resource type, but a vital-signs category and no results profile → not a Result.
        val json = IpsFhirCodec.encode(vital)
            .replace(IpsFhirCodec.PROFILE_OBSERVATION_RESULTS_LABORATORY_UV_IPS, "http://hl7.org/fhir/StructureDefinition/vitalsigns")
            .replace("\"laboratory\"", "\"vital-signs\"")
        val parsed = IpsFhirCodec.json.decodeFromString(json) as Observation
        assertFalse(IpsFhirCodec.isResultsObservation(parsed))
        assertTrue(IpsFhirCodec.isResultsObservation(IpsFhirCodec.toFhir(chestXray, patientUrn).build()))
    }
}
