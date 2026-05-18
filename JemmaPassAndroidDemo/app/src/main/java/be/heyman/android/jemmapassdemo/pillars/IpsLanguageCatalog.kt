/*
 * IpsLanguageCatalog.kt — JEMMA Pass · v2.6.0h
 *
 * BCP-47 language tags pour FHIR Patient.communication.language.
 * Subset pratique pour le pitch (BE + JP + EU principaux).
 *
 * Spec : https://www.rfc-editor.org/info/bcp47
 */
package be.heyman.android.jemmapassdemo.pillars

data class LanguageEntry(
    /** BCP-47 tag (e.g. "fr-BE", "ja-JP"). */
    val tag: String,
    /** Native autonym + English label combined. */
    val displayEn: String,
    val displayFr: String,
    val displayJa: String,
    val flag: String,
) {
    fun pick(lang: String): String = when (lang.lowercase().take(2)) {
        "fr" -> displayFr
        "ja" -> displayJa
        else -> displayEn
    }
}

object IpsLanguageCatalog {

    val ALL: List<LanguageEntry> = listOf(
        // BE — pertinent pour Aktina ASBL
        LanguageEntry("fr-BE", "French (Belgium)", "Français (Belgique)", "フランス語 (ベルギー)", "🇧🇪"),
        LanguageEntry("nl-BE", "Dutch (Belgium)",  "Néerlandais (Belgique)", "オランダ語 (ベルギー)", "🇧🇪"),
        LanguageEntry("de-BE", "German (Belgium)", "Allemand (Belgique)",    "ドイツ語 (ベルギー)",   "🇧🇪"),
        // JP — pertinent pour Misako / Henro
        LanguageEntry("ja-JP", "Japanese",         "Japonais",               "日本語",               "🇯🇵"),
        // FR
        LanguageEntry("fr-FR", "French (France)",  "Français (France)",     "フランス語 (フランス)", "🇫🇷"),
        // Other EU
        LanguageEntry("en-US", "English (US)",     "Anglais (US)",          "英語 (米国)",         "🇺🇸"),
        LanguageEntry("en-GB", "English (UK)",     "Anglais (UK)",          "英語 (英国)",         "🇬🇧"),
        LanguageEntry("de-DE", "German",           "Allemand",              "ドイツ語",            "🇩🇪"),
        LanguageEntry("es-ES", "Spanish",          "Espagnol",              "スペイン語",          "🇪🇸"),
        LanguageEntry("it-IT", "Italian",          "Italien",               "イタリア語",          "🇮🇹"),
        LanguageEntry("nl-NL", "Dutch (NL)",       "Néerlandais (Pays-Bas)", "オランダ語 (オランダ)", "🇳🇱"),
        LanguageEntry("pt-PT", "Portuguese",       "Portugais",             "ポルトガル語",        "🇵🇹"),
        LanguageEntry("zh-CN", "Chinese (Simplified)", "Chinois (simplifié)", "中国語 (簡体字)",    "🇨🇳"),
        LanguageEntry("ko-KR", "Korean",           "Coréen",                "韓国語",              "🇰🇷"),
        LanguageEntry("ar-SA", "Arabic",           "Arabe",                 "アラビア語",          "🇸🇦"),
    )

    private val byTag: Map<String, LanguageEntry> by lazy { ALL.associateBy { it.tag } }

    fun byTag(tag: String?): LanguageEntry? {
        if (tag.isNullOrBlank()) return null
        return byTag[tag.trim()]
    }

    fun getDisplay(tag: String?, lang: String): String {
        if (tag.isNullOrBlank()) return ""
        return byTag(tag)?.pick(lang) ?: tag
    }
}
