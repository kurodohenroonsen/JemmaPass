/*
 * JemmaPayloadPruner.kt — Lot 14.1 (PHASE 14)
 *
 * Port Kotlin du smartPrune de jemma_payload_codec.js (HTML legacy).
 *
 * Pourquoi pruner avant compression :
 *   • Les top-level arrays vides (al, md, cn, ph, im, pr, dv, fs, pg, rs,
 *     ad, cs, gl, en, oc, pv, ct) sont du bruit dans le JSON. Les drop
 *     gagne ~23 % de bytes AVANT déflation.
 *   • Quelques champs entry/patient sont presque toujours vides (al.d,
 *     al.m, md.rs, md.rc, cn.rs, cn.rc, p.nat, p.adr, p.ct[].adr). On
 *     les drop quand ils sont "" / null.
 *
 * Sur Haru :  raw 1009 b → pruned 777 b → compressed 500 b
 *
 * Le decode-side fait du re-hydrate des top arrays côté JemmaPayloadCodec
 * (rehydrateMissingArrays). C'est symétrique : on prune ici à l'encode,
 * on rehydrate là-bas au decode. Les champs entry/patient NE sont PAS
 * rehydratés — les readers Kotlin tolèrent `null` / `""` partout (default
 * values dans les data classes).
 *
 * Stratégie d'implémentation : on ne touche PAS à JemmaProfileJ. On
 * sérialise via Moshi en String JSON brut, on parse en Map mutable, on
 * applique le prune, on re-sérialise. Coût : ~1 ms pour un profil 1 KB.
 * Bénéfice : pas de duplication de modèle, pas de risque de désync
 * data class ↔ pruner.
 */
package be.heyman.android.jemmapassdemo.qr

import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Pruner singleton stateless. Pas de Hilt — pure utility.
 */
object JemmaPayloadPruner {

    private const val TAG = "JEMMA-CODEC"

    /**
     * Top-level arrays légitimement vides dans `_j 1.2`. Doit matcher
     * exactement TOP_ARRAYS dans jemma_payload_codec.js.
     */
    private val TOP_ARRAYS = listOf(
        "al", "md", "cn", "ph", "im", "pr", "dv", "fs",
        "pg", "rs", "ad", "cs", "gl", "en", "oc", "pv", "ct"
    )

    /**
     * Champs optionnels d'entry souvent vides. Drop quand `""`/null.
     * Doit matcher PRUNABLE_ENTRY_FIELDS dans le JS.
     */
    private val PRUNABLE_ENTRY_FIELDS: Map<String, List<String>> = mapOf(
        "al" to listOf("d", "m"),
        "md" to listOf("rs", "rc"),
        "cn" to listOf("rs", "rc"),
    )

    /**
     * Champs optionnels patient souvent vides. Drop quand `""`/null.
     * Doit matcher PRUNABLE_PATIENT_FIELDS dans le JS.
     */
    private val PRUNABLE_PATIENT_FIELDS: List<String> = listOf("nat", "adr")

    /** Adapter Moshi pour Map<String, Any?> — partagé, thread-safe. */
    private val mapAdapter: JsonAdapter<MutableMap<String, Any?>> by lazy {
        val moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(
            MutableMap::class.java,
            String::class.java,
            Any::class.java,
        )
        moshi.adapter<MutableMap<String, Any?>>(type)
    }

    /**
     * Sérialise un [JemmaProfileJ] en JSON pruné prêt pour la
     * compression. Le résultat est un JSON `_j 1.2` SHORT amputé des
     * champs vides — il reste 100 % réversible côté decode (les top
     * arrays sont rehydratés, les champs entry/patient tolèrent leur
     * absence).
     *
     * @param profile  le profile à pruner
     * @param profileAdapter  adapter Moshi pour [JemmaProfileJ] (passé
     *        en paramètre pour éviter de recréer un Moshi à chaque
     *        appel — l'appelant a déjà un Moshi configuré).
     * @return  JSON pruné en UTF-8 ready-to-deflate
     */
    fun pruneToJson(
        profile: JemmaProfileJ,
        profileAdapter: JsonAdapter<JemmaProfileJ>,
    ): String {
        val t0 = System.currentTimeMillis()

        // 1. Serialize → JSON brut via Moshi (respecte les @Json names).
        val rawJson = profileAdapter.toJson(profile)
        val rawLen = rawJson.length

        // 2. Parse → Map mutable pour pouvoir muter inline.
        val map: MutableMap<String, Any?> = mapAdapter.fromJson(rawJson)?.toMutableMap()
            ?: run {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ prune: parse failed, returning raw JSON")
                return rawJson
            }

        // 3. Drop empty top-level arrays.
        for (key in TOP_ARRAYS) {
            val v = map[key]
            if (v is List<*> && v.isEmpty()) {
                map.remove(key)
            }
        }

        // 4. Drop empty optional fields dans chaque entry des arrays
        //    listés dans PRUNABLE_ENTRY_FIELDS.
        for ((arrKey, fields) in PRUNABLE_ENTRY_FIELDS) {
            val arr = map[arrKey] as? List<*> ?: continue
            for (entry in arr) {
                if (entry !is MutableMap<*, *>) continue
                @Suppress("UNCHECKED_CAST")
                val mutable = entry as MutableMap<String, Any?>
                for (f in fields) {
                    if (isBlankOrNull(mutable[f])) mutable.remove(f)
                }
            }
        }

        // 5. Patient — drop empty optional fields (nat, adr) + contacts.adr.
        val patient = map["p"]
        if (patient is MutableMap<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val pMut = patient as MutableMap<String, Any?>
            for (f in PRUNABLE_PATIENT_FIELDS) {
                if (isBlankOrNull(pMut[f])) pMut.remove(f)
            }
            val cts = pMut["ct"] as? List<*>
            if (cts != null) {
                for (c in cts) {
                    if (c !is MutableMap<*, *>) continue
                    @Suppress("UNCHECKED_CAST")
                    val cMut = c as MutableMap<String, Any?>
                    if (isBlankOrNull(cMut["adr"])) cMut.remove("adr")
                }
            }
        }

        // 6. Re-sérialise. Moshi ne réordonne pas les keys par défaut —
        //    l'ordre d'insertion (donc l'ordre des @Json sur la data
        //    class) est conservé. Bonus : output déterministe pour
        //    debug + diff.
        val prunedJson = mapAdapter.toJson(map)
        val dt = System.currentTimeMillis() - t0
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✂️ prune ok in ${dt}ms : raw=${rawLen}b → pruned=${prunedJson.length}b" +
                " (${(prunedJson.length * 100 / rawLen.coerceAtLeast(1))}%)",
        )
        return prunedJson
    }

    /** `null`, `""`, ou pas de String → considéré comme vide. */
    private fun isBlankOrNull(v: Any?): Boolean =
        v == null || (v is String && v.isEmpty())
}
