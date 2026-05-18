/*
 * ProfilesRepository.kt — v2.2.16.10 · PHASE13 BUGFIX
 *
 * 🔧 v2.2.16.10 — BUGFIX critical : loadProfile() now re-injects the file
 *   basename as `sid` on the returned profile. Without this, every save
 *   from a downstream form (Contacts, Allergies, Medications) created a
 *   DUPLICATE profile because the JSON payload didn't include sid →
 *   saveProfile generated a fresh UUID. Visible symptom: profile list
 *   showing the same name N times after N edits.
 *
 * @Singleton repository for imported / created `_j 1.2` profiles.
 *
 * v2.2.16.5 additions :
 *   • currentProfileId persisted in SharedPreferences ("jemma_profiles_prefs")
 *   • setCurrent(id?) / clearCurrent() helpers
 *   • currentIdFlow StateFlow observable from the UI
 *   • saveProfile() returns Pair<id, alreadyExisted> so the caller can
 *     show "Profile updated" instead of "Profile imported" when re-scanning
 *
 * Storage layout :
 *
 *   {externalFilesDir}/profiles/
 *     ├── {profileId}.json                ← full JemmaProfileJ payload (Moshi)
 *     ├── {profileId}.json
 *     └── ...
 *
 *   SharedPreferences "jemma_profiles_prefs" :
 *     ├── currentProfileId : String?      ← the active profile id
 *     └── (room for future prefs)
 *
 * Profile ID generation :
 *   Prefer `profile.sid` if present (since L44.16.47 the JS-side embeds a
 *   stable sid — avoids duplicate imports of the same QR). Fallback to
 *   a UUID v4 when sid is null.
 */
package be.heyman.android.jemmapassdemo.profiles

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class ProfilesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hydrator: be.heyman.android.jemmapassdemo.kb.JemmaProfileHydrator,
) {

    companion object {
        private const val TAG = "JEMMA-PROFILES"
        private const val DIR_NAME = "profiles"
        private const val PREFS_NAME = "jemma_profiles_prefs"
        private const val KEY_CURRENT_PROFILE_ID = "currentProfileId"
    }

    private val profilesDir: File
        get() = File(context.getExternalFilesDir(null), DIR_NAME).apply { mkdirs() }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val profileAdapter: JsonAdapter<JemmaProfileJ> =
        moshi.adapter(JemmaProfileJ::class.java).indent("  ")

    private val _profilesFlow = MutableStateFlow<List<ProfileSummary>>(emptyList())

    /** Observable list of profile summaries (auto-refreshed after save/delete). */
    val profilesFlow: StateFlow<List<ProfileSummary>> = _profilesFlow.asStateFlow()

    private val _currentIdFlow = MutableStateFlow<String?>(
        prefs.getString(KEY_CURRENT_PROFILE_ID, null)
    )

    /** Observable id of the active profile, or null if none. */
    val currentIdFlow: StateFlow<String?> = _currentIdFlow.asStateFlow()

    /** Convenience synchronous accessor. */
    val currentProfileId: String? get() = _currentIdFlow.value

    @Volatile private var initialScanDone = false

    init {
        // v2.2.16.9 : eagerly scan disk on first injection so the
        // profilesFlow emits the existing profiles BEFORE any UI observes
        // it. Without this, the Fragment opens on an empty list even
        // when JSON files exist on disk.
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            ensureInitialScan()
        }
    }

    private suspend fun ensureInitialScan() {
        if (!initialScanDone) {
            initialScanDone = true

            // 🆕 Seed demo profiles only if they do not exist to prevent losing user modifications on restart
            try {
                val demoProfiles = be.heyman.android.jemmapassdemo.qr.JemmaPersonasSeeder.getDemoProfiles()
                for (demo in demoProfiles) {
                    val id = demo.sid ?: continue
                    val file = File(profilesDir, "$id.json")
                    
                    if (!file.exists()) {
                        // Seed the JSON file on the device if it doesn't exist
                        file.writeText(profileAdapter.toJson(demo))
                        
                        // Pre-hydrate and generate the FHIR IPS JSON too
                        try {
                            val hydrated = hydrator.hydrate(demo)
                            val fhirJson = be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder.build(hydrated)
                            val fhirFile = File(profilesDir, "$id.fhir.json")
                            fhirFile.writeText(fhirJson)
                            Log.d(TAG, "⭐ Seeded demo profile files for $id")
                        } catch (ex: Throwable) {
                            Log.e(TAG, "⚠️ Failed to pre-generate FHIR IPS for demo $id : ${ex.message}")
                        }
                    } else {
                        Log.d(TAG, "⭐ Demo profile $id already exists on disk. Skipping seeding to preserve user modifications.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Failed to seed demo profiles: ${e.message}", e)
            }

            rescanFromDisk()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // CRUD
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Save a freshly decoded profile to disk.
     *
     * Returns a pair :
     *   • first  = profile id (sid or generated UUID)
     *   • second = true if a file with that id already existed
     *              (i.e. this was an "update" not a fresh import — useful
     *              for a Toast like "Profile already imported, updated")
     *
     * If a profile with the same sid already exists, OVERWRITES it.
     * If currentProfileId is null and this is the first profile, sets it
     * as the current profile automatically (convenience for first-run UX).
     */
    suspend fun saveProfile(
        profile: JemmaProfileJ,
        sourceFormat: String = "UNKNOWN",
    ): SaveResult = withContext(Dispatchers.IO) {
        ensureInitialScan()
        val id = profile.sid?.takeIf { it.isNotBlank() } ?: generateNewId()
        val tStart = System.currentTimeMillis()
        val file = File(profilesDir, "$id.json")
        val fhirFile = File(profilesDir, "$id.fhir.json")
        val alreadyExisted = file.exists()

        try {
            // 1. Save standard JemmaProfileJ (for QR/Nearby)
            file.writeText(profileAdapter.toJson(profile))
            
            // 2. 🏥 Generate and Save Full FHIR IPS (for Hospital Interop)
            // Using the new JemmaFhirBundleBuilder backed by kotlin-fhir SDK.
            try {
                val hydrated = hydrator.hydrate(profile)
                val fhirJson = be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder.build(hydrated)
                fhirFile.writeText(fhirJson)
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏥 IPS FHIR profile saved for $id (${fhirJson.length} bytes)")
            } catch (e: Throwable) {
                Log.e(TAG, "⚠️ Failed to generate FHIR IPS for $id : ${e.message}", e)
                // We don't fail the whole save because the core profile is saved.
            }

            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 💾 saved id=$id name='${displayNameOf(profile)}' format=$sourceFormat alreadyExisted=$alreadyExisted in ${System.currentTimeMillis() - tStart}ms"
            )
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ saveProfile failed : ${e.message}", e)
            throw e
        }

        rescanFromDisk()

        // Auto-set as current if no current is set and this is the first
        // profile in the list (typical first-run scenario).
        if (_currentIdFlow.value == null && _profilesFlow.value.size == 1) {
            setCurrentInternal(id)
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ⭐ auto-set first profile as current : $id")
        }

        SaveResult(id, alreadyExisted)
    }

    /**
     * Load the full FHIR IPS Bundle JSON for a profile.
     */
    suspend fun loadIpsProfile(id: String): String? = withContext(Dispatchers.IO) {
        val file = File(profilesDir, "$id.fhir.json")
        if (file.exists()) file.readText() else null
    }

    /**
     * Load a full profile by ID.
     *
     * 🔧 PHASE13 BUGFIX — Re-inject the file-basename `id` as `sid` on the
     * returned profile if missing. Historically the JSON payload did not
     * include `sid` (the convention was that the file name IS the sid).
     * Downstream fragments (ContactsEditFragment, AllergiesEditFragment,
     * MedicationsEditFragment) call `loadProfile()` then later
     * `saveProfile(updatedProfile)` ; saveProfile relies on
     * `profile.sid` to decide which file to overwrite. With sid=null,
     * saveProfile generated a fresh UUID → DUPLICATE profile created on
     * every contact/allergy/med edit (visible as "Claude / Claude / Claude"
     * in the profile list). Symptom in logs :
     *   `💾 persist · profileId=null`
     *   `💾 saved id=p-NEW-UUID name='Claude' alreadyExisted=false`
     * Fix: always ensure `sid` matches the loaded file name on return.
     */
    suspend fun loadProfile(id: String): JemmaProfileJ? = withContext(Dispatchers.IO) {
        ensureInitialScan()
        val file = File(profilesDir, "$id.json")
        if (!file.exists()) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔍 loadProfile($id) → not found")
            return@withContext null
        }
        val parsed = try {
            profileAdapter.fromJson(file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ loadProfile($id) parse failed : ${e.message}", e)
            null
        } ?: return@withContext null
        // 🔧 BUGFIX — Inject sid if missing in JSON (file-name authoritative)
        if (parsed.sid.isNullOrBlank()) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔧 loadProfile($id) injected sid (was null/blank in JSON)")
            parsed.copy(sid = id)
        } else if (parsed.sid != id) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ loadProfile($id) JSON sid='${parsed.sid}' ≠ file name — using file name")
            parsed.copy(sid = id)
        } else {
            parsed
        }
    }

    /**
     * Delete a profile by ID. If it was the current profile, currentId
     * gets cleared (or auto-promoted to the new most-recent if any other
     * profile remains).
     */
    suspend fun deleteProfile(id: String) {
        withContext(Dispatchers.IO) {
            val file = File(profilesDir, "$id.json")
            val fhirFile = File(profilesDir, "$id.fhir.json")
            val deleted = file.delete()
            fhirFile.delete() // Clean up FHIR version too
            
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 🗑 deleteProfile($id) deleted=$deleted"
            )

            // If we just deleted the current profile, promote the most
            // recent remaining profile (or clear current if list is empty).
            if (_currentIdFlow.value == id) {
                rescanFromDisk()
                val nextId = _profilesFlow.value.firstOrNull()?.id
                setCurrentInternal(nextId)
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ⭐ deleted profile was current → promoted=$nextId"
                )
            } else {
                rescanFromDisk()
            }
        }
    }

    /**
     * Set the active profile id. Pass null to clear.
     * Persisted in SharedPreferences.
     */
    suspend fun setCurrent(id: String?) {
        withContext(Dispatchers.IO) {
            setCurrentInternal(id)
        }
    }

    private fun setCurrentInternal(id: String?) {
        prefs.edit { putString(KEY_CURRENT_PROFILE_ID, id) }
        _currentIdFlow.value = id
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ⭐ setCurrent($id)")
    }

    /**
     * Manually trigger a re-scan.
     */
    suspend fun refresh() {
        withContext(Dispatchers.IO) { rescanFromDisk() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal disk scan
    // ──────────────────────────────────────────────────────────────────────

    private fun rescanFromDisk() {
        val tStart = System.currentTimeMillis()
        val summaries = mutableListOf<ProfileSummary>()

        try {
            val files = profilesDir.listFiles { f ->
                f.isFile && f.name.endsWith(".json") && !f.name.endsWith(".fhir.json") && f.name != "meta.json"
            } ?: emptyArray()

            for (f in files) {
                try {
                    val json = f.readText()
                    val profile = profileAdapter.fromJson(json) ?: continue
                    summaries.add(
                        ProfileSummary(
                            id = f.nameWithoutExtension,
                            displayName = displayNameOf(profile),
                            birthDate = profile.p?.bd,
                            bloodType = profile.p?.bt,
                            allergiesCount = profile.al.size,
                            medicationsCount = profile.md.size,
                            conditionsCount = profile.cn.size,
                            lastModifiedMs = f.lastModified(),
                            fileSizeBytes = f.length(),
                        )
                    )
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "[t=${System.currentTimeMillis()}] ⚠️ skipping corrupt file ${f.name} : ${e.message}",
                        e
                    )
                }
            }

            summaries.sortByDescending { it.lastModifiedMs }
            _profilesFlow.value = summaries.toList()
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 📋 rescanFromDisk · ${summaries.size} profiles · current=${_currentIdFlow.value} in ${System.currentTimeMillis() - tStart}ms"
            )
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ rescanFromDisk failed : ${e.message}", e)
        }
    }

    private fun generateNewId(): String = "p-${UUID.randomUUID()}"

    private fun displayNameOf(profile: JemmaProfileJ): String {
        val gn = profile.p?.gn?.trim().orEmpty()
        val fn = profile.p?.fn?.trim().orEmpty()
        return when {
            gn.isNotEmpty() && fn.isNotEmpty() -> "$gn $fn"
            fn.isNotEmpty() -> fn
            gn.isNotEmpty() -> gn
            else -> "—"
        }
    }
}

/**
 * Lightweight summary of a profile, used for the Profiles list.
 */
data class ProfileSummary(
    val id: String,
    val displayName: String,
    val birthDate: String?,
    val bloodType: String?,
    val allergiesCount: Int,
    val medicationsCount: Int,
    val conditionsCount: Int,
    val lastModifiedMs: Long,
    val fileSizeBytes: Long,
)

/**
 * Returned by [ProfilesRepository.saveProfile] so the caller can react
 * differently on first import vs update of an existing profile.
 */
data class SaveResult(
    val id: String,
    val alreadyExisted: Boolean,
)
