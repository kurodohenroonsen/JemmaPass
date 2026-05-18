/*
 * IpsRelationshipCatalog.kt — JEMMA Pass · Plan B · v2.6.0 · L5b
 *
 * Catalog des 39 codes V3-RoleCode du ValueSet FHIR IPS
 * `personal-relationship-uv-ips` — utilisés pour Patient.contact.relationship.
 *
 * Source de vérité (codes + display EN) :
 *   - HL7 v3 RoleCode (http://terminology.hl7.org/CodeSystem/v3-RoleCode)
 *   - HTML legacy : assets/app/js/data/ips_registry.js (39 entries EN)
 *   - KB DIAMOND v1.2 : table ips_valuesets vs_id='personal-relationship-uv-ips' (39 rows EN)
 *
 * Traductions FR/JA — fournies dans ce fichier (pas dans le HTML legacy
 * qui n'avait que l'EN pour ces codes). Quand la KB v1.2 sera enrichie
 * avec ips_valuesets_translations peuplée pour ces 39 codes, ce catalog
 * deviendra redondant — mais en attendant il garantit que JEMMA tourne
 * en français et japonais dès maintenant.
 *
 * 🎯 PHILOSOPHIE — code-first :
 *   JContact.r persiste TOUJOURS le code V3-RoleCode (ex "FTH"),
 *   JAMAIS le display label. Le label est juste pour l'UI au moment du
 *   rendu, via getDisplay(code, lang).
 *
 * Logging : tag JEMMA-IPS-REL
 */
package be.heyman.android.jemmapassdemo.pillars

/**
 * Une entrée du ValueSet personal-relationship-uv-ips.
 *
 * @property code le code V3-RoleCode (ex "FTH", "MTH", "DOMPART")
 * @property displayEn le display canonique anglais (matches KB ips_valuesets.display_en)
 * @property displayFr le display français
 * @property displayJa le display japonais
 */
data class RelationshipEntry(
    val code: String,
    val displayEn: String,
    val displayFr: String,
    val displayJa: String,
) {
    fun pick(lang: String): String = when (lang.lowercase().take(2)) {
        "fr" -> displayFr
        "ja" -> displayJa
        else -> displayEn
    }

    /** Pour l'affichage compact dans un picker : "FTH · Père". */
    fun compactLabel(lang: String): String = "$code · ${pick(lang)}"
}

object IpsRelationshipCatalog {

    /** URI canonique du code system V3-RoleCode pour la sérialisation FHIR. */
    const val CODE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-RoleCode"

    /** vs_id du ValueSet IPS, à passer à KnowledgeBaseService.listValueSetCodes() */
    const val VALUE_SET_ID = "personal-relationship-uv-ips"

    /**
     * Les 39 codes du ValueSet personal-relationship-uv-ips (FHIR IPS).
     *
     * L'ordre suit celui du HTML legacy ips_registry.js (= ordre alpha
     * du V3-RoleCode HL7 source). L'UI peut re-trier par lang via
     * sortedByDisplay(lang).
     */
    val ALL: List<RelationshipEntry> = listOf(
        RelationshipEntry("AUNT",      "aunt",                 "Tante",                       "おば"),
        RelationshipEntry("CHILD",     "child",                "Enfant",                      "子"),
        RelationshipEntry("CHLDADOPT", "adopted child",        "Enfant adopté(e)",            "養子"),
        RelationshipEntry("CHLDFOST",  "foster child",         "Enfant en famille d'accueil", "里子"),
        RelationshipEntry("CHLDINLAW", "child in-law",         "Belle-fille / Beau-fils",     "義理の子"),
        RelationshipEntry("COUSN",     "cousin",               "Cousin(e)",                   "いとこ"),
        RelationshipEntry("DAU",       "natural daughter",     "Fille (biologique)",          "実の娘"),
        RelationshipEntry("DAUADOPT",  "adopted daughter",     "Fille adoptée",               "養女"),
        RelationshipEntry("DAUC",      "daughter",             "Fille",                       "娘"),
        RelationshipEntry("DAUFOST",   "foster daughter",      "Fille en famille d'accueil",  "里娘"),
        RelationshipEntry("DAUINLAW",  "daughter in-law",      "Belle-fille",                 "義理の娘"),
        RelationshipEntry("DOMPART",   "domestic partner",     "Partenaire",                  "パートナー"),
        RelationshipEntry("FAMMEMB",   "family member",        "Membre de la famille",        "家族"),
        RelationshipEntry("FRND",      "unrelated friend",     "Ami(e)",                      "友人"),
        RelationshipEntry("FTH",       "father",               "Père",                        "父"),
        RelationshipEntry("FTHINLAW",  "father-in-law",        "Beau-père",                   "義父"),
        RelationshipEntry("GGRPRN",    "great grandparent",    "Arrière-grand-parent",        "曾祖父母"),
        RelationshipEntry("GRNDCHILD", "grandchild",           "Petit-enfant",                "孫"),
        RelationshipEntry("GRPRN",     "grandparent",          "Grand-parent",                "祖父母"),
        RelationshipEntry("MTH",       "mother",               "Mère",                        "母"),
        RelationshipEntry("MTHINLAW",  "mother-in-law",        "Belle-mère",                  "義母"),
        RelationshipEntry("NBOR",      "neighbor",             "Voisin(e)",                   "隣人"),
        RelationshipEntry("NCHILD",    "natural child",        "Enfant biologique",           "実の子"),
        RelationshipEntry("NIENEPH",   "niece/nephew",         "Nièce / Neveu",               "姪・甥"),
        RelationshipEntry("PRN",       "parent",               "Parent",                      "親"),
        RelationshipEntry("PRNINLAW",  "parent in-law",        "Beau-parent",                 "義理の親"),
        RelationshipEntry("ROOM",      "roomate",              "Colocataire",                 "ルームメイト"),
        RelationshipEntry("SIB",       "sibling",              "Frère / Sœur",                "兄弟姉妹"),
        RelationshipEntry("SIGOTHR",   "significant other",    "Personne significative",      "重要な相手"),
        RelationshipEntry("SON",       "natural son",          "Fils (biologique)",           "実の息子"),
        RelationshipEntry("SONADOPT",  "adopted son",          "Fils adopté",                 "養子"),
        RelationshipEntry("SONC",      "son",                  "Fils",                        "息子"),
        RelationshipEntry("SONFOST",   "foster son",           "Fils en famille d'accueil",   "里息子"),
        RelationshipEntry("SONINLAW",  "son in-law",           "Gendre",                      "義理の息子"),
        RelationshipEntry("SPS",       "spouse",               "Conjoint(e)",                 "配偶者"),
        RelationshipEntry("STPCHLD",   "step child",           "Beau-fils / Belle-fille",     "継子"),
        RelationshipEntry("STPDAU",    "stepdaughter",         "Belle-fille (recomposée)",    "継娘"),
        RelationshipEntry("STPSON",    "stepson",              "Beau-fils (recomposé)",       "継息子"),
        RelationshipEntry("UNCLE",     "uncle",                "Oncle",                       "おじ"),
    )

    /** Index code → entry pour lookup O(1). Calculé lazily 1x. */
    private val byCode: Map<String, RelationshipEntry> by lazy {
        ALL.associateBy { it.code }
    }

    /**
     * Retourne le display localisé pour un code V3-RoleCode.
     * Si le code n'est pas dans le catalog (ex: code custom), retourne
     * le code lui-même pour ne JAMAIS afficher de vide.
     */
    fun getDisplay(code: String?, lang: String): String {
        if (code.isNullOrBlank()) return ""
        return byCode[code.trim().uppercase()]?.pick(lang) ?: code
    }

    /**
     * Liste les 39 entries triées par display dans la lang demandée.
     * Utile pour un picker plein écran.
     */
    fun sortedByDisplay(lang: String): List<RelationshipEntry> {
        return ALL.sortedBy { it.pick(lang).lowercase() }
    }

    /**
     * Vérifie qu'un code donné existe dans le ValueSet (sanity check
     * avant persistance, ou pour valider un code venu d'un QR import).
     */
    fun isValidCode(code: String?): Boolean {
        if (code.isNullOrBlank()) return false
        return byCode.containsKey(code.trim().uppercase())
    }
}
