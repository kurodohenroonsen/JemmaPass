/*
 * PillarStubFragment.kt — JEMMA Pass · Plan B · L4 v2.5.3
 *
 * REWRITE. The L1 trivial stub (which just inherited StubFragmentBase
 * and ignored the existing 179-line fragment_pillar_stub.xml layout) is
 * replaced with a real Fragment that :
 *   1. Reads the `pillarKey` nav arg
 *   2. Looks the pillar up in PillarRegistry
 *   3. Populates the hero card (emoji, title, IPS type, description)
 *   4. Mounts the field list RecyclerView via PillarFieldAdapter
 *   5. Fills the code-systems ChipGroup
 *   6. Wires the toolbar back arrow + "See production app" CTA to
 *      open https://jemmapass.net
 *
 * Logging :
 *   📋 onCreateView (PillarStubFragment v2.5.3 · key=<key>)
 *   ✅ Pillar bound · key=<key> · isActive=<bool> · fields=<n>
 *   ⚠ Unknown pillar key=<key> — fallback to 'conditions'
 *
 * The 4 ACTIVE pillars (patient/contacts/allergies/medications) ALSO
 * route here in v2.5.3 — the screen will display their FHIR structure
 * with a small "Active" badge instead of the lock chip, hinting that
 * an editable mode is coming in a future delivery (L5+).
 */
package be.heyman.android.jemmapassdemo.ui.stubs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.pillars.Pillar
import be.heyman.android.jemmapassdemo.pillars.PillarFieldAdapter
import be.heyman.android.jemmapassdemo.pillars.PillarRegistry
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class PillarStubFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-PROFILES"
        private const val ARG_PILLAR_KEY = "pillarKey"
        private const val FALLBACK_KEY = "conditions"
        private const val PRODUCTION_URL = "https://jemmapass.net"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val key = arguments?.getString(ARG_PILLAR_KEY) ?: FALLBACK_KEY
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView (PillarStubFragment v2.5.3 · key=$key)")
        return inflater.inflate(R.layout.fragment_pillar_stub, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rawKey = arguments?.getString(ARG_PILLAR_KEY) ?: FALLBACK_KEY
        val pillar = PillarRegistry.get(rawKey) ?: run {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ Unknown pillar key='$rawKey' — fallback to '$FALLBACK_KEY'")
            PillarRegistry.get(FALLBACK_KEY)!!
        }
        bind(view, pillar)
    }

    private fun bind(view: View, pillar: Pillar) {
        val ctx = requireContext()

        // ── Toolbar : back arrow + pillar title ─────────────────────────
        val toolbar = view.findViewById<MaterialToolbar>(R.id.stub_toolbar)
        toolbar.title = ctx.getString(pillar.titleRes)
        toolbar.setNavigationOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 toolbar back · key=${pillar.key}")
            findNavController().navigateUp()
        }

        // ── Hero card : emoji + title + IPS type + description ──────────
        view.findViewById<TextView>(R.id.stub_hero_emoji).text = pillar.emoji
        view.findViewById<TextView>(R.id.stub_hero_title).text = ctx.getString(pillar.titleRes)
        view.findViewById<TextView>(R.id.stub_hero_ips_type).text = ctx.getString(pillar.ipsTypeRes)
        view.findViewById<TextView>(R.id.stub_hero_description).text = ctx.getString(pillar.descRes)

        // ── Lock chip : ACTIVE pillars get a different message ──────────
        // The lock chip TextView lives unnamed inside the hero card. We
        // walk siblings to flip its label + tint when the pillar is active.
        // (Cheap fix — would be cleaner to give the chip an @+id, but that
        // requires editing fragment_pillar_stub.xml which we already ship
        // a patch for in this delivery if needed. For now we identify it
        // by its drawableStart and update via a child traversal.)
        flipLockChipIfActive(view, pillar.isActive)

        // ── Field list RecyclerView ─────────────────────────────────────
        val recycler = view.findViewById<RecyclerView>(R.id.stub_fields_recycler)
        recycler.layoutManager = LinearLayoutManager(ctx)
        recycler.adapter = PillarFieldAdapter(pillar.fields)
        recycler.setHasFixedSize(true)

        // ── Code-systems ChipGroup ──────────────────────────────────────
        val chipGroup = view.findViewById<ChipGroup>(R.id.stub_kb_chips)
        chipGroup.removeAllViews()
        pillar.codeSystems.forEach { system ->
            val chip = Chip(ctx).apply {
                text = system
                isClickable = false
                isCheckable = false
                // Match the existing JemmaKbChip aesthetics (small, muted)
                textSize = 11f
            }
            chipGroup.addView(chip)
        }

        // ── CTA : open the production app webpage ───────────────────────
        view.findViewById<MaterialButton>(R.id.stub_btn_production).setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 production CTA · key=${pillar.key} · url=$PRODUCTION_URL")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRODUCTION_URL)))
            } catch (e: Exception) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ failed to open $PRODUCTION_URL : ${e.message}")
            }
        }

        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✅ Pillar bound · key=${pillar.key} · " +
                "isActive=${pillar.isActive} · fields=${pillar.fields.size} · " +
                "codeSystems=${pillar.codeSystems.size}",
        )
    }

    /**
     * The hero card holds an unnamed TextView styled as a "lock" pill (drawableStart
     * = ic_lock_lock). We locate it by walking the hero card's child views and
     * detecting that drawable. For ACTIVE pillars we rewrite its text to the
     * "active · editable soon" message and clear the lock icon.
     */
    private fun flipLockChipIfActive(root: View, isActive: Boolean) {
        if (!isActive) return  // passive pillars keep their default lock chip from XML
        val ctx = requireContext()
        // Search for the lock TextView : it's the unnamed TextView with
        // ic_lock_lock as drawableStart inside the horizontal hero LinearLayout.
        val candidates = mutableListOf<TextView>()
        collectTextViews(root, candidates)
        for (tv in candidates) {
            val d = tv.compoundDrawables.firstOrNull { it != null }
            if (d != null && d.constantState != null) {
                val lockState = ctx.getDrawable(android.R.drawable.ic_lock_lock)?.constantState
                if (d.constantState == lockState) {
                    tv.setCompoundDrawables(null, null, null, null)
                    tv.text = ctx.getString(R.string.pillar_chip_active)
                    tv.alpha = 1f
                    return
                }
            }
        }
    }

    private fun collectTextViews(view: View, into: MutableList<TextView>) {
        if (view is TextView && view !is android.widget.Button && view !is ImageView) {
            into.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectTextViews(view.getChildAt(i), into)
            }
        }
    }
}
