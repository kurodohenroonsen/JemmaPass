/*
 * ProfileFiles.kt — file-level safety of the profile store (pure java.io, JVM-tested).
 *
 * Use-case review (qa/usecases/03, UC-STO): a health record must never be half written,
 * and an imported profile id must never choose where it is written.
 */
package be.heyman.android.jemmapassdemo.profiles

import java.io.File
import java.io.IOException

object ProfileFiles {

    private val SAFE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")

    /**
     * A profile id is used as a file name. Ids coming from a scanned QR / mesh payload are
     * untrusted: anything that is not a plain token (path separators, "..", leading dot,
     * blanks, > 64 chars) is rejected and the caller generates a fresh id.
     */
    fun safeIdOrNull(raw: String?): String? {
        val id = raw?.trim() ?: return null
        if (!SAFE_ID.matches(id) || id.contains("..")) return null
        if (id.equals("meta", ignoreCase = true) || id.endsWith(".fhir", ignoreCase = true)) return null
        return id
    }

    /**
     * All-or-nothing write: the text goes to a sibling temp file which then replaces [target]
     * with a rename. A crash or a full disk leaves the previous version intact.
     */
    @Throws(IOException::class)
    fun writeAtomic(target: File, text: String) {
        val dir = target.parentFile ?: throw IOException("no parent directory for $target")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("cannot create $dir")
        val tmp = File(dir, target.name + ".tmp")
        try {
            tmp.writeText(text)
            if (!tmp.renameTo(target)) {
                // Some file systems refuse to rename over an existing file.
                if (target.exists() && !target.delete()) throw IOException("cannot replace $target")
                if (!tmp.renameTo(target)) throw IOException("cannot rename $tmp to $target")
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
