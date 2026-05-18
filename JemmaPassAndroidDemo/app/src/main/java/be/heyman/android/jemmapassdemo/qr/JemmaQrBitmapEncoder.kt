/*
 * JemmaQrBitmapEncoder.kt — Lot 14.1 (PHASE 14)
 *
 * Wrapper minimal autour de ZXing core pour générer un Bitmap N&B à
 * partir d'une string. Aucune UI ici — juste le rendu de la matrice.
 *
 * On utilise ZXing core (pas zxing-android-embedded) parce qu'on n'a
 * pas besoin de scanner UI (on a déjà ML Kit pour scanner) : on veut
 * juste l'algo d'encodage QR. ZXing core = ~250 KB et 0 transitif.
 *
 * * Choix de paramètres :
 *   • Encoding hint = UTF-8 (permet de supporter nativement les Emojis, les
 *     accents français et les caractères japonais Kanji/Hiragana/Katakana,
 *     tout en forçant le mode Byte dans ZXing pour accepter les caractères `:` et `/`
 *     ainsi que le padding `=` du Base64).
 *   • Error correction = M (medium, 15 % redundancy) — équivalent au
 *     default ZXing et matche le `errorCorrection: 'M'` du JS pour le
 *     channel pruned.
 *   • Margin = 2 modules (quiet zone) — matche le JS.
 *
 * Pas de couleurs custom — N&B pur pour scanner-friendly. Pas de logo
 * embedded (Kudoro pourra ajouter ça plus tard si désiré).
 */
package be.heyman.android.jemmapassdemo.qr

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object JemmaQrBitmapEncoder {

    private const val TAG = "JEMMA-CODEC"

    /**
     * Génère un Bitmap N&B carré [sizePx] × [sizePx] représentant
     * [content] encodé en QR byte mode, [errorCorrection] (default M),
     * margin 2.
     *
     * 🆕 Lot 14.2c — [errorCorrection] est désormais paramétrable :
     *   - M (15 % redundancy) : default, robuste, pour Pruned + Text
     *   - L (7 % redundancy)  : pour FHIR (chunks plus petits, plus de
     *     frames, donc on n'a pas besoin de redondance forte par frame
     *     — le slideshow auto-loop compense les rates ratés)
     *
     * @param content          la string à encoder.
     * @param sizePx           taille cible en pixels (default 800).
     * @param errorCorrection  niveau EC ZXing (default M).
     */
    fun encode(
        content: String,
        sizePx: Int = 800,
        errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
    ): Bitmap? {
        if (content.isEmpty()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ qr encode: empty content")
            return null
        }

        val t0 = System.currentTimeMillis()
        val hints = mapOf<EncodeHintType, Any>(
            EncodeHintType.ERROR_CORRECTION to errorCorrection,
            EncodeHintType.MARGIN to 2,
            // 🆕 UTF-8 enables flawless support for multi-byte characters (Emojis, Japanese, French accents)
            // while still choosing standard Byte Mode in ZXing to support arbitrary symbols like '='.
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )

        return try {
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                0,
                0,
                hints,
            )
            val rawW = matrix.width
            val rawH = matrix.height
            val scale = Math.max(1, sizePx / rawW)
            val w = rawW * scale
            val h = rawH * scale
            val pixels = IntArray(w * h)
            var y = 0
            while (y < h) {
                val rowOffset = y * w
                val rawY = y / scale
                var x = 0
                while (x < w) {
                    val rawX = x / scale
                    pixels[rowOffset + x] = if (matrix.get(rawX, rawY)) Color.BLACK else Color.WHITE
                    x++
                }
                y++
            }

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            val dt = System.currentTimeMillis() - t0
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 🖼️ qr encode ok in ${dt}ms : len=${content.length}b → raw ${rawW}×${rawH} scaled to ${w}×${h}px · EC=${errorCorrection.name}",
            )
            bitmap
        } catch (e: WriterException) {
            // 🆕 Automatic self-healing: if too big, dynamically fall back to lower error correction level to avoid WriterException
            val fallbackEc = when (errorCorrection) {
                ErrorCorrectionLevel.H -> ErrorCorrectionLevel.Q
                ErrorCorrectionLevel.Q -> ErrorCorrectionLevel.M
                ErrorCorrectionLevel.M -> ErrorCorrectionLevel.L
                ErrorCorrectionLevel.L -> null
            }
            if (fallbackEc != null) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ qr encode failed with EC=${errorCorrection.name} due to size. Retrying with fallback EC=${fallbackEc.name}...")
                encode(content, sizePx, fallbackEc)
            } else {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ qr encode failed (len=${content.length}, EC=${errorCorrection.name})", e)
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ qr encode unexpected error", e)
            null
        }
    }
}
