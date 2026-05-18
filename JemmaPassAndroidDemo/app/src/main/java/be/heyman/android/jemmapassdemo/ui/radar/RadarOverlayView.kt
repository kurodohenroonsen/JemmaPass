/*
 * RadarOverlayView.kt — JEMMA Pass · JemmaAppDemo · L5 v2.5.6
 *
 * Custom Canvas view that renders both rescuer-mode AND victim-mode
 * radar visualisations using a single shared component. The host
 * fragment computes a `Snapshot` from `RadarController` JSON outputs,
 * then calls `setSnapshot(snapshot)`.
 *
 * ── v2.5.6 changes ────────────────────────────────────────────────
 * Each Dot can now carry a `saltCode` (the 4-char ASCII SALT code
 * resolved by `StatusResolver`). When present, the dot is rendered in
 * the SALT color (WAIT/EVAL/STAB/HELP/EVAC/DCD) — matching the colors
 * defined in `triage/SaltCode.kt`. When absent, falls back to the
 * `criticality 0..3` heuristic from previous deliveries.
 *
 * Rescuers (isRescuer=true) keep their distinct sky-blue dot + ⛑ label
 * regardless of saltCode (rescuers don't have a SALT status — they
 * assign them).
 */
package be.heyman.android.jemmapassdemo.ui.radar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import be.heyman.android.jemmapassdemo.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class RadarOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "JEMMA-RADAR"
        private const val SWEEP_PERIOD_MS = 3000L

        /**
         * SALT color hex values — KEEP IN SYNC with `triage/SaltCode.kt`.
         * We don't import the enum here to keep this view free of any
         * triage-domain coupling.
         */
        private val SALT_HEX: Map<String, String> = mapOf(
            "WAIT" to "#9E9E9E",
            "EVAL" to "#FFC107",
            "STAB" to "#4CAF50",
            "HELP" to "#F44336",
            "EVAC" to "#2196F3",
            "DCD"  to "#000000",
        )

        /**
         * 🆕 v2.5.7 — SALT code → emoji glyph for the small badge drawn next
         * to victim dots when a triage status has been assigned (Plan A HTML
         * L44.16.77b parity).
         */
        private val SALT_EMOJI = mapOf(
            "WAIT" to "\u23F3",                // ⏳
            "EVAL" to "\uD83D\uDD0D",          // 🔍
            "STAB" to "\u2705",                // ✅
            "HELP" to "\uD83D\uDD98",          // 🆘  (using S.O.S. button glyph)
            "EVAC" to "\uD83D\uDE91",          // 🚑
            "DCD"  to "\uD83D\uDD4A",          // 🕊 (peace dove for the deceased)
        )
    }

    /**
     * One radar point.
     *
     * @param sessionIdHex  4-char hex display id
     * @param name          short name (≤ 12 chars displayed)
     * @param distanceM     distance to centre in meters (null = unknown)
     * @param bearingDeg    compass bearing 0=N, 90=E… (null = unknown)
     * @param criticality   0..3 — fallback color when saltCode is null
     * @param isStale       true if `lastSeenMs` is old → render faded
     * @param isRescuer     true → render as sky-blue ⛑ dot (overrides everything)
     * @param saltCode      4-char SALT code (WAIT/EVAL/STAB/HELP/EVAC/DCD)
     *                      → overrides criticality color when present
     * @param sex           'M', 'F', or null/'?' — used to pick the human emoji
     *                      above victim dots (👨/👩/🧑) ; ignored for rescuers
     * @param ageDecade     0..9 (= 0s/10s/.../90s) — used together with sex to
     *                      pick the icon (👶 baby, 👧/👦 child, 👵/👴 elderly)
     */
    data class Dot(
        val sessionIdHex: String,
        val name: String,
        val distanceM: Double?,
        val bearingDeg: Double?,
        val criticality: Int,
        val isStale: Boolean,
        val isRescuer: Boolean = false,
        val saltCode: String? = null,
        val sex: Char? = null,
        val ageDecade: Int? = null,
    )

    data class Snapshot(
        val selfLat: Double?,
        val selfLon: Double?,
        val selfHeadingDeg: Float?,
        val dots: List<Dot>,
    )

    // ── Internal state ─────────────────────────────────────────────────

    private var snapshot: Snapshot? = null
    private var outerScaleM: Double = 100.0
    private var sweepAngleDeg: Float = 0f
    private val sweepAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = SWEEP_PERIOD_MS
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        addUpdateListener {
            sweepAngleDeg = it.animatedValue as Float
            invalidate()
        }
    }

    // ── Paints ─────────────────────────────────────────────────────────

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = ContextCompat.getColor(context, R.color.jemma_radar_ring)
        alpha = 130
    }
    private val ringLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        color = ContextCompat.getColor(context, R.color.jemma_radar_ring)
        alpha = 180
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.8f)
        color = ContextCompat.getColor(context, R.color.jemma_radar_ring)
        alpha = 60
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.jemma_radar_self)
    }
    private val centerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = ContextCompat.getColor(context, R.color.jemma_radar_self)
        alpha = 200
    }
    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        color = ContextCompat.getColor(context, R.color.jemma_primary)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.5f) }
    private val dotLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        color = ContextCompat.getColor(context, R.color.jemma_text)
        textAlign = Paint.Align.CENTER
    }
    // 🆕 v2.5.7 — medical white cross drawn over each victim dot (ported from
    // Plan A HTML L44.16.23). The rescuer dot gets a slightly thicker cross.
    private val medCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val rescuerCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2.8f)
        strokeCap = Paint.Cap.ROUND
    }
    // 🆕 v2.5.7 — bigger emoji label drawn above the dot (sex/age icon for
    // victims, helmet ⛑️ for rescuers, as in Plan A HTML).
    private val dotEmojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(18f)
        textAlign = Paint.Align.CENTER
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Fallback criticality colors (0..3) when saltCode is null. */
    private val critColors by lazy {
        intArrayOf(
            ContextCompat.getColor(context, R.color.jemma_crit_unknown),
            ContextCompat.getColor(context, R.color.jemma_crit_green),
            ContextCompat.getColor(context, R.color.jemma_crit_yellow),
            ContextCompat.getColor(context, R.color.jemma_crit_red),
        )
    }

    // ── Public API ─────────────────────────────────────────────────────

    fun setSnapshot(snap: Snapshot?) {
        this.snapshot = snap
        outerScaleM = computeOuterScale(snap?.dots ?: emptyList())
        Log.d(
            TAG,
            "[t=${System.currentTimeMillis()}] 📡 setSnapshot · " +
                "${snap?.dots?.size ?: 0} dots · scale=${outerScaleM.toInt()}m · " +
                "self=(${snap?.selfLat ?: "-"},${snap?.selfLon ?: "-"})",
        )
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!sweepAnimator.isStarted) sweepAnimator.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wSpec = MeasureSpec.getSize(widthMeasureSpec)
        val hSpec = MeasureSpec.getSize(heightMeasureSpec)
        val side = if (hSpec > 0) minOf(wSpec, hSpec) else wSpec
        val sq = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY)
        super.onMeasure(sq, sq)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sweepAnimator.cancel()
    }

    // ── Drawing ────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = min(cx, cy) * 0.92f

        drawRadialBackground(canvas, cx, cy, radius)
        drawRings(canvas, cx, cy, radius)
        drawGrid(canvas, cx, cy, radius)
        drawSweep(canvas, cx, cy, radius)
        drawCardinalPoints(canvas, cx, cy, radius)
        drawSelf(canvas, cx, cy)
        drawDots(canvas, cx, cy, radius)
    }

    private fun drawRadialBackground(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        backgroundPaint.shader = RadialGradient(
            cx, cy, r,
            ContextCompat.getColor(context, R.color.jemma_radar_bg_inner),
            ContextCompat.getColor(context, R.color.jemma_radar_bg_outer),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r, backgroundPaint)
    }

    private fun drawRings(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        for (i in 1..4) {
            val ringR = r * (i / 4f)
            canvas.drawCircle(cx, cy, ringR, ringPaint)
            val labelDistTimes2 = (outerScaleM * 2.0 * i / 4.0).toInt()
            val labelDist = labelDistTimes2 / 2.0
            val label = when {
                labelDist >= 1000 -> "${(labelDist / 1000).toInt()}km"
                labelDist >= 10   -> "${labelDist.toInt()}m"
                else              -> String.format("%.1fm", labelDist)
            }
            canvas.drawText(label, cx + dp(4f), cy - ringR - dp(2f), ringLabelPaint)
        }
    }

    private fun drawGrid(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawLine(cx - r, cy, cx + r, cy, gridPaint)
        canvas.drawLine(cx, cy - r, cx, cy + r, gridPaint)
    }

    private fun drawSweep(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.save()
        canvas.rotate(sweepAngleDeg, cx, cy)
        sweepPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.TRANSPARENT,
                ContextCompat.getColor(context, R.color.jemma_radar_sweep) and 0x40FFFFFF.toInt(),
                ContextCompat.getColor(context, R.color.jemma_radar_sweep) and 0x80FFFFFF.toInt(),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0.0f, 0.05f, 0.08f, 0.10f),
        )
        canvas.drawCircle(cx, cy, r, sweepPaint)
        canvas.restore()
    }

    private fun drawCardinalPoints(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val heading = snapshot?.selfHeadingDeg ?: 0f
        canvas.save()
        canvas.rotate(-heading, cx, cy)
        canvas.drawText("N", cx, cy - r - dp(8f), northPaint)
        canvas.drawText("S", cx, cy + r + dp(18f), northPaint)
        canvas.drawText("E", cx + r + dp(14f), cy + dp(4f), northPaint)
        canvas.drawText("W", cx - r - dp(14f), cy + dp(4f), northPaint)
        canvas.restore()
    }

    private fun drawSelf(canvas: Canvas, cx: Float, cy: Float) {
        val pulse = 0.85f + 0.15f * sin(Math.toRadians((sweepAngleDeg * 2).toDouble())).toFloat()
        canvas.drawCircle(cx, cy, dp(5f), centerPaint)
        canvas.drawCircle(cx, cy, dp(11f) * pulse, centerRingPaint)
    }

    private fun drawDots(canvas: Canvas, cx: Float, cy: Float, rOuter: Float) {
        val snap = snapshot ?: return
        val heading = snap.selfHeadingDeg ?: 0f
        val rescuerColor = ContextCompat.getColor(context, R.color.jemma_rescuer_dot)

        for (dot in snap.dots) {
            val fracR: Float = when (val d = dot.distanceM) {
                null -> 0.95f
                else -> (d / outerScaleM).toFloat().coerceIn(0.05f, 1.0f)
            }
            val angleDeg = (dot.bearingDeg ?: 0.0) - heading - 90.0
            val rad = Math.toRadians(angleDeg)
            val px = cx + (rOuter * fracR) * cos(rad).toFloat()
            val py = cy + (rOuter * fracR) * sin(rad).toFloat()

            // Color resolution priority: isRescuer > saltCode > criticality
            val baseColor = when {
                dot.isRescuer -> rescuerColor
                dot.saltCode != null -> {
                    val hex = SALT_HEX[dot.saltCode]
                    if (hex != null) Color.parseColor(hex)
                    else critColors.getOrNull(dot.criticality.coerceIn(0, 3)) ?: critColors[0]
                }
                else -> critColors.getOrNull(dot.criticality.coerceIn(0, 3)) ?: critColors[0]
            }
            val alpha = if (dot.isStale) 100 else 255

            // Halo for critical / HELP victims (not for rescuers)
            val isHighPriority = !dot.isRescuer && (
                dot.saltCode == "HELP" || dot.criticality == 3
            )
            if (isHighPriority) {
                dotPaint.color = baseColor
                dotPaint.alpha = 60
                canvas.drawCircle(px, py, dp(16f), dotPaint)
            }

            // ── Filled coloured dot (slightly bigger for rescuers) ──
            val dotRadius = if (dot.isRescuer) dp(9f) else dp(8f)
            dotPaint.color = baseColor
            dotPaint.alpha = alpha
            canvas.drawCircle(px, py, dotRadius, dotPaint)

            dotRingPaint.color = Color.WHITE
            dotRingPaint.alpha = alpha
            canvas.drawCircle(px, py, dotRadius, dotRingPaint)

            // 🆕 v2.5.7 — White medical cross at the centre of the dot (Plan A
            // HTML look). Rescuers get a slightly thicker cross.
            val crossArm = if (dot.isRescuer) dp(5f) else dp(4.5f)
            val xPaint = if (dot.isRescuer) rescuerCrossPaint else medCrossPaint
            xPaint.alpha = alpha
            canvas.drawLine(px - crossArm, py, px + crossArm, py, xPaint)
            canvas.drawLine(px, py - crossArm, px, py + crossArm, xPaint)

            // 🆕 v2.5.7 — Two-line label above the dot, mirroring Plan A HTML :
            //   ⛑️  (rescuer)   or  sexAgeEmoji(sex, age)  (victim)
            //   <First name>
            dotEmojiPaint.alpha = alpha
            dotLabelPaint.alpha = alpha
            val topEmoji = if (dot.isRescuer) {
                "⛑\uFE0F"  // rescuer helmet (U+26D1 with VS16 to force emoji style)
            } else {
                sexAgeEmoji(dot.sex, dot.ageDecade)
            }
            canvas.drawText(topEmoji, px, py - dp(20f), dotEmojiPaint)
            val nameToShow = dot.name.take(10).ifBlank { dot.sessionIdHex }
            canvas.drawText(nameToShow, px, py + dp(22f), dotLabelPaint)

            // 🆕 v2.5.7 — If a SALT triage status is set on this victim,
            // draw a small SALT emoji badge to the upper-right of the dot
            // for a glance-and-go read in busy scenes.
            if (!dot.isRescuer && dot.saltCode != null) {
                val saltEmoji = SALT_EMOJI[dot.saltCode]
                if (saltEmoji != null) {
                    dotEmojiPaint.textSize = sp(13f)
                    dotEmojiPaint.alpha = alpha
                    canvas.drawText(saltEmoji, px + dp(13f), py - dp(8f), dotEmojiPaint)
                    dotEmojiPaint.textSize = sp(18f)  // restore for next dot
                }
            }
        }
    }

    /**
     * 🆕 v2.5.7 — Pick the human emoji for a victim dot from sex + age decade,
     * mirroring Plan A HTML's `sexAgeEmoji(sex, ageExact)` (JemmaRadarOverlay.kt).
     *
     * Mapping (age decade × sex):
     *   0   (0-4)       → 👶  any sex (no gender for infants)
     *   1   (5-14)      → 👧 F · 👦 M · 🧒 other
     *   2-6 (15-64)     → 👩 F · 👨 M · 🧑 other
     *   7+  (65+)       → 👵 F · 👴 M · 🧓 other
     *   null/unknown    → 🧑
     */
    private fun sexAgeEmoji(sex: Char?, ageDecade: Int?): String {
        val d = ageDecade ?: -1
        return when {
            d == 0 -> "\uD83D\uDC76"  // 👶 baby
            d == 1 -> when (sex) {
                'F', 'f' -> "\uD83D\uDC67"  // 👧
                'M', 'm' -> "\uD83D\uDC66"  // 👦
                else      -> "\uD83E\uDDD2" // 🧒
            }
            d in 2..6 -> when (sex) {
                'F', 'f' -> "\uD83D\uDC69"  // 👩
                'M', 'm' -> "\uD83D\uDC68"  // 👨
                else      -> "\uD83E\uDDD1" // 🧑
            }
            d >= 7 -> when (sex) {
                'F', 'f' -> "\uD83D\uDC75"  // 👵
                'M', 'm' -> "\uD83D\uDC74"  // 👴
                else      -> "\uD83E\uDDD3" // 🧓
            }
            else -> "\uD83E\uDDD1"  // 🧑 unknown
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun computeOuterScale(dots: List<Dot>): Double {
        val maxD = dots.mapNotNull { it.distanceM }.maxOrNull() ?: return 50.0
        val target = maxD * 1.25
        val tiers = listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)
        return tiers.firstOrNull { it >= target } ?: 10000.0
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity
}

// ── Static helpers used by host Fragments ──────────────────────────

fun gpsDistanceMeters(lat1: Double?, lon1: Double?, lat2: Double?, lon2: Double?): Double? {
    if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return null
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dPhi = Math.toRadians(lat2 - lat1)
    val dLam = Math.toRadians(lon2 - lon1)
    val a = sin(dPhi / 2).pow(2.0) + cos(phi1) * cos(phi2) * sin(dLam / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return 6_371_000.0 * c
}

fun gpsBearingDeg(lat1: Double?, lon1: Double?, lat2: Double?, lon2: Double?): Double? {
    if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return null
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dLam = Math.toRadians(lon2 - lon1)
    val y = sin(dLam) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLam)
    val brg = Math.toDegrees(atan2(y, x))
    return (brg + 360.0) % 360.0
}
