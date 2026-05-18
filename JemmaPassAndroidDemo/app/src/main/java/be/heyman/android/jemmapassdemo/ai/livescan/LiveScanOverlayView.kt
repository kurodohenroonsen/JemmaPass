/*
 * LiveScanOverlayView.kt — JEMMA Pass · Live Scan v0.5 v4 · FIX4
 *
 * 🆕 FIX4 (2026-05-12 evening) — Phase A enrichie :
 *   • Banner top : compteur "4 frames · 2 codes · trigger at 4"
 *   • Ligne OCR live : dernière ligne OCR brute (tronquée 60 chars)
 *   • Chips candidates ATC : verts, apparaissent au fur et à mesure
 *     que JEMMA découvre des médocs (effet "JEMMA réfléchit")
 *
 * Phase B/C/D ne sont plus rendues dans cet overlay (l'utilisateur
 * a navigué back vers PatientDetailFragment où un panneau dédié
 * affiche le verdict). Cet overlay ne sert plus que pour Phase A.
 *
 * Log channel : JEMMA-LIVESCAN-OVL
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import be.heyman.android.jemmapassdemo.R

private const val TAG = "JEMMA-LIVESCAN-OVL"

class LiveScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val hintBanner: TextView
    private val ocrLiveText: TextView
    private val candidateChips: LinearLayout

    // Verdict box reste là par compat mais visibility GONE en FIX4 (rendu côté PatientDetail)
    private val verdictBox: LinearLayout
    private val verdictHeader: TextView
    private val verdictBadges: LinearLayout
    private val streamText: TextView
    private val safeAltBox: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_livescan_overlay, this, true)
        hintBanner = findViewById(R.id.livescan_hint_banner)
        ocrLiveText = findViewById(R.id.livescan_ocr_live)
        candidateChips = findViewById(R.id.livescan_candidate_chips)
        verdictBox = findViewById(R.id.livescan_verdict_box)
        verdictHeader = findViewById(R.id.livescan_verdict_header)
        verdictBadges = findViewById(R.id.livescan_verdict_badges)
        streamText = findViewById(R.id.livescan_stream_text)
        safeAltBox = findViewById(R.id.livescan_safe_alt_box)

        // FIX4 : verdict box jamais affiché côté caméra
        verdictBox.visibility = View.GONE
    }

    // ─── Render API (Phase A only en FIX4) ────────────────────────────

    fun renderAccumulating(state: LiveScanState.Accumulating) {
        val n = state.nbrOcrWithCodes
        val m = state.distinctCodes.size

        // Ligne 1 : compteur banner
        hintBanner.text = if (n == 0) {
            context.getString(R.string.livescan_hint_searching)
        } else {
            context.getString(R.string.livescan_hint_accumulating, n, m, state.triggerThreshold)
        }
        hintBanner.setBackgroundColor(0xAA000000.toInt())

        // Ligne 2 : dernière ligne OCR brute (debug live)
        val lastOcr = state.lastTextSamples.lastOrNull()
            ?.replace("\n", " | ")
            ?.take(60)
            ?: ""
        if (lastOcr.isNotEmpty()) {
            ocrLiveText.visibility = View.VISIBLE
            ocrLiveText.text = "📷 \"$lastOcr\""
        } else {
            ocrLiveText.visibility = View.GONE
        }

        // Ligne 3 : chips candidates ATC découverts (live, max 5)
        candidateChips.removeAllViews()
        if (state.candidateDisplays.isNotEmpty()) {
            candidateChips.visibility = View.VISIBLE
            state.candidateDisplays.take(5).forEach { (atc, name) ->
                candidateChips.addView(buildCandidateChip(name, atc))
            }
        } else {
            candidateChips.visibility = View.GONE
        }
    }

    /**
     * Appelé brièvement quand Phase B trigger avant la nav back vers
     * PatientDetailFragment. Affiche "🧠 Identification en cours…"
     * pendant le quart de seconde de transition.
     */
    fun renderIdentifying() {
        ocrLiveText.visibility = View.GONE
        candidateChips.visibility = View.GONE
        hintBanner.text = context.getString(R.string.livescan_hint_identifying)
        hintBanner.setBackgroundColor(0xAA1E40AF.toInt())
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🧠 renderIdentifying (transition)")
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private fun buildCandidateChip(name: String, atc: String): TextView {
        return TextView(context).apply {
            text = "✓ $name"
            textSize = 13f
            setPadding(14, 6, 14, 6)
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC15803D.toInt())  // vert découverte
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.setMargins(8, 4, 0, 4)
            layoutParams = lp
        }
    }
}
