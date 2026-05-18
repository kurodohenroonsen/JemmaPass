/*
 * JemmaPayloadCodec.kt — v2.3.0a
 *
 * Port en Kotlin pur de jemma_payload_codec.js (HTML/JS reference).
 *
 * Décode le payload `_j 1.2` que JemmaPassHTML émet via QR. Deux formats
 * supportés (mêmes que le codec JS) :
 *
 *   1. Compressed   `_j2:<base64-of-deflate-raw-of-pruned-json>`
 *      Format moderne, ~50% plus petit que le legacy. Apparu en L44.16.45
 *      du codec JS. Utilise `Inflater(nowrap=true)` (= deflate-raw, pas zlib).
 *
 *   2. Legacy       `{"_j":"1.2", ...}` JSON brut (pruné ou non)
 *      Format historique, accepté pour compatibilité.
 *
 * Sur succès, retourne le `_j 1.2` SHORT object déserialisé en data class
 * Kotlin (voir JemmaProfileJ.kt). Les top-level arrays vides sont
 * re-hydratés automatiquement (`al, md, cn, ph, im, pr, dv, fs, pg, rs,
 * ad, cs, gl, en, oc, pv, ct`) pour que les readers downstream puissent
 * faire `.size` / `.forEach` sans null-check, comme dans le code JS.
 *
 * Référence wire format :
 *
 *   payload pruné (~3-5 KB JSON) → UTF-8 bytes
 *   → deflate-raw (RFC 1951, pas RFC 1950 zlib wrapper)
 *   → base64 standard (alphabet +/=)
 *   → préfixé `_j2:`
 *
 * Côté Android, le `Inflater(nowrap=true)` dans `java.util.zip` correspond
 * exactement au `DecompressionStream('deflate-raw')` du WebView. Pas besoin
 * de lib externe (zip4j, etc.).
 *
 * Pas async sauf si on parle de gros payloads — au-delà de quelques KB
 * c'est instantané sur Pixel/Samsung. On garde l'API synchrone pour
 * simplicité (le scanner peut décoder dans un coroutine Default si besoin).
 */
package be.heyman.android.jemmapassdemo.qr

import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import android.util.Base64

/**
 * Codec singleton pour les payloads `_j 1.2` JEMMA.
 * Pas de Hilt — pas d'état mutable, pure stateless utility.
 */
object JemmaPayloadCodec {

    private const val TAG = "JEMMA-CODEC"

    /** Préfixe magique du format compressé. Doit matcher MAGIC_PREFIX du JS. */
    const val MAGIC_PREFIX = "_j2:"

    /**
     * Top-level arrays légitimement vides en `_j 1.2`. On les re-hydrate
     * sur decode (cf. TOP_ARRAYS dans le JS).
     */
    private val TOP_ARRAYS = listOf(
        "al", "md", "cn", "ph", "im", "pr", "dv", "fs",
        "pg", "rs", "ad", "cs", "gl", "en", "oc", "pv", "ct"
    )

    /**
     * Format détecté lors du parse. Aligne avec `detectKind()` du JS.
     */
    enum class Format { COMPRESSED, LEGACY, UNKNOWN }

    /**
     * Résultat d'un decode. Soit un succès avec le profile, soit un échec
     * avec un message lisible (jamais d'exception remontée — l'appelant
     * a un single error path comme dans le JS).
     */
    sealed class DecodeResult {
        data class Success(
            val profile: JemmaProfileJ,
            val format: Format,
            /** JSON brut pruné, utile pour debug + persistence côté app. */
            val rawJson: String,
        ) : DecodeResult()

        data class Failure(val reason: String, val cause: Throwable? = null) : DecodeResult()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val profileAdapter: JsonAdapter<JemmaProfileJ> by lazy {
        moshi.adapter(JemmaProfileJ::class.java)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Détecte le format du texte sans le décoder. Utile pour filtrer les QR
     * qui ne sont pas JEMMA avant même de tenter un decode coûteux.
     */
    fun detectKind(text: String?): Format {
        if (text.isNullOrBlank()) return Format.UNKNOWN
        val trimmed = text.trim()
        if (trimmed.startsWith(MAGIC_PREFIX)) return Format.COMPRESSED
        // Regex équivalente à celle du JS : `^\{\s*["']?_j["']?\s*:\s*["']?1\.2`
        if (Regex("""^\{\s*["']?_j["']?\s*:\s*["']?1\.2""").containsMatchIn(trimmed)) {
            return Format.LEGACY
        }
        return Format.UNKNOWN
    }

    /**
     * Décode un payload QR JEMMA. Jamais d'exception throw — toute erreur
     * est convertie en [DecodeResult.Failure] avec un message lisible.
     */
    fun decode(text: String?): DecodeResult {
        if (text.isNullOrBlank()) {
            return DecodeResult.Failure("payload empty")
        }
        val kind = detectKind(text)
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔍 detectKind=$kind · len=${text.length}")

        return try {
            when (kind) {
                Format.COMPRESSED -> decodeCompressed(text.trim())
                Format.LEGACY -> decodeLegacy(text.trim())
                Format.UNKNOWN -> DecodeResult.Failure("not a JEMMA payload (no _j2: prefix, no _j 1.2 marker)")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ decode failed", e)
            DecodeResult.Failure("decode failed: ${e.message}", e)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Public API — encode
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Résultat d'un encode. Soit succès avec le payload prêt à QR/share,
     * soit échec avec un message lisible.
     *
     * Symétrique de [DecodeResult] côté wire format :
     *   payload = `_j2:<base64>`     (format COMPRESSED, par défaut)
     *   payload = `{"_j":"1.2",…}`   (format LEGACY, fallback si compression
     *                                 ne gagne rien — pathologique)
     */
    sealed class EncodeResult {
        data class Success(
            /** Le payload prêt à QR (compressed ou legacy fallback). */
            val payload: String,
            /** Format effectivement émis. */
            val format: Format,
            /** JSON pruné non-compressé — pour debug + log byte savings. */
            val prunedJson: String,
            /** Bytes du payload final (= payload.length pour ASCII pur). */
            val byteSize: Int,
            /** Latence totale encode en ms. */
            val elapsedMs: Long,
        ) : EncodeResult()

        data class Failure(val reason: String, val cause: Throwable? = null) : EncodeResult()
    }

    /**
     * Encode un [JemmaProfileJ] en payload QR-ready `_j2:<base64>`.
     *
     * Pipeline (miroir de jemma_payload_codec.js#encode) :
     *   1. smartPrune via [JemmaPayloadPruner] → JSON ~23 % plus petit
     *   2. UTF-8 encode → bytes
     *   3. Deflater(nowrap=true) → deflate-raw RFC 1951 bytes
     *   4. Base64 standard (alphabet +/=, no-wrap) → ASCII string
     *   5. Préfixe `_j2:` → payload final
     *
     * Garde-fou pathologique : si `compressed.length >= prunedJson.length`
     * (cas rare : payload ultra-court ou déjà aléatoire), on émet le JSON
     * pruné brut. Le decoder accepte les deux formats donc c'est transparent
     * côté réception.
     *
     * Jamais d'exception throw — toute erreur est convertie en
     * [EncodeResult.Failure].
     */
    fun encode(profile: JemmaProfileJ): EncodeResult {
        val t0 = System.currentTimeMillis()

        // 1+2. Prune + serialize via le pruner. Le pruner re-utilise notre
        //      profileAdapter pour respecter les @Json(name=…) du modèle.
        val prunedJson = try {
            JemmaPayloadPruner.pruneToJson(profile, profileAdapter)
        } catch (e: Throwable) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ prune failed", e)
            return EncodeResult.Failure("prune failed: ${e.message}", e)
        }

        // 3. Deflate-raw.
        val deflated = try {
            deflateRaw(prunedJson.toByteArray(Charsets.UTF_8))
        } catch (e: Throwable) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ deflate failed — fallback legacy", e)
            return EncodeResult.Success(
                payload = prunedJson,
                format = Format.LEGACY,
                prunedJson = prunedJson,
                byteSize = prunedJson.length,
                elapsedMs = System.currentTimeMillis() - t0,
            )
        }

        // 4. Base64 (NO_WRAP = pas de \n tous les 76 chars, NO_PADDING désactivé
        //    = on garde le `=` final pour matcher btoa() côté JS et le décodeur
        //    standard atob()).
        val b64 = Base64.encodeToString(deflated, Base64.NO_WRAP)

        // 5. Préfixe.
        val compressed = MAGIC_PREFIX + b64

        // Garde-fou pathologique.
        if (compressed.length >= prunedJson.length) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ compressed (${compressed.length}b) >= pruned (${prunedJson.length}b) — emitting legacy plain",
            )
            return EncodeResult.Success(
                payload = prunedJson,
                format = Format.LEGACY,
                prunedJson = prunedJson,
                byteSize = prunedJson.length,
                elapsedMs = System.currentTimeMillis() - t0,
            )
        }

        val dt = System.currentTimeMillis() - t0
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 📦 encode ok in ${dt}ms : pruned=${prunedJson.length}b → compressed=${compressed.length}b" +
                " (${(compressed.length * 100 / prunedJson.length.coerceAtLeast(1))}%)",
        )

        return EncodeResult.Success(
            payload = compressed,
            format = Format.COMPRESSED,
            prunedJson = prunedJson,
            byteSize = compressed.length,
            elapsedMs = dt,
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internals — compressed path
    // ──────────────────────────────────────────────────────────────────────

    private fun decodeCompressed(text: String): DecodeResult {
        val t0 = System.currentTimeMillis()
        val b64 = text.substring(MAGIC_PREFIX.length)

        val bytes: ByteArray = try {
            android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Failure("base64 decode failed: ${e.message}", e)
        }

        val inflated: ByteArray = try {
            inflateRaw(bytes)
        } catch (e: Exception) {
            return DecodeResult.Failure("deflate-raw inflate failed: ${e.message}", e)
        }

        val json = String(inflated, Charsets.UTF_8)
        Log.d(
            TAG,
            "[t=${System.currentTimeMillis()}] 🗜️ inflated · ${text.length}b → ${json.length}b in ${System.currentTimeMillis() - t0}ms"
        )

        return parseAndRehydrate(json, Format.COMPRESSED)
    }

    private fun decodeLegacy(text: String): DecodeResult {
        return parseAndRehydrate(text, Format.LEGACY)
    }

    private fun parseAndRehydrate(json: String, format: Format): DecodeResult {
        val rehydratedJson = rehydrateMissingArrays(json)
        val profile: JemmaProfileJ = try {
            profileAdapter.fromJson(rehydratedJson)
                ?: return DecodeResult.Failure("Moshi returned null profile (malformed JSON?)")
        } catch (e: Exception) {
            return DecodeResult.Failure("JSON parse failed: ${e.message}", e)
        }

        if (profile.j != "1.2") {
            return DecodeResult.Failure("missing _j 1.2 marker (got '${profile.j}')")
        }

        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✅ decoded · format=$format · " +
                "patient=${profile.p?.gn ?: "?"} · al=${profile.al.size} · md=${profile.md.size}"
        )

        return DecodeResult.Success(profile, format, rehydratedJson)
    }

    /**
     * Re-ajoute les top arrays vides au JSON (text-level), en utilisant le
     * fait que Moshi gère les missing fields via les defaults Kotlin (`emptyList()`).
     * En réalité on n'a pas besoin de toucher au JSON — les data classes ont
     * déjà des defaults `emptyList()` partout. Cette fonction reste pour
     * symétrie avec le JS smartReHydrate qui mute l'objet.
     *
     * Garde cette fonction au cas où on ajoute du tooling qui veut relire le
     * JSON ré-hydraté plus tard. Pour l'instant c'est un passthrough.
     */
    private fun rehydrateMissingArrays(json: String): String {
        // Moshi avec les data classes Kotlin gère les defaults nativement
        // (cf. KotlinJsonAdapterFactory). Pas besoin de manipuler le JSON.
        // Le JS le fait via Object.keys parce que JSON.parse retourne des
        // objets nus sans defaults. En Kotlin avec data class + emptyList()
        // on a la garantie au type level. Cette fonction est un no-op
        // intentionnel pour rester symétrique avec smartReHydrate du JS.
        return json
    }

    // ──────────────────────────────────────────────────────────────────────
    // Deflate-raw inflate
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inflate des données compressées au format `deflate-raw` (RFC 1951,
     * sans le wrapper zlib RFC 1950). Équivalent strict de
     * `DecompressionStream('deflate-raw')` côté navigateur.
     *
     * `Inflater(nowrap=true)` dans java.util.zip = deflate-raw.
     * `Inflater(nowrap=false)` (default) = zlib (rejette les payloads bruts
     * avec une `DataFormatException: incorrect header check`).
     *
     * On chunk par 8 KB pour gérer les payloads de plusieurs MB sans OOM
     * (pas critique ici — les payloads `_j 1.2` font au max 5 KB — mais
     * c'est gratuit et plus robuste).
     */
    private fun inflateRaw(deflated: ByteArray): ByteArray {
        val inflater = Inflater(/* nowrap= */ true)
        inflater.setInput(deflated)

        val out = ByteArrayOutputStream(deflated.size * 4)
        val buf = ByteArray(8 * 1024)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) {
                    if (inflater.needsInput()) {
                        throw IllegalStateException("inflater needs more input but stream ended")
                    }
                    if (inflater.needsDictionary()) {
                        throw IllegalStateException("inflater needs dictionary (deflate-raw should not)")
                    }
                    // n == 0 sans needsInput/needsDictionary peut arriver à
                    // la fin si finished() ne s'est pas encore mis à jour.
                    if (inflater.finished()) break
                }
                out.write(buf, 0, n)
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    /**
     * Compression deflate-raw (RFC 1951, pas RFC 1950 zlib wrapper) —
     * miroir exact de `_deflateRaw` côté JS (CompressionStream('deflate-raw')).
     *
     * `Deflater(level, nowrap=true)` produit la même sortie que le
     * `CompressionStream('deflate-raw')` du WebView. Sans `nowrap=true`,
     * Java ajouterait un header zlib 2-byte + un checksum Adler-32 4-byte
     * (= zlib RFC 1950) que le côté JS ne saurait pas décoder via
     * DecompressionStream('deflate-raw').
     *
     * Niveau BEST_COMPRESSION (= 9) : le payload `_j 1.2` est tout petit
     * (~1 KB pruné), donc compresser à fond est gratuit en CPU et gagne
     * ~30 b par rapport à DEFAULT_COMPRESSION (= 6). Mesuré sur Haru.
     */
    private fun deflateRaw(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, /* nowrap= */ true)
        deflater.setInput(input)
        deflater.finish()

        val out = ByteArrayOutputStream(input.size)
        val buf = ByteArray(8 * 1024)
        try {
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                if (n <= 0) {
                    // Edge case : deflater n'a plus rien à donner alors
                    // qu'il n'est pas marqué finished. Sortir pour ne pas
                    // boucler à l'infini.
                    if (deflater.finished()) break
                    throw IllegalStateException("deflater stalled (n=$n, finished=${deflater.finished()})")
                }
                out.write(buf, 0, n)
            }
        } finally {
            deflater.end()
        }
        return out.toByteArray()
    }
}
