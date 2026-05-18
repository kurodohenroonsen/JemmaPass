/*
 * RadarFragment.kt — JEMMA Pass · JemmaAppDemo · L5 v2.5.6
 *
 * Rescuer-mode radar screen. Lifecycle :
 *   onResume  → radarStart() if not active
 *   tick 1Hz  → poll radarGetPeersJson + sosGetCurrentLocationJson,
 *               project each peer onto bearing+distance, look up its
 *               current SALT status via triageGetEventForJson(sid),
 *               push Snapshot to RadarOverlayView, refresh victim list.
 *   onPause   → stop ticking (radar service keeps running in foreground)
 *   STOP RADAR → radarStop() + navigateUp
 *   tap victim card → TriageSaltBottomSheet
 *
 * Log channel : JEMMA-RADAR + JEMMA-TRIAGE-UI
 */
package be.heyman.android.jemmapassdemo.ui.radar

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.compass.JemmaCompass
import be.heyman.android.jemmapassdemo.databinding.FragmentRadarBinding
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

@AndroidEntryPoint
class RadarFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-RADAR"
        private const val TICK_MS = 1000L
    }

    @Inject lateinit var radar: RadarController
    @Inject lateinit var profilesRepo: ProfilesRepository

    private var _binding: FragmentRadarBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var adapter: VictimCardAdapter

    // 🆕 v2.5.12 — Rescuer's display name, resolved once at start and
    // reused in the toolbar title (composite "⛑️ {name} · 👤N · ⛑️M").
    private var myRescuerName: String = ""

    // 🆕 v2.5.6.3 — compass for the radar's N/S/E/W cardinal markers.
    // Previously selfHeadingDeg was hardcoded null → the cardinal labels
    // were frozen no matter how the phone was rotated.
    private val compass: JemmaCompass by lazy { JemmaCompass(requireContext()) }

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
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView (RadarFragment v2.5.6.3)")
        _binding = FragmentRadarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.radarToolbarBack.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 back tap")
            findNavController().navigateUp()
        }

        // 🆕 v2.5.11 — Tap a victim card → open PatientDetailFragment over
        // the radar (Plan A parity : "patient sheet" with hero + 4 vitals
        // + allergies/meds/conditions/immunizations). The inline SALT
        // buttons handle triage directly, so the bottom sheet is no longer
        // needed on tap. The action passes the 4-char SID as nav arg.
        adapter = VictimCardAdapter(
            onClick = { victim ->
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 👆 victim card tap · sid=${victim.sessionIdHex} " +
                        "→ open PatientDetailFragment",
                )
                try {
                    val args = Bundle().apply { putString("peerSid", victim.sessionIdHex) }
                    findNavController().navigate(R.id.action_radar_to_patient, args)
                } catch (e: Exception) {
                    Log.e(TAG, "[t=${System.currentTimeMillis()}] ⚠ patient detail navigate threw", e)
                }
            },
            // Inline SALT buttons publish directly through RadarController.
            onSalt = { victim, wireCode, isExplicitOverride ->
                val mySid = JemmaDeviceId.get(requireContext())
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🚑 inline-publish · " +
                        "victim=${victim.sessionIdHex} status=$wireCode " +
                        "rescuer=$mySid override=$isExplicitOverride",
                )
                val wire = radar.triagePublishEvent(
                    victimSid = victim.sessionIdHex,
                    statusCode = wireCode,
                    rescuerSid = mySid,
                    isExplicitOverride = isExplicitOverride,
                )
                Log.i(TAG, "[t=${System.currentTimeMillis()}]   ↳ $wire")
            },
        )
        binding.radarVictimList.layoutManager = LinearLayoutManager(requireContext())
        binding.radarVictimList.adapter = adapter

        // 🆕 v2.5.10 — SCAN QR button : opens the dedicated rescue
        // scanner that injects the decoded profile into the mesh
        // broadcast (RadarController.injectExternalVictim).
        binding.radarBtnScanQr.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 SCAN QR tap → navigate to scanner")
            try {
                findNavController().navigate(R.id.action_radar_to_rescue_qr_scan)
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ⚠ scan QR navigate threw", e)
            }
        }

        binding.radarBtnStop.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 STOP RADAR tap")
            val r = radar.radarStop()
            Log.i(TAG, "[t=${System.currentTimeMillis()}]   ↳ ${r.asWireString()}")
            findNavController().navigateUp()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!radar.radarIsActive()) {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🟢 radar not active · starting (async profile lookup)")
            // 🆕 v2.5.6.2 — pass profile name + langCode so the rescuer beacon
            // actually advertises with a real identity (not "JEMMA" fallback).
            // The profile is read off-thread; the startup itself is fast once
            // we have the name. If no profile exists yet, fallback to "JEMMA".
            viewLifecycleOwner.lifecycleScope.launch {
                val (rescuerName, langCode) = resolveRescuerIdentity()
                Log.i(TAG, "[t=${System.currentTimeMillis()}]   · resolved name='$rescuerName' lang='$langCode'")
                myRescuerName = rescuerName  // 🆕 v2.5.12 — cache for toolbar title
                val r = radar.radarStart(name = rescuerName, langCode = langCode)
                Log.i(TAG, "[t=${System.currentTimeMillis()}]   ↳ radarStart ${r.asWireString()}")
            }
        } else {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🟢 radar already active · resuming polling")
            // 🆕 v2.5.12 — also re-resolve the name on resume if we lost it
            // (config change, process restart, etc.)
            if (myRescuerName.isBlank()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val (name, _) = resolveRescuerIdentity()
                    myRescuerName = name
                }
            }
        }
        compass.start()  // 🆕 v2.5.6.3 — wake the magnetometer/gyro fusion
        handler.post(tickRunnable)
    }

    /**
     * Read the current profile's display name + language from disk and
     * surface them for the rescuer beacon. Falls back to "JEMMA" / system
     * locale when no profile is selected.
     *
     * The JSON read is identical to what SosBroadcastFragment does — we
     * deliberately avoid Moshi reflection (root cause of the 3-livraison
     * regression in v2.5.5/v2.5.5.2).
     */
    private suspend fun resolveRescuerIdentity(): Pair<String, String> = withContext(Dispatchers.IO) {
        val systemLang = Locale.getDefault().language.take(2).ifBlank { "en" }
        val currentId = profilesRepo.currentProfileId
        if (currentId.isNullOrBlank()) {
            return@withContext Pair("JEMMA", systemLang)
        }
        val file = File(requireContext().getExternalFilesDir(null), "profiles/$currentId.json")
        if (!file.exists()) return@withContext Pair("JEMMA", systemLang)
        try {
            val o = JSONObject(file.readText())
            val p = o.optJSONObject("p")
            val gn = p?.optString("gn", "")?.trim().orEmpty()
            val fn = p?.optString("fn", "")?.trim().orEmpty()
            val name = listOf(gn, fn).filter { it.isNotBlank() }
                .joinToString(" ").take(16).ifBlank { "JEMMA" }
            val lng = p?.optString("lng", "")?.trim()?.lowercase().orEmpty().take(2)
            val lang = lng.ifBlank { systemLang }
            Pair(name, lang)
        } catch (e: Exception) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ profile parse failed for identity: ${e.message}")
            Pair("JEMMA", systemLang)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
        compass.stop()  // 🆕 v2.5.6.3 — release the sensor to save battery
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ──────────────────────────────────────────────────────────────────
    //  Polling tick
    // ──────────────────────────────────────────────────────────────────

    private fun tick() {
        val tStart = System.currentTimeMillis()

        // 1. self GPS (keys are "lat","lon" — not "latitude","longitude")
        val selfJson = JSONObject(radar.sosGetCurrentLocationJson())
        val selfLat = selfJson.optDouble("lat").takeUnless { it.isNaN() }
        val selfLon = selfJson.optDouble("lon").takeUnless { it.isNaN() }

        // 2. peers + their current SALT status (from StatusResolver via mesh events)
        val peersJson = JSONArray(radar.radarGetPeersJson())
        val peers = mutableListOf<VictimCardAdapter.Victim>()
        val dots = mutableListOf<RadarOverlayView.Dot>()

        for (i in 0 until peersJson.length()) {
            val p = peersJson.getJSONObject(i)
            val sidFull   = p.optString("sessionId", "????")
            val sid       = sidFull.take(4)
            val name      = p.optString("name", "")
            val pLat      = if (p.isNull("latitude")) null
                            else p.optDouble("latitude").takeUnless { it.isNaN() }
            val pLon      = if (p.isNull("longitude")) null
                            else p.optDouble("longitude").takeUnless { it.isNaN() }
            val sex       = p.optString("sex", "?").firstOrNull() ?: '?'
            val ageDecade = p.optInt("ageDecade", 0)
            val bloodType = p.optString("bloodType", "")
            val crit      = p.optInt("criticality", 0)
            val rssi      = p.optInt("rssi", 0)
            val chunksR   = p.optInt("chunksReceived", 0)
            val chunksT   = p.optInt("chunksTotal", 0)
            val complete  = p.optBoolean("complete", false)
            val firstSeen = p.optLong("firstSeenMs", 0L)
            val lastSeen  = p.optLong("lastSeenMs", 0L)
            val nAllergies = p.optJSONArray("allergies")?.length() ?: 0
            val nMeds      = p.optJSONArray("medications")?.length() ?: 0
            val langCode   = p.optString("langCode", "")
            val isStale    = (tStart - lastSeen) > 30_000L

            val distance = gpsDistanceMeters(selfLat, selfLon, pLat, pLon)
            val bearing  = gpsBearingDeg(selfLat, selfLon, pLat, pLon)

            // Look up current SALT status for this victim (full sid, not truncated)
            val saltCode = parseSaltCode(radar.triageGetEventForJson(sidFull))

            dots += RadarOverlayView.Dot(
                sessionIdHex = sid,
                name         = name,
                distanceM    = distance,
                bearingDeg   = bearing,
                criticality  = crit,
                isStale      = isStale,
                isRescuer    = false,
                saltCode     = saltCode,
            )
            peers += VictimCardAdapter.Victim(
                sessionIdHex = sid,
                name         = name,
                sex          = sex,
                ageDecade    = ageDecade,
                bloodType    = bloodType,
                criticality  = crit,
                rssi         = rssi,
                chunksReceived = chunksR,
                chunksTotal  = chunksT,
                complete     = complete,
                distanceM    = distance,
                bearingDeg   = bearing,
                hasGps       = pLat != null && pLon != null,
                nAllergies   = nAllergies,
                nMedications = nMeds,
                langCode     = langCode,
                lastSeenAgoMs = if (lastSeen > 0) tStart - lastSeen else null,
                isStale      = isStale,
                saltCode     = saltCode,
            )
        }

        // 3. push snapshot to RadarOverlayView
        //    🆕 v2.5.6.3 — selfHeadingDeg now wired to JemmaCompass (was null
        //    pre-2.5.6.3, which is why the cardinal markers froze when the
        //    user rotated the phone).
        binding.radarOverlay.setSnapshot(
            RadarOverlayView.Snapshot(
                selfLat = selfLat,
                selfLon = selfLon,
                selfHeadingDeg = compass.currentHeadingDeg,
                dots = dots,
            ),
        )

        // 4. update list + 🆕 v2.5.12 toolbar title composite
        adapter.submit(peers.sortedWith(victimSortOrder()))

        // 🆕 v2.5.12 — Count rescuers detected around us via sosListRescuersJson.
        // The user himself is the rescuer ; this counts OTHER rescuers in mesh
        // range (could be 0). All shown compactly in the toolbar title :
        //   "⛑️ Kamekichi · 👤 2 · ⛑️ 1"   ← 2 victims + 1 other rescuer detected
        val rescuersJson = try { JSONArray(radar.sosListRescuersJson()) } catch (_: Exception) { JSONArray() }
        val rescuersDetected = rescuersJson.length()
        val name = myRescuerName.ifBlank { "JEMMA" }
        binding.radarToolbarBack.title = buildString {
            append("⛑️ ").append(name)
            append(" · 👤 ").append(peers.size)
            append(" · ⛑️ ").append(rescuersDetected)
        }

        binding.radarEmptyState.visibility = if (peers.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Parse the JSON returned by triageGetEventForJson into a SALT wire code, or null. */
    private fun parseSaltCode(json: String): String? {
        return try {
            val o = JSONObject(json)
            if (o.length() == 0) null
            // 🐛 v2.5.11 fix — StatusEvent.toMap() emits the key "status",
            //                  NOT "statusCode". The earlier reader was
            //                  always returning null, which is why every
            //                  victim card stayed "INCONNU" even after the
            //                  user tapped an inline SALT button and the
            //                  publish/relay/mesh flow logged "STAB applied".
            else o.optString("status", "").ifBlank { null }
        } catch (_: Exception) { null }
    }

    /**
     * Sort victims by SALT priority first (HELP > EVAC > EVAL > WAIT > STAB > DCD > none),
     * then by distance ascending.
     */
    private fun victimSortOrder(): Comparator<VictimCardAdapter.Victim> =
        compareBy<VictimCardAdapter.Victim> {
            saltPriority(it.saltCode, it.criticality)
        }.thenBy {
            it.distanceM ?: Double.MAX_VALUE
        }

    /** Lower number = higher visual priority on the list. */
    private fun saltPriority(salt: String?, crit: Int): Int = when (salt?.uppercase()?.trim()) {
        "HELP" -> 0
        "EVAC" -> 1
        "EVAL" -> 2
        "WAIT" -> 3
        "STAB" -> 4
        "DCD"  -> 5
        else   -> 6 - crit  // unrated victims sorted by criticality descending
    }
}
