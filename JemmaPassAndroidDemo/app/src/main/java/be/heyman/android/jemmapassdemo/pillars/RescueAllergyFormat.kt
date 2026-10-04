package be.heyman.android.jemmapassdemo.pillars

import be.heyman.android.jemmapassdemo.qr.CodeLabelResolver
import org.json.JSONObject

data class RescueAllergyLine(val text: String, val severe: Boolean, val spoken: String)

object RescueAllergyFormat {
    private const val WARNING_SIGN = "⚠"

    /**
     * Ligne de la fiche secouriste pour une allergie reçue (`display`/`name`/`c`, criticité `s`/`criticality`, réaction `d`/`manifestations`).
     */
    fun line(entry: JSONObject, labels: CodeLabelResolver, lang: String): RescueAllergyLine {
        val rawCrit = entry.optString("criticality")
            .ifBlank { entry.optString("s") }
            .trim()
        val severe = rawCrit.equals("high", ignoreCase = true) || rawCrit.equals("h", ignoreCase = true)

        val name = (entry.optString("display")
            .takeUnless { it.equals("null", ignoreCase = true) }
            ?.ifBlank { null }
            ?: entry.optString("name")
                .takeUnless { it.equals("null", ignoreCase = true) }
                ?.ifBlank { null }
            ?: entry.optString("c")
                .takeUnless { it.equals("null", ignoreCase = true) }
                ?.ifBlank { null }
            ?: "").trim()

        val reaction = (entry.optString("manifestations")
            .takeUnless { it.equals("null", ignoreCase = true) }
            ?.ifBlank { null }
            ?: entry.optString("d")
                .takeUnless { it.equals("null", ignoreCase = true) }
                ?.ifBlank { null }
            ?: "").trim()

        val severityWord = if (severe) {
            labels.getLabel("criticality", "high", lang)?.trim().orEmpty()
        } else {
            ""
        }

        val text = buildString {
            if (severe) {
                append(WARNING_SIGN)
                if (severityWord.isNotBlank()) {
                    append(" ")
                    append(severityWord)
                    append(" :")
                }
                append(" ")
            }
            append(name)
            if (reaction.isNotBlank()) {
                append(" : ")
                append(reaction)
            }
        }.trim()

        val spoken = buildString {
            if (severe && severityWord.isNotBlank()) {
                append(severityWord)
                append(" : ")
            }
            append(name)
            if (reaction.isNotBlank()) {
                append(" : ")
                append(reaction)
            }
        }.trim()

        return RescueAllergyLine(text = text, severe = severe, spoken = spoken)
    }
}
