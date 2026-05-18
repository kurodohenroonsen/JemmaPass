/*
 * PreflightState.kt — UI state for the 5-row preflight checklist on Settings.
 *
 * Computed by [SettingsViewModel] by combining 4 sources :
 *   • PermissionsViewModel (already exists) → grantedCount, totalCount
 *   • JemmaDownloadCoordinator.statuses    → which models are downloaded
 *   • KnowledgeBaseManager.state            → KB ready or not
 *   • JemmaDownloadStorage.freeStorageBytes → free disk space
 *   • (future) ProfilesRepository.count     → number of profiles created
 *
 * Each row exposes :
 *   • Status (OK / KO / IN_PROGRESS / PENDING)
 *   • A short detail string (e.g. "5 / 5 granted", "E4B 3.4 GB · active")
 *
 * The Fragment uses these to drive the icon/color/detail of each row in
 * the preflight Card and the orange "demo not ready" banner on Profiles.
 */
package be.heyman.android.jemmapassdemo.ui.settings

enum class PreflightStatus {
    OK,             // ✅ green
    KO,             // ❌ red
    IN_PROGRESS,    // ⚙ blue (e.g. KB validating, download in progress)
    PENDING,        // ⏳ grey (not yet attempted)
}

/**
 * One row of the preflight checklist.
 *
 * @param status  visual indicator
 * @param detail  short human-readable text under the row label, already
 *                localized by the producer
 */
data class PreflightRow(
    val status: PreflightStatus,
    val detail: String,
)

/**
 * Top-level state of the preflight checklist. The 5 rows match the 5
 * rows of fragment_settings.xml :
 *   permissions / model / kb / profile / storage
 */
data class PreflightState(
    val permissions: PreflightRow,
    val model: PreflightRow,
    val kb: PreflightRow,
    val profile: PreflightRow,
    val storage: PreflightRow,
) {
    /** True iff every row is OK. Drives the demo-ready chip + banner. */
    val allReady: Boolean
        get() = listOf(permissions, model, kb, profile, storage)
            .all { it.status == PreflightStatus.OK }

    /** Count of KO rows (used in the orange banner subtitle). */
    val koCount: Int
        get() = listOf(permissions, model, kb, profile, storage)
            .count { it.status == PreflightStatus.KO }

    val totalRows: Int = 5

    companion object {
        /** Initial state shown before any data has been computed. */
        fun initial(): PreflightState {
            val pending = PreflightRow(PreflightStatus.PENDING, "")
            return PreflightState(
                permissions = pending,
                model = pending,
                kb = pending,
                profile = pending,
                storage = pending,
            )
        }
    }
}
