/*
 * IpsDeviceCatalog.kt — JEMMA Pass · Medical Devices pillar (sprint 2)
 *
 * Curated common implants / assistive devices (SNOMED CT physical objects, short
 * EN/FR/JA labels) shown as suggestions before the user types; the live tier is
 * the knowledge base (`terminology_codes.category = 'Device'`, FTS5).
 */
package be.heyman.android.jemmapassdemo.pillars

data class DeviceEntry(
    val code: String,
    val displayEn: String,
    val displayFr: String,
    val displayJa: String,
    val emoji: String = "📟",
) {
    fun pick(lang: String): String = when (lang.lowercase().take(2)) {
        "fr" -> displayFr
        "ja" -> displayJa
        else -> displayEn
    }
    fun searchAliases(): String = "$code $displayEn $displayFr $displayJa"
}

data class DeviceStatusEntry(
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

object IpsDeviceCatalog {

    const val CODE_SYSTEM = "http://snomed.info/sct"

    val ALL: List<DeviceEntry> = listOf(
        DeviceEntry("14106009", "Cardiac pacemaker", "Stimulateur cardiaque (pacemaker)", "心臓ペースメーカー", "❤️"),
        DeviceEntry("72506001", "Implantable cardioverter-defibrillator", "Défibrillateur implantable", "植込み型除細動器", "❤️"),
        DeviceEntry("102303004", "Coronary artery stent", "Stent coronaire", "冠動脈ステント", "❤️"),
        DeviceEntry("304120007", "Total hip replacement prosthesis", "Prothèse totale de hanche", "人工股関節", "🦴"),
        DeviceEntry("69805005", "Insulin pump", "Pompe à insuline", "インスリンポンプ", "💉"),
        DeviceEntry("6012004", "Hearing aid", "Appareil auditif", "補聴器", "👂"),
    )

    private val byCodeMap: Map<String, DeviceEntry> by lazy { ALL.associateBy { it.code } }

    fun byCode(code: String?): DeviceEntry? = if (code.isNullOrBlank()) null else byCodeMap[code.trim()]

    fun getDisplay(code: String?, lang: String): String? = byCode(code)?.pick(lang)
}

object IpsDeviceStatusCatalog {

    const val CODE_SYSTEM = "http://hl7.org/fhir/device-status"
    const val DEFAULT_CODE = "active"

    val ALL: List<DeviceStatusEntry> = listOf(
        DeviceStatusEntry("active", "In use", "En place / utilisé", "使用中", "✅"),
        DeviceStatusEntry("inactive", "Removed / no longer used", "Retiré / plus utilisé", "取り外し済み・使用停止", "⏹"),
        DeviceStatusEntry("entered-in-error", "Entered in error", "Saisi par erreur", "誤入力", "⚠️"),
    )

    private val byCodeMap: Map<String, DeviceStatusEntry> by lazy { ALL.associateBy { it.code } }

    fun byCode(code: String?): DeviceStatusEntry? = if (code.isNullOrBlank()) null else byCodeMap[code.trim().lowercase()]

    fun getDisplay(code: String?, lang: String): String = if (code.isNullOrBlank()) "" else byCode(code)?.pick(lang) ?: code
}
