/*
 * KnowledgeBaseServiceDrugDetail.kt — JEMMA Pass · Lot 14.5c19
 *
 * Extension to KnowledgeBaseService to fetch detailed drug information
 * from the knowledge base (ddinter_drugs, atc_hierarchy, who_eml, who_aware, dosages).
 */
package be.heyman.android.jemmapassdemo.kb

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG_DRUG = "JEMMA-KB-DRUG"

/**
 * 🆕 Lot 14.5c19 — Fetch detailed medication info from multiple KB tables.
 */
suspend fun KnowledgeBaseService.getDetailedDrugInfo(
    kbManager: KnowledgeBaseManager,
    atcCode: String
): DetailedDrugInfo? = withContext(Dispatchers.IO) {
    if (atcCode.isBlank()) return@withContext null
    val db = kbManager.database() ?: run {
        Log.w(TAG_DRUG, "KB DB not ready for drug detail lookup")
        return@withContext null
    }

    var info: DetailedDrugInfo? = null
    val cleanAtc = atcCode.trim().uppercase()

    // 1. Fetch from ddinter_drugs (primary source for description/chem)
    // We search by primary_atc or within atc_codes CSV.
    val sqlDdinter = """
        SELECT name, primary_atc, description, formula, weight, smiles
        FROM ddinter_drugs
        WHERE primary_atc = ? COLLATE NOCASE
           OR atc_codes LIKE ? COLLATE NOCASE
           OR atc_codes LIKE ? COLLATE NOCASE
           OR atc_codes LIKE ? COLLATE NOCASE
           OR atc_codes = ? COLLATE NOCASE
        LIMIT 1
    """.trimIndent()

    try {
        db.rawQuery(sqlDdinter, arrayOf(
            cleanAtc,
            "$cleanAtc,%",
            "%,$cleanAtc,%",
            "%,$cleanAtc",
            cleanAtc
        )).use { cursor ->
            if (cursor.moveToFirst()) {
                info = DetailedDrugInfo(
                    atcCode = cleanAtc,
                    canonicalName = cursor.getString(0) ?: "",
                    description = cursor.getString(2),
                    chemicalFormula = cursor.getString(3),
                    molecularWeight = if (cursor.isNull(4)) null else cursor.getDouble(4),
                    smiles = cursor.getString(5)
                )
            }
        }
    } catch (e: Exception) {
        Log.e(TAG_DRUG, "Error fetching ddinter_drugs for $cleanAtc", e)
    }

    // 2. Fallback to atc_hierarchy if not in ddinter_drugs
    if (info == null) {
        val sqlHierarchy = """
            SELECT name_en, description
            FROM atc_hierarchy
            WHERE atc_code = ? COLLATE NOCASE
            LIMIT 1
        """.trimIndent()
        try {
            db.rawQuery(sqlHierarchy, arrayOf(cleanAtc)).use { cursor ->
                if (cursor.moveToFirst()) {
                    info = DetailedDrugInfo(
                        atcCode = cleanAtc,
                        canonicalName = cursor.getString(0) ?: cleanAtc,
                        description = cursor.getString(1)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_DRUG, "Error fetching atc_hierarchy for $cleanAtc", e)
        }
    }

    if (info == null) {
        Log.d(TAG_DRUG, "No detailed info found for $cleanAtc in ddinter or hierarchy")
        return@withContext null
    }

    // 3. Enrich with EML / AWaRe status
    val sqlEml = "SELECT eml_section FROM who_eml_2023 WHERE atc_code = ? LIMIT 1"
    try {
        db.rawQuery(sqlEml, arrayOf(cleanAtc)).use { cursor ->
            if (cursor.moveToFirst()) {
                info = info?.copy(whoEmlStatus = cursor.getString(0))
            }
        }
    } catch (e: Exception) {}

    val sqlAware = "SELECT category FROM who_aware_2024 WHERE atc_code = ? LIMIT 1"
    try {
        db.rawQuery(sqlAware, arrayOf(cleanAtc)).use { cursor ->
            if (cursor.moveToFirst()) {
                info = info?.copy(whoAwareCategory = cursor.getString(0))
            }
        }
    } catch (e: Exception) {}

    // 4. Enrich with dosages
    val sqlDose = "SELECT route, dose_ddd, dose_unit, population, note FROM dosages WHERE drug_atc = ?"
    try {
        db.rawQuery(sqlDose, arrayOf(cleanAtc)).use { cursor ->
            val doses = mutableListOf<DrugDoseInfo>()
            while (cursor.moveToNext()) {
                doses.add(DrugDoseInfo(
                    route = cursor.getString(0),
                    ddd = if (cursor.isNull(1)) null else cursor.getDouble(1),
                    unit = cursor.getString(2),
                    population = cursor.getString(3),
                    note = cursor.getString(4)
                ))
            }
            info = info?.copy(dosages = doses)
        }
    } catch (e: Exception) {}

    // 5. Enrich with food interactions
    val sqlFood = "SELECT food_name_en, severity, description_en, management_en FROM interactions_food WHERE drug_atc = ?"
    try {
        db.rawQuery(sqlFood, arrayOf(cleanAtc)).use { cursor ->
            val foods = mutableListOf<InteractionInfo>()
            while (cursor.moveToNext()) {
                foods.add(InteractionInfo(
                    entityName = cursor.getString(0) ?: "",
                    severity = cursor.getString(1),
                    description = cursor.getString(2),
                    management = cursor.getString(3)
                ))
            }
            info = info?.copy(foodInteractions = foods)
        }
    } catch (e: Exception) {}

    // 6. Enrich with disease interactions (general ones for this drug)
    val sqlDisease = "SELECT disease_name_en, severity, description_en, management_en FROM drug_disease_interactions WHERE drug_atc = ?"
    try {
        db.rawQuery(sqlDisease, arrayOf(cleanAtc)).use { cursor ->
            val diseases = mutableListOf<InteractionInfo>()
            while (cursor.moveToNext()) {
                diseases.add(InteractionInfo(
                    entityName = cursor.getString(0) ?: "",
                    severity = cursor.getString(1),
                    description = cursor.getString(2),
                    management = cursor.getString(3)
                ))
            }
            info = info?.copy(diseaseInteractions = diseases)
        }
    } catch (e: Exception) {}

    Log.i(TAG_DRUG, "Successfully hydrated drug details for $cleanAtc (${info?.canonicalName})")
    info
}
