/*
 * KnowledgeBaseServiceDose.kt — JEMMA Pass · v2.6.0 · L_PHASE12
 *
 * Extension à KnowledgeBaseService pour récupérer les doses standard
 * WHO ATC/DDD depuis la table `dosages` de knowledge_full.db.
 *
 * Données disponibles (audit DB v2.0-omnis) :
 *   - 2229 médicaments couverts (sur 6109 ATC totaux = 36.5%)
 *   - Population : adult uniquement (zéro entrée pediatric/geriatric)
 *   - Unités UCUM : g (1089), mg (1004), u (53), mcg (41), tu (18),
 *     mu (12), ml (8), tablet (2), mmol (1), lsu (1)
 *   - 16 routes : oral (1652), parenteral (442), nasal (36), vaginal (27),
 *     inhal.aerosol (23), inhal.powder (13), inhal.solution (10),
 *     implant (5), sublingual (3), rectal (3), intravesical (3),
 *     transdermal (1), s.c. implant (1), instill.solution (1), chewing gum (1)
 *   - Notes cliniques pour ~80-100 médicaments (insulines, héparines, fer, etc.)
 *
 * Concept clé — DDD (Defined Daily Dose) :
 *   "Dose moyenne quotidienne d'entretien d'un médicament utilisé pour son
 *   indication principale chez l'adulte" — référence statistique WHO, PAS une
 *   prescription individuelle. À utiliser uniquement comme suggestion avec
 *   disclaimer clair.
 *
 * Sécurité critique :
 *   - JAMAIS auto-fill chez les patients pédiatriques. Si l'âge est connu et < 18,
 *     on ne propose pas la DDD adulte (risque médico-légal massif).
 *   - Toujours afficher la note clinique quand elle existe (contexte essentiel).
 *
 * Tag log : JEMMA-DOSE-KB · 💊 dose / ❌ no dose
 */
package be.heyman.android.jemmapassdemo.kb

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG_DOSE = "JEMMA-DOSE-KB"

/**
 * Une dose standard WHO ATC/DDD pour un médicament.
 *
 * @property atcCode le code ATC source (ex: "A10BA02")
 * @property doseDdd valeur numérique de la DDD (ex: 2.0)
 * @property doseUnit unité raw de la KB (ex: "g", "mg", "u", "mcg", "tu", "mu", "ml")
 * @property route route raw de la KB (ex: "oral", "parenteral", "inhal.aerosol")
 * @property note contexte clinique optionnel (ex: "as sodium salt", "anti Xa", "depot inj")
 * @property population toujours "adult" dans cette KB
 */
data class DoseStandard(
    val atcCode: String,
    val doseDdd: Double,
    val doseUnit: String,
    val route: String,
    val population: String,
    val note: String?,
)

/**
 * 🆕 PHASE12 — Récupère la dose WHO ATC/DDD pour un code ATC donné.
 *
 * Retourne null si :
 *   - atcCode est blank
 *   - Pas d'entrée dans la table dosages pour cet ATC (couverture 36.5%)
 *
 * Latence typique : 1-3 ms (table indexée sur drug_atc).
 *
 * Usage typique : appelé après `resolveAtcCode()` pour pré-fill le formulaire
 * de médicament. Le caller doit vérifier l'âge du patient avant de proposer
 * cette dose à l'utilisateur.
 */
suspend fun KnowledgeBaseService.getDoseStandard(
    kbManager: KnowledgeBaseManager,
    atcCode: String?,
): DoseStandard? = withContext(Dispatchers.IO) {
    if (atcCode.isNullOrBlank()) return@withContext null
    val tStart = System.currentTimeMillis()
    val db = kbManager.database() ?: run {
        Log.w(TAG_DOSE, "[t=${System.currentTimeMillis()}] ⚠ KB DB not ready for dose lookup")
        return@withContext null
    }

    val sql = """
        SELECT drug_atc, route, dose_ddd, dose_unit, population, note
        FROM dosages
        WHERE drug_atc = ?
        LIMIT 1
    """.trimIndent()

    try {
        db.rawQuery(sql, arrayOf(atcCode.trim())).use { cursor ->
            if (cursor.moveToFirst()) {
                val atc = cursor.getString(0) ?: return@withContext null
                val route = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "oral"
                val dose = if (cursor.isNull(2)) 0.0 else cursor.getDouble(2)
                val unit = cursor.getString(3)?.takeIf { it.isNotBlank() } ?: ""
                val pop = cursor.getString(4)?.takeIf { it.isNotBlank() } ?: "adult"
                val note = cursor.getString(5)?.takeIf { it.isNotBlank() }
                val result = DoseStandard(
                    atcCode = atc,
                    doseDdd = dose,
                    doseUnit = unit,
                    route = route,
                    population = pop,
                    note = note,
                )
                val durationMs = System.currentTimeMillis() - tStart
                Log.i(TAG_DOSE, "[t=${System.currentTimeMillis()}] 💊 dose found · " +
                    "atc=$atc · ${dose}${unit} · route=$route · note='${note?.take(30) ?: ""}' · ${durationMs}ms")
                return@withContext result
            }
        }
    } catch (e: Exception) {
        Log.e(TAG_DOSE, "[t=${System.currentTimeMillis()}] ❌ dose query failed for $atcCode: ${e.message}", e)
        return@withContext null
    }
    Log.d(TAG_DOSE, "[t=${System.currentTimeMillis()}] ❌ no dose for atc=$atcCode")
    null
}

/**
 * 🆕 PHASE12 — Récupère les doses standard pour une LIST d'ATC codes en batch.
 *
 * Utilisé par le picker pour enrichir les résultats de recherche avec la dose
 * en une seule query SQL (au lieu de N queries séparées).
 *
 * Chunking limité à 900 placeholders par sécurité (SQLite limit ~999).
 *
 * @return Map<atcCode, DoseStandard> — atcs sans entry dans dosages sont absents
 */
suspend fun KnowledgeBaseService.batchGetDoseStandards(
    kbManager: KnowledgeBaseManager,
    atcCodes: List<String>,
): Map<String, DoseStandard> = withContext(Dispatchers.IO) {
    if (atcCodes.isEmpty()) return@withContext emptyMap()
    val tStart = System.currentTimeMillis()
    val db = kbManager.database() ?: return@withContext emptyMap()

    val out = mutableMapOf<String, DoseStandard>()
    val cleanCodes = atcCodes.filter { it.isNotBlank() }.distinct()
    cleanCodes.chunked(900).forEach { chunk ->
        val placeholders = chunk.joinToString(",") { "?" }
        val sql = """
            SELECT drug_atc, route, dose_ddd, dose_unit, population, note
            FROM dosages
            WHERE drug_atc IN ($placeholders)
        """.trimIndent()
        try {
            db.rawQuery(sql, chunk.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val atc = cursor.getString(0) ?: continue
                    val route = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "oral"
                    val dose = if (cursor.isNull(2)) 0.0 else cursor.getDouble(2)
                    val unit = cursor.getString(3)?.takeIf { it.isNotBlank() } ?: ""
                    val pop = cursor.getString(4)?.takeIf { it.isNotBlank() } ?: "adult"
                    val note = cursor.getString(5)?.takeIf { it.isNotBlank() }
                    out[atc] = DoseStandard(
                        atcCode = atc,
                        doseDdd = dose,
                        doseUnit = unit,
                        route = route,
                        population = pop,
                        note = note,
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_DOSE, "[t=${System.currentTimeMillis()}] ❌ batch dose query failed: ${e.message}", e)
        }
    }
    val durationMs = System.currentTimeMillis() - tStart
    Log.i(TAG_DOSE, "[t=${System.currentTimeMillis()}] 💊 batchGetDoseStandards · " +
        "asked=${cleanCodes.size} · found=${out.size} · ${durationMs}ms")
    out
}

/**
 * 🆕 PHASE12 — Helper pour formater une dose pour affichage UI.
 *
 * Convertit les unités raw de la KB vers une forme display-friendly :
 *   - "mcg" → "μg"
 *   - "u"   → "UI" (FR) / "IU" (EN)
 *   - "tu"  → "kUI" / "kIU"
 *   - "mu"  → "MUI" / "MIU"
 *
 * Et formate le nombre proprement (pas de "2.0" pour Metformine, "2" suffit).
 *
 * @param lang code langue ISO (fr, en, ja, etc.) pour les unités traduites
 * @return string courte type "2 g" ou "500 mg"
 */
fun formatDoseForDisplay(dose: DoseStandard, lang: String = "fr"): String {
    val unit = when (dose.doseUnit.lowercase()) {
        "mcg" -> "μg"
        "u" -> if (lang.startsWith("en")) "IU" else "UI"
        "tu" -> if (lang.startsWith("en")) "kIU" else "kUI"
        "mu" -> if (lang.startsWith("en")) "MIU" else "MUI"
        "ml" -> "mL"
        "tablet" -> if (lang.startsWith("en")) "tab" else "cp"
        else -> dose.doseUnit
    }
    // Format le nombre : 2.0 → "2", 0.85 → "0.85", 500.0 → "500"
    val value = if (dose.doseDdd == dose.doseDdd.toLong().toDouble()) {
        dose.doseDdd.toLong().toString()
    } else {
        dose.doseDdd.toString().trimEnd('0').trimEnd('.')
    }
    return "$value $unit"
}

/**
 * 🆕 PHASE12 — Mappe une route de la KB vers le shortCode de IpsRouteCatalog (O/I/T/S).
 *
 * Les 16 routes de la KB n'ont que 4 correspondants dans IpsRouteCatalog :
 *   oral, sublingual, chewing gum         → O (Oral)
 *   parenteral, implant, s.c. implant     → I (Injection)
 *   inhal.aerosol, inhal.powder,
 *     inhal.solution                       → I (faute de mieux — pas idéal cliniquement)
 *   topical, transdermal, nasal,
 *     instill.solution, intravesical,
 *     rectal, vaginal                      → T (Topical)
 *   (subcutaneous serait S mais aucune entrée DB n'utilise ce mot exact)
 *
 * @return un shortCode parmi "O"/"I"/"T"/"S" ou null si route inconnue
 */
fun mapKbRouteToShortCode(kbRoute: String?): String? {
    if (kbRoute.isNullOrBlank()) return null
    return when (kbRoute.lowercase().trim()) {
        "oral", "sublingual", "chewing gum" -> "O"
        "parenteral", "implant", "s.c. implant",
        "inhal.aerosol", "inhal.powder", "inhal.solution" -> "I"
        "topical", "transdermal", "nasal",
        "instill.solution", "intravesical",
        "rectal", "vaginal" -> "T"
        else -> null
    }
}
