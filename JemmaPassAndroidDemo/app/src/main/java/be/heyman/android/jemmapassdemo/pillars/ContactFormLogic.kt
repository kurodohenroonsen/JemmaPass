package be.heyman.android.jemmapassdemo.pillars

import be.heyman.android.jemmapassdemo.qr.CodeLabelResolver

object ContactFormLogic {
    /** Relation proposée d'après le nom saisi, ou null. Ne rend jamais un code absent du catalogue. */
    fun suggestedRelation(name: String?): String? = TODO()

    /** Texte montré à l'écran pour une relation enregistrée ; null quand c'est un code sans libellé. */
    fun relationDisplay(raw: String?, labels: CodeLabelResolver, lang: String): String? = TODO()
}
