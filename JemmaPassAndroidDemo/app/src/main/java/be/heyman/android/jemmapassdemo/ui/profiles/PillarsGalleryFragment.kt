/*
 * PillarsGalleryFragment.kt — JEMMA Pass · Plan B · L4 v2.5.4
 *
 * Standalone gallery showing the 18 IPS pillars as a 5-row × 4-column
 * grid of clickable tiles. Reachable from the Profiles toolbar via the
 * "🩺 IPS Pillars" menu item. Each tile tap navigates to PillarStubFragment
 * with the matching `pillarKey` argument.
 *
 * Why a dedicated screen rather than wiring up the rich card grid in
 * item_profile_active.xml :
 *   • The rich card is not yet integrated anywhere (the ProfilesFragment
 *     uses item_profile_summary.xml for every row, current or not).
 *   • A standalone gallery is the minimum-friction way to surface the
 *     pillar pages while we figure out the right home for the rich card
 *     in a later UX iteration.
 *
 * Layout : fragment_pillars_gallery.xml (5×4 grid, 2 trailing spacers).
 *
 * Logging :
 *   📋 onCreateView (PillarsGalleryFragment v2.5.4)
 *   🔗 wired tile · key=patient    (×18, on each bound)
 *   👆 tile tap · key=allergies → dest_pillar_stub
 */
package be.heyman.android.jemmapassdemo.ui.profiles

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.pillars.PillarRegistry
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PillarsGalleryFragment : Fragment() {

    @Inject
    lateinit var profilesRepo: ProfilesRepository

    companion object {
        private const val TAG = "JEMMA-PROFILES"
    }

    /**
     * The 18 tiles on screen, ordered exactly as in PillarRegistry.ALL :
     *   row 1 : patient · contacts · allergies · medications        (active)
     *   row 2 : conditions · pastProblems · immunizations · procedures
     *   row 3 : devices · functional · pregnancy · results
     *   row 4 : advanceDirectives · consents · goals · encounters
     *   row 5 : occupational · providers · ⊘ · ⊘
     *
     * Tile id → pillar key. Iterated at onViewCreated to populate emoji,
     * label and click listeners in one pass.
     */
    private val tileBindings: List<Pair<Int, String>> = listOf(
        R.id.gallery_tile_patient           to "patient",
        R.id.gallery_tile_contacts          to "contacts",
        R.id.gallery_tile_allergies         to "allergies",
        R.id.gallery_tile_medications       to "medications",
        R.id.gallery_tile_conditions        to "conditions",
        R.id.gallery_tile_pastproblems      to "pastProblems",
        R.id.gallery_tile_immunizations     to "immunizations",
        R.id.gallery_tile_procedures        to "procedures",
        R.id.gallery_tile_devices           to "devices",
        R.id.gallery_tile_functional        to "functional",
        R.id.gallery_tile_pregnancy         to "pregnancy",
        R.id.gallery_tile_results           to "results",
        R.id.gallery_tile_advance           to "advanceDirectives",
        R.id.gallery_tile_consents          to "consents",
        R.id.gallery_tile_goals             to "goals",
        R.id.gallery_tile_encounters        to "encounters",
        R.id.gallery_tile_occupational      to "occupational",
        R.id.gallery_tile_providers         to "providers",
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView (PillarsGalleryFragment v2.5.4)")
        return inflater.inflate(R.layout.fragment_pillars_gallery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar back arrow
        view.findViewById<View>(R.id.gallery_toolbar_back)?.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 gallery back tap")
            findNavController().navigateUp()
        }

        var bound = 0
        tileBindings.forEach { (tileId, pillarKey) ->
            val pillar = PillarRegistry.get(pillarKey) ?: run {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ no pillar for key=$pillarKey — skipping tile")
                return@forEach
            }
            val tileRoot = view.findViewById<View>(tileId) ?: run {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ no view for id=$tileId · key=$pillarKey")
                return@forEach
            }

            // Populate the included <view_pillar_tile> / <view_pillar_tile_stub>
            // with the pillar emoji + label. The included layouts give two
            // possible TextView ids depending on whether the tile was the
            // active or stub variant — try both.
            val emojiTv = tileRoot.findViewById<TextView>(R.id.pillar_tile_emoji)
                ?: tileRoot.findViewById<TextView>(R.id.pillar_tile_stub_emoji)
            val labelTv = tileRoot.findViewById<TextView>(R.id.pillar_tile_label)
                ?: tileRoot.findViewById<TextView>(R.id.pillar_tile_stub_label)
            emojiTv?.text = pillar.emoji
            labelTv?.text = getString(pillar.titleRes)

            // Hide the count chip on active tiles for now — we don't
            // know item counts until edit-mode pillars land in v2.5.5+.
            tileRoot.findViewById<View>(R.id.pillar_tile_count)?.visibility = View.GONE

            tileRoot.setOnClickListener {
                // 🆕 v2.6.0 L5 — Active pillars route to dedicated edit
                // fragments. Info-only pillars keep routing to the stub.
                //
                // For the active patient pillar, we pass the current profile
                // id (if any) so the form pre-fills with existing values.
                // null arg = create-new-profile mode.
                if (pillar.isActive) {
                    val destAction = when (pillarKey) {
                        "patient" -> R.id.action_gallery_to_perso
                        "contacts" -> R.id.action_gallery_to_contacts
                        "allergies" -> R.id.action_gallery_to_allergies
                        "medications" -> R.id.action_gallery_to_medications
                        "immunizations" -> R.id.action_gallery_to_immunizations
                        "procedures" -> R.id.action_gallery_to_procedures
                        "devices" -> R.id.action_gallery_to_devices
                        "results" -> R.id.action_gallery_to_results
                        "pastProblems" -> R.id.action_gallery_to_past_problems
                        else -> R.id.action_gallery_to_pillar_stub
                    }
                    val args = if (pillarKey == "patient" || pillarKey == "contacts" ||
                                   pillarKey == "allergies" || pillarKey == "medications" ||
                                   pillarKey == "immunizations" || pillarKey == "procedures" ||
                                   pillarKey == "devices" || pillarKey == "results" ||
                                   pillarKey == "pastProblems") {
                        bundleOf(
                            "pillarKey" to pillarKey,
                            "profileId" to profilesRepo.currentProfileId,
                        )
                    } else {
                        bundleOf("pillarKey" to pillarKey)
                    }
                    Log.i(
                        TAG,
                        "[t=${System.currentTimeMillis()}] 👆 tile tap · key=$pillarKey · " +
                            "isActive=true → ${resources.getResourceEntryName(destAction)}",
                    )
                    findNavController().navigate(destAction, args)
                } else {
                    Log.i(
                        TAG,
                        "[t=${System.currentTimeMillis()}] 👆 tile tap · key=$pillarKey · " +
                            "isActive=false → dest_pillar_stub",
                    )
                    findNavController().navigate(
                        R.id.action_gallery_to_pillar_stub,
                        bundleOf("pillarKey" to pillarKey),
                    )
                }
            }
            bound++
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔗 wired tile · key=$pillarKey")
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ Gallery ready · $bound/18 tiles wired")
    }
}
