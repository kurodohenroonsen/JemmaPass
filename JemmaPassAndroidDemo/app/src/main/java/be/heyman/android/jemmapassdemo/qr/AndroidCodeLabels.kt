/*
 * AndroidCodeLabels.kt — JEMMA Pass (PROTOCOL §9.1)
 *
 * Implements CodeLabelResolver using Android string resources.
 * Looks up code_label_<prefix>_<code> in the requested locale's resources,
 * falling back to default resources.
 */
package be.heyman.android.jemmapassdemo.qr

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

class AndroidCodeLabels(private val context: Context) : CodeLabelResolver {

    override fun getLabel(system: String, code: String, lang: String): String? {
        val resName = codeLabelResourceName(system, code) ?: return null
        val targetLocale = Locale.forLanguageTag(lang.lowercase().take(2))
        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)
        val localizedContext = context.createConfigurationContext(config)
        val resId = localizedContext.resources.getIdentifier(resName, "string", localizedContext.packageName)
        if (resId == 0) return null
        return try {
            localizedContext.getString(resId)
        } catch (_: Exception) {
            null
        }
    }
}
