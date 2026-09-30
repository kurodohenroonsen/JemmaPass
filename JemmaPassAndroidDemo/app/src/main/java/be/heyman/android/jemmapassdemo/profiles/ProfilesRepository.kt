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
 *     ├── {profileId}.json                ← JemmaProfileJ payload (Moshi) — legacy-authored
 *     │                                      pillars (patient/allergies/meds/conditions) +
 *     │                                      the pruned PROJECTION of the FHIR-native ones
 *     ├── {profileId}.fhir.json           ← FHIR R4 IPS Bundle — SOURCE OF TRUTH for the
 *     │                                      FHIR-native pillars (Immunizations first)
 *     └── ...
 *
 * FHIR-native pillars (feat/ips-18-pillars-cleanup) :
 *   • Every write goes through [writeProfileFiles] which regenerates BOTH files
 *     from (JemmaProfileJ, IpsNativePillars), so `_j.im` is always the
 *     projection of the Bundle's Immunization resources.
 *   • [resolveNativePillars] decides who is authoritative on a `_j` save :
 *     local edits keep the Bundle, imports (QR / mesh) take the incoming `_j`.
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
import be.heyman.android.jemmapassdemo.ips.IpsDevice
import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.ips.IpsImmunization
import be.heyman.android.jemmapassdemo.ips.IpsNativePillars
import be.heyman.android.jemmapassdemo.ips.IpsResult
import be.heyman.android.jemmapassdemo.ips.IpsProcedure
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaPersonasSeeder
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

        const val SOURCE_MANUAL_EDIT = "MANUAL_EDIT"
        const val SOURCE_DEMO_SEED = "DEMO_SEED"
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
                val demoProfiles = JemmaPersonasSeeder.getDemoProfiles()
                for (demo in demoProfiles) {
                    val id = demo.sid ?: continue
                    val file = File(profilesDir, "$id.json")
                    
                    if (!file.exists()) {
                        // Seed both files (JSON projection + FHIR Bundle) through the
                        // single write path so the demo immunizations land in the Bundle.
                        val native = JemmaPersonasSeeder.getDemoNativePillars(id)
                            ?: IpsNativePillars.fromJEntries(demo.im)
                        writeProfileFiles(id, demo, native)
                        Log.d(TAG, "⭐ Seeded demo profile files for $id")
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
        val alreadyExisted = file.exists()

        try {
            // FHIR-native pillars : decide who is authoritative for this save,
            // then regenerate BOTH files (JSON projection + FHIR Bundle).
            val native = resolveNativePillars(id, profile, sourceFormat)
            writeProfileFiles(id, profile, native)

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
        } else if (id == _currentIdFlow.value) {
            try {
                be.heyman.android.jemmapassdemo.sos.JemmaEmergencyWidget.updateAllWidgets(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widgets after saving current profile", e)
            }
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

    // ──────────────────────────────────────────────────────────────────────
    // FHIR-native pillars (source of truth = {id}.fhir.json)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Native pillars of a stored profile. Reads the Bundle; when it carries
     * none (legacy file written before the FHIR-native store, or a fresh
     * import) falls back to the `_j` projection so nothing is lost.
     */
    suspend fun loadNativePillars(id: String): IpsNativePillars = withContext(Dispatchers.IO) {
        ensureInitialScan()
        val profile = loadProfile(id) ?: return@withContext IpsNativePillars.EMPTY
        readNativePillars(id, profile)
    }

    suspend fun loadImmunizations(id: String): List<IpsImmunization> =
        loadNativePillars(id).immunizations

    suspend fun loadProcedures(id: String): List<IpsProcedure> =
        loadNativePillars(id).procedures

    suspend fun loadDevices(id: String): List<IpsDevice> =
        loadNativePillars(id).devices

    suspend fun loadResults(id: String): List<IpsResult> =
        loadNativePillars(id).results

    /**
     * Replace the immunizations of a profile. Returns false when the profile
     * does not exist. Regenerates the Bundle (authoritative) and the `_j`
     * projection, then refreshes the list / widget like [saveProfile].
     */
    suspend fun saveImmunizations(id: String, immunizations: List<IpsImmunization>): Boolean =
        saveNativePillars(id, "💉 ${immunizations.size} immunizations") { it.copy(immunizations = immunizations) }

    suspend fun saveProcedures(id: String, procedures: List<IpsProcedure>): Boolean =
        saveNativePillars(id, "🏥 ${procedures.size} procedures") { it.copy(procedures = procedures) }

    suspend fun saveDevices(id: String, devices: List<IpsDevice>): Boolean =
        saveNativePillars(id, "📟 ${devices.size} devices") { it.copy(devices = devices) }

    suspend fun saveResults(id: String, results: List<IpsResult>): Boolean =
        saveNativePillars(id, "🧪 ${results.size} results") { it.copy(results = results) }

    /** Shared write path of the FHIR-native pillar editors. */
    private suspend fun saveNativePillars(
        id: String,
        what: String,
        update: (IpsNativePillars) -> IpsNativePillars,
    ): Boolean = withContext(Dispatchers.IO) {
        ensureInitialScan()
        val profile = loadProfile(id) ?: return@withContext false
        val native = update(readNativePillars(id, profile))
        try {
            writeProfileFiles(id, profile, native)
            Log.i(TAG, "[t=${System.currentTimeMillis()}] $what saved for $id")
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ saveNativePillars($id, $what) failed : ${e.message}", e)
            throw e
        }
        rescanFromDisk()
        if (id == _currentIdFlow.value) {
            try {
                be.heyman.android.jemmapassdemo.sos.JemmaEmergencyWidget.updateAllWidgets(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widgets after saving $what", e)
            }
        }
        true
    }

    /** Bundle first, `_j` projection as fallback (legacy / freshly imported files). */
    private fun readNativePillars(id: String, profile: JemmaProfileJ): IpsNativePillars {
        val fhirFile = File(profilesDir, "$id.fhir.json")
        val fromBundle = if (fhirFile.exists()) {
            try {
                IpsFhirCodec.parseBundle(fhirFile.readText())?.let { IpsFhirCodec.nativeOf(it) }
            } catch (e: Throwable) {
                Log.w(TAG, "⚠️ readNativePillars($id) bundle unreadable : ${e.message}")
                null
            }
        } else null
        val fromJ = IpsNativePillars.fromJEntries(profile.im, profile.pr, profile.dv, profile.rs)
        if (fromBundle == null) return fromJ
        // Pillar by pillar: a Bundle written before a pillar went FHIR-native has no
        // resources for it, while the `_j` array may still carry legacy entries.
        return IpsNativePillars(
            immunizations = fromBundle.immunizations.ifEmpty { fromJ.immunizations },
            procedures = fromBundle.procedures.ifEmpty { fromJ.procedures },
            devices = fromBundle.devices.ifEmpty { fromJ.devices },
            results = fromBundle.results.ifEmpty { fromJ.results },
        )
    }

    /**
     * Who is authoritative for the FHIR-native pillars when a `_j` profile is
     * saved ?
     *   • DEMO_SEED            → the seeder's native data (else the `_j` arrays)
     *   • MANUAL_EDIT / ASSISTANT_* (local edits of other pillars)
     *                          → the existing Bundle (fallback : `_j` arrays)
     *   • anything else (QR / mesh / legacy imports) → the incoming `_j` arrays
     */
    private fun resolveNativePillars(id: String, profile: JemmaProfileJ, sourceFormat: String): IpsNativePillars {
        val fromJ = IpsNativePillars.fromJEntries(profile.im, profile.pr, profile.dv, profile.rs)
        return when {
            sourceFormat == SOURCE_DEMO_SEED ->
                JemmaPersonasSeeder.getDemoNativePillars(id) ?: fromJ
            sourceFormat == SOURCE_MANUAL_EDIT || sourceFormat.startsWith("ASSISTANT") ->
                readNativePillars(id, profile)
            else -> fromJ
        }
    }

    /**
     * The single write path : `_j` JSON (with the native pillars PROJECTED into
     * it) + FHIR IPS Bundle (native resources included). A Bundle failure does
     * not fail the save — the JSON is the compatibility layer for QR / mesh.
     */
    private suspend fun writeProfileFiles(id: String, profile: JemmaProfileJ, native: IpsNativePillars) {
        val file = File(profilesDir, "$id.json")
        val fhirFile = File(profilesDir, "$id.fhir.json")
        val projected = profile.copy(
            sid = id,
            im = native.immunizations.map { it.toJEntry() },
            pr = native.procedures.map { it.toJEntry() },
            dv = native.devices.map { it.toJEntry() },
            rs = native.results.map { it.toJEntry() },
        )
        // 1. `_j` projection (QR / Nearby / legacy screens)
        file.writeText(profileAdapter.toJson(projected))
        // 2. 🏥 FHIR IPS Bundle (source of truth for the native pillars)
        try {
            val hydrated = hydrator.hydrate(projected)
            val fhirJson = JemmaFhirBundleBuilder.build(hydrated, native)
            fhirFile.writeText(fhirJson)
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏥 IPS FHIR profile saved for $id (${fhirJson.length} bytes · ${native.immunizations.size} immunizations · ${native.procedures.size} procedures · ${native.devices.size} devices · ${native.results.size} results)")
        } catch (e: Throwable) {
            Log.e(TAG, "⚠️ Failed to generate FHIR IPS for $id : ${e.message}", e)
        }
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
        try {
            be.heyman.android.jemmapassdemo.sos.JemmaEmergencyWidget.updateAllWidgets(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update widgets after setting current profile", e)
        }
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
                            immunizationsCount = profile.im.size,
                            proceduresCount = profile.pr.size,
                            devicesCount = profile.dv.size,
                            resultsCount = profile.rs.size,
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
    val immunizationsCount: Int = 0,
    val proceduresCount: Int = 0,
    val devicesCount: Int = 0,
    val resultsCount: Int = 0,
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
