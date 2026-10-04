package be.heyman.android.jemmapassdemo.pillars

import be.heyman.android.jemmapassdemo.qr.CodeLabelResolver

object ContactFormLogic {
    /** Relation proposée d'après le nom saisi, ou null. Ne rend jamais un code absent du catalogue. */
    fun suggestedRelation(name: String?): String? {
        if (name.isNullOrBlank()) return null
        // No medical/doctor title code exists in the HL7 v3-RoleCode / IPS relationship catalog.
        // Never invent an uncatalogued code (like MEDPROVR).
        return null
    }

    /** Texte montré à l'écran pour une relation enregistrée ; null quand c'est un code sans libellé. */
    fun relationDisplay(raw: String?, labels: CodeLabelResolver, lang: String): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val upper = trimmed.uppercase()
        val lang2 = lang.take(2).lowercase()

        // 1. If it's a known catalog code (case-insensitive, e.g. "DAUC" or "dauc"):
        if (IpsRelationshipCatalog.isValidCode(upper)) {
            val resolved = labels.getLabel(IpsRelationshipCatalog.CODE_SYSTEM, upper, lang2)
                ?: labels.getLabel(IpsRelationshipCatalog.CODE_SYSTEM, trimmed, lang2)
            if (!resolved.isNullOrBlank()) return resolved

            val entry = IpsRelationshipCatalog.ALL.firstOrNull { it.code == upper }
            if (entry != null) {
                return entry.pick(lang2)
            }
        }

        // 2. If it is a role code (HL7 v3 RoleCode pattern ^[A-Z0-9_]{2,}$):
        if (IpsRelationshipCatalog.isRoleCode(trimmed)) {
            val resolved = labels.getLabel(IpsRelationshipCatalog.CODE_SYSTEM, upper, lang2)
                ?: labels.getLabel(IpsRelationshipCatalog.CODE_SYSTEM, trimmed, lang2)
            if (!resolved.isNullOrBlank()) return resolved
            // Unknown code without label is never displayed raw on screen
            return null
        }

        // 3. Free text ("ami", "voisine du 3e étage", "友人", "幼なじみ", "Best friend", "Maman"):
        // Must stay word for word as typed
        return raw
    }
}
