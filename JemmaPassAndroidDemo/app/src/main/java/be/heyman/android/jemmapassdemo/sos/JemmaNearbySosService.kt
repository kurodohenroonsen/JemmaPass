package be.heyman.android.jemmapassdemo.sos

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import be.heyman.android.jemmapassdemo.mesh.codec.MeshByteSafety
import be.heyman.android.jemmapassdemo.mesh.relay.RelayManager
import be.heyman.android.jemmapassdemo.triage.SaltCode
import be.heyman.android.jemmapassdemo.triage.StatusEvent
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Strategy

/**
 * 🆕 L44.16.23 — JemmaNearbySosService
 *
 * Replaces the previous BLE-direct implementation
 * (JemmaSosBleAdvertiser + JemmaSosBleScanner + JemmaRescuerBroadcaster)
 * with Google Nearby Connections — exactly the same architecture
 * proven to work reliably in our sister app `kikko` (ClashArenaService).
 *
 * ── Why this rewrite? ─────────────────────────────────────────
 * Tested on Pixel 9 Pro XL + Samsung S22, the BLE direct approach
 * had peers that never discovered each other despite both scan
 * and advertise running successfully. Nearby Connections has
 * battle-tested discovery across all OEMs.
 *
 * ── Architecture: pure broadcast (NO connections) ─────────────
 * We use Nearby Connections in "broadcast mode" by exploiting
 * the fact that all the data we need fits in the `endpointName`
 * field (max 131 chars). Discovery callbacks fire `onEndpointFound`
 * with the parsed endpoint info — no need to actually connect.
 *
 * If a peer's connection is initiated, we immediately reject it
 * via `rejectConnection()` so no UI dialog ever appears.
 *
 * ── Multi-chunk rotation for victim profiles ──────────────────
 * Victim profiles can be larger than 131 chars when there are
 * many medication / allergy codes. We rotate the broadcast
 * endpointName every CHUNK_ROTATION_MS:
 *
 *   t=0    : V|sid|1|5|<header>
 *   t=1500 : V|sid|2|5|<allergies>
 *   t=3000 : V|sid|3|5|<meds>
 *   t=4500 : V|sid|4|5|<conditions>
 *   t=6000 : V|sid|5|5|<immunizations>
 *   t=7500 : retour à 1/5
 *
 * The receiver assembles them into a PeerSession indexed by sid.
 * Lost chunks are no problem — they'll arrive on the next cycle.
 *
 * ── Persistence after peer disappears ─────────────────────────
 * We do NOT call `onPeerLost` when Nearby reports an endpoint
 * lost. Instead we mark the session as "stale" (markStaleAt)
 * and notify the listener via `onPeerStale`. The peer stays in
 * the UI with its last known position — this is critical so the
 * rescuer can still navigate to a victim whose phone died.
 */
class JemmaNearbySosService(private val context: Context) {

    companion object {
        private const val TAG = "JEMMA-NEARBY"

        // Service ID = unique identifier for our app's Nearby presence.
        // Other JEMMA installations will discover each other when both
        // advertise+discover with this exact string.
        const val SERVICE_ID = "be.heyman.android.jemmapassdemo.sos.SOS"

        // We use P2P_CLUSTER (mesh-style, M:N) instead of P2P_STAR.
        // STAR has a 1-center + N-leaves topology which would block
        // multiple victims being seen by multiple rescuers simultaneously.
        // CLUSTER allows everyone to see everyone — exactly what we need.
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER

        // Rotation period for victim chunks (ms). Tuned for fast
        // assembly but slow enough that Nearby reliably picks each one
        // up. 1500ms = 5-chunk profile fully captured in 7.5s typical.
        const val CHUNK_ROTATION_MS = 1500L
        // 🆕 L44.16.61 — Quiet gap between stopAdvertising and the
        // next startAdvertising. Lets GMS Nearby reset the endpoint
        // cache so the discoverer side actually sees the new chunk.
        // 250ms determined empirically as the sweet spot on Pixel 9
        // Pro XL / Android 16. Lower values = same bug as before
        // (chunks dropped). Higher = less throughput.
        const val CHUNK_REPUBLISH_GAP_MS = 250L

        // 🆕 L44.16.78 — Periodic eviction of stale rotation cache
        // entries. Every 30s we sweep the cache and remove relayed
        // entries past their TTL (60s for VR|/SR|, 180s for E|).
        // Own V|/S| chunks are never evicted.
        const val ROTATION_EVICT_PERIOD_MS = 30_000L

        // After this delay without seeing an endpoint, mark its session
        // as STALE (but keep it in the UI with last-known data).
        const val STALE_DELAY_MS = 60_000L

        // Internal evaluation interval for stale detection
        const val STALE_CHECK_INTERVAL_MS = 5_000L
    }

    /** Listener interface compatible with the previous BLE pipeline. */
    interface Listener {
        /** Fired when a victim is first seen (header chunk received). */
        fun onPeerFound(peer: PeerInfo) {}
        /** Fired when more chunks arrive (allergies/meds added). */
        fun onPeerUpdate(peer: PeerInfo) {}
        /** Fired when the peer's all chunks are received. */
        fun onPeerComplete(peer: PeerInfo) {}
        /**
         * Fired when a peer hasn't been seen for STALE_DELAY_MS.
         * Note: we do NOT remove the peer here — caller's UI keeps
         * showing it with isStale=true so rescuer can still see the
         * last known position.
         */
        fun onPeerStale(sessionIdHex: String) {}

        /** Equivalent for rescuer beacons (single-chunk, no assembly). */
        fun onRescuerFound(rescuer: RescuerInfo) {}
        fun onRescuerUpdate(rescuer: RescuerInfo) {}
        fun onRescuerStale(sessionIdHex: String) {}

        /**
         * 🆕 L44.16.76 — Fired when a triage event (E| chunk) is applied
         * to a victim's state. The radar UI uses this to update the
         * SALT badge and the bottom sheet.
         *
         * Fired only when the StatusResolver decided to overwrite the
         * existing state — no spurious notifications for stale duplicates.
         */
        fun onTriageEvent(event: StatusEvent) {}
    }

    private val connectionsClient: ConnectionsClient =
        Nearby.getConnectionsClient(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var listener: Listener? = null
    fun setListener(l: Listener?) { listener = l }

    // 🆕 L44.16.23 — Additional listeners (multi-listener pattern).
    // The primary `listener` (set via setListener) is reserved for the
    // current UI surface (overlay or bridge). `extraListeners` is for
    // permanent observers like JemmaRadarBridge that needs to keep
    // pushing TMP victim profiles to JS regardless of overlay state.
    private val extraListeners = java.util.concurrent.CopyOnWriteArrayList<Listener>()
    /**
     * 🆕 L44.16.27 — Make addListener idempotent so callers can call it
     * multiple times (e.g. JemmaRadarBridge.start() ensuring listener
     * is wired after a recreateFresh). Without this, repeated calls
     * accumulate duplicate listeners and onPeerComplete fires N times.
     */
    fun addListener(l: Listener) {
        if (!extraListeners.contains(l)) {
            extraListeners.add(l)
            Log.d(TAG, "addListener: registered new listener (total=${extraListeners.size})")
        } else {
            Log.d(TAG, "addListener: already registered, skipping (total=${extraListeners.size})")
        }
    }
    fun removeListener(l: Listener) { extraListeners.remove(l) }

    /**
     * 🆕 L44.16.32 — Snapshot of current listeners for migration during
     * recreateFresh(). The radar overlay state needs to copy the
     * listener list from the OLD instance to the NEW one so events
     * keep being dispatched to the JS bridge after a Nearby instance
     * swap.
     */
    fun getExtraListenersSnapshot(): List<Listener> = extraListeners.toList()
    fun getPrimaryListener(): Listener? = listener

    /** Dispatch helper: invoke a callback on all listeners (primary + extras). */
    private fun dispatch(action: (Listener) -> Unit) {
        listener?.let { try { action(it) } catch (e: Exception) { Log.w(TAG, "listener threw", e) } }
        for (l in extraListeners) {
            try { action(l) } catch (e: Exception) { Log.w(TAG, "extra listener threw", e) }
        }
    }

    /**
     * 🆕 L44.16.46 — Inject a synthetic peer locally for the OWN radar.
     *
     * Why this exists : Nearby Connections does not loopback. When this
     * device advertises a victim (e.g. Haru after a paper-badge QR scan),
     * other devices receive `onPeerFound` for Haru, but THIS device never
     * does — Nearby's framework does not invoke its own discovery
     * callbacks for its own advertise.
     *
     * Without this method, the rescuer who scans a paper badge sees the
     * victim re-broadcast successfully on the mesh but her dot never
     * appears on their own radar — confusing UX.
     *
     * This method bypasses the usual Nearby ingestion (chunk → reassembly
     * → dispatch) and goes straight to dispatch with a pre-built
     * `PeerInfo`. It also registers the peer in `peerSessions` so any
     * subsequent stale-check / list-snapshot logic treats it like any
     * other peer (including being correctly cleared on `recreateFresh()`).
     *
     * The caller should set `flags = 0x40` to mark the peer as
     * "synthetic / scanned-locally" so listeners that care can apply
     * different UI treatment (e.g. a 📷 badge instead of 📡).
     *
     * Both `onPeerFound` and `onPeerComplete` are dispatched on the main
     * thread, identical to a real peer. `firstSeenMs` and `lastSeenMs`
     * are taken from `peer` as-is.
     *
     * Idempotent : if a peer with the same `sessionIdHex` is already
     * tracked, the existing session is replaced and `onPeerUpdate` fires
     * (instead of `onPeerFound`) so listeners can re-render without a
     * fresh-discovery animation.
     */
    fun injectSyntheticPeer(peer: PeerInfo) {
        Log.i(TAG, "➕ injectSyntheticPeer(sid=${peer.sessionIdHex} name='${peer.broadcasterName}' flags=0x${"%02x".format(peer.flags)})")
        val isUpdate: Boolean
        synchronized(peerSessions) {
            isUpdate = peerSessions.containsKey(peer.sessionIdHex)
            // Build a backing PeerSessionState so getPeerSnapshot()
            // continues to find this peer after the dispatch returns.
            val state = PeerSessionState(peer.sessionIdHex, peer.firstSeenMs).apply {
                lastSeenMs = peer.lastSeenMs
                hasHeader = true
                name = peer.broadcasterName
                sex = peer.sex
                age = peer.ageExact
                bloodType = peer.bloodType
                criticality = peer.criticality
                lat = peer.latitude ?: 0.0
                lon = peer.longitude ?: 0.0
                chunkTotal = peer.chunksTotal
                langCode = peer.langCode
                flags = peer.flags         // 🆕 L44.16.49 — preserve 0x40 synthetic marker
                completeDispatched = true       // synthetic peer is "complete" by construction
                sectionsSeen.addAll(setOf('H', 'A', 'M', 'C', 'I'))
                allergies.addAll(peer.allergyCodes.map { it.code })
                meds.addAll(peer.medCodes.map { it.code })
                conditions.addAll(peer.conditionCodes.map { it.code })
                immun.addAll(peer.immunCodes.map { it.code })
            }
            peerSessions[peer.sessionIdHex] = state
        }
        Log.d(TAG, "  → tracked in peerSessions (${if (isUpdate) "update" else "new"})")
        mainHandler.post {
            if (isUpdate) {
                dispatch { it.onPeerUpdate(peer) }
            } else {
                dispatch { it.onPeerFound(peer) }
            }
            // Always also fire onPeerComplete since synthetic peers
            // are fully-formed by construction (not an in-progress
            // multi-chunk reassembly).
            dispatch { it.onPeerComplete(peer) }
        }
    }

    // ─── State for OUR advertise side ─────────────────────────
    @Volatile private var isAdvertising: Boolean = false
    // 🆕 L44.16.78 — Map-based rotation cache (replaces flat List<String>).
    //
    // Why : the dry-run logs (logs_fr_2b/4b at 06:56 May 8) revealed
    // that 4 E| events were advertised but Phone A received 0 — they
    // were drowned in 142 V|/S|/VR| chunks at ~5% capture probability.
    // The Map keyed by `${type}|<sid>|<seq?>` deduplicates payload
    // updates (Haru moved → same slot, refreshed payload) and lets
    // a priority scheduler boost E| events 3× per cycle.
    //
    // Council R2 spec (5/5 IA validated):
    //   • V|/S| (own)         → never evicted
    //   • VR|/SR| (relayed)   → 60s TTL
    //   • E| (events)         → 180s TTL (Pierre-Paul-Jacques scenario)
    //   • E| put via StatusResolver.merge (compute), not raw overwrite
    private val rotationCache = be.heyman.android.jemmapassdemo.mesh.relay.RotationCache()

    // 🆕 L44.16.78 — Round-robin cursor over rotationCache.snapshot().
    // Replaces `currentChunkIdx` int. We keep a key here so that if
    // the cache is mutated between ticks (new peer chunk arrives),
    // we don't lose our place — we resume from the next entry.
    @Volatile private var lastBroadcastKey: String? = null
    @Volatile private var advertiseRole: String = ""  // "victim" | "rescuer"

    // ─── State for OUR discover side ──────────────────────────
    @Volatile private var isDiscovering: Boolean = false

    // ─── Reassembly sessions (peers we've seen) ───────────────
    private val peerSessions = mutableMapOf<String, PeerSessionState>()
    private val rescuerSessions = mutableMapOf<String, RescuerSessionState>()

    // Map endpointId (Nearby's runtime id, changes each rotation) → sid
    // so we can correlate onEndpointLost back to the user-facing sid.
    private val endpointToSid = mutableMapOf<String, String>()

    // ─── 🆕 L44.16.76 — Mesh foundation wiring ────────────────────
    /**
     * The mesh orchestrator. Created lazily on first start so that
     * `mySid` can be set from the local user's session id. The same
     * instance is reused across start/stop cycles within a service
     * lifetime.
     */
    @Volatile private var relayManager: RelayManager? = null

    /**
     * Get-or-create the relay manager. The mySid for relay purposes is
     * the local user's session id (4 hex chars). For now we use a fixed
     * placeholder; this will be wired to the user profile in L44.16.77.
     */
    private fun ensureRelayManager(): RelayManager {
        return relayManager ?: synchronized(this) {
            relayManager ?: RelayManager(mySid = currentLocalSid()).also {
                relayManager = it
                it.setTriageListener { event ->
                    Log.i(TAG, "🩺 onTriageEvent dispatch: ${event.victimSid} → ${event.status.code} (hops=${event.hopCount})")
                    dispatch { listener -> listener.onTriageEvent(event) }
                }
            }
        }
    }

    /**
     * Local session id used for mesh self-loop guard. Derived from the
     * first own V|/S| chunk in the rotation cache. Falls back to a
     * synthetic id before any advertise has started.
     *
     * 🆕 L44.16.78 — refactored to read from RotationCache instead of
     * the old List<String>.
     */
    private fun currentLocalSid(): String {
        val ownChunk = rotationCache.snapshot()
            .firstOrNull { (_, entry) -> entry.isOwn }
            ?.second?.chunk
            ?: return "0000"
        // V|<sid>|... or S|<sid>|...
        val parts = ownChunk.split('|')
        return parts.getOrNull(1) ?: "0000"
    }

    /**
     * 🆕 L44.16.76 — Public API for the JS bridge to publish a triage
     * event (e.g. user tapped STAB on the radar UI).
     *
     * The event is :
     *   1. Encoded as an `E|` chunk
     *   2. Applied locally via StatusResolver (UI updates immediately)
     *   3. Marked in BloomDedup (so we don't re-relay our own echo)
     *   4. Returned as a wire string for the caller to broadcast
     *
     * @param victimSid the victim being annotated (4 hex chars)
     * @param statusCode SALT 4-char code: WAIT, EVAL, STAB, HELP, EVAC, DCD
     * @param rescuerSid the rescuer making the call (their local SID)
     * @param isExplicitOverride true if explicitly overriding a DCD lockdown
     * @return the wire-format chunk to broadcast, or null if status is unknown
     */
    fun publishTriageEvent(
        victimSid: String,
        statusCode: String,
        rescuerSid: String,
        isExplicitOverride: Boolean = false
    ): String? {
        val salt = SaltCode.parse(statusCode) ?: run {
            Log.w(TAG, "publishTriageEvent: unknown status code '$statusCode'")
            return null
        }
        val chunk = ensureRelayManager().publishEvent(
            victimSid = victimSid,
            status = salt,
            rescuerSid = rescuerSid,
            isExplicitOverride = isExplicitOverride
        )

        // 🆕 L44.16.78 — Insert into rotation cache. The Map-based store
        // keys this by victim SID alone, so a fresh HELP automatically
        // overwrites a previous STAB for the same patient (LWW). The
        // priority scheduler will broadcast E| events at 3× weight.
        rotationCache.put(chunk, isOwn = false)
        Log.i(TAG, "🩺 published event in rotationCache (size=${rotationCache.size()}): $chunk")
        return chunk
    }

    /**
     * 🆕 L44.16.76 — Snapshot of all current triage events for JS UI.
     * Returns Map<victimSid → StatusEvent>.
     */
    fun getAllTriageEvents(): Map<String, StatusEvent> {
        return relayManager?.getAllStatuses() ?: emptyMap()
    }

    /**
     * 🆕 L44.16.76 — Get current event for a single victim.
     */
    fun getTriageEventFor(victimSid: String): StatusEvent? {
        return relayManager?.getStatusFor(victimSid)
    }

    // ═══════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Start advertising as a VICTIM, broadcasting the full profile
     * via rotating chunks. The chunks list is computed here from
     * the inputs.
     *
     * Returns true on success, false if Nearby refused the request.
     */
    fun startAdvertisingVictim(
        sid: String,
        name: String,
        sex: Char,
        age: Int,
        bloodType: String,
        criticality: Int,
        lat: Double,
        lon: Double,
        allergyCodes: List<String>,
        medCodes: List<String>,
        conditionCodes: List<String>,
        immunCodes: List<String>,
        victimLang: String = ""        // 🆕 L44.16.48
    ): Boolean {
        Log.i(TAG, "▶ startAdvertisingVictim: sid=$sid name='$name' lang='$victimLang' " +
            "(${allergyCodes.size}A ${medCodes.size}M ${conditionCodes.size}C ${immunCodes.size}I)")

        val vChunks = buildList {
            add(JemmaNearbyEndpointCodec.encodeVictimHeader(
                sid, JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL,
                name, sex, age, bloodType, criticality, lat, lon, victimLang
            ))
            add(JemmaNearbyEndpointCodec.encodeVictimCodes(
                sid, JemmaNearbyEndpointCodec.VICTIM_CHUNK_ALLERGIES,
                JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL,
                'A', allergyCodes
            ))
            add(JemmaNearbyEndpointCodec.encodeVictimCodes(
                sid, JemmaNearbyEndpointCodec.VICTIM_CHUNK_MEDS,
                JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL,
                'M', medCodes
            ))
            add(JemmaNearbyEndpointCodec.encodeVictimCodes(
                sid, JemmaNearbyEndpointCodec.VICTIM_CHUNK_CONDITIONS,
                JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL,
                'C', conditionCodes
            ))
            add(JemmaNearbyEndpointCodec.encodeVictimCodes(
                sid, JemmaNearbyEndpointCodec.VICTIM_CHUNK_IMMUN,
                JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL,
                'I', immunCodes
            ))
        }
        vChunks.forEachIndexed { i, c ->
            Log.i(TAG, "  chunk ${i+1}/${vChunks.size} (${c.length} chars): $c")
        }

        // 🆕 L44.16.86 — Compute and add the profile fingerprint chunk.
        //
        // The F| chunk is a 24-byte hash of the 5 V| chunks. Receivers
        // that already have a matching profile in cache use this to
        // skip the V| rotation for this peer for 60s, freeing airtime
        // for events and other peers.
        //
        // We append it to the rotation list so it gets advertised at
        // ~10% of the rate (weight=1, vs weight=2 for own V|), which
        // works out to ~10s between F| broadcasts in a 6-chunk rotation.
        val fingerprint = be.heyman.android.jemmapassdemo.mesh.codec.FingerprintChunk.build(
            sid = sid,
            orderedVChunks = vChunks,
            lastModifiedTs = System.currentTimeMillis() / 1_000L
        )
        val fChunk = fingerprint.encode()
        Log.i(TAG, "  🎯 [F|] fingerprint chunk (${fChunk.length} chars): $fChunk")

        val chunks = vChunks + fChunk
        return startAdvertisingWithChunks(chunks, "victim")
    }

    /**
     * Start advertising as a RESCUER (single chunk, no rotation).
     *
     * 🆕 L44.16.87 — All v1.7+ rescuers automatically advertise the
     * `J` (junction snapshot) capability flag. This signals to Plan B's
     * SessionPromoter that this rescuer can serve a junction snapshot
     * over Wi-Fi Direct when a new peer arrives. Other capability bits
     * (H photo, A audio, F FHIR) will be wired in Plan B sprints B4+.
     */
    fun startAdvertisingRescuer(
        sid: String,
        name: String,
        langCode: String,
        lat: Double,
        lon: Double
    ): Boolean {
        Log.i(TAG, "▶ startAdvertisingRescuer: sid=$sid name='$name' lang=$langCode @$lat,$lon")
        // 🆕 L44.16.87 — Advertise junction-snapshot capability by default.
        // Cheap to advertise (just 3 wire bytes : "|08") and prepares the
        // mesh for Plan B sessions without requiring a v1.5+ flag day.
        val caps = be.heyman.android.jemmapassdemo.mesh.codec.CapFlags.of(servesJunction = true)
        val chunk = JemmaNearbyEndpointCodec.encodeRescuer(sid, name, langCode, lat, lon, caps)
        Log.i(TAG, "  rescuer chunk (${chunk.length} chars, caps=${caps.toHex()}): $chunk")
        return startAdvertisingWithChunks(listOf(chunk), "rescuer")
    }

    /**
     * 🆕 L44.16.47 — Advertise BOTH a rescuer beacon AND a victim
     * relay in the same rotation cycle.
     *
     * Background : Nearby Connections supports only ONE active
     * advertise at a time per service ID. Before this method, when
     * Kamekichi (the rescuer) scanned Haru's paper QR badge :
     *
     *   1. Kamekichi was advertising as rescuer → other rescuers
     *      could see "Kamekichi here, JA-speaking" on their radar.
     *   2. Scan Haru → call `startAdvertisingVictim(sid=Haru, ...)`
     *      → REPLACES the rescuer advertise with a 5-chunk victim
     *      relay → Kamekichi vanishes from other rescuers' radars.
     *      They now see "Haru here" but lose the rescuer info.
     *
     * Fix : interleave both roles in the rotation list. The rescuer
     * chunk is repeated at the start of each victim cycle so it gets
     * fair airtime :
     *
     *   [rescuer, victim_header, victim_allergies, victim_meds,
     *    victim_conditions, victim_immun]   → 6 chunks total
     *
     * At CHUNK_ROTATION_MS = 1500ms, a full cycle = 9s, well within
     * BLE advertise stability windows. Receivers parse each chunk
     * independently (they don't care about order — assembly is by
     * sid + chunk index in the preamble).
     *
     * Returns true on success, false if Nearby refused.
     */
    fun startAdvertisingMixed(
        rescuerSid: String,
        rescuerName: String,
        rescuerLangCode: String,
        rescuerLat: Double,
        rescuerLon: Double,
        victimSid: String,
        victimName: String,
        victimSex: Char,
        victimAge: Int,
        victimBloodType: String,
        victimCriticality: Int,
        victimLat: Double,
        victimLon: Double,
        allergyCodes: List<String>,
        medCodes: List<String>,
        conditionCodes: List<String>,
        immunCodes: List<String>,
        victimLang: String = ""        // 🆕 L44.16.48
    ): Boolean {
        Log.i(TAG, "▶▶ startAdvertisingMixed: rescuer sid=$rescuerSid name='$rescuerName' lang=$rescuerLangCode + victim sid=$victimSid name='$victimName' lang='$victimLang'")

        // 🆕 L44.16.87 — Rescuer side of mixed advertise also gets the
        // junction capability flag, just like pure-rescuer mode.
        val rescuerCaps = be.heyman.android.jemmapassdemo.mesh.codec.CapFlags.of(servesJunction = true)
        val rescuerChunk = JemmaNearbyEndpointCodec.encodeRescuer(
            rescuerSid, rescuerName, rescuerLangCode, rescuerLat, rescuerLon, rescuerCaps
        )

        val victimChunks = listOf(
            JemmaNearbyEndpointCodec.encodeVictimHeader(
                victimSid, JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL,
                victimName, victimSex, victimAge, victimBloodType,
                victimCriticality, victimLat, victimLon, victimLang
            ),
            JemmaNearbyEndpointCodec.encodeVictimCodes(
                victimSid, JemmaNearbyEndpointCodec.VICTIM_CHUNK_ALLERGIES,
                JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL,
                'A', allergyCodes
            ),
            JemmaNearbyEndpointCodec.encodeVictimCodes(
                victimSid, JemmaNearbyEndpointCodec.VICTIM_CHUNK_MEDS,
                JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL,
                'M', medCodes
            ),
            JemmaNearbyEndpointCodec.encodeVictimCodes(
                victimSid, JemmaNearbyEndpointCodec.VICTIM_CHUNK_CONDITIONS,
                JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL,
                'C', conditionCodes
            ),
            JemmaNearbyEndpointCodec.encodeVictimCodes(
                victimSid, JemmaNearbyEndpointCodec.VICTIM_CHUNK_IMMUN,
                JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL,
                'I', immunCodes
            )
        )

        // Compose the rotation list: rescuer first, then all 5 victim
        // chunks. Total cycle = 6 × CHUNK_ROTATION_MS.
        val mixed = listOf(rescuerChunk) + victimChunks
        Log.i(TAG, "  mixed rotation : ${mixed.size} chunks (1 rescuer + 5 victim)")
        mixed.forEachIndexed { i, c ->
            Log.i(TAG, "    [${i+1}/${mixed.size}] (${c.length} chars): $c")
        }

        return startAdvertisingWithChunks(mixed, "mixed")
    }

    private fun startAdvertisingWithChunks(chunks: List<String>, role: String): Boolean {
        if (chunks.isEmpty()) {
            Log.w(TAG, "startAdvertisingWithChunks: empty chunks list, ignoring")
            return false
        }
        // Stop existing rotation if any (but keep the relayed VR|/SR|/E|
        // entries — see clearOwn() below).
        if (isAdvertising) {
            Log.i(TAG, "  (already advertising — stopping previous before restart)")
            stopAdvertising()
        }

        // 🆕 L44.16.80 — Only clear OWN entries on restart, NOT relayed.
        // Why : when a rescuer does startAdvertisingMixed every time GPS
        // moves by 1m, the previous rotationCache.clear() was throwing
        // away all the VR|HW,H Kurodo entries that were just received.
        // Confirmed bug in dry-run L44.16.79 logs (Pixel rescuer
        // advertised 0 VR| despite cache reaching 11 entries).
        //
        // The own chunks are re-seeded fresh below (so payload refresh
        // works for GPS updates), and the relayed entries stay alive
        // until their TTL expires (60s for VR|/SR|, 180s for E|).
        val removedOwn = rotationCache.clearOwn()
        if (removedOwn.isNotEmpty()) {
            Log.v(TAG, "  ↳ restart: removed ${removedOwn.size} own entries (kept ${rotationCache.size()} relayed)")
        }
        // 🆕 L44.16.78 — Seed the rotation cache with our own chunks.
        // isOwn=true so the reaper never evicts them. Each chunk gets
        // its own slot keyed by type+sid+seq, so rebroadcasting the
        // same V|H**-|1 a hundred times doesn't pollute the map — it
        // refreshes the single slot.
        for (chunk in chunks) {
            rotationCache.put(chunk, isOwn = true)
        }
        lastBroadcastKey = null
        advertiseRole = role
        isAdvertising = true
        startCurrentChunk()

        // Start rotation runnable if we have multiple chunks
        if (chunks.size > 1) {
            mainHandler.postDelayed(rotationRunnable, CHUNK_ROTATION_MS)
        }
        // Schedule periodic eviction of stale relayed entries
        mainHandler.postDelayed(rotationEvictRunnable, ROTATION_EVICT_PERIOD_MS)
        return true
    }

    private val rotationRunnable = object : Runnable {
        override fun run() {
            if (!isAdvertising) return
            if (rotationCache.size() <= 1) {
                // Single chunk → no rotation needed, just keep advertising it
                mainHandler.postDelayed(this, CHUNK_ROTATION_MS)
                return
            }

            // 🆕 L44.16.85 — Adaptive duty cycle for VICTIM_SOS role.
            //
            // Half-duplex BLE on Samsung Exynos 990 (and most Android
            // controllers) cannot advertise + scan reliably at the same
            // time. Empirically the L44.16.84 dryrun showed Samsung's
            // capture rate drop to 15.6% precisely because it was
            // advertising 100% of the time.
            //
            // Insert a 3-second scan-only pause every 10 seconds when
            // we're in victim or mixed mode. The 7s of advertising in
            // each window is more than enough for rescuers to catch us,
            // and the 3s scan window lets us receive triage events
            // (HELP/STAB/EVAC/DCD) and cross-victim broadcasts.
            //
            // See RotationCache.shouldPauseForScan() for full rationale.
            val now = System.currentTimeMillis()
            if (rotationCache.shouldPauseForScan(advertiseRole, now)) {
                Log.d(TAG, "  🔇 [DUTY-CYCLE] Pausing advertise for scan window " +
                    "(role=$advertiseRole, slot=${(now / 1_000L) % 10L})")
                connectionsClient.stopAdvertising()
                // Re-check in 1s; the pause will release naturally when
                // we cross back into the advertise side of the window.
                mainHandler.postDelayed(this, 1_000L)
                return
            }

            // 🆕 L44.16.61 — Insert a 250ms gap between stopAdvertising
            // and startAdvertising so GMS Nearby on the discoverer side
            // has a chance to actually invalidate its endpoint-name cache
            // and propagate the new chunk.
            //
            // Why : on Pixel 9 Pro XL (Android 16), tearing down +
            // re-publishing instantly meant GMS only ever surfaced the
            // FIRST chunk to onEndpointFound — the discoverer never saw
            // chunks 2-6 (V|RegT|...|Haru|...). Samsung S20 FE / Android 13
            // didn't have this throttle so it picked up all 6 chunks
            // (we confirmed in logs_fr_2b.txt). The 250ms breathing
            // room costs us 1.5s extra per full rotation cycle (6 chunks,
            // so 6 × 250ms = 1.5s) — acceptable for our use case.
            connectionsClient.stopAdvertising()
            mainHandler.postDelayed({
                if (isAdvertising) startCurrentChunk()
            }, CHUNK_REPUBLISH_GAP_MS)

            mainHandler.postDelayed(this, CHUNK_ROTATION_MS)
        }
    }

    /**
     * 🆕 L44.16.78 — Periodic eviction of stale relayed entries.
     *
     * Runs every ROTATION_EVICT_PERIOD_MS (default 30s). Removes
     * VR|/SR| chunks older than 60s and E| chunks older than 180s.
     * Own V|/S| chunks are never evicted.
     */
    private val rotationEvictRunnable = object : Runnable {
        override fun run() {
            if (!isAdvertising) return
            val evicted = rotationCache.evictOld()
            if (evicted.isNotEmpty()) {
                Log.i(TAG, "🧹 rotationCache evicted ${evicted.size} stale entries " +
                    "(remaining=${rotationCache.size()})")
                for ((key, _) in evicted) {
                    Log.v(TAG, "    ↳ evicted: $key")
                }
            }
            mainHandler.postDelayed(this, ROTATION_EVICT_PERIOD_MS)
        }
    }

    private fun startCurrentChunk() {
        // 🆕 L44.16.78 — Pick the next chunk from the priority-aware
        // RotationCache instead of an int index over a flat List.
        // The cache returns the highest-priority slot (events boosted 3×,
        // own V|/S| at full priority, fresh relays normal, cold relays
        // demoted) with ties broken by oldest broadcastCount.
        val key = rotationCache.selectNextChunk() ?: run {
            Log.w(TAG, "  ⚠ rotation cache empty, nothing to advertise")
            return
        }
        val entry = rotationCache.get(key) ?: return
        val chunk = entry.chunk
        lastBroadcastKey = key

        // 🆕 L44.16.74 — Pre-flight byte-safety check. Council Round 2
        // verdict : the 131-char limit is actually 131 BYTES (UTF-8).
        // Japanese chars consume 3B each so a "fits-in-131-chars" check
        // can silently overflow the BLE advertising payload. Fail fast
        // here with a clear error rather than a silent advertise failure.
        //
        // In RELEASE we skip the chunk and continue (better than crash);
        // in DEBUG we throw to surface the bug during development.
        try {
            MeshByteSafety.assertChunkFits(chunk)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "  ❌ chunk [$key] BYTE OVERFLOW " +
                "(${MeshByteSafety.utf8ByteSize(chunk)}B > 131B), " +
                "SKIPPING this rotation slot. Cause: ${e.message}")
            return  // skip this chunk, next rotation will pick the next one
        }

        val opts = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .setLowPower(false)  // need full power for radar use case
            // 🆕 FIX7 — Disable WIFI_LAN/WIFI_HOTSPOT bandwidth upgrade.
            // JEMMA mesh n'utilise jamais que BLE/Bluetooth pour relayer
            // les chunks SOS. L'upgrade vers WIFI_LAN provoquait des
            // MEDIUM_ERROR [ACCEPT_CONNECTION_FAILED][SOCKET_CLOSED] en
            // boucle (AP isolation, ports bloqués, ou pas de Wi-Fi).
            // setDisruptiveUpgrade(false) coupe l'upgrade dès la
            // configuration et garde la liaison BLE.
            .setDisruptiveUpgrade(false)
            .build()
        // 🆕 L44.16.74 — Log byte length too (NOT char length).
        // 🆕 L44.16.78 — Log the rotation cache key for clarity.
        Log.i(TAG, "  📡 advertising [$key] cache=${rotationCache.size()} " +
            "(${MeshByteSafety.utf8ByteSize(chunk)}B): $chunk")
        connectionsClient.startAdvertising(
            chunk, SERVICE_ID, connectionLifecycleCallback, opts
        ).addOnSuccessListener {
            // Record the broadcast so the priority scheduler advances
            // through tiers fairly on subsequent ticks.
            rotationCache.recordBroadcast(key)
            Log.i(TAG, "  ✓ advertising [$key] (broadcastCount=${rotationCache.get(key)?.broadcastCount})")
        }.addOnFailureListener { e ->
            Log.e(TAG, "  ❌ advertising [$key] failed: ${e.message}", e)
        }
    }

    /**
     * Stop the active advertise rotation. By default, this is a SOFT
     * stop : the rotation runnable is killed and Nearby is told to stop,
     * but the rotationCache keeps the RELAYED entries (VR|/SR|/E|) so
     * they can be re-broadcast as soon as advertise resumes.
     *
     * 🆕 L44.16.80 — split semantics : soft stop keeps relays, full stop
     * wipes everything. Use full stop only when the user explicitly
     * cancels SOS or the service is destroyed.
     */
    fun stopAdvertising() {
        stopAdvertising(wipeRelayed = false)
    }

    /**
     * Full version of stopAdvertising with explicit control over what
     * to do with relayed entries.
     *
     * @param wipeRelayed if true, also clear all VR|/SR|/E| entries
     *                    (use when SOS is fully cancelled or service
     *                    is destroyed); if false, keep them alive so
     *                    a future restart can resume relaying
     */
    fun stopAdvertising(wipeRelayed: Boolean) {
        if (!isAdvertising) return
        Log.i(TAG, "⏹ stopAdvertising (wipeRelayed=$wipeRelayed)")
        isAdvertising = false
        mainHandler.removeCallbacks(rotationRunnable)
        mainHandler.removeCallbacks(rotationEvictRunnable)
        try {
            connectionsClient.stopAdvertising()
        } catch (e: Exception) {
            Log.w(TAG, "stopAdvertising threw (continuing)", e)
        }
        // 🆕 L44.16.80 — Only clear own entries by default. Relayed
        // VR|/SR|/E| stay alive so a future restart can keep relaying
        // without losing the mesh state we built up.
        if (wipeRelayed) {
            rotationCache.clear()
        } else {
            val removedOwn = rotationCache.clearOwn()
            Log.v(TAG, "  ↳ soft stop: removed ${removedOwn.size} own, " +
                "kept ${rotationCache.size()} relayed")
        }
        lastBroadcastKey = null
        advertiseRole = ""
    }

    /**
     * Start discovering nearby JEMMA endpoints (both victims and rescuers).
     */
    fun startDiscovery(): Boolean {
        if (isDiscovering) {
            Log.i(TAG, "  (already discovering — no-op)")
            return true
        }
        Log.i(TAG, "▶ startDiscovery (service=$SERVICE_ID, strategy=$STRATEGY)")
        // 🆕 L44.16.23-hotfix2 — Set the flag SYNCHRONOUSLY *before*
        // calling startDiscovery. Without this, two callers in quick
        // succession (e.g. radarStart from JS bridge + DisposableEffect
        // in overlay) both pass the `if (isDiscovering)` guard because
        // the addOnSuccessListener flips the flag asynchronously, AFTER
        // both calls already fired. Result: STATUS_ALREADY_DISCOVERING.
        // Roll the flag back to false only if the request actually fails.
        isDiscovering = true
        mainHandler.post(staleCheckRunnable)
        val opts = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            // 🆕 FIX7b — Note : setDisruptiveUpgrade n'existe PAS sur
            // DiscoveryOptions.Builder (seulement sur AdvertisingOptions
            // et ConnectionOptions). Le fait de le disable côté advertise
            // suffit à empêcher l'upgrade WIFI_LAN bidirectionnel.
            .build()
        return try {
            connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, opts)
                .addOnSuccessListener {
                    Log.i(TAG, "  ✓ discovery started")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "  ❌ discovery start failed: ${e.message}", e)
                    // Roll back the flag so a future call can retry
                    isDiscovering = false
                    mainHandler.removeCallbacks(staleCheckRunnable)
                }
            true
        } catch (e: Exception) {
            Log.e(TAG, "startDiscovery threw", e)
            isDiscovering = false
            mainHandler.removeCallbacks(staleCheckRunnable)
            false
        }
    }

    /** Stop discovery. Sessions are NOT cleared — keep last-known data. */
    fun stopDiscovery() {
        if (!isDiscovering) return
        Log.i(TAG, "⏹ stopDiscovery")
        isDiscovering = false
        mainHandler.removeCallbacks(staleCheckRunnable)
        try {
            connectionsClient.stopDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "stopDiscovery threw (continuing)", e)
        }
    }

    /** Stop everything + clear sessions. Use this for full reset (STOP RADAR). */
    fun fullStop() {
        // 🆕 L44.16.35 — Verbose teardown logs.
        //
        // Bug observed : after swipe-kill of the app, GMS persistent
        // (process com.google.android.gms.persistent, separate PID)
        // continues to advertise for our service-id for ~minutes.
        // Other phones still see us as a victim/rescuer in radar.
        //
        // Root cause : connectionsClient.stopAdvertising() / stopDiscovery() /
        // stopAllEndpoints() are FIRE-AND-FORGET (return void, not Task).
        // The IPC to GMS persistent is asynchronous. If our process dies
        // before the IPC is processed by GMS, it never receives the stop
        // signal → continues to advertise.
        //
        // Mitigations in this version :
        //   1. REORDER : stopAllEndpoints() FIRST. It cuts active sessions
        //      at the GMS level before we ask GMS to stop our advertise.
        //      The reverse order (current) lets GMS treat stopAdvertising
        //      as "no more new sessions" while keeping existing ones alive.
        //   2. Verbose logs at each step so we can see in logcat which
        //      IPC actually went out before the process died.
        //   3. Caller (Activity.onDestroy) will sleep ~300ms after this
        //      to give GMS time to process the IPCs.
        val t0 = System.currentTimeMillis()
        Log.i(TAG, "🛑 [FULLSTOP] start — was advertising=$isAdvertising discovering=$isDiscovering")

        // Step 1 : stopAllEndpoints FIRST (cuts active sessions at GMS level)
        try {
            Log.i(TAG, "🛑 [FULLSTOP] step 1/4: connectionsClient.stopAllEndpoints()")
            connectionsClient.stopAllEndpoints()
            Log.i(TAG, "🛑 [FULLSTOP]   ✓ stopAllEndpoints IPC sent (fire-and-forget)")
        } catch (e: Exception) {
            Log.w(TAG, "🛑 [FULLSTOP]   ❌ stopAllEndpoints threw", e)
        }

        // Step 2 : stop our advertise rotation + IPC to GMS
        try {
            Log.i(TAG, "🛑 [FULLSTOP] step 2/4: stopAdvertising(wipeRelayed=true)")
            // 🆕 L44.16.80 — User-initiated stop : wipe everything
            // including relayed VR|/SR|/E| (no point keeping them when
            // SOS is fully cancelled).
            stopAdvertising(wipeRelayed = true)
            Log.i(TAG, "🛑 [FULLSTOP]   ✓ stopAdvertising done")
        } catch (e: Exception) {
            Log.w(TAG, "🛑 [FULLSTOP]   ❌ stopAdvertising threw", e)
        }

        // Step 3 : stop discovery + IPC to GMS
        try {
            Log.i(TAG, "🛑 [FULLSTOP] step 3/4: stopDiscovery()")
            stopDiscovery()
            Log.i(TAG, "🛑 [FULLSTOP]   ✓ stopDiscovery done")
        } catch (e: Exception) {
            Log.w(TAG, "🛑 [FULLSTOP]   ❌ stopDiscovery threw", e)
        }

        // Step 4 : clear our local state
        Log.i(TAG, "🛑 [FULLSTOP] step 4/4: clearing local sessions")
        val (peerCount, rescuerCount, endpointCount) = synchronized(peerSessions) {
            val a = peerSessions.size
            peerSessions.clear()
            val b = synchronized(rescuerSessions) {
                val s = rescuerSessions.size
                rescuerSessions.clear()
                s
            }
            val c = synchronized(endpointToSid) {
                val s = endpointToSid.size
                endpointToSid.clear()
                s
            }
            Triple(a, b, c)
        }
        Log.i(TAG, "🛑 [FULLSTOP]   ✓ cleared $peerCount peer(s), $rescuerCount rescuer(s), $endpointCount endpoint(s)")

        val dt = System.currentTimeMillis() - t0
        Log.i(TAG, "🛑 [FULLSTOP] complete in ${dt}ms (NB: GMS-side cleanup is async, may take 1-2 min)")
    }

    fun isAdvertising(): Boolean = isAdvertising
    fun isDiscovering(): Boolean = isDiscovering

    @Synchronized
    fun snapshotPeers(): List<PeerInfo> = peerSessions.values.map { it.toPeerInfo() }

    @Synchronized
    fun snapshotRescuers(): List<RescuerInfo> = rescuerSessions.values.map { it.toRescuerInfo() }

    // ═══════════════════════════════════════════════════════════════
    //  Internal: discovery callbacks
    // ═══════════════════════════════════════════════════════════════

    /**
     * 🆕 L44.16.76 — Process a chunk through the mesh layer.
     *
     * For E| chunks : applies StatusResolver and notifies onTriageEvent
     *                  via the listener pipeline.
     * For VR|/SR| chunks : unwraps the relay header, then decoder reads
     *                       the underlying V|/S| as if it were direct.
     * For V|/S| chunks  : feeds into RelayManager so they get cached
     *                      for forward-relay if appropriate.
     *
     * Returns the rewritten chunk if relay was decided (caller adds to
     * advertise rotation). Returns null otherwise.
     */
    private fun handleMeshChunk(endpointId: String, rawChunk: String): String? {
        // 🆕 L44.16.86 — Intercept F| fingerprint chunks BEFORE RelayManager.
        //
        // Why before : RelayManager doesn't know about F| (it's pure metadata
        // for the rotation scheduler, not a relay-able profile/event). We
        // process it locally to decide cache-hit, then return null so it
        // doesn't propagate further.
        if (rawChunk.startsWith("F|")) {
            return handleFingerprintChunk(rawChunk)
        }

        val mgr = ensureRelayManager()
        return when (val action = mgr.onChunkReceived(rawChunk)) {
            is RelayManager.MeshAction.Drop -> {
                Log.v(TAG, "  ↳ mesh drop: ${rawChunk.take(20)}…")
                null
            }
            is RelayManager.MeshAction.UseLocallyOnly -> {
                Log.v(TAG, "  ↳ mesh use-locally-only (${action.reason}): ${rawChunk.take(20)}…")
                null
            }
            is RelayManager.MeshAction.Relay -> {
                // 🆕 L44.16.78 — Insert the relayed chunk into the
                // RotationCache. The Map structure deduplicates by
                // (type, sid, seq) — receiving Haru's V|RegT|1 ten
                // times in a row produces a single slot that gets
                // payload-refreshed each time, not 10 list entries.
                //
                // No more `if (size < 25)` cap : the cache size is
                // bounded naturally by the number of unique chunks
                // visible on the mesh (≤ 5 chunks × N peers + events).
                // Stale entries are reaped by rotationEvictRunnable.
                val outcome = rotationCache.put(action.rewrittenChunk, isOwn = false)
                Log.i(TAG, "  ↳ mesh relay $outcome (backoff=${action.backoffMs}ms, " +
                    "cache=${rotationCache.size()}): ${action.rewrittenChunk.take(40)}…")
                action.rewrittenChunk
            }
        }
    }

    /**
     * 🆕 L44.16.86 — Handle an incoming F| fingerprint chunk.
     *
     * If we already have the same victim's profile cached AND our locally
     * computed fingerprint matches the incoming one, mark the peer as
     * "cache-hit" so the rotation scheduler skips their V|/VR| chunks
     * for the next 60 seconds. This frees airtime for events and peers
     * we DO need to learn about.
     *
     * If the hashes differ, log the cache miss and continue normally
     * (the V|/VR| rotation will eventually rebuild our local cache).
     *
     * Returns null because F| chunks are never relayed (they are
     * locally-meaningful only — every device computes its own fingerprint
     * from the V| chunks it has cached).
     */
    private fun handleFingerprintChunk(rawChunk: String): String? {
        val fp = be.heyman.android.jemmapassdemo.mesh.codec.FingerprintChunk.decode(rawChunk)
        if (fp == null) {
            Log.v(TAG, "  ↳ malformed F| chunk, dropping: $rawChunk")
            return null
        }

        // 🆕 L44.16.89 — Canonicalise VR| → V| before hash computation.
        //
        // ── The bug we fix ──────────────────────────────────────────
        // Empirical from dryrun 21:46 of 2026-05-08 on L44.16.88 :
        // Samsung emits hash=bfa73a6e (computed from V| chunks).
        // Pixel computes locally hash=bff9a8c1, persistent CACHE MISS
        // even though the underlying victim profile data is bit-identical
        // (same name/age/blood/lat/lon/payload).
        //
        // Root cause : Pixel's rotation cache contains the RELAYED form
        // of Kurodo's profile (`VR|HW,H|N|5|3|payload` with TTL=3 in
        // field [4]) because that's how Pixel acts upon V| chunks it
        // receives — wrap them for further relay. Samsung emits hashes
        // computed on V| direct (`V|HW,H|N|5|payload`, no TTL field).
        // Different wire strings → different SHA-256 → CACHE HIT
        // impossible on a rescuer that is also a relay node, which is
        // the most common case in practice.
        //
        // ── The fix ──────────────────────────────────────────────────
        // Before hashing, unwrap any VR| chunk back to its V| direct
        // equivalent using RelayedChunkCodec.unwrapToDirect(). This
        // produces identical input across all peers regardless of
        // whether they have a fresh V| (heard directly from origin)
        // or a relayed VR| (heard from an intermediate hop).
        //
        // ── Validation runtime (dryrun 22:01 of 2026-05-08) ─────────
        // After this fix, CACHE HIT was observed runtime for the first
        // time : Pixel computed hash=2ae7490b matching Samsung's
        // emitted hash. 60s skip-window worked as designed (0 V|/VR|
        // broadcasts during the window).
        val localVChunks = (1..5).mapNotNull { seq ->
            val raw = rotationCache.get("V|${fp.sid}|$seq")?.chunk
                ?: rotationCache.get("VR|${fp.sid}|$seq")?.chunk
            if (raw == null) {
                null
            } else if (raw.startsWith("VR|")) {
                // Canonicalise to V| form for hash computation
                be.heyman.android.jemmapassdemo.mesh.codec.RelayedChunkCodec
                    .unwrapToDirect(raw) ?: raw  // fallback to raw if unwrap fails
            } else {
                raw  // already V|, use as-is
            }
        }

        if (localVChunks.size != 5) {
            // We don't have all 5 chunks yet → cache miss, listen normally
            Log.d(TAG, "  🎯 [F|] CACHE MISS for ${fp.sid} " +
                "(have ${localVChunks.size}/5 chunks, incoming hash=${fp.sha256Hex8})")
            return null
        }

        val localHash = try {
            be.heyman.android.jemmapassdemo.mesh.codec.FingerprintChunk.computeHash(localVChunks)
        } catch (e: Exception) {
            Log.w(TAG, "  🎯 [F|] failed to compute local hash for ${fp.sid}", e)
            return null
        }

        if (localHash == fp.sha256Hex8) {
            // CACHE HIT — we already have this exact profile
            rotationCache.markCacheHit(fp.sid)
            Log.i(TAG, "  🎯 [F|] CACHE HIT for ${fp.sid} (hash=$localHash) " +
                "→ skipping V|/VR| rotation 60s")
        } else {
            Log.d(TAG, "  🎯 [F|] CACHE MISS for ${fp.sid} " +
                "(local=$localHash, incoming=${fp.sha256Hex8})")
        }

        return null  // F| chunks are not relayed, only consumed
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val name = info.endpointName
            // 🆕 L44.16.23-hotfix2 — Promoted to Log.i so we can see
            // *every* endpoint detected in default logcat. This is
            // the crucial signal for "is Nearby actually picking up
            // packets from the other phone?".
            Log.i(TAG, "📡 onEndpointFound: id=$endpointId len=${name.length} → $name")

            // 🆕 L44.16.76 — Hook the relay manager BEFORE the legacy
            // decoder. This lets us :
            //   1. Catch E| events (process via StatusResolver, notify UI)
            //   2. Catch VR|/SR| relayed chunks (unwrap to V|/S| for legacy decoder)
            //   3. Decide whether to forward to peers (relay or drop)
            //
            // The legacy decoder still runs for V|/S| direct chunks unchanged.
            handleMeshChunk(endpointId, name)

            val unwrappedName = if (name.startsWith("VR|") || name.startsWith("SR|")) {
                be.heyman.android.jemmapassdemo.mesh.codec.RelayedChunkCodec.unwrapToDirect(name) ?: name
            } else {
                name
            }
            val decoded = JemmaNearbyEndpointCodec.decode(unwrappedName)
            if (decoded == null) {
                Log.v(TAG, "  ↳ ignored (not a JEMMA endpoint)")
                return
            }

            when (decoded) {
                is JemmaNearbyEndpointCodec.Decoded.VictimHeader -> {
                    handleVictimHeader(endpointId, decoded)
                }
                is JemmaNearbyEndpointCodec.Decoded.VictimCodes -> {
                    handleVictimCodes(endpointId, decoded)
                }
                is JemmaNearbyEndpointCodec.Decoded.Rescuer -> {
                    handleRescuer(endpointId, decoded)
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            // Per spec: we do NOT clear the session here.
            // Stale detection is done via the timer (staleCheckRunnable).
            // Just log + keep the user-facing peer alive in the UI.
            val sid = synchronized(endpointToSid) { endpointToSid[endpointId] }
            Log.d(TAG, "📡 onEndpointLost: id=$endpointId (sid=$sid) — keeping session, will mark stale on timer")
            synchronized(endpointToSid) { endpointToSid.remove(endpointId) }
        }
    }

    /**
     * REJECT all incoming connections immediately. We're broadcast-only.
     * This is critical: if we accepted, Android would show a popup
     * asking the user to confirm — breaking the "no UI" rule.
     */
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "  ↪ rejecting incoming connection from $endpointId (broadcast-only mode)")
            try {
                connectionsClient.rejectConnection(endpointId)
            } catch (e: Exception) {
                Log.w(TAG, "rejectConnection threw (ignoring)", e)
            }
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) { /* no-op */ }
        override fun onDisconnected(endpointId: String) { /* no-op */ }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internal: chunk assembly
    // ═══════════════════════════════════════════════════════════════

    private fun handleVictimHeader(
        endpointId: String,
        h: JemmaNearbyEndpointCodec.Decoded.VictimHeader
    ) {
        val sid = h.sid
        synchronized(endpointToSid) { endpointToSid[endpointId] = sid }

        val (created, snap) = synchronized(peerSessions) {
            val existed = peerSessions.containsKey(sid)
            val sess = peerSessions.getOrPut(sid) {
                PeerSessionState(sid, System.currentTimeMillis())
            }
            sess.applyHeader(h)
            sess.lastSeenMs = System.currentTimeMillis()
            sess.isStale = false
            Pair(!existed, sess.toPeerInfo())
        }

        if (created) {
            Log.i(TAG, "🐢 PEER FOUND: sid=$sid name='${h.name}' crit=${h.criticality}")
            mainHandler.post { dispatch { it.onPeerFound(snap) } }
        } else {
            mainHandler.post { dispatch { it.onPeerUpdate(snap) } }
        }
        if (snap.complete) {
            // 🆕 L44.16.27 — Guard against repeated onPeerComplete dispatch
            // (would be fired every rotation cycle). One-shot per session.
            val shouldDispatch = synchronized(peerSessions) {
                val sess = peerSessions[sid]
                if (sess != null && !sess.completeDispatched) {
                    sess.completeDispatched = true
                    true
                } else false
            }
            if (shouldDispatch) {
                Log.i(TAG, "🐢 PEER COMPLETE (dispatch ONCE): sid=$sid")
                mainHandler.post { dispatch { it.onPeerComplete(snap) } }
            }
        }
    }

    private fun handleVictimCodes(
        endpointId: String,
        c: JemmaNearbyEndpointCodec.Decoded.VictimCodes
    ) {
        val sid = c.sid
        synchronized(endpointToSid) { endpointToSid[endpointId] = sid }

        val (created, snap) = synchronized(peerSessions) {
            val existed = peerSessions.containsKey(sid)
            val sess = peerSessions.getOrPut(sid) {
                PeerSessionState(sid, System.currentTimeMillis())
            }
            sess.applyCodes(c)
            sess.lastSeenMs = System.currentTimeMillis()
            sess.isStale = false
            Pair(!existed, sess.toPeerInfo())
        }

        if (created) {
            Log.i(TAG, "🐢 PEER FOUND (via codes chunk): sid=$sid section=${c.sectionMarker}")
            mainHandler.post { dispatch { it.onPeerFound(snap) } }
        }
        Log.d(TAG, "  🐢 chunk ${c.chunkIdx}/${c.chunkTotal} for sid=$sid section=${c.sectionMarker} (${c.codes.size} codes)")
        mainHandler.post { dispatch { it.onPeerUpdate(snap) } }
        if (snap.complete) {
            // 🆕 L44.16.27 — Same one-shot guard as handleVictimHeader.
            val shouldDispatch = synchronized(peerSessions) {
                val sess = peerSessions[sid]
                if (sess != null && !sess.completeDispatched) {
                    sess.completeDispatched = true
                    true
                } else false
            }
            if (shouldDispatch) {
                Log.i(TAG, "🐢 PEER COMPLETE (dispatch ONCE): sid=$sid (all chunks received)")
                mainHandler.post { dispatch { it.onPeerComplete(snap) } }
            }
        }
    }

    private fun handleRescuer(
        endpointId: String,
        r: JemmaNearbyEndpointCodec.Decoded.Rescuer
    ) {
        val sid = r.sid
        synchronized(endpointToSid) { endpointToSid[endpointId] = sid }

        val (created, snap) = synchronized(rescuerSessions) {
            val existed = rescuerSessions.containsKey(sid)
            val sess = rescuerSessions.getOrPut(sid) {
                RescuerSessionState(sid, System.currentTimeMillis())
            }
            sess.applyRescuer(r)
            sess.lastSeenMs = System.currentTimeMillis()
            sess.isStale = false
            Pair(!existed, sess.toRescuerInfo())
        }

        if (created) {
            Log.i(TAG, "⛑️ RESCUER FOUND: sid=$sid name='${r.name}' lang=${r.langCode}")
            mainHandler.post { dispatch { it.onRescuerFound(snap) } }
        } else {
            mainHandler.post { dispatch { it.onRescuerUpdate(snap) } }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Stale detection (peers we haven't seen for STALE_DELAY_MS)
    // ═══════════════════════════════════════════════════════════════

    private val staleCheckRunnable = object : Runnable {
        override fun run() {
            if (!isDiscovering) return
            val now = System.currentTimeMillis()
            val staleSids = mutableListOf<Pair<String, Boolean>>()  // (sid, isRescuer)

            synchronized(peerSessions) {
                for (sess in peerSessions.values) {
                    // 🆕 L44.16.49 — Synthetic-local peers (QR-scanned by
                    // me, flags=0x40) are IMMORTAL. They don't have a
                    // BLE/Nearby range — the QR payload is offline-first
                    // and the peer "exists" wherever the rescuer carries
                    // their phone. Marking them stale and showing
                    // "Hors portée depuis 2min" is misleading and was
                    // confusing the user (Image 2 of L44.16.48 tests).
                    if (sess.flags and 0x40 != 0) continue
                    if (!sess.isStale && now - sess.lastSeenMs > STALE_DELAY_MS) {
                        sess.isStale = true
                        staleSids.add(Pair(sess.sid, false))
                    }
                }
            }
            synchronized(rescuerSessions) {
                for (sess in rescuerSessions.values) {
                    if (!sess.isStale && now - sess.lastSeenMs > STALE_DELAY_MS) {
                        sess.isStale = true
                        staleSids.add(Pair(sess.sid, true))
                    }
                }
            }

            staleSids.forEach { (sid, isRescuer) ->
                Log.i(TAG, "⏳ marking ${if (isRescuer) "rescuer" else "peer"} $sid as STALE (no signal for >${STALE_DELAY_MS / 1000}s)")
                if (isRescuer) dispatch { it.onRescuerStale(sid) }
                else dispatch { it.onPeerStale(sid) }
            }

            mainHandler.postDelayed(this, STALE_CHECK_INTERVAL_MS)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Internal session state classes (reassembly)
// ═══════════════════════════════════════════════════════════════

/**
 * Per-victim assembly state. Each chunk we receive contributes to
 * one of these fields. When we have header + (optionally) all
 * referenced sections, we mark complete=true.
 */
internal class PeerSessionState(
    val sid: String,
    val firstSeenMs: Long
) {
    var lastSeenMs: Long = firstSeenMs
    var isStale: Boolean = false

    // 🆕 L44.16.27 — One-shot guard for onPeerComplete dispatch.
    // Without this flag, onPeerComplete fires every time the rotation
    // hits the last chunk again (i.e. every ~7.5s for a 5-chunk rotation).
    // Result: receivers' JS layer creates duplicate TMP profiles each
    // time. This flag ensures we dispatch onPeerComplete exactly ONCE
    // per peer session — when the last missing chunk arrives.
    //
    // Note: stays true even after subsequent updates, so re-receiving
    // the same chunks won't trigger re-dispatch. Only a fresh peer
    // session (different sid OR session evicted by stale check) restarts.
    var completeDispatched: Boolean = false

    // From header chunk
    var hasHeader: Boolean = false
    var name: String = ""
    var sex: Char = '?'
    var age: Int = 0
    var bloodType: String = ""
    var criticality: Int = 0
    var lat: Double = 0.0
    var lon: Double = 0.0
    var chunkTotal: Int = JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL
    var langCode: String = ""    // 🆕 L44.16.48 — victim's spoken language
    // 🆕 L44.16.49 — flags from the original PeerInfo (e.g. 0x40 for
    // synthetic-local QR-scanned peers). Preserved across the lifetime
    // of the session so the stale-check can skip them ("a peer scanned
    // by my own QR camera doesn't have a BLE range — it's offline-first
    // and immortal until the user manually deletes the profile").
    var flags: Int = 0

    // Code sections received
    val allergies = mutableListOf<String>()
    val meds = mutableListOf<String>()
    val conditions = mutableListOf<String>()
    val immun = mutableListOf<String>()
    val sectionsSeen = mutableSetOf<Char>()

    fun applyHeader(h: JemmaNearbyEndpointCodec.Decoded.VictimHeader) {
        hasHeader = true
        name = h.name
        sex = h.sex
        age = h.age
        bloodType = h.bloodType
        criticality = h.criticality
        lat = h.lat
        lon = h.lon
        chunkTotal = h.chunkTotal
        langCode = h.langCode    // 🆕 L44.16.48
        sectionsSeen.add('H')
    }

    fun applyCodes(c: JemmaNearbyEndpointCodec.Decoded.VictimCodes) {
        val target: MutableList<String> = when (c.sectionMarker) {
            'A' -> allergies
            'M' -> meds
            'C' -> conditions
            'I' -> immun
            else -> return
        }
        // Idempotent merge: don't duplicate codes
        for (code in c.codes) {
            if (code !in target) target.add(code)
        }
        sectionsSeen.add(c.sectionMarker)
    }

    /** Complete = we've seen the header AND all 4 section chunks. */
    val complete: Boolean
        get() = hasHeader && sectionsSeen.containsAll(setOf('H', 'A', 'M', 'C', 'I'))

    fun toPeerInfo(): PeerInfo = PeerInfo(
        sessionIdHex = sid,
        broadcasterName = name,
        latitude = if (lat == 0.0 && lon == 0.0) null else lat,
        longitude = if (lat == 0.0 && lon == 0.0) null else lon,
        sex = sex,
        ageDecade = age / 10,   // legacy compat: ageDecade was 0..9 buckets
        bloodType = bloodType,
        criticality = criticality,
        flags = flags,           // 🆕 L44.16.49 — surface synthetic 0x40 marker
        rssi = 0,                // not exposed by Nearby
        chunksReceived = sectionsSeen.size,
        chunksTotal = chunkTotal,
        complete = complete,
        firstSeenMs = firstSeenMs,
        lastSeenMs = lastSeenMs,
        allergyCodes = allergies.map { JemmaSosChunkCodec.CodeEntry(it, 0.toByte()) },
        medCodes = meds.map { JemmaSosChunkCodec.CodeEntry(it, 0.toByte()) },
        conditionCodes = conditions.map { JemmaSosChunkCodec.CodeEntry(it, 0.toByte()) },
        immunCodes = immun.map { JemmaSosChunkCodec.CodeEntry(it, 0.toByte()) },
        freeTextNote = "",
        // 🆕 L44.16.23 — exact age from Nearby endpoint (not bucketed)
        ageExact = age,
        // 🆕 L44.16.23 — staleness flag for UI
        isStale = isStale,
        // 🆕 L44.16.48 — propagate victim's spoken language
        langCode = langCode
    )
}

internal class RescuerSessionState(
    val sid: String,
    val firstSeenMs: Long
) {
    var lastSeenMs: Long = firstSeenMs
    var isStale: Boolean = false

    var name: String = ""
    var langCode: String = "en"
    var lat: Double = 0.0
    var lon: Double = 0.0

    fun applyRescuer(r: JemmaNearbyEndpointCodec.Decoded.Rescuer) {
        name = r.name
        langCode = r.langCode
        lat = r.lat
        lon = r.lon
    }

    fun toRescuerInfo(): RescuerInfo = RescuerInfo(
        sessionIdHex = sid,
        name = name,
        langCode = langCode,
        latitude = if (lat == 0.0 && lon == 0.0) null else lat,
        longitude = if (lat == 0.0 && lon == 0.0) null else lon,
        rssi = 0,
        firstSeenMs = firstSeenMs,
        lastSeenMs = lastSeenMs,
        // 🆕 L44.16.23 — staleness flag
        isStale = isStale
    )
}
