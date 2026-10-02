/*
 * ProfileFixtures.kt — shared helpers of the use-case test set (qa/usecases/coverage-matrix.md).
 *
 * Not a test class. It reproduces, in pure Kotlin, the two things the app does around a
 * profile that the JVM cannot run (they need Android / the knowledge base) :
 *
 *   • [hydrated]  — what JemmaProfileHydrator.hydrate returns when no KB label is found :
 *                   display = `d_display`, criticality / status / route read from the short codes
 *                   (same mapping as JemmaProfileHydrator.parseCriticality / parseClinicalStatus).
 *   • [store]     — the projection step of ProfilesRepository.writeProfileFiles : the blood group
 *                   is reconciled with `p.bt`, then the 8 native pillars are projected into `_j`.
 *
 * Nothing here changes production behaviour ; when the repository changes its write path,
 * [store] has to follow.
 */
package be.heyman.android.jemmapassdemo.testsupport

import be.heyman.android.jemmapassdemo.ips.IpsBloodGroup
import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.ips.IpsNativePillars
import be.heyman.android.jemmapassdemo.kb.AllergyCriticality
import be.heyman.android.jemmapassdemo.kb.ClinicalStatus
import be.heyman.android.jemmapassdemo.kb.HydratedAllergy
import be.heyman.android.jemmapassdemo.kb.HydratedGenericEntry
import be.heyman.android.jemmapassdemo.kb.HydratedMedication
import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.kb.MedicationRoute
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JCondition
import be.heyman.android.jemmapassdemo.qr.JEntryGeneric
import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaPersonasSeeder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Composition

object ProfileFixtures {

    /** Same Moshi configuration as ProfilesRepository / JemmaPayloadCodec. */
    val adapter: JsonAdapter<JemmaProfileJ> =
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(JemmaProfileJ::class.java)

    /** What is on disk for one profile : the `_j` projection and the native pillars of the Bundle. */
    data class Stored(val j: JemmaProfileJ, val native: IpsNativePillars)

    fun allergy(raw: JAllergy): HydratedAllergy = HydratedAllergy(
        raw = raw,
        resolvedConcept = null,
        displayLocalized = raw.displayLabel?.takeIf { it.isNotBlank() } ?: raw.c.orEmpty(),
        criticality = when (raw.s?.uppercase()) {
            "H" -> AllergyCriticality.HIGH
            "L" -> AllergyCriticality.LOW
            else -> AllergyCriticality.UNABLE_TO_ASSESS
        },
        clinicalStatus = when (raw.st?.uppercase()) {
            "I" -> ClinicalStatus.INACTIVE
            "R" -> ClinicalStatus.RESOLVED
            else -> ClinicalStatus.ACTIVE
        },
    )

    fun medication(raw: JMedication): HydratedMedication = HydratedMedication(
        raw = raw,
        resolvedConcept = null,
        displayLocalized = raw.displayLabel?.takeIf { it.isNotBlank() } ?: raw.c.orEmpty(),
        timing = raw.t,
        doseValue = raw.v,
        doseUnit = raw.u,
        route = MedicationRoute.fromShortCode(raw.r),
        atcCode = null,
        allAtcCodes = emptyList(),
        rxnormCui = null,
    )

    fun condition(raw: JCondition): HydratedGenericEntry = HydratedGenericEntry(
        raw = JEntryGeneric(c = raw.c, d = raw.d, displayLabel = raw.displayLabel),
        resolvedConcept = null,
        displayLocalized = raw.displayLabel?.takeIf { it.isNotBlank() } ?: raw.c.orEmpty(),
    )

    /** The hydrated view of [raw] without any KB (labels = the stored `d_display`). */
    fun hydrated(
        raw: JemmaProfileJ,
        uiLang: String = "en",
        pastProblemLabels: Map<String, String> = emptyMap(),
    ): HydratedProfile = HydratedProfile(
        raw = raw,
        uiLang = uiLang,
        allergies = raw.al.map { allergy(it) },
        medications = raw.md.map { medication(it) },
        conditions = raw.cn.map { condition(it) },
        ddiAlerts = emptyList(),
        allergyAlerts = emptyList(),
        drugDiseaseAlerts = emptyList(),
        hydrationMs = 0L,
        pastProblemLabels = pastProblemLabels,
    )

    /** `_j` arrays of the 8 native pillars, as ProfilesRepository projects them. */
    fun project(profile: JemmaProfileJ, id: String, native: IpsNativePillars): JemmaProfileJ = profile.copy(
        sid = id,
        im = native.immunizations.map { it.toJEntry() },
        pr = native.procedures.map { it.toJEntry() },
        dv = native.devices.map { it.toJEntry() },
        rs = native.results.map { it.toJEntry() },
        ph = native.pastProblems.map { it.toJEntry() },
        cn = native.problems.map { it.toJCondition() },
        pg = native.pregnancy.map { it.toJEntry() },
        fs = native.functional.map { it.toJEntry() },
    )

    /** Mirror of ProfilesRepository.writeProfileFiles, without the files. */
    fun store(id: String, profile: JemmaProfileJ, nativeIn: IpsNativePillars): Stored {
        val reconciled = IpsBloodGroup.reconcile(nativeIn.results, id, profile.p?.bt)
        val native = nativeIn.copy(results = reconciled.results)
        return Stored(project(profile, id, native), native)
    }

    /** One of the three demo personas, as seeded on first launch. */
    fun persona(sid: String): Stored {
        val demo = JemmaPersonasSeeder.getDemoProfiles().single { it.sid == sid }
        val native = JemmaPersonasSeeder.getDemoNativePillars(sid)
            ?: IpsNativePillars.fromJEntries(demo.im, demo.pr, demo.dv, demo.rs, demo.ph, demo.cn, demo.pg, demo.fs)
        return store(sid, demo, native)
    }

    val PERSONA_SIDS: List<String> = listOf(
        JemmaPersonasSeeder.SID_KURODO, JemmaPersonasSeeder.SID_HARU, JemmaPersonasSeeder.SID_KAMEKICHI,
    )

    /** `{id}.fhir.json` of a stored profile. */
    fun bundleJson(stored: Stored): String = JemmaFhirBundleBuilder.build(hydrated(stored.j), stored.native)

    /** The FHIR QR channel : Bundle rebuilt from the `_j` projection alone. */
    fun bundleJsonFromProjection(j: JemmaProfileJ): String = JemmaFhirBundleBuilder.build(hydrated(j))

    fun parse(json: String): Bundle =
        IpsFhirCodec.parseBundle(json) ?: throw AssertionError("builder output is not a FHIR Bundle")

    /** References listed by the Composition section [loinc] (empty when the section is absent). */
    fun sectionRefs(bundle: Bundle, loinc: String): List<String> =
        IpsFhirCodec.resourcesOf(bundle).filterIsInstance<Composition>().single().section
            .filter { s -> s.code?.coding?.any { it.code?.value == loinc } == true }
            .flatMap { s -> s.entry.mapNotNull { it.reference?.value } }

    fun hasSection(bundle: Bundle, loinc: String): Boolean =
        IpsFhirCodec.resourcesOf(bundle).filterIsInstance<Composition>().single().section
            .any { s -> s.code?.coding?.any { it.code?.value == loinc } == true }

    fun utf8(s: String): Int = s.toByteArray(Charsets.UTF_8).size
}
