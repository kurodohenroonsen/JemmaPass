/*
 * JemmaModelCatalog.kt — single source of truth for the 3 downloadable
 * artifacts hosted at https://jemmapass.net/models/ :
 *
 *   gemma-4-E2B-it.litertlm    →  ~2.4 GB · text + vision + audio  (light)
 *   gemma-4-E4B-it.litertlm    →  ~3.4 GB · text + vision + audio  (premium)
 *   knowledge_full.db          →  ~2.0 GB · ATC + RxNorm + SNOMED + DDinter + FTS5
 *
 * We REUSE Google's `Model` data class from Edge Gallery (no fork). The
 * `DefaultDownloadRepository` from Google takes any `Model` and downloads
 * it via its `DownloadWorker` (WorkManager + foreground service + resume).
 *
 * The 3 entries below are the public contract :
 *   - URL is fixed (jemmapass.net)
 *   - filename, sizeInBytes, version are stable for cache lookups
 *   - normalizedName is what determines the on-disk folder
 *
 * Storage layout on device (managed by Google's DownloadWorker) :
 *   {externalFilesDir}/{normalizedName}/{version}/{downloadFileName}
 *
 * For us :
 *   externalFilesDir/gemma-4-E2B-it/1.0/gemma-4-E2B-it.litertlm
 *   externalFilesDir/gemma-4-E4B-it/1.0/gemma-4-E4B-it.litertlm
 *   externalFilesDir/knowledge-full-db/1.0/knowledge_full.db
 */
package be.heyman.android.jemmapassdemo.downloads

import com.google.ai.edge.gallery.data.Model

object JemmaModelCatalog {

    /** Public base URL where all JEMMA artifacts live. */
    const val BASE_URL = "https://jemmapass.net/models"

    /**
     * Stable identifiers — used as Map keys, preferences keys, etc.
     * MUST match the `name` field of the corresponding [Model] (Google
     * uses `name` as the unique key in their DownloadWorker).
     */
    object Id {
        const val GEMMA_E2B = "gemma-4-E2B-it"
        const val GEMMA_E4B = "gemma-4-E4B-it"
        const val KB = "knowledge-full-db"
    }

    // ────────────────────────────────────────────────────────────────────
    // Gemma 4 E2B — light multimodal (~2.4 GB)
    // ────────────────────────────────────────────────────────────────────

    val gemmaE2B: Model = Model(
        name = Id.GEMMA_E2B,
        displayName = "Gemma 4 E2B",
        info = "Light multimodal model. ~2.4 GB. Recommended for phones with 6 GB RAM.",
        version = "1.0",
        url = "$BASE_URL/gemma-4-E2B-it.litertlm",
        downloadFileName = "gemma-4-E2B-it.litertlm",
        sizeInBytes = 2_588_147_712L,    // 2.41 GB (real upload size)
                                          // server HEAD is performed by worker)
        // Google's DownloadWorker writes to externalFilesDir/{name}/{version}/{filename}
    ).apply { preProcess() }

    // ────────────────────────────────────────────────────────────────────
    // Gemma 4 E4B — premium multimodal (~3.4 GB)
    // ────────────────────────────────────────────────────────────────────

    val gemmaE4B: Model = Model(
        name = Id.GEMMA_E4B,
        displayName = "Gemma 4 E4B",
        info = "Premium multimodal model. ~3.4 GB. Recommended for phones with ≥ 8 GB RAM.",
        version = "1.0",
        url = "$BASE_URL/gemma-4-E4B-it.litertlm",
        downloadFileName = "gemma-4-E4B-it.litertlm",
        sizeInBytes = 3_659_530_240L,    // 3.41 GB (real upload size)
    ).apply { preProcess() }

    // ────────────────────────────────────────────────────────────────────
    // Knowledge Base — clinical SQLite + FTS5 (~2.0 GB)
    // ────────────────────────────────────────────────────────────────────

    val knowledgeBase: Model = Model(
        name = Id.KB,
        displayName = "knowledge_full.db",
        info = "Clinical KB v2 DIAMOND — 95K drugs FTS5 multilingual (13 latin + ja) " +
            "+ 5,616 ATC family stats + WHO EML/AWaRe " +
            "+ 300K drug-drug interactions DDInter 2.0.",
        version = "1.1",
        url = "$BASE_URL/knowledge_full.db",
        downloadFileName = "knowledge_full.db",
        sizeInBytes = 3_360_727_040L,    // 3.13 GB — updated with SNOMED IPS validated propagation
    ).apply { preProcess() }

    // ────────────────────────────────────────────────────────────────────
    // Lookup helpers
    // ────────────────────────────────────────────────────────────────────

    /** All 3 entries in canonical order (E2B, E4B, KB). */
    fun all(): List<Model> = listOf(gemmaE2B, gemmaE4B, knowledgeBase)

    /** Just the 2 Gemma models (used in benchmark + model picker). */
    fun gemmaModels(): List<Model> = listOf(gemmaE2B, gemmaE4B)

    /**
     * Lookup by stable id. Returns null if the id is unknown ; this should
     * never happen unless someone passes a hand-typed string.
     */
    fun byId(id: String): Model? = when (id) {
        Id.GEMMA_E2B -> gemmaE2B
        Id.GEMMA_E4B -> gemmaE4B
        Id.KB -> knowledgeBase
        else -> null
    }

    /** Total bytes for the "Préparer la démo (6.1 GB)" 1-tap setup. */
    fun preparePackageSizeBytes(): Long =
        gemmaE4B.sizeInBytes + knowledgeBase.sizeInBytes
}
