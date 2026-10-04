package be.heyman.android.jemmapassdemo.pillars

data class RescueAllergyLine(val text: String, val severe: Boolean, val spoken: String)

object RescueAllergyFormat {
    /** Ligne de la fiche secouriste pour une allergie reçue (`display`/`name`/`c`, criticité `s`/`criticality`, réaction `d`/`manifestations`). */
    fun line(entry: org.json.JSONObject, labels: be.heyman.android.jemmapassdemo.qr.CodeLabelResolver, lang: String): RescueAllergyLine = TODO()
}
