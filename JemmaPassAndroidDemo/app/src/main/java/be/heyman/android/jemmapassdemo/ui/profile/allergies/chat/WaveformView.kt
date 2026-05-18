/*
 * WaveformView.kt — Lot 14.5c30 v6
 *
 * Custom View that renders an animated waveform of N vertical bars, each
 * height driven by the latest RMS dB pushed via [setRmsDb]. Bars decay
 * smoothly toward zero when RMS drops, and have a tiny per-bar idle
 * jitter so the wave still pulses gently when the user is silent.
 *
 * Inspired by AI Edge Gallery's AudioRecorderPanel (which uses Compose
 * + AudioRecord's peak amplitude). We instead piggyback on Android
 * SpeechRecognizer's onRmsChanged callback (no need for a second mic
 * stream).
 *
 * Wire from a Fragment :
 *   waveform.start()
 *   sttService.start(lang, listener with onRms { waveform.setRmsDb(it) })
 *   ... when STT ends → waveform.stop()
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies.chat

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    /** Number of bars drawn left-to-right. */
    private val barCount: Int = 28

    /** Latest RMS amplitude, normalized 0f..1f. Updated by [setRmsDb]. */
    @Volatile private var amplitude: Float = 0f

    /** Smoothed amplitude that decays toward `amplitude`. */
    private var smoothed: Float = 0f

    /** Time-based phase for the gentle idle wave. */
    private var phase: Float = 0f

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF22D69A.toInt() // Jemma green
        style = Paint.Style.FILL
    }

    private val rect = RectF()
    private val rng = Random(System.currentTimeMillis())
    private val perBarOffset = FloatArray(barCount) { rng.nextFloat() * 6.283f }

    private var animator: ValueAnimator? = null

    /** Push latest RMS reading (typically Android STT RMS is -2..10 dB). */
    fun setRmsDb(rmsDb: Float) {
        // Map RMS dB to 0..1. STT RMS rarely goes above 10 dB, often 1..7 dB
        // when talking. Map -2..10 to 0..1, clipped.
        val norm = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
        amplitude = norm
    }

    /**
     * 🆕 Lot 14.5c31 — push raw 16-bit PCM peak amplitude (0..32767), as
     * produced by `JemmaAudioRecorder.calculatePeakAmplitude`. This is the
     * real audio peak, not an RMS estimate, so the waveform actually
     * tracks speech energy.
     *
     * Normalization : 0..32767 → 0..1 via division by ~16384 (half range)
     * so typical speech (peaks ~6000..12000) lands in mid-bar territory
     * rather than tiny. Clipped at 1.0 for loud talkers.
     */
    fun setAmplitude(rawPeak: Int) {
        val norm = (rawPeak / 16_384f).coerceIn(0f, 1f)
        amplitude = norm
    }

    fun start() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 60_000  // long-running, restart via repeat
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                // Smooth amplitude toward target. Tau ~ 4 frames.
                smoothed = smoothed * 0.78f + amplitude * 0.22f
                phase += 0.18f
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
        amplitude = 0f
        smoothed = 0f
        invalidate()
    }

    fun setBarColor(color: Int) {
        barPaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val gap = 4f
        val barWidth = (w - gap * (barCount - 1)) / barCount
        val midY = h / 2f
        val minBarH = h * 0.08f
        val maxBarH = h * 0.92f

        for (i in 0 until barCount) {
            // Each bar pulses with a phase offset for "Siri" effect, scaled
            // by the smoothed amplitude. Idle is a gentle sine wave.
            val idle = (sin(phase + perBarOffset[i]) + 1f) * 0.5f  // 0..1
            val active = smoothed * (0.6f + 0.4f * idle)
            val barH = minBarH + (maxBarH - minBarH) * max(active, idle * 0.15f)
            val x = i * (barWidth + gap)
            rect.set(x, midY - barH / 2f, x + barWidth, midY + barH / 2f)
            val radius = min(barWidth, barH) / 2f
            canvas.drawRoundRect(rect, radius, radius, barPaint)
        }
    }
}
