/*
 * AllergiesEditFragment.kt — JEMMA Pass · Plan B · v2.6.0 · L_HERO_BAR
 *
 * 🆕 v2.6.0 L_HERO_BAR — La toolbar Material + hero séparés ont été
 * fusionnés en une seule bande horizontale (ImageButton back + emoji +
 * titre + count). Le back est maintenant un ImageButton, donc
 * setOnClickListener et plus setNavigationOnClickListener.
 *
 * Édition manuelle du pilier FHIR AllergyIntolerance IPS.
 * Persisté dans JemmaProfileJ.al : List<JAllergy>.
 *
 * Couverture FHIR :
 *   AllergyIntolerance.code                   → JAllergy.c   (SNOMED IPS code)
 *   AllergyIntolerance.criticality            → JAllergy.s   (H | L | U)
 *   AllergyIntolerance.clinicalStatus.coding  → JAllergy.st  (A | I | R)
 *   AllergyIntolerance.note.text              → JAllergy.d   (manifestation / notes)
 *   AllergyIntolerance.reaction.exposureRoute → JAllergy.m   (mechanism)
 *
 * Flow :
 *   1. onViewCreated : load profile via argProfileId / currentProfileId
 *   2. RecyclerView affiche current.al
 *   3. FAB + → AllergyFormBottomSheet (mode CREATE)
 *   4. Tap row → AllergyFormBottomSheet (mode EDIT) pré-rempli
 *   5. Long-press → confirm delete dialog
 *   6. FragmentResult reçoit le submit → mise à jour liste + saveProfile
 *
 * Tag log : JEMMA-ALLERGIES-EDIT · 🩹 lifecycle / 📋 list / 💾 save / 🗑 delete
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies

import android.os.Bundle
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
import be.heyman.android.jemmapassdemo.databinding.FragmentAllergiesEditBinding
import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JReaction
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.ui.assistant.AddItemWithAssistantBottomSheet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AllergiesEditFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-ALLERGIES-EDIT"
        private const val ARG_PROFILE_ID = "profileId"
    }

    @Inject
    lateinit var profilesRepo: ProfilesRepository

    @Inject
    lateinit var translations: IpsTranslationsRepository

    private var _binding: FragmentAllergiesEditBinding? = null
    private val binding get() = _binding!!

    private val argProfileId: String? by lazy { arguments?.getString(ARG_PROFILE_ID) }

    private var current: JemmaProfileJ? = null
    private val allergies = mutableListOf<JAllergy>()
    private lateinit var adapter: AllergiesAdapter

    private val currentLang: String by lazy {
        Locale.getDefault().language.lowercase().take(2)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAllergiesEditBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩹 onCreateView · argProfileId=$argProfileId")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩹 onViewCreated · lang=$currentLang")

        // 🆕 L_HERO_BAR — `allergies_toolbar_back` est maintenant un ImageButton
        // (anciennement MaterialToolbar), donc setOnClickListener et plus
        // setNavigationOnClickListener.
        binding.allergiesToolbarBack.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 back tap → navigateUp")
            findNavController().navigateUp()
        }

        adapter = AllergiesAdapter(
            lang = currentLang,
            translations = translations,
            scope = viewLifecycleOwner.lifecycleScope,
            onTap = { allergy, position ->
                openForm(AllergyFormMode.EDIT, existing = allergy, index = position)
            },
            onLongPress = { allergy, position ->
                confirmDelete(allergy, position)
            },
        )
        binding.allergiesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.allergiesRecycler.adapter = adapter

        binding.allergiesFabAdd.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 FAB add tap · opening assistant bottom sheet")
            // 🆕 Lot 14.5c26 (regression fix) — Le FAB ouvre le bottom sheet
            // assistant Jemma (4 modes IA + 1 manuel). Pour les allergies, le
            // mode actif est "voice" (mic) : il fait naviguer vers le chat
            // vocal allergies. Le mode "Saisir manuellement" rappelle openForm
            // CREATE comme le legacy.
            AddItemWithAssistantBottomSheet
                .newInstance(AddItemWithAssistantBottomSheet.Companion.Pillar.ALLERGIES)
                .setOnManualPicked {
                    Log.i(
                        TAG,
                        "[t=${System.currentTimeMillis()}] ✏️ manual mode → openForm CREATE (legacy path)",
                    )
                    openForm(AllergyFormMode.CREATE)
                }
                .show(parentFragmentManager, "add_allergy_assistant")
        }

        // 🆕 Lot 14.5c26 — Listener pour le mode picked par le bottom sheet
        // assistant. Si l'user choisit "voice", on navigate vers le chat
        // vocal allergies. Les autres modes (photo, livescan, text) sont
        // grisés dans le sheet pour le pillar ALLERGIES, donc on ne devrait
        // pas les recevoir, mais on les logge au cas où.
        parentFragmentManager.setFragmentResultListener(
            AddItemWithAssistantBottomSheet.RESULT_KEY_MODE_PICKED,
            viewLifecycleOwner,
        ) { _, bundle ->
            val mode = bundle.getString(AddItemWithAssistantBottomSheet.RESULT_MODE)
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🤖 assistant mode picked · mode=$mode")
            when (mode) {
                "voice" -> {
                    val pid = argProfileId ?: profilesRepo.currentProfileId
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🚦 navigate to allergies_chat · profileId=$pid")
                    val args = Bundle().apply {
                        if (pid != null) putString("profileId", pid)
                    }
                    findNavController().navigate(R.id.action_allergies_to_chat, args)
                }
                else -> {
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ unsupported mode for allergies: $mode")
                }
            }
        }

        parentFragmentManager.setFragmentResultListener(
            AllergyFormBottomSheet.RESULT_KEY, viewLifecycleOwner,
        ) { _, bundle ->
            handleFormResult(bundle)
        }

        // 🆕 Lot 14.5c22 — Race condition fix : si `current` est déjà set
        // (cas où on revient depuis le chat ou un sous-écran : le Fragment
        // instance survit dans la backstack, seule la View est recréée),
        // on ne refait PAS un loadAndRender() async depuis le disque.
        //
        // Pourquoi : si un pending setFragmentResult arrive entre le
        // launch{} de loadAndRender et son exécution réelle, on a :
        //   1. handleFormResult → allergies.add(newAllergy) → persist
        //   2. loadAndRender coroutine se réveille → loadProfile renvoie
        //      l'état d'avant le write (cache ou flush incomplet) →
        //      allergies.clear() + addAll(old) → ÉCRASE la nouvelle allergie
        //
        // Avec ce guard, le retour depuis le chat fait juste un renderList()
        // sur la liste en mémoire (qui aura été updated par handleFormResult).
        // Le loadAndRender disque ne se fait qu'à la PREMIÈRE création du
        // fragment (current == null).
        if (current != null) {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 🔁 view recreated · profile already in memory · " +
                    "rendering ${allergies.size} allergies without disk reload",
            )
            renderList()
        } else {
            loadAndRender()
        }
    }

    private fun loadAndRender() {
        val pid = argProfileId ?: profilesRepo.currentProfileId
        if (pid == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ no current profile · empty state")
            showEmptyStateNoProfile()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            current = profilesRepo.loadProfile(pid)
            if (current == null) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ profile $pid not found · empty state")
                showEmptyStateNoProfile()
                return@launch
            }
            allergies.clear()
            allergies.addAll(current!!.al)
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 loaded ${allergies.size} allergy(ies) · " +
                "profileId=$pid · codes=${allergies.mapNotNull { it.c }}")
            renderList()
        }
    }

    private fun renderList() {
        adapter.submitList(allergies.toList())
        binding.allergiesEmptyState.visibility =
            if (allergies.isEmpty()) View.VISIBLE else View.GONE
        binding.allergiesRecycler.visibility =
            if (allergies.isEmpty()) View.GONE else View.VISIBLE
        binding.allergiesCount.text = resources.getQuantityString(
            R.plurals.allergies_count, allergies.size, allergies.size,
        )
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 🎨 rendered list · size=${allergies.size}")
    }

    private fun showEmptyStateNoProfile() {
        binding.allergiesEmptyState.visibility = View.VISIBLE
        binding.allergiesRecycler.visibility = View.GONE
        binding.allergiesFabAdd.isEnabled = false
        binding.allergiesEmptyText.setText(R.string.allergies_empty_no_profile)
    }

    // ─── Form dispatchers ─────────────────────────────────────────

    private fun openForm(
        mode: AllergyFormMode,
        existing: JAllergy? = null,
        index: Int = -1,
    ) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 open form · mode=$mode · idx=$index · " +
            "existing.code=${existing?.c}")
        AllergyFormBottomSheet
            .newInstance(mode, currentLang, index, existing,
                profileId = argProfileId ?: profilesRepo.currentProfileId)
            .show(parentFragmentManager, "allergy_form")
    }

    private fun handleFormResult(bundle: Bundle) {
        val mode = AllergyFormMode.valueOf(
            bundle.getString(AllergyFormBottomSheet.ARG_MODE) ?: AllergyFormMode.CREATE.name
        )
        val idx = bundle.getInt(AllergyFormBottomSheet.ARG_INDEX, -1)

        // 🆕 v2.6.0j — Build reaction structured if manifestation code present
        val reactCode = bundle.getString(AllergyFormBottomSheet.ARG_REACTION_MANIF_CODE)
        val newReactions: List<JReaction> = if (!reactCode.isNullOrBlank()) {
            listOf(
                JReaction(
                    manifestationCode = reactCode,
                    manifestationDisplay = bundle.getString(AllergyFormBottomSheet.ARG_REACTION_MANIF_DISPLAY),
                    manifestationSystem = bundle.getString(AllergyFormBottomSheet.ARG_REACTION_MANIF_SYSTEM),
                    severity = bundle.getString(AllergyFormBottomSheet.ARG_REACTION_SEVERITY),
                ),
            )
        } else emptyList()

        val newAllergy = JAllergy(
            c = bundle.getString(AllergyFormBottomSheet.ARG_CODE),
            s = bundle.getString(AllergyFormBottomSheet.ARG_SEVERITY),
            st = bundle.getString(AllergyFormBottomSheet.ARG_STATUS),
            d = bundle.getString(AllergyFormBottomSheet.ARG_NOTES),
            m = bundle.getString(AllergyFormBottomSheet.ARG_MECHANISM),
            displayLabel = bundle.getString(AllergyFormBottomSheet.ARG_DISPLAY),
            // 🆕 v2.6.0j — IPS-FULL fields
            codeSystem = bundle.getString(AllergyFormBottomSheet.ARG_CODE_SYSTEM),
            type = bundle.getString(AllergyFormBottomSheet.ARG_TYPE),
            category = bundle.getString(AllergyFormBottomSheet.ARG_CATEGORY),
            onset = bundle.getString(AllergyFormBottomSheet.ARG_ONSET),
            reactions = newReactions,
        )

        when (mode) {
            AllergyFormMode.CREATE -> {
                allergies.add(newAllergy)
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ➕ added allergy · " +
                    "code=${newAllergy.c} · s=${newAllergy.s} · type=${newAllergy.type} · " +
                    "cat=${newAllergy.category} · onset=${newAllergy.onset} · " +
                    "rxns=${newAllergy.reactions.size} · total=${allergies.size}")
            }
            AllergyFormMode.EDIT -> {
                if (idx in allergies.indices) {
                    // UC-ALG-004 — the form only carries one reaction : merge it into
                    // the stored entry so the reactions it does not show are kept.
                    val merged = AllergyFormMerge.apply(allergies[idx], newAllergy)
                    allergies[idx] = merged
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✏ updated allergy · " +
                        "idx=$idx · code=${merged.c} · type=${merged.type} · " +
                        "cat=${merged.category} · rxns=${merged.reactions.size}")
                } else {
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ EDIT with invalid idx=$idx · skipped")
                }
            }
        }
        renderList()
        persistAllergies()
    }

    // ─── Delete confirm ───────────────────────────────────────────

    private fun confirmDelete(allergy: JAllergy, position: Int) {
        val label = allergy.displayLabel?.takeIf { it.isNotBlank() }
            ?: allergy.c
            ?: getString(R.string.allergies_unnamed)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 confirm delete dialog · pos=$position · label=$label")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.allergies_delete_title)
            .setMessage(getString(R.string.allergies_delete_message, label))
            .setNegativeButton(R.string.allergies_delete_cancel) { d, _ -> d.dismiss() }
            .setPositiveButton(R.string.allergies_delete_confirm) { d, _ ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 deleting · pos=$position · label=$label")
                if (position in allergies.indices) {
                    allergies.removeAt(position)
                    renderList()
                    persistAllergies()
                }
                d.dismiss()
            }
            .show()
    }

    // ─── Persist ──────────────────────────────────────────────────

    private fun persistAllergies() {
        val baseProfile = current ?: run {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ persist called with no current profile · skipped")
            return
        }
        val updatedProfile = baseProfile.copy(al = allergies.toList())
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 persist · " +
            "profileId=${baseProfile.sid} · al.size=${allergies.size}")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = profilesRepo.saveProfile(updatedProfile, sourceFormat = "MANUAL_EDIT")
                current = updatedProfile
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ saved · id=${result.id}")
                Toast.makeText(requireContext(), R.string.allergies_saved, Toast.LENGTH_SHORT).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // saveProfile rethrows write errors : tell the user instead of crashing.
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ save failed: ${e.message}", e)
                context?.let {
                    Toast.makeText(it, getString(R.string.assistant_save_failed, e.message.orEmpty()),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩹 onDestroyView")
        _binding = null
    }
}
