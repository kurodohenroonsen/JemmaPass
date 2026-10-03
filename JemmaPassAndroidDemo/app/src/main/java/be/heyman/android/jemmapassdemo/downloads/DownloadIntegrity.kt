package be.heyman.android.jemmapassdemo.downloads

enum class DownloadVerdict {
    COMPLETE,
    TRUNCATED,
    CORRUPT,
    UNVERIFIED,
}

object DownloadIntegrity {
    private val HEX_64_REGEX = Regex("^[0-9a-fA-F]{64}$")

    fun downloadVerdict(
        onDiskBytes: Long,
        expectedBytes: Long,
        onDiskSha256: String?,
        expectedSha256: String?,
    ): DownloadVerdict {
        if (expectedBytes <= 0L) {
            return DownloadVerdict.UNVERIFIED
        }
        if (onDiskBytes < expectedBytes) {
            return DownloadVerdict.TRUNCATED
        }
        if (onDiskBytes > expectedBytes) {
            return DownloadVerdict.CORRUPT
        }

        val cleanExpected = expectedSha256?.trim()
        val cleanOnDisk = onDiskSha256?.trim()

        if (cleanExpected.isNullOrEmpty() || cleanOnDisk.isNullOrEmpty()) {
            return DownloadVerdict.UNVERIFIED
        }

        if (!HEX_64_REGEX.matches(cleanExpected) || !HEX_64_REGEX.matches(cleanOnDisk)) {
            return DownloadVerdict.UNVERIFIED
        }

        return if (cleanOnDisk.equals(cleanExpected, ignoreCase = true)) {
            DownloadVerdict.COMPLETE
        } else {
            DownloadVerdict.CORRUPT
        }
    }
}
