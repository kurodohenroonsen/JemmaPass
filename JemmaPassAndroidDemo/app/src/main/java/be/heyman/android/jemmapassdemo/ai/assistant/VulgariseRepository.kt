package be.heyman.android.jemmapassdemo.ai.assistant

import android.content.Context
import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = false)
data class GlobalVulgarisation(
    @Json(name = "k") val key: String,
    @Json(name = "l") val lang: String,
    @Json(name = "t") val text: String,
    @Json(name = "ts") val ts: Long = System.currentTimeMillis()
)

@Singleton
class VulgariseRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, GlobalVulgarisation::class.java)
    private val adapter = moshi.adapter<List<GlobalVulgarisation>>(listType)

    private val cacheFile: File
        get() = File(context.filesDir, "vulgarise_cache.json")

    private var memoryCache: MutableList<GlobalVulgarisation>? = null
    private var loadingJob: Deferred<MutableList<GlobalVulgarisation>>? = null
    private val mutex = Mutex()

    /** Charge le cache de manière asynchrone sans bloquer le mutex inutilement. */
    private suspend fun ensureCacheLoaded(): MutableList<GlobalVulgarisation> {
        memoryCache?.let { return it }

        val job = mutex.withLock {
            loadingJob ?: CoroutineScope(Dispatchers.IO).async {
                try {
                    if (cacheFile.exists()) {
                        val json = cacheFile.readText()
                        adapter.fromJson(json)?.toMutableList() ?: mutableListOf()
                    } else {
                        mutableListOf()
                    }
                } catch (e: Exception) {
                    Log.e("VULG-REPO", "Load fail", e)
                    mutableListOf()
                }
            }.also { loadingJob = it }
        }
        
        val result = job.await()
        memoryCache = result
        return result
    }

    suspend fun get(key: String, lang: String): String? {
        val cache = ensureCacheLoaded()
        // Recherche thread-safe (lecture seule ici, mais protégée par précaution)
        return mutex.withLock {
            cache.find { it.key == key && it.lang == lang }?.text
        }
    }

    suspend fun save(key: String, lang: String, text: String) {
        val cache = ensureCacheLoaded()
        
        val listToSerialize = mutex.withLock {
            cache.removeAll { it.key == key && it.lang == lang }
            cache.add(GlobalVulgarisation(key, lang, text))
            ArrayList(cache) // Copie pour sérialisation thread-safe
        }

        withContext(Dispatchers.IO) {
            try {
                val json = adapter.toJson(listToSerialize)
                cacheFile.writeText(json)
            } catch (e: Exception) {
                Log.e("VULG-REPO", "Save fail", e)
            }
        }
    }
}
