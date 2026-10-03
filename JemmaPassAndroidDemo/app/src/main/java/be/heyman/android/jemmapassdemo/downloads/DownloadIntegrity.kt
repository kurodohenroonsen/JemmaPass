package be.heyman.android.jemmapassdemo.downloads

enum class DownloadVerdict {
    COMPLETE,
    TRUNCATED,
    CORRUPT,
    UNVERIFIED,
}

object DownloadIntegrity {
    fun downloadVerdict(
        onDiskBytes: Long,
        expectedBytes: Long,
        onDiskSha256: String?,
        expectedSha256: String?,
    ): DownloadVerdict = TODO()
}
