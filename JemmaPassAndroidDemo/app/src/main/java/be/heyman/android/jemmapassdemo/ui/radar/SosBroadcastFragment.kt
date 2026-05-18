/*
 * SosBroadcastFragment.kt — JEMMA Pass · JemmaAppDemo · v2.5.12
 *
 * Victim-side SOS broadcast screen — v2.5.12 UX refonte :
 *
 *   • Toolbar title is composed every 1 Hz tick as a compact composite :
 *       "🆘 {profileName} · {mm:ss} · 👤 {peers} · ⛑️ {rescuers}"
 *     Replaces the dedicated "DIFFUSION SOS" toolbar text + the
 *     in-body cards (status pill / SALT card / profile pill).
 *   • Radar canvas is fixed at the top, full-width, NOT inside a
 *     NestedScrollView. The user always sees their tactical context.
 *   • Below the radar : ONE unified RecyclerView listing every peer
 *     detected at least once (other victims AND rescuers around me).
 *     Victims show their SALT status if any rescuer has triaged them
 *     via the mesh ; rescuers show a ⛑️ accent strip and no SALT row.
 *   • Bottom-left FAB "STOP SOS" kills the broadcast and closes the
 *     screen. No more cluttered cards above the radar.
 *
 * The business logic (doAutoStart, configureBeaconFromRaw, loadProfile
 * RawJson, parsePeers, parseRescuers) is preserved verbatim from v2.5.6.
 * Only the UI binding and the tick() output surfaces changed.
 *
 * Log channel : JEMMA-SOS-UI
 */
package be.heyman.android.jemmapassdemo.ui.radar

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.compass.JemmaCompass
import be.heyman.android.jemmapassdemo.databinding.FragmentSosBroadcastBinding
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import be.heyman.android.jemmapassdemo.radar.RadarController
import be.heyman.android.jemmapassdemo.sos.JemmaDeviceId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@AndroidEntryPoint
class SosBroadcastFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-SOS-UI"
        private const val TICK_MS = 1000L
        private const val PROFILES_DIR_NAME = "profiles"
        private const val CRIT_STABLE_DEFAULT = 0
    }

    @Inject lateinit var radar: RadarController
    @Inject lateinit var profilesRepo: ProfilesRepository

    private var _binding: FragmentSosBroadcastBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private val compass: JemmaCompass by lazy { JemmaCompass(requireContext()) }
    private lateinit var peerAdapter: VictimCardAdapter

    /** Cached at first onResume — used to look up our own SALT status. */
    private var mySid: String = ""

    /** Cached at bind — used in the toolbar title. */
    private var myProfileName: String = ""

    /** Set when sosToggleBroadcast() succeeds — drives the elapsed timer. */
    private var broadcastStartedAtMs: Long = 0L
    private var autoStartLaunched: Boolean = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            try { tick() } catch (e: Exception) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ tick failed: ${e.message}")
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView (SosBroadcastFragment v2.5.12)")
        _binding = FragmentSosBroadcastBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mySid = JemmaDeviceId.get(requireContext())
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🆔 mySid=$mySid")

        binding.sosToolbarBack.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.sosBtnStop.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 STOP SOS tap")
            val r = radar.sosStop()
            Log.i(TAG, "[t=${System.currentTimeMillis()}]   ↳ sosStop ${r.asWireString()}")
            broadcastStartedAtMs = 0L
            autoStartLaunched = false
            findNavController().navigateUp()
        }

        // 🆕 v2.5.12 — Resolve our profile name once and cache it for the
        // composite toolbar title. The pill UI is gone but we still need
        // the name to display "🆘 Kurodo · 09:06 …".
        cacheProfileName()

        // 🆕 v2.5.12 — Single unified RecyclerView : victims + rescuers
        // mixed. The user is themselves a victim ; tapping a peer is
        // purely informational (no triage from the victim side).
        peerAdapter = VictimCardAdapter(
            onClick = { v ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 (info) tap on peer sid=${v.sessionIdHex} " +
                    "isRescuer=${v.isRescuer}")
            },
            showInlineTriage = false,
        )
        binding.sosPeerList.layoutManager = LinearLayoutManager(requireContext())
        binding.sosPeerList.adapter = peerAdapter
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ▶ onResume · sosActive=${radar.sosIsActive()} · broadcasting=${radar.sosIsBroadcasting()}")
        if (!radar.sosIsActive() && !autoStartLaunched) {
            autoStartLaunched = true
            doAutoStart()
        }
        compass.start()
        handler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
        compass.stop()
        Log.d(TAG, "[t=${System.currentTimeMillis()}] ⏸ polling paused")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ──────────────────────────────────────────────────────────────────
    //  Auto-start sequence — runs once on first onResume
    // ──────────────────────────────────────────────────────────────────

    private fun doAutoStart() {
        val tStart = System.currentTimeMillis()
        Log.i(TAG, "[t=$tStart] 🆘 AUTO-START SOS")

        val currentId = profilesRepo.currentProfileId
        if (currentId.isNullOrBlank()) {
            Log.w(TAG, "[t=$tStart] ⚠ no current profile · cannot configure beacon")
            Toast.makeText(requireContext(), R.string.sos_err_no_profile, Toast.LENGTH_LONG).show()
            return
        }

        val startResult = radar.sosStart()
        Log.i(TAG, "[t=$tStart]   ↳ sosStart ${startResult.asWireString()}")
        if (startResult !is RadarController.Result.Ok) {
            Toast.makeText(
                requireContext(),
                getString(R.string.sos_err_start_failed, startResult.asWireString()),
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val rawJson = loadProfileRawJson(currentId)
            if (rawJson.isNullOrBlank()) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ profile raw JSON load returned null/empty")
                Toast.makeText(requireContext(), R.string.sos_err_no_profile, Toast.LENGTH_LONG).show()
                return@launch
            }
            Log.i(TAG, "[t=${System.currentTimeMillis()}]   ↳ profile raw json loaded · ${rawJson.length} bytes")

            val ok = configureBeaconFromRaw(rawJson)
            if (!ok) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ configureBeacon failed · skipping toggle")
                return@launch
            }

            val toggleR = radar.sosToggleBroadcast()
            Log.i(TAG, "[t=${System.currentTimeMillis()}]   ↳ sosToggleBroadcast ${toggleR.asWireString()}")
            broadcastStartedAtMs = System.currentTimeMillis()
        }
    }

    private suspend fun loadProfileRawJson(profileId: String): String? = withContext(Dispatchers.IO) {
        val dir = File(requireContext().getExternalFilesDir(null), PROFILES_DIR_NAME)
        val file = File(dir, "$profileId.json")
        if (!file.exists()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ profile file does not exist: ${file.absolutePath}")
            return@withContext null
        }
        try { file.readText() } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ profile read failed", e); null
        }
    }

    private fun configureBeaconFromRaw(rawJson: String): Boolean {
        val tNow = System.currentTimeMillis()
        val gpsJson = JSONObject(radar.sosGetCurrentLocationJson())
        val lat = gpsJson.optDouble("lat").takeUnless { it.isNaN() } ?: 0.0
        val lon = gpsJson.optDouble("lon").takeUnless { it.isNaN() } ?: 0.0
        val latE6 = (lat * 1e6).toInt()
        val lonE6 = (lon * 1e6).toInt()

        val displayName = try {
            val o = JSONObject(rawJson)
            val p = o.optJSONObject("p")
            val gn = p?.optString("gn", "")?.trim().orEmpty()
            val fn = p?.optString("fn", "")?.trim().orEmpty()
            listOf(gn, fn).filter { it.isNotBlank() }
                .joinToString(" ").take(16).ifBlank { "JEMMA" }
        } catch (e: Exception) {
            Log.w(TAG, "[t=$tNow] ⚠ name extraction failed: ${e.message}"); "JEMMA"
        }

        val langCode = Locale.getDefault().language.take(2)
        Log.i(TAG, "[t=$tNow] ⚙ configureBeacon · name='$displayName' lat=$lat lon=$lon " +
            "lang='$langCode' rawBytes=${rawJson.length}")
        val r = radar.sosConfigureBeacon(
            profileJson = rawJson,
            latE6 = latE6,
            lonE6 = lonE6,
            broadcasterName = displayName,
            criticality = CRIT_STABLE_DEFAULT,
            flags = 0,
            langCode = langCode,
        )
        Log.i(TAG, "[t=$tNow]   ↳ ${r.asWireString()}")
        return r is RadarController.Result.Ok
    }

    /**
     * 🆕 v2.5.12 — Cache the current profile's display name into
     * [myProfileName] for the composite toolbar title. Background coroutine,
     * one-shot at view creation.
     */
    private fun cacheProfileName() {
        viewLifecycleOwner.lifecycleScope.launch {
            profilesRepo.profilesFlow.collect { profiles ->
                val currentId = profilesRepo.currentProfileId
                val current = profiles.firstOrNull { it.id == currentId }
                val name = current?.displayName.orEmpty()
                if (name.isNotBlank() && name != myProfileName) {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🆔 profile name cached: '$name'")
                    myProfileName = name
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Polling tick (1Hz) — v2.5.12 unified
    // ──────────────────────────────────────────────────────────────────

    private fun tick() {
        val tNow = System.currentTimeMillis()
        val active = radar.sosIsActive()
        val broadcasting = radar.sosIsBroadcasting()

        // My own SALT status (assigned by rescuers via mesh)
        val mySaltCode = parseSaltCode(radar.triageGetEventForJson(mySid))

        // GPS
        val selfJson = JSONObject(radar.sosGetCurrentLocationJson())
        val selfLat = selfJson.optDouble("lat").takeUnless { it.isNaN() }
        val selfLon = selfJson.optDouble("lon").takeUnless { it.isNaN() }

        // Peers + rescuers
        val (peerVictims, peerDots) = parsePeers(radar.radarGetPeersJson(), selfLat, selfLon, tNow)
        val (rescuers, rescuerDots) = parseRescuers(radar.sosListRescuersJson(), selfLat, selfLon, tNow)

        // Mini-radar
        binding.sosRadarOverlay.setSnapshot(
            RadarOverlayView.Snapshot(
                selfLat = selfLat,
                selfLon = selfLon,
                selfHeadingDeg = compass.currentHeadingDeg,
                dots = peerDots + rescuerDots,
            ),
        )
        binding.sosRadarEmpty.visibility =
            if (peerDots.isEmpty() && rescuerDots.isEmpty()) View.VISIBLE else View.GONE

        // 🆕 v2.5.12 — Unified peer list : victims + rescuers as VictimCardAdapter.Victim
        // entries (rescuers have isRescuer=true so the adapter hides their SALT row and
        // paints a blue accent strip instead of a traffic light).
        val unifiedList = mutableListOf<VictimCardAdapter.Victim>()
        unifiedList.addAll(peerVictims)
        unifiedList.addAll(rescuers.map { it.toVictimRow() })
        peerAdapter.submit(unifiedList)

        // 🆕 v2.5.12 — Compose the toolbar title :
        //   "🆘 {name} · 09:06 · 👤 N · ⛑️ M"
        //   "⏸ {name} · 09:06 · 👤 N · ⛑️ M" (when paused)
        //   "◉ {name} (idle)"                (when not active yet)
        val saltPrefix = when {
            !active -> "◉"
            broadcasting -> {
                // If a rescuer has triaged me, surface their SALT emoji ahead of 🆘
                val spec = SaltUi.resolve(mySaltCode)
                spec?.emoji ?: "🆘"
            }
            else -> "⏸"
        }
        val timer = if (broadcastStartedAtMs > 0L) {
            val secs = (tNow - broadcastStartedAtMs) / 1000
            String.format("%02d:%02d", secs / 60, secs % 60)
        } else "--:--"
        val name = myProfileName.ifBlank { "JEMMA" }
        binding.sosToolbarBack.title = buildString {
            append(saltPrefix).append(' ').append(name)
            append(" · ").append(timer)
            append(" · 👤 ").append(peerVictims.size)
            append(" · ⛑️ ").append(rescuers.size)
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Parsers (unchanged from v2.5.6.3)
    // ──────────────────────────────────────────────────────────────────

    private fun parsePeers(
        json: String,
        selfLat: Double?,
        selfLon: Double?,
        tNow: Long,
    ): Pair<List<VictimCardAdapter.Victim>, List<RadarOverlayView.Dot>> {
        val arr = try { JSONArray(json) } catch (_: Exception) { return Pair(emptyList(), emptyList()) }
        val victims = mutableListOf<VictimCardAdapter.Victim>()
        val dots = mutableListOf<RadarOverlayView.Dot>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val sidFull = p.optString("sessionId", "????")
            val sid = sidFull.take(4)
            if (sidFull == mySid || sid == mySid.take(4)) continue
            val name = p.optString("name", "")
            val pLat = if (p.isNull("latitude")) null else p.optDouble("latitude").takeUnless { it.isNaN() }
            val pLon = if (p.isNull("longitude")) null else p.optDouble("longitude").takeUnless { it.isNaN() }
            val sex = p.optString("sex", "?").firstOrNull() ?: '?'
            val ageDecade = p.optInt("ageDecade", 0)
            val bloodType = p.optString("bloodType", "")
            val crit = p.optInt("criticality", 0)
            val rssi = p.optInt("rssi", 0)
            val chunksR = p.optInt("chunksReceived", 0)
            val chunksT = p.optInt("chunksTotal", 0)
            val complete = p.optBoolean("complete", false)
            val lastSeen = p.optLong("lastSeenMs", 0L)
            val nAllergies = p.optJSONArray("allergies")?.length() ?: 0
            val nMeds = p.optJSONArray("medications")?.length() ?: 0
            val langCode = p.optString("langCode", "")
            val isStale = (tNow - lastSeen) > 30_000L

            val distance = gpsDistanceMeters(selfLat, selfLon, pLat, pLon)
            val bearing = gpsBearingDeg(selfLat, selfLon, pLat, pLon)
            val salt = parseSaltCode(radar.triageGetEventForJson(sidFull))

            dots += RadarOverlayView.Dot(
                sessionIdHex = sid,
                name = name,
                distanceM = distance,
                bearingDeg = bearing,
                criticality = crit,
                isStale = isStale,
                isRescuer = false,
                saltCode = salt,
            )
            victims += VictimCardAdapter.Victim(
                sessionIdHex = sid,
                name = name,
                sex = sex,
                ageDecade = ageDecade,
                bloodType = bloodType,
                criticality = crit,
                rssi = rssi,
                chunksReceived = chunksR,
                chunksTotal = chunksT,
                complete = complete,
                distanceM = distance,
                bearingDeg = bearing,
                hasGps = pLat != null && pLon != null,
                nAllergies = nAllergies,
                nMedications = nMeds,
                langCode = langCode,
                lastSeenAgoMs = if (lastSeen > 0) tNow - lastSeen else null,
                isStale = isStale,
                saltCode = salt,
                isRescuer = false,
            )
        }
        return Pair(victims, dots)
    }

    private fun parseRescuers(
        json: String,
        selfLat: Double?,
        selfLon: Double?,
        tNow: Long,
    ): Pair<List<VictimCardAdapter.Victim>, List<RadarOverlayView.Dot>> {
        val arr = try { JSONArray(json) } catch (_: Exception) { return Pair(emptyList(), emptyList()) }
        val rescuers = mutableListOf<VictimCardAdapter.Victim>()
        val dots = mutableListOf<RadarOverlayView.Dot>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val sidFull = r.optString("sessionId", "????")
            val sid = sidFull.take(4)
            val name = r.optString("name", "")
            val langCode = r.optString("langCode", "")
            val rLat = if (r.isNull("latitude")) null else r.optDouble("latitude").takeUnless { it.isNaN() }
            val rLon = if (r.isNull("longitude")) null else r.optDouble("longitude").takeUnless { it.isNaN() }
            val lastSeen = r.optLong("lastSeenMs", 0L)
            val isStale = r.optBoolean("isStale", false) ||
                (lastSeen > 0 && tNow - lastSeen > 30_000L)

            val distance = gpsDistanceMeters(selfLat, selfLon, rLat, rLon)
            val bearing = gpsBearingDeg(selfLat, selfLon, rLat, rLon)
            dots += RadarOverlayView.Dot(
                sessionIdHex = sid,
                name = name,
                distanceM = distance,
                bearingDeg = bearing,
                criticality = 0,
                isStale = isStale,
                isRescuer = true,
                saltCode = null,
            )
            rescuers += VictimCardAdapter.Victim(
                sessionIdHex = sid,
                name = name,
                sex = '?',
                ageDecade = 0,
                bloodType = "",
                criticality = 0,
                rssi = 0,
                chunksReceived = 0,
                chunksTotal = 0,
                complete = false,
                distanceM = distance,
                bearingDeg = bearing,
                hasGps = rLat != null && rLon != null,
                nAllergies = 0,
                nMedications = 0,
                langCode = langCode,
                lastSeenAgoMs = if (lastSeen > 0) tNow - lastSeen else null,
                isStale = isStale,
                saltCode = null,
                isRescuer = true,
            )
        }
        return Pair(rescuers, dots)
    }

    /** No-op kept for legacy callers — v2.5.12 maps rescuers directly to Victim rows. */
    private fun Any.toVictimRow(): VictimCardAdapter.Victim =
        this as VictimCardAdapter.Victim

    private fun parseSaltCode(json: String): String? {
        return try {
            val o = JSONObject(json)
            if (o.length() == 0) null
            else o.optString("status", "").ifBlank { null }
        } catch (_: Exception) { null }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Geo helpers (unchanged)
    // ──────────────────────────────────────────────────────────────────

    private fun gpsDistanceMeters(
        lat1: Double?, lon1: Double?, lat2: Double?, lon2: Double?
    ): Double? {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return null
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun gpsBearingDeg(
        lat1: Double?, lon1: Double?, lat2: Double?, lon2: Double?
    ): Double? {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return null
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δλ = Math.toRadians(lon2 - lon1)
        val y = sin(Δλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
        val θ = atan2(y, x)
        return (Math.toDegrees(θ) + 360.0) % 360.0
    }
}
