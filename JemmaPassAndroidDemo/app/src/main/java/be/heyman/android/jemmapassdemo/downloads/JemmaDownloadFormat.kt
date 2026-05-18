/*
 * JemmaDownloadFormat.kt — locale-aware formatters for download UI strings.
 *
 * Designed to be called from any Fragment / ViewModel that displays
 * `JemmaDownloadAggregateState` values. Delegates to Android's built-in
 * `Formatter.formatFileSize` for byte sizes — Android handles locale
 * conventions (decimal separators, GB/Go/GB) automatically based on
 * the device's current locale.
 *
 * Why a separate helper :
 *   - Single source of truth for how we format download numbers
 *   - Easy to swap to a different formatter later (e.g. fixed-width
 *     monospace for the status pill on Settings)
 *   - Lets Settings, Profiles preflight banner, and any future screen
 *     show identical strings for the same numbers
 */
package be.heyman.android.jemmapassdemo.downloads

import android.content.Context
import android.text.format.Formatter
import be.heyman.android.jemmapassdemo.R
import kotlin.math.roundToLong

object JemmaDownloadFormat {

    /**
     * Locale-aware byte size like "3.4 GB" (en) / "3,4 Go" (fr) / "3.4 GB" (ja).
     * Wrapper around Android's [Formatter.formatFileSize].
     */
    fun bytes(context: Context, bytes: Long): String =
        Formatter.formatFileSize(context, bytes)

    /**
     * Locale-aware transfer rate like "18 MB/s" (en) / "18 Mo/s" (fr).
     * Uses the i18n template `dl_rate_template`. The number itself is
     * formatted via [bytes] for consistency.
     */
    fun rate(context: Context, bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0L) return ""
        val perSec = bytes(context, bytesPerSecond)
        return context.getString(R.string.dl_rate_template, perSec)
    }

    /**
     * Locale-aware ETA like "~3 min", "~45 s", "~2 h 15 min".
     * Picks the unit based on remaining duration to keep the string short.
     */
    fun eta(context: Context, remainingMs: Long): String {
        if (remainingMs <= 0L) return ""
        val totalSec = (remainingMs / 1000.0).roundToLong()
        return when {
            totalSec < 60L ->
                context.getString(R.string.dl_eta_sec_template, totalSec.toInt())
            totalSec < 3600L ->
                context.getString(R.string.dl_eta_min_template, (totalSec / 60).toInt())
            else -> {
                val h = (totalSec / 3600).toInt()
                val m = ((totalSec % 3600) / 60).toInt()
                context.getString(R.string.dl_eta_hour_template, h, m)
            }
        }
    }

    /** Step indicator like "Step 2 / 3" or "Étape 2 / 3" or "ステップ 2 / 3". */
    fun step(context: Context, currentOneBased: Int, total: Int): String =
        context.getString(R.string.dl_step_template, currentOneBased, total)

    /** Bytes ratio like "1.2 GB / 2.7 GB" / "1,2 Go / 2,7 Go" / "1.2 GB / 2.7 GB". */
    fun bytesRatio(context: Context, received: Long, total: Long): String {
        val r = bytes(context, received)
        val t = bytes(context, total)
        return context.getString(R.string.dl_bytes_template, r, t)
    }

    /**
     * Compose the full status line shown under the prepare-demo progress bar.
     * Example output (FR) :
     *     "Étape 2 / 2 · 1,2 Go / 2,0 Go · 18 Mo/s · ~3 min"
     *
     * Segments are skipped if their value is 0 or unavailable (early ticks
     * before bytes_per_second is computed).
     */
    fun aggregateStatusLine(context: Context, state: JemmaDownloadAggregateState): String {
        val parts = mutableListOf<String>()

        if (state.totalSteps > 1) {
            parts += step(context, state.stepIndex + 1, state.totalSteps)
        }
        if (state.totalBytes > 0L) {
            parts += bytesRatio(context, state.receivedBytes, state.totalBytes)
        }
        rate(context, state.bytesPerSecond).takeIf { it.isNotEmpty() }?.let { parts += it }
        eta(context, state.remainingMs).takeIf { it.isNotEmpty() }?.let { parts += it }

        return parts.joinToString(" · ")
    }
}
