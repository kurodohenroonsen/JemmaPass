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
 * Cap byte size : [JemmaTextPayloadBuilder.MAX_BYTES] UTF-8 bytes = the
 * single-frame capacity of the QR viewer (JemmaQrFrameSplitter.QR_MAX_SINGLE,
 * EC = M), so the text channel is ALWAYS one QR readable by any generic
 * scanner. Over budget, whole lines are dropped lowest-priority first and
 * an explicit "✂️ …" marker line says the record is incomplete (never a
 * silent cut).
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
 *       ▪️ Misako Kudoro (spouse) +32 478 45 45 45
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

    /**
     * Hard cap of the text payload, in UTF-8 BYTES (not chars: a kanji is 3 bytes,
     * an emoji 4). Equal to the single-frame threshold of the QR viewer so the text
     * channel never spills into `JF:i/N|` frames, which a generic scanner cannot
     * reassemble. 1800 bytes + ZXing's ECI/byte-mode header fits a version 35 QR at
     * EC = M (capacity 1809 bytes).
     */
    const val MAX_BYTES = JemmaQrFrameSplitter.QR_MAX_SINGLE

    /**
     * Language-neutral prefix of the "record is incomplete" lines. Present in the
     * payload if and only if something was left out to respect the byte cap.
     */
    const val TRUNCATION_MARK = "✂️ …"

    private const val EOL = "\r\n"
    private const val BULLET = "  ▪️ "

    // Keep-ranks: when the payload is over budget, lines are removed from the
    // section with the HIGHEST rank first (last line first), then the next one…
    // Header, patient identity (name, birth, blood group, language), the marker
    // and the footer are never removed.
    private const val RANK_ALLERGIES = 1
    private const val RANK_MEDICATIONS = 2
    private const val RANK_CONDITIONS = 3
    private const val RANK_CONTACTS = 4
    private const val RANK_PATIENT_EXTRA = 5   // address, phone, e-mail, national id
    private const val RANK_DEVICES = 6
    private const val RANK_PAST_PROBLEMS = 7
    private const val RANK_PROCEDURES = 8
    private const val RANK_RESULTS = 9
    private const val RANK_IMMUNIZATIONS = 10
    private const val RANK_PREGNANCY = 11
    private const val RANK_FUNCTIONAL = 12

    /** One droppable group of lines. [title] == null → bare lines (patient extras). */
    private class Part(
        val icon: String,
        val title: String?,
        val rank: Int,
        val lines: MutableList<String>,
    ) {
        var dropped: Int = 0
    }

    /** Size of [s] once encoded in UTF-8 — the unit of every QR budget in this app. */
    fun utf8Size(s: String): Int = s.toByteArray(Charsets.UTF_8).size

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
     * Construit le texte de la langue [lang] à partir du profile hydraté.
     *
     * The result is at most [maxBytes] UTF-8 bytes. When the full text does not fit,
     * nothing is cut mid-line: whole lines are removed, least important section
     * first (functional status, pregnancy history, immunizations, results,
     * procedures, past illnesses, devices, patient address/phone/e-mail/id,
     * emergency contacts, conditions, medications, allergies — identity is never
     * removed). A partially kept section ends with "✂️ … +N" and a final
     * "✂️ … [ INCOMPLETE RECORD ] <icons>" line (localised label, icons of the
     * affected sections) is added before the footer.
     *
     * Pas suspending parce que toute la résolution KB est faite en
     * amont par [JemmaProfileHydrator]. Pure CPU string assembly.
     */
    fun build(hydrated: HydratedProfile, lang: Lang, maxBytes: Int = MAX_BYTES): String {
        val t0 = System.currentTimeMillis()
        fun label(key: String): String = JemmaTranslations.getLabel(lang, key)

        // ─── Patient ──────────────────────────────────────────────
        val p = hydrated.raw.p
        val patientCore = ArrayList<String>()
        val patientExtra = Part("👤", null, RANK_PATIENT_EXTRA, ArrayList())
        if (p != null) {
            val name = listOfNotNull(p.gn, p.fn).joinToString(" ")
            val gender = formatGender(p.gs, lang)
            patientCore += " 🔹 " + name + (if (gender.isNotEmpty()) " ($gender)" else "")
            p.bd?.takeIf { it.isNotBlank() }?.let { patientCore += " 📅 " + label("patient_birth") + ": " + it }
            p.bt?.takeIf { it.isNotBlank() }?.let { patientCore += " 🩸 " + label("patient_blood") + ": " + it }
            p.lang?.takeIf { it.isNotBlank() }?.let { patientCore += " 🗣 " + label("patient_lang") + ": " + it }
            p.adr?.takeIf { it.isNotBlank() }?.let { patientExtra.lines += " 📍 " + label("patient_addr") + ": " + it }
            p.tel?.takeIf { it.isNotBlank() }?.let { patientExtra.lines += " 📞 " + label("patient_phone") + ": " + safePhone(it) }
            p.eml?.takeIf { it.isNotBlank() }?.let { patientExtra.lines += " 📧 " + label("patient_email") + ": " + it }
            p.idn?.takeIf { it.isNotBlank() }?.let { patientExtra.lines += " 🆔 " + label("patient_id") + ": " + it }
        }

        fun <T> part(icon: String, titleKey: String, rank: Int, items: List<T>, formatter: (T) -> String): Part =
            Part(icon, label(titleKey), rank, items.mapTo(ArrayList<String>()) { BULLET + formatter(it) })

        val byDateDesc = compareByDescending<JEntryGeneric> { it.date != null }.thenByDescending { it.date ?: "" }

        // Display order (unchanged, contacts inserted right after the clinical core).
        val sections: List<Part> = listOf(
            part("⚠️", "allergies_title", RANK_ALLERGIES, hydrated.allergies) { a -> formatAllergy(a) },
            part("💊", "medications_title", RANK_MEDICATIONS, hydrated.medications) { m -> formatMedication(m) },
            part("🩺", "conditions_title", RANK_CONDITIONS, hydrated.conditions) { c ->
                c.displayLocalized.ifBlank { c.raw.c.orEmpty() }
            },
            // ─── Emergency contacts (`p.ct`) ──
            part("☎️", "contacts_title", RANK_CONTACTS, p?.ct.orEmpty().mapNotNull { formatContact(it, lang) }) { it },
            // ─── FHIR-native pillars (`_j.im` / `pr` / `dv` / `rs` / `ph` / `pg` / `fs` projections) ──
            part("💉", "immunizations_title", RANK_IMMUNIZATIONS, hydrated.raw.im.sortedWith(byDateDesc)) { im ->
                formatImmunization(im, lang)
            },
            part("🏥", "procedures_title", RANK_PROCEDURES, hydrated.raw.pr.sortedWith(byDateDesc)) { pr ->
                formatProcedure(pr, lang)
            },
            part(
                "📟", "devices_title", RANK_DEVICES,
                hydrated.raw.dv.sortedWith(
                    compareByDescending<JEntryGeneric> { it.status.isNullOrBlank() || it.status == "active" }
                        .thenByDescending { it.date ?: "" }
                ),
            ) { dv -> formatDevice(dv, lang) },
            part("🧪", "results_title", RANK_RESULTS, hydrated.raw.rs.sortedWith(byDateDesc)) { rs ->
                formatResult(rs, lang)
            },
            part("📜", "past_problems_title", RANK_PAST_PROBLEMS, hydrated.raw.ph.sortedWith(byDateDesc)) { ph ->
                formatPastProblem(ph, hydrated.pastProblemLabels)
            },
            part(
                "🤰", "pregnancy_title", RANK_PREGNANCY,
                hydrated.raw.pg.mapIndexedNotNull { i, e -> be.heyman.android.jemmapassdemo.ips.IpsPregnancyObs.fromJEntry(e, i) },
            ) { pg -> be.heyman.android.jemmapassdemo.pillars.IpsPregnancyCatalog.format(pg, lang.isoCode) },
            part("♿", "functional_title", RANK_FUNCTIONAL, hydrated.raw.fs) { fs ->
                val fsLabel = fs.c?.let { hydrated.pastProblemLabels[it] } ?: fs.displayLabel?.takeIf { it.isNotBlank() } ?: fs.c.orEmpty()
                fsLabel + (fs.date?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "") +
                    (fs.status?.takeIf { it.isNotBlank() && it != "active" }?.let { " ($it)" } ?: "")
            },
        )
        val droppable: List<Part> = sections + patientExtra

        fun render(): String {
            val sb = StringBuilder()
            sb.append(label("header")).append(EOL).append(EOL)
            if (p != null) {
                sb.append("👤 [ ").append(label("patient_title")).append(" ]").append(EOL)
                for (l in patientCore) sb.append(l).append(EOL)
                for (l in patientExtra.lines) sb.append(l).append(EOL)
                sb.append(EOL)
            }
            for (s in sections) {
                if (s.lines.isEmpty()) continue  // Match le JS : skip section vide.
                sb.append(s.icon).append(" [ ").append(s.title).append(" ]").append(EOL)
                for (l in s.lines) sb.append(l).append(EOL)
                if (s.dropped > 0) sb.append("  ").append(TRUNCATION_MARK).append(" +").append(s.dropped).append(EOL)
                sb.append(EOL)
            }
            val cut = droppable.filter { it.dropped > 0 }
            if (cut.isNotEmpty()) {
                sb.append(TRUNCATION_MARK).append(" [ ").append(label("truncated")).append(" ] ")
                    .append(cut.sortedBy { it.rank }.joinToString(" ") { it.icon })
                    .append(EOL).append(EOL)
            }
            sb.append(label("footer")).append(EOL)
            return sb.toString()
        }

        val full = render()
        var capped = full
        while (utf8Size(capped) > maxBytes) {
            val victim = droppable.filter { it.lines.isNotEmpty() }.maxByOrNull { it.rank } ?: break
            victim.lines.removeAt(victim.lines.size - 1)
            victim.dropped++
            capped = render()
        }
        if (utf8Size(capped) > maxBytes) {
            // Pathological: header + identity + footer alone exceed the cap (giant
            // name, absurd maxBytes). Hard cut on a code-point boundary, still marked.
            val tail = EOL + TRUNCATION_MARK + " [ " + label("truncated") + " ]" + EOL
            capped = if (utf8Size(tail) <= maxBytes) {
                cutUtf8(capped, maxBytes - utf8Size(tail)) + tail
            } else {
                cutUtf8(capped, maxBytes)
            }
        }

        val dt = System.currentTimeMillis() - t0
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 📝 text build $lang in ${dt}ms : ${utf8Size(full)} bytes" +
                " → ${utf8Size(capped)} bytes (cap $maxBytes, " +
                "${droppable.sumOf { it.dropped }} lines dropped)",
        )
        return capped
    }

    /** "Misako Kudoro (Spouse) +32 478 45 45 45" — null when the contact is entirely blank. */
    private fun formatContact(c: JContact, lang: Lang): String? {
        val name = c.n?.trim().orEmpty()
        val relation = be.heyman.android.jemmapassdemo.pillars.IpsRelationshipCatalog
            .getDisplay(c.r, lang.isoCode).trim()
        // Phone printed as typed (NOT through safePhone): the responder must be able to dial it.
        val reach = c.p?.trim()?.takeIf { it.isNotEmpty() } ?: c.e?.trim().orEmpty()
        val line = listOfNotNull(
            name.takeIf { it.isNotEmpty() },
            relation.takeIf { it.isNotEmpty() }?.let { "($it)" },
            reach.takeIf { it.isNotEmpty() },
        ).joinToString(" ")
        return line.takeIf { it.isNotEmpty() }
    }

    /** Longest prefix of [text] that fits in [maxBytes] UTF-8 bytes, never splitting a code point. */
    internal fun cutUtf8(text: String, maxBytes: Int): String {
        var bytes = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                cp < 0x10000 -> 3
                else -> 4
            }
            if (bytes + n > maxBytes) break
            bytes += n
            i += Character.charCount(cp)
        }
        return text.substring(0, i)
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

    /** "Appendicitis — 1995-07-10 → 1995-07-12", status appended when not resolved. */
    private fun formatPastProblem(ph: be.heyman.android.jemmapassdemo.qr.JEntryGeneric, labels: Map<String, String>): String {
        val sb = StringBuilder(ph.c?.let { labels[it] } ?: ph.displayLabel?.takeIf { it.isNotBlank() } ?: ph.c.orEmpty())
        val onset = ph.date?.takeIf { it.isNotBlank() }
        val abatement = ph.abatement?.takeIf { it.isNotBlank() }
        when {
            onset != null && abatement != null -> sb.append(" — ").append(onset).append(" → ").append(abatement)
            onset != null -> sb.append(" — ").append(onset)
            abatement != null -> sb.append(" — → ").append(abatement)
        }
        ph.status?.takeIf { it.isNotBlank() && it != "resolved" }?.let { sb.append(" (").append(it).append(")") }
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
}
