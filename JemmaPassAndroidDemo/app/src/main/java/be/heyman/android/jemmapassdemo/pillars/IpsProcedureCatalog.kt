/*
 * IpsProcedureCatalog.kt — JEMMA Pass · Procedures pillar (sprint 2)
 *
 * Curated common procedures (SNOMED CT, short EN/FR/JA labels) shown as
 * suggestions before the user types; the live tier is the knowledge base
 * (`terminology_codes.category = 'Procedure'`, FTS5). Only well-established
 * SNOMED CT concept ids are listed here — nothing invented.
 */
package be.heyman.android.jemmapassdemo.pillars

data class ProcedureEntry(
    val code: String,
    val displayEn: String,
    val displayFr: String,
    val displayJa: String,
    val emoji: String = "🏥",
) {
    fun pick(lang: String): String = when (lang.lowercase().take(2)) {
        "fr" -> displayFr
        "ja" -> displayJa
        else -> displayEn
    }
    fun searchAliases(): String = "$code $displayEn $displayFr $displayJa"
}

data class ProcedureStatusEntry(
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

object IpsProcedureCatalog {

    const val CODE_SYSTEM = "http://snomed.info/sct"

    val ALL: List<ProcedureEntry> = listOf(
        ProcedureEntry("80146002", "Appendectomy", "Appendicectomie", "虫垂切除術", "🔪"),
        ProcedureEntry("38102005", "Cholecystectomy", "Cholécystectomie", "胆嚢摘出術", "🔪"),
        ProcedureEntry("73761001", "Colonoscopy", "Coloscopie", "大腸内視鏡検査", "🔬"),
        ProcedureEntry("232717009", "Coronary artery bypass graft", "Pontage coronarien", "冠動脈バイパス術", "❤️"),
        ProcedureEntry("34068001", "Heart valve replacement", "Remplacement valvulaire cardiaque", "心臓弁置換術", "❤️"),
        ProcedureEntry("52734007", "Total hip replacement", "Prothèse totale de hanche", "人工股関節全置換術", "🦴"),
        ProcedureEntry("609588000", "Total knee replacement", "Prothèse totale de genou", "人工膝関節全置換術", "🦴"),
        ProcedureEntry("11466000", "Cesarean section", "Césarienne", "帝王切開", "👶"),
        ProcedureEntry("236886002", "Hysterectomy", "Hystérectomie", "子宮摘出術", "🔪"),
        ProcedureEntry("367336001", "Chemotherapy", "Chimiothérapie", "化学療法", "💧"),
        ProcedureEntry("108290001", "Radiotherapy", "Radiothérapie", "放射線治療", "☢️"),
        ProcedureEntry("387713003", "Surgical procedure (unspecified)", "Intervention chirurgicale (non précisée)", "外科手術（未特定）", "🏥"),
    )

    private val byCodeMap: Map<String, ProcedureEntry> by lazy { ALL.associateBy { it.code } }

    fun byCode(code: String?): ProcedureEntry? = if (code.isNullOrBlank()) null else byCodeMap[code.trim()]

    fun getDisplay(code: String?, lang: String): String? = byCode(code)?.pick(lang)
}

object IpsProcedureStatusCatalog {

    const val CODE_SYSTEM = "http://hl7.org/fhir/event-status"
    const val DEFAULT_CODE = "completed"

    val ALL: List<ProcedureStatusEntry> = listOf(
        ProcedureStatusEntry("completed", "Completed", "Réalisée", "実施済み", "✅"),
        ProcedureStatusEntry("in-progress", "In progress", "En cours", "実施中", "⏳"),
        ProcedureStatusEntry("not-done", "Not done", "Non réalisée", "未実施", "🚫"),
        ProcedureStatusEntry("stopped", "Stopped", "Interrompue", "中止", "⛔"),
        ProcedureStatusEntry("unknown", "Unknown", "Inconnu", "不明", "❔"),
        ProcedureStatusEntry("entered-in-error", "Entered in error", "Saisie par erreur", "誤入力", "⚠️"),
    )

    private val byCodeMap: Map<String, ProcedureStatusEntry> by lazy { ALL.associateBy { it.code } }

    fun byCode(code: String?): ProcedureStatusEntry? = if (code.isNullOrBlank()) null else byCodeMap[code.trim().lowercase()]

    fun getDisplay(code: String?, lang: String): String = if (code.isNullOrBlank()) "" else byCode(code)?.pick(lang) ?: code
}
