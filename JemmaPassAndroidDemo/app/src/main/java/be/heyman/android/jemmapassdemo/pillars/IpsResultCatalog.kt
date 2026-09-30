/*
 * IpsResultCatalog.kt — JEMMA Pass · Results pillar (sprint 3)
 *
 * The on-device knowledge base has no LOINC table (cf. device-reports/kb/), so the
 * Results picker is a curated, embedded catalog of the laboratory tests a rescuer
 * or a patient most often carries: LOINC code, EN/FR/JA short label, default UCUM
 * unit and the kind of value expected. Free text remains possible for anything else.
 *
 * Only well-established LOINC codes are listed — nothing invented.
 */
package be.heyman.android.jemmapassdemo.pillars

import be.heyman.android.jemmapassdemo.ips.IpsResultCategory

enum class ResultValueKind { NUMERIC, CODED, TEXT }

data class ResultEntry(
    val code: String,
    val displayEn: String,
    val displayFr: String,
    val displayJa: String,
    /** Default UCUM unit for a numeric result (null for coded / text results). */
    val unit: String?,
    val kind: ResultValueKind = ResultValueKind.NUMERIC,
    val category: String = IpsResultCategory.LABORATORY,
    val emoji: String = "🧪",
    /** Extra search words (abbreviations, synonyms) in any language. */
    val aliases: String = "",
) {
    fun pick(lang: String): String = when (lang.lowercase().take(2)) {
        "fr" -> displayFr
        "ja" -> displayJa
        else -> displayEn
    }
    fun searchAliases(): String = "$code $displayEn $displayFr $displayJa $aliases"
}

data class ResultCodeEntry(
    val code: String,
    val displayEn: String,
    val displayFr: String,
    val displayJa: String,
    val emoji: String,
) {
    fun pick(lang: String): String = when (lang.lowercase().take(2)) {
        "fr" -> displayFr
        "ja" -> displayJa
        else -> displayEn
    }
}

object IpsResultCatalog {

    const val CODE_SYSTEM = "http://loinc.org"

    val ALL: List<ResultEntry> = listOf(
        // ── Glucose / diabetes ──
        ResultEntry("2345-7", "Glucose (serum/plasma)", "Glycémie (sérum/plasma)", "血糖（血清/血漿）", "mg/dL", aliases = "glycemie sugar sucre blood glucose"),
        ResultEntry("14749-6", "Glucose (serum/plasma, mmol/L)", "Glycémie (mmol/L)", "血糖（mmol/L）", "mmol/L", aliases = "glycemie sugar sucre glucose"),
        ResultEntry("4548-4", "Hemoglobin A1c", "Hémoglobine glyquée (HbA1c)", "HbA1c（ヘモグロビンA1c）", "%", aliases = "hba1c a1c glycated diabete diabetes"),
        // ── Kidney ──
        ResultEntry("2160-0", "Creatinine (serum/plasma)", "Créatinine (sérum/plasma)", "クレアチニン（血清）", "mg/dL", aliases = "creat kidney rein"),
        ResultEntry("14682-9", "Creatinine (serum/plasma, µmol/L)", "Créatinine (µmol/L)", "クレアチニン（µmol/L）", "umol/L", aliases = "creat kidney rein"),
        ResultEntry("33914-3", "eGFR (MDRD)", "DFG estimé (MDRD)", "推算糸球体濾過量（eGFR, MDRD）", "mL/min/{1.73_m2}", aliases = "gfr dfg egfr kidney rein filtration"),
        ResultEntry("3094-0", "Urea nitrogen (BUN)", "Urée (azote uréique)", "尿素窒素（BUN）", "mg/dL", aliases = "bun uree urea"),
        ResultEntry("3084-1", "Uric acid", "Acide urique", "尿酸", "mg/dL", aliases = "urate goutte gout"),
        // ── Electrolytes ──
        ResultEntry("2951-2", "Sodium", "Sodium (natrémie)", "ナトリウム", "mmol/L", aliases = "na natremie"),
        ResultEntry("2823-3", "Potassium", "Potassium (kaliémie)", "カリウム", "mmol/L", aliases = "k kaliemie"),
        // ── Blood count ──
        ResultEntry("718-7", "Hemoglobin", "Hémoglobine", "ヘモグロビン", "g/dL", aliases = "hb hgb anemia anemie", emoji = "🩸"),
        ResultEntry("4544-3", "Hematocrit", "Hématocrite", "ヘマトクリット", "%", aliases = "hct", emoji = "🩸"),
        ResultEntry("6690-2", "White blood cells (leukocytes)", "Globules blancs (leucocytes)", "白血球数", "10*3/uL", aliases = "wbc leucocytes leukocytes gb", emoji = "🩸"),
        ResultEntry("777-3", "Platelets", "Plaquettes", "血小板数", "10*3/uL", aliases = "plt thrombocytes", emoji = "🩸"),
        // ── Lipids ──
        ResultEntry("2093-3", "Total cholesterol", "Cholestérol total", "総コレステロール", "mg/dL", aliases = "chol lipides lipids"),
        ResultEntry("14647-2", "Total cholesterol (mmol/L)", "Cholestérol total (mmol/L)", "総コレステロール（mmol/L）", "mmol/L", aliases = "chol lipides lipids"),
        ResultEntry("2085-9", "HDL cholesterol", "Cholestérol HDL", "HDLコレステロール", "mg/dL", aliases = "hdl bon cholesterol"),
        ResultEntry("2089-1", "LDL cholesterol", "Cholestérol LDL", "LDLコレステロール", "mg/dL", aliases = "ldl mauvais cholesterol"),
        ResultEntry("2571-8", "Triglycerides", "Triglycérides", "中性脂肪（トリグリセリド）", "mg/dL", aliases = "tg trig"),
        // ── Liver ──
        ResultEntry("1742-6", "ALT (SGPT)", "ALAT (SGPT)", "ALT（GPT）", "U/L", aliases = "alat alt sgpt transaminase foie liver"),
        ResultEntry("1920-8", "AST (SGOT)", "ASAT (SGOT)", "AST（GOT）", "U/L", aliases = "asat ast sgot transaminase foie liver"),
        ResultEntry("2324-2", "Gamma-GT", "Gamma-GT", "γ-GT", "U/L", aliases = "ggt gamma glutamyl"),
        ResultEntry("1975-2", "Total bilirubin", "Bilirubine totale", "総ビリルビン", "mg/dL", aliases = "bili jaundice ictere"),
        ResultEntry("1751-7", "Albumin", "Albumine", "アルブミン", "g/dL", aliases = "alb"),
        // ── Inflammation / iron / hormones ──
        ResultEntry("1988-5", "C-reactive protein (CRP)", "Protéine C réactive (CRP)", "CRP（C反応性蛋白）", "mg/L", aliases = "crp inflammation"),
        ResultEntry("2276-4", "Ferritin", "Ferritine", "フェリチン", "ng/mL", aliases = "fer iron"),
        ResultEntry("3016-3", "TSH", "TSH (thyréostimuline)", "TSH（甲状腺刺激ホルモン）", "m[IU]/L", aliases = "thyroid thyroide thyrotropin"),
        ResultEntry("1989-3", "Vitamin D (25-OH)", "Vitamine D (25-OH)", "ビタミンD（25-OH）", "ng/mL", aliases = "vit d calcidiol"),
        ResultEntry("2857-1", "PSA", "PSA (antigène prostatique)", "PSA（前立腺特異抗原）", "ng/mL", aliases = "prostate"),
        // ── Coagulation ──
        ResultEntry("6301-6", "INR", "INR", "INR（PT-INR）", "{INR}", aliases = "inr anticoagulant warfarine warfarin coumadin"),
        // ── Coded results ──
        ResultEntry("882-1", "ABO and Rh blood group", "Groupe sanguin ABO / Rhésus", "ABO・Rh血液型", null, kind = ResultValueKind.CODED, emoji = "🩸", aliases = "blood group groupe sanguin rhesus abo"),
    )

    private val byCodeMap: Map<String, ResultEntry> by lazy { ALL.associateBy { it.code } }

    fun byCode(code: String?): ResultEntry? = if (code.isNullOrBlank()) null else byCodeMap[code.trim()]

    fun getDisplay(code: String?, lang: String): String? = byCode(code)?.pick(lang)

    /** UCUM units offered by the unit picker (the catalog default first when known). */
    val UNITS: List<String> = listOf(
        "mmol/L", "mg/dL", "g/dL", "g/L", "umol/L", "µg/L", "ng/mL", "pg/mL", "mg/L", "%",
        "U/L", "m[IU]/L", "[IU]/L", "10*3/uL", "10*6/uL", "10*9/L", "10*12/L", "fL", "pg",
        "mL/min/{1.73_m2}", "{INR}", "s", "mm[Hg]", "kg", "cm",
    )
}

object IpsResultStatusCatalog {

    const val CODE_SYSTEM = "http://hl7.org/fhir/observation-status"
    const val DEFAULT_CODE = "final"

    val ALL: List<ResultCodeEntry> = listOf(
        ResultCodeEntry("final", "Final", "Définitif", "確定", "✅"),
        ResultCodeEntry("preliminary", "Preliminary", "Préliminaire", "暫定", "⏳"),
        ResultCodeEntry("amended", "Amended", "Modifié", "修正済み", "✏️"),
        ResultCodeEntry("corrected", "Corrected", "Corrigé", "訂正済み", "✏️"),
        ResultCodeEntry("cancelled", "Cancelled", "Annulé", "取消", "⛔"),
        ResultCodeEntry("unknown", "Unknown", "Inconnu", "不明", "❔"),
        ResultCodeEntry("entered-in-error", "Entered in error", "Saisi par erreur", "誤入力", "⚠️"),
    )

    private val byCodeMap: Map<String, ResultCodeEntry> by lazy { ALL.associateBy { it.code } }

    fun byCode(code: String?): ResultCodeEntry? = if (code.isNullOrBlank()) null else byCodeMap[code.trim().lowercase()]
}

object IpsResultInterpretationCatalog {

    const val CODE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation"

    /** First entry = "not specified" (code null in the domain). */
    val ALL: List<ResultCodeEntry> = listOf(
        ResultCodeEntry("N", "Normal", "Normal", "正常", "✅"),
        ResultCodeEntry("H", "High", "Élevé", "高値", "🔺"),
        ResultCodeEntry("L", "Low", "Bas", "低値", "🔻"),
        ResultCodeEntry("HH", "Critical high", "Très élevé (critique)", "著しい高値（パニック値）", "🚨"),
        ResultCodeEntry("LL", "Critical low", "Très bas (critique)", "著しい低値（パニック値）", "🚨"),
        ResultCodeEntry("A", "Abnormal", "Anormal", "異常", "⚠️"),
        ResultCodeEntry("POS", "Positive", "Positif", "陽性", "➕"),
        ResultCodeEntry("NEG", "Negative", "Négatif", "陰性", "➖"),
    )

    private val byCodeMap: Map<String, ResultCodeEntry> by lazy { ALL.associateBy { it.code } }

    fun byCode(code: String?): ResultCodeEntry? = if (code.isNullOrBlank()) null else byCodeMap[code.trim().uppercase()]
}

object IpsResultCategoryCatalog {

    val ALL: List<ResultCodeEntry> = listOf(
        ResultCodeEntry(IpsResultCategory.LABORATORY, "Laboratory", "Laboratoire (biologie)", "検査（臨床検査）", "🧪"),
        ResultCodeEntry(IpsResultCategory.IMAGING, "Imaging", "Imagerie (radiologie)", "画像検査", "🩻"),
        ResultCodeEntry(IpsResultCategory.PROCEDURE, "Other diagnostic procedure", "Autre examen diagnostique", "その他の診断検査", "📈"),
    )

    private val byCodeMap: Map<String, ResultCodeEntry> by lazy { ALL.associateBy { it.code } }

    fun byCode(code: String?): ResultCodeEntry? = if (code.isNullOrBlank()) null else byCodeMap[code.trim().lowercase()]
}
