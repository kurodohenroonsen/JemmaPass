package be.heyman.android.jemmapassdemo.triage

/**
 * 🆕 L44.16.76 — SALT mass-casualty triage codes.
 *
 * ── Council R1 verdict (8/8 IAs) ────────────────────────────────
 * SALT (Sort, Assess, Lifesaving interventions, Treatment/transport)
 * is the modern doctrine for mass-casualty triage. Council unanimously
 * picked SALT over START because :
 *   • SALT explicitly captures HELP-needed and STABILIZED states
 *   • Maps cleanly to 4-char ASCII codes for the wire format
 *   • Maps cleanly to emojis for the UI badges
 *
 * START's 4-color scheme (Immediate/Delayed/Minor/Deceased) is more
 * universal but lacks the "needs help" and "stabilized" granularity
 * that makes our coordination scenario compelling.
 *
 * ── UI rendering compromise ─────────────────────────────────────
 * Internally store SALT, but render with START-inspired colors so
 * civilian responders aren't disoriented :
 *
 *   WAIT  ⏳  grey      (assessing, low priority)
 *   EVAL  🔍  yellow    (under evaluation)
 *   STAB  ✅  green     (stabilized, no immediate need)
 *   HELP  🆘  red       (needs help right now)
 *   EVAC  🚑  blue      (ready for evacuation)
 *   DCD   🕊️ black     (deceased)
 *
 * ── Wire encoding ───────────────────────────────────────────────
 * 4-char ASCII codes : `WAIT`, `EVAL`, `STAB`, `HELP`, `EVAC`, `DCD `
 * (DCD is 3 chars — pad with space at wire level OR accept variable
 * length; we accept variable length to save 1 byte).
 */
enum class SaltCode(val code: String, val emoji: String, val colorHex: String) {
    /** Awaiting assessment (low priority). */
    WAIT("WAIT", "⏳", "#9E9E9E"),

    /** Under active evaluation (vital signs being measured). */
    EVAL("EVAL", "🔍", "#FFC107"),

    /** Stabilized — no immediate intervention needed. */
    STAB("STAB", "✅", "#4CAF50"),

    /** HELP needed — needs more rescuers, urgent. */
    HELP("HELP", "🆘", "#F44336"),

    /** Ready for evacuation (transport requested). */
    EVAC("EVAC", "🚑", "#2196F3"),

    /** Deceased / black tag. Terminal state with grace-window override. */
    DCD("DCD", "🕊️", "#000000");

    companion object {
        /**
         * Parse a 4-char wire code into a SaltCode. Tolerates trailing
         * spaces and case differences (always uppercased). Returns null
         * for unknown codes — the parser layer drops malformed events.
         */
        fun parse(code: String): SaltCode? {
            val cleaned = code.uppercase().trim()
            return values().firstOrNull { it.code == cleaned }
        }

        /** All recognised wire codes (used in EventChunk validation). */
        fun allCodes(): Set<String> = values().map { it.code }.toSet()
    }
}
