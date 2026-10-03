package be.heyman.android.jemmapassdemo.downloads

data class PatchDescriptor(
    val fromVersion: String,
    val toVersion: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

object PatchChainResolver {
    fun resolveChain(
        currentVersion: String,
        targetVersion: String,
        availablePatches: List<PatchDescriptor>,
    ): List<PatchDescriptor>? = TODO()
}
