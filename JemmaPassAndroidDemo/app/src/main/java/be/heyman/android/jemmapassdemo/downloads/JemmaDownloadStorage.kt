/*
 * JemmaDownloadStorage.kt — read-only helpers for JEMMA download state.
 *
 * Wraps the on-disk layout written by Google's `DownloadWorker` :
 *   {externalFilesDir}/{model.normalizedName}/{model.version}/{model.downloadFileName}
 *
 * `normalizedName` = `Regex("[^a-zA-Z0-9]").replace(name, "_")` (cf. Model.kt).
 * For JEMMA's "knowledge-full-db", this produces "knowledge_full_db".
 *
 * Provides :
 *   • modelFile(model)         absolute File for the final artifact
 *   • tmpFile(model)           absolute File for the in-progress .tmp
 *   • isFullyDownloaded(model) true iff the final file exists with expected size
 *   • partialBytes(model)      tmp file size if partial, 0 if none
 *   • freeStorageBytes()       free bytes on the partition holding our files
 *   • totalDownloadedBytes()   sum of fully downloaded artifacts' on-disk size
 *
 * Consumed by :
 *   • Settings preflight checklist
 *   • Model picker UI ("DOWNLOADED 3.4 GB" pill vs "NOT DOWNLOADED")
 *   • Coordinator decision : skip already-downloaded items in the chain
 */
package be.heyman.android.jemmapassdemo.downloads

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.TMP_FILE_EXT
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JemmaDownloadStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "JEMMA-DB"
    }

    /** Root dir where Google's DownloadWorker writes : externalFilesDir. */
    private val rootDir: File?
        get() = context.getExternalFilesDir(null)

    /**
     * Absolute path to the final artifact for [model], whether or not it
     * exists yet. Same layout as Google's `DownloadWorker` :
     *   {externalFilesDir}/{normalizedName}/{version}/{downloadFileName}
     *
     * v2.2.14 — uses `model.normalizedName` (NOT `model.name`) because
     * Google's `DownloadWorker` normalizes via Regex("[^a-zA-Z0-9]")→"_".
     * For "knowledge-full-db", normalizedName = "knowledge_full_db" (the
     * dash becomes underscore). Using `model.name` here caused a path
     * mismatch : worker wrote to .../knowledge_full_db/, but JEMMA looked
     * in .../knowledge-full-db/ → "file not present" + "non téléchargé"
     * at every cold start.
     */
    fun modelFile(model: Model): File {
        val root = rootDir ?: throw IllegalStateException("externalFilesDir is null")
        return File(root, "${model.normalizedName}/${model.version}/${model.downloadFileName}")
    }

    /** Absolute path to the in-progress tmp file (Google's pattern). */
    fun tmpFile(model: Model): File {
        val root = rootDir ?: throw IllegalStateException("externalFilesDir is null")
        return File(root, "${model.normalizedName}/${model.version}/${model.downloadFileName}.$TMP_FILE_EXT")
    }

    /**
     * True iff the final file exists with size ≥ expectedSize. We tolerate
     * a slightly larger file on disk (server may have served a more
     * accurate size) but never accept a smaller file as valid.
     */
    fun isFullyDownloaded(model: Model): Boolean {
        val f = modelFile(model)
        if (!f.exists()) return false
        val onDisk = f.length()
        val expected = model.sizeInBytes
        // Tolerate ±5% (safer than exact match — sizes in catalog are
        // best-effort, server may report slightly different).
        val ok = onDisk >= (expected * 0.95).toLong()
        Log.d(
            TAG,
            "[t=${System.currentTimeMillis()}] 🔍 isFullyDownloaded ${model.name} = $ok " +
                "(onDisk=$onDisk · expected=$expected)"
        )
        return ok
    }

    /**
     * Bytes already written to the tmp file (partial download). Returns 0
     * if no tmp file or if it has been removed by a previous successful
     * completion.
     */
    fun partialBytes(model: Model): Long {
        val f = tmpFile(model)
        return if (f.exists()) f.length() else 0L
    }

    /**
     * Free bytes on the partition holding [rootDir]. Used by Settings
     * preflight to check we have enough room for the upcoming download.
     */
    fun freeStorageBytes(): Long {
        val root = rootDir ?: return 0L
        return try {
            val stat = StatFs(root.absolutePath)
            stat.availableBytes
        } catch (e: Exception) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ freeStorageBytes failed : ${e.message}"
            )
            0L
        }
    }

    /**
     * Sum of bytes used by fully downloaded artifacts (final files only,
     * tmp not included). Useful for the "Wipe all data" Settings entry.
     */
    fun totalDownloadedBytes(): Long {
        return JemmaModelCatalog.all().sumOf { model ->
            if (isFullyDownloaded(model)) modelFile(model).length() else 0L
        }
    }

    /**
     * Delete the final + tmp files for [model]. Used by the per-card
     * "Delete" button in Settings. No-op if files don't exist.
     */
    fun deleteModel(model: Model): Boolean {
        var deleted = 0
        modelFile(model).takeIf { it.exists() }?.let {
            if (it.delete()) deleted++
        }
        tmpFile(model).takeIf { it.exists() }?.let {
            if (it.delete()) deleted++
        }
        // Also clean up the empty version dir.
        modelFile(model).parentFile?.takeIf {
            it.exists() && it.listFiles().isNullOrEmpty()
        }?.delete()
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🛑 deleteModel ${model.name} · removed=$deleted file(s)"
        )
        return deleted > 0
    }
}
