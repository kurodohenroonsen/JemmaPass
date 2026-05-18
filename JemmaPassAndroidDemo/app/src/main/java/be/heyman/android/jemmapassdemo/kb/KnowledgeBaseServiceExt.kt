/*
 * KnowledgeBaseServiceExt.kt — JEMMA Pass · v4 · FIXES (#1 + #2)
 *
 * Extensions à KnowledgeBaseService pour la refonte Live Scan v4.
 * Fichier séparé pour ne PAS toucher KnowledgeBaseService.kt original.
 *
 * 🆕 v4.1 FIX-AWAIT-DB — Avant ce fix, on utilisait reflection pour
 * appeler la méthode privée `awaitDb()` du KnowledgeBaseService. Ça
 * échouait à chaque call ('NoSuchMethodException') car le nom mangled
 * Kotlin pour `private suspend fun` n'est pas `awaitDb$app_debug`.
 * Conséquence : `getAlternativesForAtc` retournait toujours emptyList
 * silencieusement → cascade ne testait jamais aucun candidat.
 *
 * Solution : on prend en argument le KnowledgeBaseManager (dont
 * `database()` est PUBLIC, cf KnowledgeBaseManager.kt:141). Plus de
 * reflection, code propre, testable.
 *
 * 🆕 v4.1 FIX-ALLERGY-XR — Nouvelle fonction `getAllergyAvoidClasses`
 * qui mappe un code allergie patient (SNOMED/RxNorm/...) vers la
 * liste des classes ATC à éviter, via la table allergy_cross_reactivity
 * (52 rows seed clinique dans DIAMOND v1.1). Permet à la cascade
 * AlternativeFinder de filtrer correctement même quand l'allergie
 * patient n'est pas codée en ATC.
 *
 * Tables sources :
 *   atc_alternatives          (643,763 rows · forge GOLDEN step)
 *   allergy_cross_reactivity  (52 rows · seed clinique manuel)
 *
 * Log channel : JEMMA-KB-ALT
 */
package be.heyman.android.jemmapassdemo.kb

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "JEMMA-KB-ALT"

/**
 * Une alternative ATC pour un drug donné.
 *
 * @param altAtc ATC L5 de l'alternative
 * @param score score de pertinence clinique (0.0 à 1.0)
 * @param relation type de lien ('sibling_l5' / 'sibling_l4' / 'ddinter_alternative')
 * @param avoidIfAllergic CSV de classes ATC à éviter si patient allergique
 *   (ex: "J01C,J01D" → cette alternative est dans la même famille que ces classes)
 */
data class AtcAlternative(
    val altAtc: String,
    val score: Float,
    val relation: String,
    val avoidIfAllergic: String,
)

/**
 * Retourne les alternatives pour un ATC donné, triées par score desc.
 *
 * 🆕 v4.1 FIX-AWAIT-DB : prend `KnowledgeBaseManager` en argument.
 * Plus de reflection — `kbManager.database()` est public.
 *
 * Si la DB n'est pas prête ou la query throw, retourne emptyList +
 * log warning. **Ne crash JAMAIS** le caller.
 *
 * @param kbManager le KnowledgeBaseManager Hilt-injected (expose database())
 * @param atc le code ATC L5 source (ex: "J01CR02")
 * @param maxCandidates plafond (défaut 30 — suffit pour la cascade)
 */
suspend fun KnowledgeBaseService.getAlternativesForAtc(
    kbManager: KnowledgeBaseManager,
    atc: String,
    maxCandidates: Int = 30,
): List<AtcAlternative> = withContext(Dispatchers.IO) {
    val tStart = System.currentTimeMillis()
    val out = mutableListOf<AtcAlternative>()

    val db = kbManager.database()
    if (db == null) {
        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ DB not ready for getAlternativesForAtc($atc)")
        return@withContext emptyList()
    }

    try {
        val cur = db.rawQuery(
            """
            SELECT alt_atc, score, relation, COALESCE(avoid_if_allergic, '') as avoid
            FROM atc_alternatives
            WHERE source_atc = ?
            ORDER BY score DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(atc, maxCandidates.toString()),
        )
        cur.use { c ->
            while (c.moveToNext()) {
                out.add(
                    AtcAlternative(
                        altAtc = c.getString(0),
                        score = c.getDouble(1).toFloat(),
                        relation = c.getString(2) ?: "",
                        avoidIfAllergic = c.getString(3) ?: "",
                    )
                )
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ getAlternativesForAtc($atc) threw: ${e.message}")
        return@withContext emptyList()
    }

    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔁 alternatives for $atc → ${out.size} " +
        "(${System.currentTimeMillis() - tStart}ms)")
    out
}

/**
 * Lookup de la classe ATC à éviter pour un code allergie patient.
 * Utilise la table allergy_cross_reactivity (seed clinique 52 rows).
 *
 * 🆕 v4.1 FIX-ALLERGY-XR : nouvelle fonction. Avant ce fix,
 * `AlternativeFinder.allergyAtcClass()` retournait null pour les
 * codes SNOMED non-ATC → la cascade ne filtrait pas correctement.
 *
 * Schéma attendu (à confirmer avec adb shell sqlite3 .schema) :
 *   CREATE TABLE allergy_cross_reactivity (
 *     allergen_code TEXT,         -- SNOMED ou RxNorm CUI
 *     allergen_name TEXT,         -- "penicillin" / "sulfa" / etc.
 *     avoid_atc_class TEXT,       -- "J01C" / "J01D"
 *     severity TEXT,              -- "high" / "low"
 *     rationale TEXT,
 *     PRIMARY KEY (allergen_code, avoid_atc_class)
 *   );
 *
 * @param kbManager le KnowledgeBaseManager (expose database())
 * @param allergyCode code SNOMED/RxNorm/autre du patient
 * @return liste de classes ATC à éviter (ex ["J01C", "J01D"]) ou vide
 */
/**
 * Lookup des classes ATC cross-réactives pour une classe d'allergie patient.
 *
 * 🆕 v4.2 DIAMOND-v1.2 ATC-ONLY ALIGNMENT — Cette fonction est maintenant
 * strictement ATC-only conformément au guide v2 de la KB.
 *
 *   Schéma DIAMOND v1.2 `allergy_cross_reactivity` :
 *     allergen_class TEXT          -- ATC L3/L4 ex "J01C" (penicillins)
 *     cross_reactive_class TEXT    -- ATC L3/L4 ex "J01D" (cephalosporins)
 *     risk_level TEXT              -- ENUM: HIGH/MODERATE/LOW/NONE
 *     percent_estimate TEXT        -- ex "1-10%" (UI only)
 *     source TEXT                  -- ex "WHO_2019"
 *     PRIMARY KEY (allergen_class, cross_reactive_class)
 *
 * **Important** — le caller doit avoir déjà résolu son SNOMED allergy code en
 * une classe ATC L3/L4 (via `terminology_codes.snomed_code → atc_code` +
 * `atc_hierarchy` walk pour remonter au niveau L3/L4). Cette fonction NE FAIT
 * PAS de string matching sur noms.
 *
 * **Auto-réactivité** : si le drug appartient déjà à la même classe que
 * l'allergie patient (drugAtc starts with allergenClass), le caller doit
 * matcher directement sans même appeler cette fonction (cf KbCrossCheck
 * étape 1 et 2).
 *
 * Cette fonction sert UNIQUEMENT à trouver les **cross-réactivités
 * inter-classes** (penicillin × cephalosporin, sulfa × diuretics, etc.).
 *
 * @param kbManager le KnowledgeBaseManager (expose database())
 * @param allergenClass classe ATC L3 ou L4 du patient (ex "J01C")
 * @return liste de classes ATC cross-réactives (ex ["J01D"]) ou vide
 */
suspend fun KnowledgeBaseService.getAllergyAvoidClasses(
    kbManager: KnowledgeBaseManager,
    allergenClass: String,
): List<String> = withContext(Dispatchers.IO) {
    if (allergenClass.isBlank()) return@withContext emptyList()
    // Sanity : doit être un ATC L1-L4 (1, 3, 4 ou 5 chars), pas un L5 ni un SNOMED.
    val cls = allergenClass.trim().uppercase()
    if (!cls.matches(Regex("^[A-Z](\\d{2}([A-Z]([A-Z])?)?)?$"))) {
        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ getAllergyAvoidClasses: " +
            "allergenClass '$cls' is not a valid ATC L1-L4 code — skipping")
        return@withContext emptyList()
    }

    val db = kbManager.database() ?: run {
        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ DB not ready for getAllergyAvoidClasses($cls)")
        return@withContext emptyList<String>()
    }

    val out = mutableListOf<String>()
    try {
        // DIAMOND v1.2 schema (canonique)
        val cur = db.rawQuery(
            """
            SELECT DISTINCT cross_reactive_class
            FROM allergy_cross_reactivity
            WHERE allergen_class = ?
              AND risk_level IN ('HIGH', 'MODERATE')
            """.trimIndent(),
            arrayOf(cls),
        )
        cur.use { c ->
            while (c.moveToNext()) {
                val crossCls = c.getString(0)
                if (!crossCls.isNullOrBlank()) out.add(crossCls)
            }
        }
    } catch (e: Exception) {
        // Si le schema diffère, log et fallback essai legacy v1.
        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ getAllergyAvoidClasses($cls) " +
            "v1.2 schema failed: ${e.message} — trying v1 fallback")
        try {
            val cur2 = db.rawQuery(
                """
                SELECT DISTINCT avoid_atc_class
                FROM allergy_cross_reactivity
                WHERE allergen_code = ? OR allergen_class = ?
                """.trimIndent(),
                arrayOf(cls, cls),
            )
            cur2.use { c2 ->
                while (c2.moveToNext()) {
                    val crossCls = c2.getString(0)
                    if (!crossCls.isNullOrBlank()) out.add(crossCls)
                }
            }
        } catch (e2: Exception) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ legacy v1 schema also failed: ${e2.message}")
        }
    }
    if (out.isNotEmpty()) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🤧 cross-reactivity " +
            "$cls → cross_classes=$out")
    }
    out
}

/**
 * Helper : récupère le display name d'un drug par son ATC.
 * 🆕 v4.1 : version simplifiée — utilise resolveDrug puis primaryDisplay.
 */
suspend fun KnowledgeBaseService.getDrugDisplayForAtc(atc: String, lang: String = "en"): String {
    if (atc.isBlank() || atc == "UNKNOWN") return atc
    return try {
        val resolved = resolveDrug(atc)
        val concept = resolved.concept
        concept?.primaryDisplay ?: atc
    } catch (e: Exception) {
        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ getDrugDisplayForAtc($atc) threw: ${e.message}")
        atc
    }
}
