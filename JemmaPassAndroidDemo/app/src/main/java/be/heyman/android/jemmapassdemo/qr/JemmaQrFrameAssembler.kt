/*
 * JemmaQrFrameAssembler.kt — reader side of JemmaQrFrameSplitter.
 *
 * JemmaQrFrameSplitter emits `JF:<index>/<total>|<data>` frames when a payload
 * does not fit a single QR. This class collects scanned frames and rebuilds the
 * original payload:
 *
 *   • order-independent (frames may be scanned 3, 1, 2…) ;
 *   • duplicate-tolerant (a slideshow loops, the camera sees a frame many times) ;
 *   • knows which frames are still missing (never returns a partial payload) ;
 *   • a frame announcing another total, or another content for an index already
 *     held, belongs to another QR set → the collection restarts from that frame.
 *
 * Pure Kotlin (no android.*), so it is unit-testable on the JVM.
 *
 * Limit of the wire format: a frame carries no set identifier nor checksum. Frames
 * of two different payloads with the same total, scanned on indices not yet held,
 * cannot be told apart here; the joined text then fails in JemmaPayloadCodec.decode
 * (base64 / inflate / JSON), which is where the error is reported.
 */
package be.heyman.android.jemmapassdemo.qr

class JemmaQrFrameAssembler {

    /** One parsed `JF:i/N|data` frame. [index] is 1-based. */
    data class Frame(val index: Int, val total: Int, val data: String)

    sealed class Result {
        /** The text is not a `JF:` frame (single-frame payload or foreign QR): handle it as before. */
        object NotAFrame : Result()

        /** Frame accepted (or already known); [missing] lists the 1-based indices still to scan. */
        data class Progress(
            val received: Int,
            val total: Int,
            val missing: List<Int>,
            val duplicate: Boolean,
            /** True when this frame did not belong to the set in progress and started a new one. */
            val restarted: Boolean,
        ) : Result()

        /** Every frame is there: [payload] is the original text, byte for byte. */
        data class Complete(val payload: String, val total: Int) : Result()
    }

    private var total: Int = 0
    private val parts = HashMap<Int, String>()

    /** Total announced by the set in progress, 0 when nothing is being collected. */
    val expectedTotal: Int get() = total

    /** 1-based indices not scanned yet for the set in progress (empty when idle). */
    fun missing(): List<Int> = (1..total).filter { it !in parts }

    /** Forget the set in progress. */
    fun reset() {
        total = 0
        parts.clear()
    }

    /**
     * Feed one scanned QR text. Returns [Result.Complete] exactly when the last
     * missing frame arrives; the assembler is then reset and ready for a new set.
     */
    fun offer(raw: String?): Result {
        val frame = parse(raw) ?: return Result.NotAFrame

        var restarted = false
        val known = parts[frame.index]
        if (total != 0 && (frame.total != total || (known != null && known != frame.data))) {
            reset()
            restarted = true
        }
        if (total == 0) total = frame.total

        val duplicate = parts.containsKey(frame.index)
        if (!duplicate) parts[frame.index] = frame.data

        if (parts.size == total) {
            val sb = StringBuilder()
            for (i in 1..total) sb.append(parts[i])
            val n = total
            reset()
            return Result.Complete(sb.toString(), n)
        }
        return Result.Progress(parts.size, total, missing(), duplicate, restarted)
    }

    companion object {
        const val PREFIX = "JF:"

        /** Sanity bound on the announced total (a FHIR bundle is a few dozen frames). */
        const val MAX_FRAMES = 999

        /** True when [raw] looks like a multi-frame part (cheap pre-filter for the camera loop). */
        fun isFrame(raw: String?): Boolean = parse(raw) != null

        /**
         * Parse `JF:<index>/<total>|<data>`. Returns null when [raw] is not a
         * well-formed frame (bad prefix, non-numeric or out-of-range index/total).
         * The data part is kept verbatim — never trimmed, it may start or end with
         * whitespace cut from the middle of a text payload.
         */
        fun parse(raw: String?): Frame? {
            if (raw == null || !raw.startsWith(PREFIX)) return null
            val slash = raw.indexOf('/', PREFIX.length)
            if (slash < 0) return null
            val pipe = raw.indexOf('|', slash + 1)
            if (pipe < 0) return null
            val index = positiveInt(raw.substring(PREFIX.length, slash)) ?: return null
            val total = positiveInt(raw.substring(slash + 1, pipe)) ?: return null
            if (total > MAX_FRAMES || index > total) return null
            return Frame(index, total, raw.substring(pipe + 1))
        }

        /** Strict decimal parser: ASCII digits only, 1..4 digits, value ≥ 1. */
        private fun positiveInt(s: String): Int? {
            if (s.isEmpty() || s.length > 4) return null
            var v = 0
            for (c in s) {
                if (c < '0' || c > '9') return null
                v = v * 10 + (c - '0')
            }
            return if (v >= 1) v else null
        }

        /**
         * One-shot reassembly of a collection of scanned texts (any order, duplicates
         * allowed). Returns the payload, or null when a frame is missing, a text is
         * not a frame, or the frames do not belong to one single set.
         */
        fun join(frames: Collection<String>): String? {
            if (frames.isEmpty()) return null
            val byIndex = HashMap<Int, String>()
            var total = 0
            for (raw in frames) {
                val f = parse(raw) ?: return null
                if (total == 0) total = f.total
                if (f.total != total) return null
                val known = byIndex[f.index]
                if (known != null && known != f.data) return null
                byIndex[f.index] = f.data
            }
            if (byIndex.size != total) return null
            val sb = StringBuilder()
            for (i in 1..total) sb.append(byIndex[i])
            return sb.toString()
        }
    }
}
