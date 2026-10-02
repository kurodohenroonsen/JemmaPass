/*
 * PatientFormMerge.kt — pure merge of the identity form into the stored JPatient
 * (UC-PAT-011 : addresses, telecoms and identifiers the form does not show are kept).
 *
 * The form shows ONE address (adrs[0]), ONE phone (first phone/sms telecom), ONE email
 * (first email telecom) and ONE identifier (ids[0]). Everything else must survive
 * load → edit → save. No Android dependency : covered by PatientFormMergeTest.
 */
package be.heyman.android.jemmapassdemo.ui.profile.perso

import be.heyman.android.jemmapassdemo.qr.JAddress
import be.heyman.android.jemmapassdemo.qr.JIdentifier
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JTelecom

/** What the identity form holds at save time (blank values already turned into null). */
data class PatientFormInput(
    val givenName: String?,
    val familyName: String?,
    val gender: String?,
    val birthDate: String?,
    val nationality: String?,
    val bloodType: String?,
    val addressUse: String?,
    val addressLine: String?,
    val addressCity: String?,
    val addressPostalCode: String?,
    val addressCountry: String?,
    val phone: String?,
    val email: String?,
    val identifierSystem: String?,
    val identifierValue: String?,
    val generalPractitioner: String?,
    val language: String?,
)

object PatientFormMerge {

    fun isPhone(t: JTelecom): Boolean = t.system == "phone" || t.system == "sms"

    fun isEmail(t: JTelecom): Boolean = t.system == "email"

    /** The phone entry the form displays (same rule as the form's pre-fill). */
    fun displayedPhone(tels: List<JTelecom>): JTelecom? = tels.firstOrNull { isPhone(it) }

    /** The email entry the form displays. */
    fun displayedEmail(tels: List<JTelecom>): JTelecom? = tels.firstOrNull { isEmail(it) }

    /** Replaces the first element, or removes it when [edited] is null ; keeps the rest. */
    private fun <T> replaceFirst(existing: List<T>, edited: T?): List<T> =
        listOfNotNull(edited) + existing.drop(1)

    fun mergeAddresses(existing: List<JAddress>, f: PatientFormInput): List<JAddress> {
        val hasAddress = f.addressLine != null || f.addressCity != null ||
            f.addressPostalCode != null || f.addressCountry != null
        val edited = if (hasAddress) {
            // copy() keeps what the form does not edit (state / region).
            (existing.firstOrNull() ?: JAddress()).copy(
                use = f.addressUse,
                line = f.addressLine,
                city = f.addressCity,
                postalCode = f.addressPostalCode,
                country = f.addressCountry,
            )
        } else null
        return replaceFirst(existing, edited)
    }

    fun mergeIdentifiers(existing: List<JIdentifier>, f: PatientFormInput): List<JIdentifier> {
        val edited = f.identifierValue?.let { JIdentifier(system = f.identifierSystem, value = it) }
        return replaceFirst(existing, edited)
    }

    fun mergeTelecoms(existing: List<JTelecom>, f: PatientFormInput): List<JTelecom> {
        val phoneIdx = existing.indexOfFirst { isPhone(it) }
        val emailIdx = existing.indexOfFirst { isEmail(it) }
        val out = mutableListOf<JTelecom>()
        for ((i, t) in existing.withIndex()) {
            // copy() keeps the stored system (sms) and use (work, home…).
            if (i == phoneIdx) {
                if (f.phone != null) out.add(t.copy(value = f.phone))
            } else if (i == emailIdx) {
                if (f.email != null) out.add(t.copy(value = f.email))
            } else {
                out.add(t)
            }
        }
        if (phoneIdx < 0 && f.phone != null) {
            out.add(JTelecom(system = "phone", value = f.phone, use = "mobile"))
        }
        if (emailIdx < 0 && f.email != null) {
            out.add(JTelecom(system = "email", value = f.email, use = "home"))
        }
        return out
    }

    /**
     * @param existing the stored patient (null for a new profile)
     * @param f        the form content
     */
    fun buildPatient(existing: JPatient?, f: PatientFormInput): JPatient {
        val base = existing ?: JPatient()
        val hasAddress = f.addressLine != null || f.addressCity != null ||
            f.addressPostalCode != null || f.addressCountry != null
        // Legacy single-field mirrors, for older readers : they follow the form.
        val legacyAddress = if (hasAddress) {
            listOfNotNull(f.addressLine, f.addressCity, f.addressPostalCode, f.addressCountry)
                .joinToString(", ")
        } else null
        // copy() keeps every field the form does not edit (emergency contacts, …).
        return base.copy(
            gn = f.givenName,
            fn = f.familyName,
            gs = f.gender,
            bd = f.birthDate,
            nat = f.nationality,
            bt = f.bloodType,
            adr = legacyAddress,
            tel = f.phone,
            eml = f.email,
            idn = f.identifierValue,
            ids = mergeIdentifiers(base.ids, f),
            adrs = mergeAddresses(base.adrs, f),
            tels = mergeTelecoms(base.tels, f),
            gp = f.generalPractitioner,
            lang = f.language,
        )
    }
}
