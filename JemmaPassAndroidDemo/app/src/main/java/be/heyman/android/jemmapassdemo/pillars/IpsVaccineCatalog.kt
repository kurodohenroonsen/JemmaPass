/*
 * IpsVaccineCatalog.kt — JEMMA Pass · Immunizations pillar
 *
 * Curated, human-friendly vaccine picker entries (SNOMED CT vaccine products,
 * the IPS "Vaccines - IPS" value set family). The long SNOMED fully specified
 * names shipped in assets/jemma/ips_translations.json stay available as the
 * picker's second tier; this catalog gives the ~two dozen vaccines a Belgian
 * or Japanese patient actually carries, with short EN / FR / JA labels.
 *
 * Codes come from the SNOMED CT International "Vaccine product" hierarchy
 * (same codes as the bundled asset list) — no invented identifiers.
 */
package be.heyman.android.jemmapassdemo.pillars

data class VaccineEntry(
    val code: String,
    val displayEn: String,
    val displayFr: String,
    val displayJa: String,
    val emoji: String = "💉",
) {
    fun pick(lang: String): String = when (lang.lowercase().take(2)) {
        "fr" -> displayFr
        "ja" -> displayJa
        else -> displayEn
    }

    /** Every language at once, for pickers: a French user may type the international name. */
    fun searchAliases(): String = "$code $displayEn $displayFr $displayJa"
}

data class ImmunizationStatusEntry(
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

object IpsVaccineCatalog {

    const val CODE_SYSTEM = "http://snomed.info/sct"

    val ALL: List<VaccineEntry> = listOf(
        VaccineEntry("1119349007", "COVID-19 mRNA vaccine", "Vaccin COVID-19 (ARNm)", "新型コロナワクチン（mRNA）", "🦠"),
        VaccineEntry("1181000221105", "Seasonal influenza vaccine", "Vaccin grippe saisonnière", "季節性インフルエンザワクチン", "🤧"),
        VaccineEntry("871826000", "Tetanus-diphtheria (Td)", "Tétanos-diphtérie (Td)", "ジフテリア・破傷風（DT）"),
        VaccineEntry("871876003", "Tetanus-diphtheria-pertussis (Tdap)", "Tétanos-diphtérie-coqueluche (dTpa)", "三種混合（DTaP）"),
        VaccineEntry("871878002", "DTP-polio (DTaP-IPV)", "DTP-polio (DTaP-IPV)", "四種混合（DPT-IPV）"),
        VaccineEntry("871895005", "Hexavalent (DTaP-IPV-Hib-HepB)", "Hexavalent (DTaP-IPV-Hib-HepB)", "六種混合（DPT-IPV-Hib-HepB）"),
        VaccineEntry("863911006", "Tetanus vaccine", "Vaccin antitétanique", "破傷風ワクチン"),
        VaccineEntry("1031000221108", "Polio vaccine (IPV)", "Vaccin polio (IPV)", "ポリオワクチン（IPV）"),
        VaccineEntry("836374004", "Hepatitis B vaccine", "Vaccin hépatite B", "B型肝炎ワクチン"),
        VaccineEntry("836375003", "Hepatitis A vaccine", "Vaccin hépatite A", "A型肝炎ワクチン"),
        VaccineEntry("871803007", "Hepatitis A + B vaccine", "Vaccin hépatite A + B", "A型・B型肝炎混合ワクチン"),
        VaccineEntry("871831003", "Measles-mumps-rubella (MMR)", "Rougeole-oreillons-rubéole (RRO)", "麻疹・おたふく・風疹（MMR）"),
        VaccineEntry("871765008", "Measles vaccine", "Vaccin rougeole", "麻疹ワクチン"),
        VaccineEntry("836388000", "Rubella vaccine", "Vaccin rubéole", "風疹ワクチン"),
        VaccineEntry("836495005", "Varicella / zoster vaccine", "Vaccin varicelle / zona", "水痘・帯状疱疹ワクチン"),
        VaccineEntry("836379009", "HPV vaccine", "Vaccin HPV", "HPVワクチン"),
        VaccineEntry("836380007", "Hib vaccine", "Vaccin Hib", "ヒブワクチン"),
        VaccineEntry("836398006", "Pneumococcal vaccine", "Vaccin pneumocoque", "肺炎球菌ワクチン"),
        VaccineEntry("1801000221105", "Pneumococcal conjugate vaccine (PCV)", "Vaccin pneumocoque conjugué (PCV)", "肺炎球菌結合型ワクチン（PCV）"),
        VaccineEntry("836401009", "Meningococcal vaccine", "Vaccin méningocoque", "髄膜炎菌ワクチン"),
        VaccineEntry("871873006", "Meningococcal ACWY vaccine", "Vaccin méningocoque ACWY", "髄膜炎菌ACWYワクチン"),
        VaccineEntry("1981000221108", "Meningococcal B vaccine", "Vaccin méningocoque B", "髄膜炎菌Bワクチン"),
        VaccineEntry("836387005", "Rotavirus vaccine", "Vaccin rotavirus", "ロタウイルスワクチン"),
        VaccineEntry("836402002", "BCG (tuberculosis) vaccine", "Vaccin BCG (tuberculose)", "BCGワクチン（結核）"),
        VaccineEntry("836378001", "Japanese encephalitis vaccine", "Vaccin encéphalite japonaise", "日本脳炎ワクチン"),
        VaccineEntry("836403007", "Tick-borne encephalitis vaccine", "Vaccin encéphalite à tiques", "ダニ媒介性脳炎ワクチン"),
        VaccineEntry("836393002", "Rabies vaccine", "Vaccin rage", "狂犬病ワクチン"),
        VaccineEntry("836385002", "Yellow fever vaccine", "Vaccin fièvre jaune", "黄熱ワクチン"),
        VaccineEntry("836390004", "Typhoid vaccine", "Vaccin typhoïde", "腸チフスワクチン"),
        VaccineEntry("836383009", "Cholera vaccine", "Vaccin choléra", "コレラワクチン"),
        VaccineEntry("787859002", "Other vaccine (unspecified)", "Autre vaccin (non précisé)", "その他のワクチン（未特定）", "❔"),
    )

    private val byCodeMap: Map<String, VaccineEntry> by lazy { ALL.associateBy { it.code } }

    fun byCode(code: String?): VaccineEntry? {
        if (code.isNullOrBlank()) return null
        return byCodeMap[code.trim()]
    }

    /** Localized short label, or null when the code is not in the curated list. */
    fun getDisplay(code: String?, lang: String): String? = byCode(code)?.pick(lang)
}

object IpsImmunizationStatusCatalog {

    const val CODE_SYSTEM = "http://hl7.org/fhir/event-status"
    const val DEFAULT_CODE = "completed"

    val ALL: List<ImmunizationStatusEntry> = listOf(
        ImmunizationStatusEntry("completed", "Given", "Administré", "接種済み", "✅"),
        ImmunizationStatusEntry("not-done", "Not done", "Non administré", "未接種", "🚫"),
        ImmunizationStatusEntry("entered-in-error", "Entered in error", "Saisi par erreur", "誤入力", "⚠️"),
    )

    private val byCodeMap: Map<String, ImmunizationStatusEntry> by lazy { ALL.associateBy { it.code } }

    fun byCode(code: String?): ImmunizationStatusEntry? {
        if (code.isNullOrBlank()) return null
        return byCodeMap[code.trim().lowercase()]
    }

    fun getDisplay(code: String?, lang: String): String {
        if (code.isNullOrBlank()) return ""
        return byCode(code)?.pick(lang) ?: code
    }
}
