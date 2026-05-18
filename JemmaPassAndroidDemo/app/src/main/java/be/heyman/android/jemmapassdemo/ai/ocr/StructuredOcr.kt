/*
 * StructuredOcr.kt — Lot 14.5b (PHASE 14)
 *
 * Modèle de données pour conserver la STRUCTURE spatiale du résultat
 * OCR ML Kit. La version Lot 14.5a applatissait tout en un seul String
 * `\n`-séparé, ce qui détruit l'info de layout sur des documents
 * tabulaires comme la fiche Careplus du papa de Kudoro :
 *
 *     BURINEX COMP 20 X5 MG (-89 )       ← bloc nom de médoc
 *       Hebdomadaire 12/02/2026...        ← bloc fréquence/dates (sub-line)
 *                            1            ← cell tableau (SOIR colonne)
 *
 * Sans la structure, Gemma ne peut pas associer "BURINEX" à
 * "Hebdomadaire". Avec la structure (blocks + lines + bbox), on peut
 * sérialiser par bloc visuel et Gemma reconstruit naturellement les
 * 12 médicaments séparés.
 *
 * Structure mirror ML Kit `Text` API :
 *   Text
 *    └─ textBlocks: List<TextBlock>   ← gros pavés (souvent 1 médoc + sa freq)
 *         └─ lines: List<Line>        ← lignes physiques dans le bloc
 *              └─ elements: List<Element>  ← mots/tokens (pas utilisés ici)
 *
 * Pour le pipeline JEMMA, on garde Block + Line (Element est inutile
 * et explose le payload Gemma).
 *
 * Sérialisation pour Gemma au lot 14.5c (à brancher) :
 *
 *     === PAGE 1 ===
 *     [bloc 1]
 *     BURINEX COMP 20 X5 MG (-89 )
 *     Hebdomadaire 12/02/2026-.J.J....
 *     [bloc 2]
 *     CARBONATE CALCIUM 1GR (-279 )
 *     Quotidienne 14/01/2026-.J.J..
 *     ...
 *
 * Cette présentation prepare Gemma à traiter chaque bloc comme une
 * "BOX" L27 — exactement le pattern qui fonctionnait dans le HTML.
 */
package be.heyman.android.jemmapassdemo.ai.ocr

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

/** Une ligne physique de texte avec sa bounding box dans l'image source. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerY: Int get() = (top + bottom) / 2
}

/** Un bloc OCR (pavé visuel souvent ≈ un médicament + sa freq sur Careplus). */
data class OcrBlock(
    val text: String,
    val lines: List<OcrLine>,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerY: Int get() = (top + bottom) / 2
}

/**
 * Résultat OCR pour UNE page, structuré + flatten en parallèle.
 *
 * @property pageIndex 0-based, ordre de scan
 * @property sourceUri URI de l'image source (content:// du Document Scanner)
 * @property blocks Blocs ordonnés top-to-bottom puis left-to-right
 * @property flatText Le `result.text` brut ML Kit (compat downstream)
 * @property durationMs Temps OCR de cette page seulement
 */
data class StructuredOcrPage(
    val pageIndex: Int,
    val sourceUri: String,
    val blocks: List<OcrBlock>,
    val flatText: String,
    val durationMs: Long,
) {
    /** Nombre total de lignes physiques détectées sur la page. */
    val lineCount: Int get() = blocks.sumOf { it.lines.size }
    /** Nombre total de caractères (compteur visible UI). */
    val charCount: Int get() = flatText.length
}

/** Agrégat de N pages (Document Scanner peut produire 1 à 20 pages). */
data class StructuredOcrResult(
    val pages: List<StructuredOcrPage>,
    val totalDurationMs: Long,
) {
    val pageCount: Int get() = pages.size
    val totalBlocks: Int get() = pages.sumOf { it.blocks.size }
    val totalChars: Int get() = pages.sumOf { it.charCount }
    val isEmpty: Boolean get() = totalChars == 0

    /**
     * Sérialise pour Gemma au lot 14.5c. Format : un section header
     * par page, puis chaque bloc indenté avec ses lignes. Ce format est
     * passé tel quel comme "OCR text" dans le prompt L27.
     *
     * @param maxCharsPerBlock cap pour éviter d'exploser le contexte
     *        Gemma sur les pages denses (200 chars suffisent à
     *        identifier un médoc + sa freq).
     */
    fun serializeForLlm(maxCharsPerBlock: Int = 300): String = buildString {
        for (page in pages) {
            append("=== PAGE ${page.pageIndex + 1} ===\n")
            for ((bIdx, block) in page.blocks.withIndex()) {
                append("[bloc ${bIdx + 1}]\n")
                val txt = block.text.take(maxCharsPerBlock)
                append(txt)
                if (block.text.length > maxCharsPerBlock) append("…")
                append("\n")
            }
            append("\n")
        }
    }.trimEnd()
}

/**
 * Convertit un ML Kit `Text` en `StructuredOcrPage` en préservant
 * les bounding boxes (utile au lot 14.5c pour des tris spatiaux et
 * pour skiper les colonnes "MATIN/SOIR/COUCHER" qui sont des cells
 * isolées, pas des blocs informationnels).
 */
fun Text.toStructuredPage(
    pageIndex: Int,
    sourceUri: String,
    durationMs: Long,
): StructuredOcrPage {
    val blocks = textBlocks.map { block ->
        val bbox = block.boundingBox ?: Rect()
        val lines = block.lines.map { line ->
            val lbb = line.boundingBox ?: Rect()
            OcrLine(
                text = line.text,
                left = lbb.left,
                top = lbb.top,
                right = lbb.right,
                bottom = lbb.bottom,
            )
        }
        OcrBlock(
            text = block.text,
            lines = lines,
            left = bbox.left,
            top = bbox.top,
            right = bbox.right,
            bottom = bbox.bottom,
        )
    }
    return StructuredOcrPage(
        pageIndex = pageIndex,
        sourceUri = sourceUri,
        blocks = blocks,
        flatText = text,
        durationMs = durationMs,
    )
}
