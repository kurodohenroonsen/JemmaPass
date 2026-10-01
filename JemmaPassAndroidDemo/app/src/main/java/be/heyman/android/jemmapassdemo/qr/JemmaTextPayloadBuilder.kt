/*
 * JemmaTextPayloadBuilder.kt — Lot 14.2 (PHASE 14)
 *
 * Port du `generateMasterPlainTextMessage` de text_generator.js
 * (channel 2 du HTML legacy : texte humain lisible par tout scanner
 * QR, FR/EN/JA).
 *
 * Différence importante avec le HTML legacy :
 *   • Le JS consomme un `profileData` LONG-format avec `display` déjà
 *     présent. Côté Android on a un `_j 1.2` SHORT-format où on n'a
 *     que des codes. On part donc d'un [HydratedProfile] (déjà résolu
 *     par [JemmaProfileHydrator]) — c'est ce qui rend les noms
 *     cliniques lisibles ("Pénicilline" au lieu de "27658006").
 *
 *   • i18n simplifié : 18 labels × 3 langues hardcoded dans un Map.
 *     Pas d'extraction vers strings.xml parce que ces labels sortent
 *     dans le QR scanné par n'importe quel device (pas dans l'UI
 *     native Android), donc indépendants de la locale Android.
 *
 * Cap byte size : 2200 bytes (matche le JS — au-delà ça ne tient plus
 * en QR byte mode avec error correction L raisonnable).
 *
 * Format de sortie (exemple FR pour Haru) :
 *
 *     🏥 === JEMMA CLINICAL SUMMARY (FR) ===
 *
 *     👤 [ PATIENT ]
 *      🔹 Haru Nakamura (F)
 *      📅 Naissance: 1956-02-05
 *      🩸 Groupe: A+
 *
 *     ⚠️ [ ALLERGIES ]
 *       ▪️ Pénicilline (HIGH)
 *       ▪️ Aspirine (LOW)
 *
 *     💊 [ MÉDICAMENTS ]
 *       ▪️ Warfarine 5mg OD
 *       ▪️ Ibuprofène 400mg PRN
 *
 *     ☎️ [ CONTACTS ]
 *       ▪️ Misako Kudoro (spouse) +32 478.45.45.45
 *
 *     ✅ JEMMA v2.6 - on-device
 */
package be.heyman.android.jemmapassdemo.qr

import android.util.Log
import be.heyman.android.jemmapassdemo.kb.HydratedAllergy
import be.heyman.android.jemmapassdemo.kb.HydratedMedication
import be.heyman.android.jemmapassdemo.kb.HydratedProfile

object JemmaTextPayloadBuilder {

    private const val TAG = "JEMMA-CODEC"

    /** Cap byte size — au-delà le QR devient illisible. Matche le JS. */
    private const val MAX_BYTES = 2200

    /** Langues supportées. Match les flags du HTML legacy. */
    enum class Lang(val isoCode: String, val flag: String) {
        EN("en", "🇬🇧"),
        FR("fr", "🇫🇷"),
        JA("ja", "🇯🇵"),
        ES("es", "🇪🇸"),
        DE("de", "🇩🇪"),
        IT("it", "🇮🇹"),
        PT("pt", "🇵🇹"),
        NL("nl", "🇳🇱"),
        ZH("zh", "🇨🇳"),
        KO("ko", "🇰🇷"),
        AR("ar", "🇸🇦"),
        RU("ru", "🇷🇺"),
        HI("hi", "🇮🇳"),
        BN("bn", "🇧🇩"),
        TR("tr", "🇹🇷"),
        PL("pl", "🇵🇱"),
        UK("uk", "🇺🇦"),
        VI("vi", "🇻🇳"),
        TH("th", "🇹🇭"),
        ID("id", "🇮🇩"),
        SV("sv", "🇸🇪"),
        NO("no", "🇳🇴"),
        DA("da", "🇩🇰"),
        FI("fi", "🇫🇮"),
        RO("ro", "🇷🇴"),
    }

    /**
     * Construit le texte de la langue [lang] à partir du profile
     * hydraté. Cap à [MAX_BYTES] bytes UTF-8 — si dépassé, tronqué
     * proprement par item (pas mid-line) + ajout d'un `…` indicateur.
     *
     * Pas suspending parce que toute la résolution KB est faite en
     * amont par [JemmaProfileHydrator]. Pure CPU string assembly.
     */
    fun build(hydrated: HydratedProfile, lang: Lang): String {
        val t0 = System.currentTimeMillis()
        val sb = StringBuilder()

        sb.append(JemmaTranslations.getLabel(lang, "header")).append("\r\n\r\n")

        // ─── Patient ──────────────────────────────────────────────
        val p = hydrated.raw.p
        if (p != null) {
            sb.append("👤 [ ").append(JemmaTranslations.getLabel(lang, "patient_title")).append(" ]\r\n")
            val name = listOfNotNull(p.gn, p.fn).joinToString(" ")
            val gender = formatGender(p.gs, lang)
            sb.append(" 🔹 ").append(name).append(if (gender.isNotEmpty()) " ($gender)" else "").append("\r\n")
            p.bd?.takeIf { it.isNotBlank() }?.let { sb.append(" 📅 ").append(JemmaTranslations.getLabel(lang, "patient_birth")).append(": ").append(it).append("\r\n") }
            p.bt?.takeIf { it.isNotBlank() }?.let { sb.append(" 🩸 ").append(JemmaTranslations.getLabel(lang, "patient_blood")).append(": ").append(it).append("\r\n") }
            p.lang?.takeIf { it.isNotBlank() }?.let { sb.append(" 🗣 ").append(JemmaTranslations.getLabel(lang, "patient_lang")).append(": ").append(it).append("\r\n") }
            p.adr?.takeIf { it.isNotBlank() }?.let { sb.append(" 📍 ").append(JemmaTranslations.getLabel(lang, "patient_addr")).append(": ").append(it).append("\r\n") }
            p.tel?.takeIf { it.isNotBlank() }?.let { sb.append(" 📞 ").append(JemmaTranslations.getLabel(lang, "patient_phone")).append(": ").append(safePhone(it)).append("\r\n") }
            p.eml?.takeIf { it.isNotBlank() }?.let { sb.append(" 📧 ").append(JemmaTranslations.getLabel(lang, "patient_email")).append(": ").append(it).append("\r\n") }
            p.idn?.takeIf { it.isNotBlank() }?.let { sb.append(" 🆔 ").append(JemmaTranslations.getLabel(lang, "patient_id")).append(": ").append(it).append("\r\n") }
            sb.append("\r\n")
        }

        // ─── Allergies ────────────────────────────────────────────
        appendSection(
            sb = sb,
            icon = "⚠️",
            title = JemmaTranslations.getLabel(lang, "allergies_title"),
            items = hydrated.allergies,
            empty = JemmaTranslations.getLabel(lang, "empty"),
            formatter = { a -> formatAllergy(a) },
        )

        // ─── Medications ──────────────────────────────────────────
        appendSection(
            sb = sb,
            icon = "💊",
            title = JemmaTranslations.getLabel(lang, "medications_title"),
            items = hydrated.medications,
            empty = JemmaTranslations.getLabel(lang, "empty"),
            formatter = { m -> formatMedication(m) },
        )

        // ─── Conditions ───────────────────────────────────────────
        appendSection(
            sb = sb,
            icon = "🩺",
            title = JemmaTranslations.getLabel(lang, "conditions_title"),
            items = hydrated.conditions,
            empty = JemmaTranslations.getLabel(lang, "empty"),
            formatter = { c -> c.displayLocalized.ifBlank { c.raw.c.orEmpty() } },
        )

        // ─── Immunizations (FHIR-native pillar, `_j.im` projection) ──
        appendSection(
            sb = sb,
            icon = "💉",
            title = JemmaTranslations.getLabel(lang, "immunizations_title"),
            items = hydrated.raw.im.sortedWith(
                compareByDescending<be.heyman.android.jemmapassdemo.qr.JEntryGeneric> { it.date != null }
                    .thenByDescending { it.date ?: "" }
            ),
            empty = JemmaTranslations.getLabel(lang, "empty"),
            formatter = { im -> formatImmunization(im, lang) },
        )

        // ─── Procedures (FHIR-native pillar, `_j.pr` projection) ──
        appendSection(
            sb = sb,
            icon = "🏥",
            title = JemmaTranslations.getLabel(lang, "procedures_title"),
            items = hydrated.raw.pr.sortedWith(
                compareByDescending<be.heyman.android.jemmapassdemo.qr.JEntryGeneric> { it.date != null }
                    .thenByDescending { it.date ?: "" }
            ),
            empty = JemmaTranslations.getLabel(lang, "empty"),
            formatter = { pr -> formatProcedure(pr, lang) },
        )

        // ─── Medical devices (FHIR-native pillar, `_j.dv` projection) ──
        appendSection(
            sb = sb,
            icon = "📟",
            title = JemmaTranslations.getLabel(lang, "devices_title"),
            items = hydrated.raw.dv.sortedWith(
                compareByDescending<be.heyman.android.jemmapassdemo.qr.JEntryGeneric> { it.status.isNullOrBlank() || it.status == "active" }
                    .thenByDescending { it.date ?: "" }
            ),
            empty = JemmaTranslations.getLabel(lang, "empty"),
            formatter = { dv -> formatDevice(dv, lang) },
        )

        // ─── Results (FHIR-native pillar, `_j.rs` projection) ──
        appendSection(
            sb = sb,
            icon = "🧪",
            title = JemmaTranslations.getLabel(lang, "results_title"),
            items = hydrated.raw.rs.sortedWith(
                compareByDescending<be.heyman.android.jemmapassdemo.qr.JEntryGeneric> { it.date != null }
                    .thenByDescending { it.date ?: "" }
            ),
            empty = JemmaTranslations.getLabel(lang, "empty"),
            formatter = { rs -> formatResult(rs, lang) },
        )

        sb.append(JemmaTranslations.getLabel(lang, "footer")).append("\r\n")

        // Cap byte-size en UTF-8.
        val full = sb.toString()
        val capped = capBytes(full, MAX_BYTES)
        val dt = System.currentTimeMillis() - t0
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 📝 text build $lang in ${dt}ms : ${full.length} chars" +
                " → ${capped.length} chars (${capped.toByteArray(Charsets.UTF_8).size} bytes)",
        )
        return capped
    }

    /** "Tdap — 2022-05-17 · dose 2" from the `_j.im` projection (catalog label when known). */
    private fun formatImmunization(im: be.heyman.android.jemmapassdemo.qr.JEntryGeneric, lang: Lang): String {
        val langCode = lang.isoCode
        val label = be.heyman.android.jemmapassdemo.pillars.IpsVaccineCatalog.getDisplay(im.c, langCode)
            ?: im.displayLabel?.takeIf { it.isNotBlank() }
            ?: im.c.orEmpty()
        val sb = StringBuilder(label)
        im.date?.takeIf { it.isNotBlank() }?.let { sb.append(" — ").append(it) }
        im.doseNumber?.let { sb.append(" · #").append(it) }
        im.status?.takeIf { it.isNotBlank() }?.let { sb.append(" (").append(it).append(")") }
        return sb.toString()
    }

    /** "Appendectomy — 1995-07-12" from the `_j.pr` projection (status appended when not completed). */
    private fun formatProcedure(pr: be.heyman.android.jemmapassdemo.qr.JEntryGeneric, lang: Lang): String {
        val langCode = lang.isoCode
        val label = be.heyman.android.jemmapassdemo.pillars.IpsProcedureCatalog.getDisplay(pr.c, langCode)
            ?: pr.displayLabel?.takeIf { it.isNotBlank() }
            ?: pr.c.orEmpty()
        val sb = StringBuilder(label)
        pr.date?.takeIf { it.isNotBlank() }?.let { sb.append(" — ").append(it) }
        pr.status?.takeIf { it.isNotBlank() && it != "completed" }?.let { sb.append(" (").append(it).append(")") }
        return sb.toString()
    }

    /** "Cardiac pacemaker — 2021-03-15 · Medtronic" from the `_j.dv` projection (status appended when not active). */
    private fun formatDevice(dv: be.heyman.android.jemmapassdemo.qr.JEntryGeneric, lang: Lang): String {
        val langCode = lang.isoCode
        val label = be.heyman.android.jemmapassdemo.pillars.IpsDeviceCatalog.getDisplay(dv.c, langCode)
            ?: dv.displayLabel?.takeIf { it.isNotBlank() }
            ?: dv.c.orEmpty()
        val sb = StringBuilder(label)
        dv.date?.takeIf { it.isNotBlank() }?.let { sb.append(" — ").append(it) }
        dv.status?.takeIf { it.isNotBlank() && it != "active" }?.let { sb.append(" (").append(it).append(")") }
        return sb.toString()
    }

    /** "Potassium: 4.1 mmol/L (N) — 2026-02-10", blood group as "O+". */
    private fun formatResult(rs: be.heyman.android.jemmapassdemo.qr.JEntryGeneric, lang: Lang): String {
        val label = be.heyman.android.jemmapassdemo.pillars.IpsResultCatalog.getDisplay(rs.c, lang.isoCode)
            ?: rs.displayLabel?.takeIf { it.isNotBlank() }
            ?: rs.c.orEmpty()
        val value = be.heyman.android.jemmapassdemo.ips.IpsBloodGroup.labelFromSnomed(rs.valueCode)
            ?: listOfNotNull(rs.value, rs.unit).joinToString(" ")
        val sb = StringBuilder(label)
        if (value.isNotBlank()) sb.append(": ").append(value)
        rs.interpretation?.takeIf { it.isNotBlank() && it != "N" }?.let { sb.append(" (").append(it).append(")") }
        rs.date?.takeIf { it.isNotBlank() }?.let { sb.append(" — ").append(it) }
        return sb.toString()
    }

    /** Append une section avec icon + items mappés via [formatter]. Skipé si liste vide. */
    private fun <T> appendSection(
        sb: StringBuilder,
        icon: String,
        title: String,
        items: List<T>,
        empty: String,
        formatter: (T) -> String,
    ) {
        if (items.isEmpty()) return  // Match le JS : skip section vide.
        sb.append(icon).append(" [ ").append(title).append(" ]\r\n")
        for (it in items) {
            sb.append("  ▪️ ").append(formatter(it)).append("\r\n")
        }
        sb.append("\r\n")
    }

    private fun formatAllergy(a: HydratedAllergy): String {
        val name = a.displayLocalized.ifBlank { a.raw.c.orEmpty() }
        val crit = a.criticality.name  // HIGH / LOW / UNABLE-TO-ASSESS
        return "$name ($crit)"
    }

    private fun formatMedication(m: HydratedMedication): String {
        val name = m.displayLocalized.ifBlank { m.raw.c.orEmpty() }
        val dose = listOfNotNull(
            m.doseValue?.takeIf { it.isNotBlank() },
            m.doseUnit?.takeIf { it.isNotBlank() },
        ).joinToString("")
        val timing = m.timing?.takeIf { it.isNotBlank() } ?: ""
        return listOfNotNull(
            name.takeIf { it.isNotBlank() },
            dose.takeIf { it.isNotBlank() },
            timing.takeIf { it.isNotBlank() },
        ).joinToString(" ")
    }

    /** Encode courte du gender code (M/F/O/U) → label par langue. */
    private fun formatGender(gs: String?, lang: Lang): String = when (gs?.uppercase()) {
        "M" -> JemmaTranslations.getPdfLabel(lang, "gender_m")
        "F" -> JemmaTranslations.getPdfLabel(lang, "gender_f")
        "O" -> JemmaTranslations.getPdfLabel(lang, "gender_o")
        "U" -> JemmaTranslations.getPdfLabel(lang, "gender_u")
        else -> ""
    }

    /**
     * Casse les téléphones pour iOS/Android n'aient pas envie de
     * proposer "appeler" en mid-text. Matche le `safePhone` du JS :
     * split tous les 2 chiffres avec un `.`.
     */
    private fun safePhone(phone: String): String =
        phone.replace(Regex("(\\d{2})"), "$1.")
            .trimEnd('.')

    /**
     * Cap byte UTF-8 à [max]. Tronque sur les `\r\n` les plus proches
     * pour rester lisible. Si pas de retour à la ligne dans la marge,
     * fallback char-truncate + `…`.
     */
    private fun capBytes(text: String, max: Int): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= max) return text

        // Truncate en cherchant le dernier `\r\n` avant la limite.
        var truncIndex = text.length
        var probeBytes: Int
        do {
            truncIndex = text.lastIndexOf("\r\n", truncIndex - 1)
            if (truncIndex <= 0) break
            probeBytes = text.substring(0, truncIndex).toByteArray(Charsets.UTF_8).size
        } while (probeBytes > max)

        return if (truncIndex > 0) {
            text.substring(0, truncIndex) + "\r\n…\r\n"
        } else {
            // Pas de \r\n exploitable — char-truncate.
            val sb = StringBuilder()
            var bytesSoFar = 0
            for (c in text) {
                val charBytes = c.toString().toByteArray(Charsets.UTF_8).size
                if (bytesSoFar + charBytes > max - 4) break
                sb.append(c)
                bytesSoFar += charBytes
            }
            sb.append("…")
            sb.toString()
        }
    }
}
