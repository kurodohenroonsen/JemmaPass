/*
 * IpsPregnancyCatalog.kt — JEMMA Pass · History of Pregnancy pillar
 *
 * Human-friendly short labels (EN/FR/JA/DE/NL/ZH) for the LOINC codes of the
 * pregnancy pillar (status, EDD methods, pregnancies summary) and for the three
 * pregnancy-status answers. The codes themselves live in IpsPregnancyCodes;
 * this catalog only localises them. Pure Kotlin, no Android dependency.
 */
package be.heyman.android.jemmapassdemo.pillars

import be.heyman.android.jemmapassdemo.ips.IpsPregnancyKind
import be.heyman.android.jemmapassdemo.ips.IpsPregnancyObs

data class PregnancyLabel(
    val code: String,
    val en: String,
    val fr: String,
    val ja: String,
    val de: String,
    val nl: String,
    val zh: String,
    val emoji: String = "🤰",
) {
    fun pick(lang: String): String = when (lang.lowercase().take(2)) {
        "fr" -> fr
        "ja" -> ja
        "de" -> de
        "nl" -> nl
        "zh" -> zh
        else -> en
    }
}

object IpsPregnancyCatalog {

    /** Same order as IpsPregnancyCodes: status, EDD methods, pregnancies summary. */
    val CODES: List<PregnancyLabel> = listOf(
        PregnancyLabel("82810-3", "Pregnancy status", "Statut de grossesse", "妊娠状態",
            "Schwangerschaftsstatus", "Zwangerschapsstatus", "妊娠状态"),
        PregnancyLabel("11778-8", "Estimated delivery date", "Date prévue d'accouchement", "分娩予定日",
            "Errechneter Geburtstermin", "Vermoedelijke bevallingsdatum", "预产期", "📅"),
        PregnancyLabel("11779-6", "Estimated delivery date (from last period)", "Date prévue d'accouchement (dernières règles)", "分娩予定日（最終月経から算出）",
            "Errechneter Geburtstermin (nach letzter Periode)", "Vermoedelijke bevallingsdatum (laatste menstruatie)", "预产期（按末次月经推算）", "📅"),
        PregnancyLabel("11780-4", "Estimated delivery date (from ovulation)", "Date prévue d'accouchement (ovulation)", "分娩予定日（排卵日から算出）",
            "Errechneter Geburtstermin (nach Eisprung)", "Vermoedelijke bevallingsdatum (ovulatie)", "预产期（按排卵日推算）", "📅"),
        PregnancyLabel("11640-0", "Births (total)", "Naissances (total)", "出産回数（合計）",
            "Geburten (gesamt)", "Geboorten (totaal)", "分娩次数（总计）", "👶"),
        PregnancyLabel("11636-8", "Live births", "Naissances vivantes", "生産数",
            "Lebendgeburten", "Levendgeborenen", "活产数", "👶"),
        PregnancyLabel("11639-2", "Term births", "Naissances à terme", "正期産数",
            "Termingeburten", "Voldragen geboorten", "足月产数", "👶"),
        PregnancyLabel("11637-6", "Preterm births", "Naissances prématurées", "早産数",
            "Frühgeburten", "Vroeggeboorten", "早产数", "👶"),
        PregnancyLabel("11638-4", "Children still living", "Enfants en vie", "生存している子の数",
            "Lebende Kinder", "Kinderen in leven", "现存子女数", "👶"),
        PregnancyLabel("11612-9", "Abortions (total)", "Interruptions de grossesse (total)", "流産・中絶（合計）",
            "Aborte (gesamt)", "Zwangerschapsafbrekingen (totaal)", "流产（总计）"),
        PregnancyLabel("11614-5", "Miscarriages", "Fausses couches", "自然流産",
            "Fehlgeburten", "Miskramen", "自然流产"),
        PregnancyLabel("11613-7", "Induced abortions", "Interruptions volontaires", "人工妊娠中絶",
            "Schwangerschaftsabbrüche", "Opgewekte abortussen", "人工流产"),
        PregnancyLabel("33065-4", "Ectopic pregnancies", "Grossesses extra-utérines", "子宮外妊娠",
            "Eileiterschwangerschaften", "Buitenbaarmoederlijke zwangerschappen", "异位妊娠"),
    )

    /** pregnancy-status-uv-ips answers. */
    val ANSWERS: List<PregnancyLabel> = listOf(
        PregnancyLabel("LA15173-0", "Pregnant", "Enceinte", "妊娠中", "Schwanger", "Zwanger", "已怀孕", "🤰"),
        PregnancyLabel("LA26683-5", "Not pregnant", "Non enceinte", "妊娠していない", "Nicht schwanger", "Niet zwanger", "未怀孕", "➖"),
        PregnancyLabel("LA4489-6", "Unknown", "Inconnu", "不明", "Unbekannt", "Onbekend", "未知", "❔"),
    )

    private val codeMap: Map<String, PregnancyLabel> by lazy { CODES.associateBy { it.code } }
    private val answerMap: Map<String, PregnancyLabel> by lazy { ANSWERS.associateBy { it.code } }

    fun label(code: String?, lang: String): String? =
        if (code.isNullOrBlank()) null else codeMap[code.trim()]?.pick(lang)

    fun answer(code: String?, lang: String): String? =
        if (code.isNullOrBlank()) null else answerMap[code.trim()]?.pick(lang)

    /** "<localised label>: <value>" (+ " — <date>" when the observation is dated). */
    fun format(obs: IpsPregnancyObs, lang: String): String {
        val name = label(obs.code, lang) ?: obs.label()
        val value = when (obs.kind) {
            IpsPregnancyKind.STATUS -> answer(obs.valueCode, lang) ?: obs.valueLabel()
            IpsPregnancyKind.EDD -> obs.valueDate.orEmpty()
            IpsPregnancyKind.OUTCOME -> obs.count?.toString().orEmpty()
        }
        val date = obs.date?.takeIf { it.isNotBlank() }
        return "$name: $value" + (if (date != null) " — $date" else "")
    }
}
