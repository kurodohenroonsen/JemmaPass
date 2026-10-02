/*
 * JemmaQrFrameSplitter.kt — Lot 14.1 (PHASE 14)
 *
 * Port Kotlin du `_splitIntoFrames` de jemma_export_engine.js.
 *
 * Quand un payload dépasse la capacité raisonnable d'un seul QR (~2400
 * chars en byte mode, error correction L/M), on le split en N frames
 * scannables séquentiellement par le receiver.
 *
 * Wire format d'une frame :
 *   `JF:<index>/<total>|<data>`
 *
 * où :
 *   • `JF:`     magic prefix court (3 chars)
 *   • `<index>` 1-based
 *   • `<total>` total frame count
 *   • `|`       séparateur
 *   • `<data>`  le chunk du payload
 *
 * Exemple pour 3 frames :
 *   Frame 1 : "JF:1/3|<part1>"
 *   Frame 2 : "JF:2/3|<part2>"
 *   Frame 3 : "JF:3/3|<part3>"
 *
 * Le receiver concatène les `<part…>` dans l'ordre puis appelle
 * `JemmaPayloadCodec.decode(joined)` : voir [JemmaQrFrameAssembler]
 * (ordre libre, doublons tolérés, frames manquantes détectées), câblé dans
 * QrImportScanFragment et RescueQrScanFragment.
 *
 * Note : pour les payloads single-frame (< QR_MAX_SINGLE), on n'utilise
 * PAS de prefix `JF:1/1|` — comme le JS, on émet le payload brut pour
 * rester interop avec n'importe quel scanner QR (Channel 1 single = un
 * QR scannable par une app JEMMA quelconque sans gérer le wrapper).
 */
package be.heyman.android.jemmapassdemo.qr

import android.util.Log

object JemmaQrFrameSplitter {

    private const val TAG = "JEMMA-CODEC"

    /**
     * Limite "safe" par DÉFAUT pour 1 QR en byte mode, EC=M, en OCTETS UTF-8
     * (1800 octets + en-tête ECI/byte = QR version 35 en EC=M, capacité 1809).
     * Convient aux channels Pruned + Text. JemmaTextPayloadBuilder.MAX_BYTES
     * est aligné dessus : le QR texte tient toujours en 1 frame.
     *
     * 🆕 Lot 14.2c — réduit de 2400 → 1800 parce que les caméras Android
     * peinent à lire un QR version 25+ en pratique (alors que WebView du
     * HTML legacy s'en sortait grâce à un meilleur preprocessing image).
     */
    const val QR_MAX_SINGLE = 1800

    /**
     * Taille de chunk pour multi-frame par DÉFAUT. Overhead `JF:NN/NN|`
     * ≈ 10 chars inclus dans la marge.
     *
     * 🆕 Lot 14.2c — réduit de 1800 → 1400 pour la même raison.
     */
    const val QR_FRAME_CHUNK = 1400

    /**
     * Cap "safe" pour FHIR — version ≤ 13, EC=L (capacité ~862 chars).
     * On reste bien sous la capacité théorique pour garder une marge de
     * scanner réelle (caméras médiocres, low light, distance ~50 cm,
     * scénario démo Shikoku earthquake).
     *
     * Le HTML legacy utilisait 1800 sur WebView qui a un meilleur image
     * preprocessing — d'où les valeurs plus conservatives ici.
     */
    const val QR_MAX_SINGLE_FHIR = 900
    const val QR_FRAME_CHUNK_FHIR = 700

    /** Overhead approximé pour le header `JF:NN/NN|` (10 chars max). */
    private const val FRAME_HEADER_OVERHEAD = 10

    /**
     * Wrapper "défaut" : split avec les seuils standards (Pruned/Text).
     * Pour FHIR, appeler [split] avec [QR_MAX_SINGLE_FHIR] /
     * [QR_FRAME_CHUNK_FHIR].
     */
    fun split(payload: String): List<String> =
        split(payload, QR_MAX_SINGLE, QR_FRAME_CHUNK)

    /**
     * Découpe `payload` en frames scannables. Si `payload.length <=
     * [maxSingle]`, retourne une liste à 1 élément SANS prefix
     * (interop avec scanners QR génériques).
     *
     * Sinon, split en N chunks de ~[frameChunk] - overhead chars
     * chacun, préfixés `JF:i/N|`. Chunks équilibrés.
     */
    fun split(payload: String, maxSingle: Int, frameChunk: Int): List<String> {
        if (payload.isEmpty()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ frame split: empty payload")
            return listOf("")
        }

        // Get total UTF-8 bytes
        val totalBytes = payload.toByteArray(Charsets.UTF_8).size

        if (totalBytes <= maxSingle) {
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 🪟 single frame ($totalBytes bytes ≤ $maxSingle bytes)",
            )
            return listOf(payload)
        }

        val usableBytesPerFrame = frameChunk - FRAME_HEADER_OVERHEAD

        // First pass: greedy split to find the number of frames N based on bytes
        val tempChunks = mutableListOf<String>()
        var currentChunk = StringBuilder()
        var currentChunkBytes = 0
        var offset = 0
        while (offset < payload.length) {
            val codePoint = payload.codePointAt(offset)
            val charCount = Character.charCount(codePoint)
            val charStr = payload.substring(offset, offset + charCount)
            val charBytes = charStr.toByteArray(Charsets.UTF_8).size

            if (currentChunkBytes + charBytes > usableBytesPerFrame) {
                if (currentChunk.isNotEmpty()) {
                    tempChunks.add(currentChunk.toString())
                    currentChunk = StringBuilder(charStr)
                    currentChunkBytes = charBytes
                } else {
                    // Pathological: a single character is too big to fit in a frame
                    tempChunks.add(charStr)
                    currentChunk = StringBuilder()
                    currentChunkBytes = 0
                }
            } else {
                currentChunk.append(charStr)
                currentChunkBytes += charBytes
            }
            offset += charCount
        }
        if (currentChunk.isNotEmpty()) {
            tempChunks.add(currentChunk.toString())
        }

        val frameCount = tempChunks.size

        // Calculate balanced target byte size per chunk
        val targetByteSize = (totalBytes + frameCount - 1) / frameCount

        // Second pass: actual balanced split
        val chunks = ArrayList<String>(frameCount)
        currentChunk = StringBuilder()
        currentChunkBytes = 0
        offset = 0
        while (offset < payload.length) {
            val codePoint = payload.codePointAt(offset)
            val charCount = Character.charCount(codePoint)
            val charStr = payload.substring(offset, offset + charCount)
            val charBytes = charStr.toByteArray(Charsets.UTF_8).size

            val exceedsTarget = currentChunkBytes + charBytes > targetByteSize
            val exceedsAbsolute = currentChunkBytes + charBytes > usableBytesPerFrame

            val shouldSplit = currentChunk.isNotEmpty() && (
                exceedsAbsolute || (exceedsTarget && chunks.size < frameCount - 1)
            )

            if (shouldSplit) {
                chunks.add(currentChunk.toString())
                currentChunk = StringBuilder(charStr)
                currentChunkBytes = charBytes
            } else {
                currentChunk.append(charStr)
                currentChunkBytes += charBytes
            }
            offset += charCount
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }

        // Add headers to get the final frames
        val finalFrames = chunks.mapIndexed { index, chunk ->
            "JF:${index + 1}/${chunks.size}|$chunk"
        }

        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🪟 multi-frame split: $totalBytes bytes → ${finalFrames.size} frames" +
                " (targetByteSize=$targetByteSize bytes, usable=$usableBytesPerFrame bytes, maxSingle=$maxSingle bytes, frameChunk=$frameChunk bytes)",
        )
        return finalFrames
    }
}
