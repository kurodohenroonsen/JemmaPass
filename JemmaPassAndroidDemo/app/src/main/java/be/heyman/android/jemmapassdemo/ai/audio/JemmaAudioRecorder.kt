/*
 * JemmaAudioRecorder.kt — Lot 14.5c31
 *
 * Mirror of AI Edge Gallery's AudioRecorderPanel.kt audio capture
 * pipeline, but exposed as a plain (non-Compose) Kotlin class so it
 * can be driven by our XML-based fragments.
 *
 * Captures PCM 16-bit mono @ 16 kHz from the device microphone — the
 * exact format Gemma 4's audio adapter expects (per `tf_lite_audio_encoder_hw`
 * + `tf_lite_audio_adapter` configuration in the .litertlm file).
 *
 * For each buffer read off the mic, calls `onAmplitudeChanged(0..32767)`
 * so the UI can drive a real waveform (NOT an RMS estimate).
 *
 * Audio is accumulated in a ByteArrayOutputStream until `stop()` is
 * called, then returned as a single ByteArray ready to be wrapped in
 * `Content.AudioBytes(...)` and sent to `GemmaSession.ask(audioClip=...)`.
 *
 * Auto-stops at `maxDurationMs` to bound the inference cost.
 *
 * Usage:
 *     val recorder = JemmaAudioRecorder(context)
 *     recorder.start(
 *         onAmplitudeChanged = { amp -> waveform.setAmplitude(amp) },
 *         onMaxDurationReached = { stopAndSubmit() },
 *     )
 *     // ... later, on user tap:
 *     val pcm: ByteArray = recorder.stop()
 *     gemma.ask(prompt = "transcrits…", audioClip = pcm)
 */
package be.heyman.android.jemmapassdemo.ai.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class JemmaAudioRecorder @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
) {
    companion object {
        private const val TAG = "JEMMA-AUDIO-REC"
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val DEFAULT_MAX_DURATION_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var recordJob: Job? = null
    @Volatile private var audioRecord: AudioRecord? = null
    private val audioStream = ByteArrayOutputStream()
    @Volatile private var active: Boolean = false

    /** True while recording is in progress. */
    val isRecording: Boolean get() = active

    /**
     * Start recording. Must hold RECORD_AUDIO permission (caller's
     * responsibility — we throw SecurityException if not).
     *
     * @param onAmplitudeChanged called from a background thread for each
     *        buffer read off the mic. The Int is the peak absolute sample
     *        in the buffer, range 0..32767. Forward to the UI via
     *        `view.post { ... }` or post to main dispatcher.
     * @param onMaxDurationReached called once if the recording reaches
     *        maxDurationMs. Auto-stops the recorder before invoking.
     */
    @SuppressLint("MissingPermission")
    fun start(
        maxDurationMs: Long = DEFAULT_MAX_DURATION_MS,
        onAmplitudeChanged: (Int) -> Unit,
        onMaxDurationReached: (ByteArray) -> Unit,
    ) {
        if (active) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ start called while already active — ignoring")
            return
        }
        active = true
        audioStream.reset()

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ getMinBufferSize returned $minBufferSize")
            active = false
            return
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎙 start · sampleRate=$SAMPLE_RATE · " +
            "minBufferSize=$minBufferSize · maxDurationMs=$maxDurationMs")

        recordJob = scope.launch {
            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    minBufferSize,
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ AudioRecord construction failed · ${e.message}")
                active = false
                return@launch
            }
            audioRecord = recorder
            val buffer = ByteArray(minBufferSize)

            try {
                recorder.startRecording()
                val tStart = System.currentTimeMillis()
                Log.i(TAG, "[t=$tStart] 🟢 mic recording started")

                while (active && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val bytesRead = recorder.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        val amp = calculatePeakAmplitude(buffer, bytesRead)
                        onAmplitudeChanged(amp)
                        audioStream.write(buffer, 0, bytesRead)
                    }
                    val elapsed = System.currentTimeMillis() - tStart
                    if (elapsed >= maxDurationMs) {
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] ⏱ max duration reached · elapsed=${elapsed}ms")
                        val pcm = drainAndStopInternal()
                        withContext(Dispatchers.Main) { onMaxDurationReached(pcm) }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ recording loop threw", e)
            } finally {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔚 recording loop exited · " +
                    "audioStream.size=${audioStream.size()}")
            }
        }
    }

    /**
     * Stop recording and return the accumulated PCM byte array.
     * Idempotent — calling stop() when not active returns an empty array.
     */
    fun stop(): ByteArray {
        if (!active) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ stop called while not active — returning empty")
            return ByteArray(0)
        }
        return drainAndStopInternal()
    }

    /** Stop without returning bytes (e.g. user cancelled). */
    fun cancel() {
        if (!active) return
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✂ cancel · discarding ${audioStream.size()} bytes")
        active = false
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        recordJob?.cancel()
        recordJob = null
        audioStream.reset()
    }

    /** Best-effort cleanup for app shutdown / fragment destroy. */
    fun release() {
        cancel()
        scope.cancel()
    }

    private fun drainAndStopInternal(): ByteArray {
        active = false
        val recorder = audioRecord
        if (recorder != null && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            try { recorder.stop() } catch (e: Exception) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ recorder.stop threw · ${e.message}")
            }
        }
        try { recorder?.release() } catch (_: Exception) {}
        audioRecord = null

        val pcm = audioStream.toByteArray()
        audioStream.reset()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 drained · pcmBytes=${pcm.size} · " +
            "approxSeconds=${"%.2f".format(pcm.size / 2f / SAMPLE_RATE)}")
        return pcm
    }

    /**
     * Peak absolute amplitude in a buffer of 16-bit little-endian PCM.
     */
    private fun calculatePeakAmplitude(buffer: ByteArray, bytesRead: Int): Int {
        val shortBuffer =
            ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var maxAmplitude = 0
        while (shortBuffer.hasRemaining()) {
            val s = abs(shortBuffer.get().toInt())
            if (s > maxAmplitude) maxAmplitude = s
        }
        return maxAmplitude
    }

    /**
     * 🆕 Lot 14.5c32 — Wrap raw PCM in a 44-byte RIFF/WAVE header so it
     * becomes a valid WAV file. Gemma 4 audio input uses miniaudio which
     * needs a file format header — raw PCM throws `miniaudio decoder
     * error -10` (MA_INVALID_FILE).
     *
     * Byte-for-byte port of Gallery's ChatMessageAudioClip.genByteArrayForWav
     * (jdemo_new/.../ChatMessage.kt:187). 16kHz mono 16-bit PCM ↔ 32000 byteRate.
     */
    fun pcmToWav(pcm: ByteArray): ByteArray = buildWavHeader(pcm.size) + pcm

    private fun buildWavHeader(pcmDataSize: Int): ByteArray {
        val header = ByteArray(44)
        val wavFileSize = pcmDataSize + 44
        val channels = 1
        val bitsPerSample: Short = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (wavFileSize and 0xff).toByte()
        header[5] = (wavFileSize shr 8 and 0xff).toByte()
        header[6] = (wavFileSize shr 16 and 0xff).toByte()
        header[7] = (wavFileSize shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (SAMPLE_RATE and 0xff).toByte()
        header[25] = (SAMPLE_RATE shr 8 and 0xff).toByte()
        header[26] = (SAMPLE_RATE shr 16 and 0xff).toByte()
        header[27] = (SAMPLE_RATE shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = (bitsPerSample.toInt() shr 8 and 0xff).toByte()
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmDataSize and 0xff).toByte()
        header[41] = (pcmDataSize shr 8 and 0xff).toByte()
        header[42] = (pcmDataSize shr 16 and 0xff).toByte()
        header[43] = (pcmDataSize shr 24 and 0xff).toByte()
        return header
    }
}
