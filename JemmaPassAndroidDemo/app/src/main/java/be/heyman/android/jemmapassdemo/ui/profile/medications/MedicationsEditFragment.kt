/*
 * MedicationsEditFragment.kt — JEMMA Pass · Plan B · v2.6.0 · L5d
 *
 * Édition manuelle du pilier FHIR MedicationStatement IPS.
 * Persisté dans JemmaProfileJ.md : List<JMedication>.
 *
 * Couverture FHIR :
 *   MedicationStatement.medicationCodeableConcept → JMedication.c   (ATC/RxNorm)
 *   MedicationStatement.dosage.timing.text        → JMedication.t
 *   MedicationStatement.dosage.route              → JMedication.r   (O/I/T/S)
 *   MedicationStatement.dosage.doseAndRate.dose.value → JMedication.v
 *   MedicationStatement.dosage.doseAndRate.dose.unit  → JMedication.u
 *   MedicationStatement.reasonCode.text           → JMedication.rs
 *
 * Layout :
 *   AppBarLayout > MaterialToolbar (back ←)
 *   LinearLayout (scrolling_behavior) :
 *     - Hero "💊 Médicaments · N"
 *     - RecyclerView des rows JMedication
 *     - Empty state ou liste
 *   FAB + en bas droite
 *
 * Flow :
 *   1. onViewCreated : load profile via argProfileId / currentProfileId
 *   2. RecyclerView affiche current.md
 *   3. FAB + → MedicationFormBottomSheet (mode CREATE)
 *   4. Tap row → mode EDIT pré-rempli
 *   5. Long-press → confirm delete
 *   6. FragmentResult listener → mise à jour + saveProfile
 *
 * Tag log : JEMMA-MEDICATIONS-EDIT
 */
package be.heyman.android.jemmapassdemo.ui.profile.medications

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
import be.heyman.android.jemmapassdemo.databinding.FragmentMedicationsEditBinding
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.ui.assistant.AddItemWithAssistantBottomSheet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MedicationsEditFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-MEDICATIONS-EDIT"
        private const val ARG_PROFILE_ID = "profileId"
    }

    @Inject
    lateinit var profilesRepo: ProfilesRepository

    @Inject
    lateinit var kb: KnowledgeBaseService

    private var _binding: FragmentMedicationsEditBinding? = null
    private val binding get() = _binding!!

    private val argProfileId: String? by lazy { arguments?.getString(ARG_PROFILE_ID) }

    private var current: JemmaProfileJ? = null
    private val medications = mutableListOf<JMedication>()
    private lateinit var adapter: MedicationsAdapter

    private val currentLang: String by lazy {
        Locale.getDefault().language.lowercase().take(2)
    }

    /** 🆕 Lot 14.5a — Helper pour picker caméra/galerie + permission. */
    private lateinit var photoHelper: be.heyman.android.jemmapassdemo.ui.assistant.AssistantPhotoCaptureHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 🆕 Lot 14.5b — register photo helper en onCreate(). Le helper
        // utilise désormais Document Scanner ML Kit qui peut retourner
        // 1-20 pages en une session (multi-page natif + import galerie).
        photoHelper = be.heyman.android.jemmapassdemo.ui.assistant
            .AssistantPhotoCaptureHelper.register(this) { uris ->
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 📸 ${uris.size} page(s) ready → navigate to assistant pipeline",
                )
                // 🔧 Lot 14.5c2 — Set le profil cible AVANT le navigate.
                // Sinon le save batch retombe sur `profilesRepo.currentProfileId`
                // (= profil ⭐ actif global) au lieu du profil que l'user édite
                // vraiment ici. Le pid résolu = arg explicite OU fallback current.
                val targetPid = argProfileId ?: profilesRepo.currentProfileId
                be.heyman.android.jemmapassdemo.ai.assistant.AssistantHandoff
                    .setTargetProfile(targetPid)
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🎯 handoff target profile set · pid=$targetPid",
                )
                // Joins les N URIs avec '|' (séparateur impossible dans une URI).
                val joined = uris.joinToString("|") { it.toString() }
                val args = androidx.core.os.bundleOf(
                    "pillar" to "MEDICATIONS",
                    "mode" to "photo",
                    "imageUris" to joined,
                )
                findNavController().navigate(R.id.action_medications_to_assistant_pipeline, args)
            }

        // 🆕 Lot 14.5a — listen le FragmentResult émis par le bottom sheet
        // quand l'user choisit "📸 Scanner". Le mode=photo déclenche le
        // chooser caméra/galerie.
        parentFragmentManager.setFragmentResultListener(
            be.heyman.android.jemmapassdemo.ui.assistant
                .AddItemWithAssistantBottomSheet.RESULT_KEY_MODE_PICKED,
            this,
        ) { _, bundle ->
            val mode = bundle.getString(
                be.heyman.android.jemmapassdemo.ui.assistant
                    .AddItemWithAssistantBottomSheet.RESULT_MODE,
            )
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 🤖 assistant mode picked · mode=$mode",
            )
            if (mode == "photo") {
                photoHelper.showSourceChooser()
            }
            // Les autres modes (livescan/voice/text) arrivent au lot 14.6/14.7.
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMedicationsEditBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💊 onCreateView · argProfileId=$argProfileId")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💊 onViewCreated · lang=$currentLang")

        // 🆕 L_HERO_BAR — `medications_toolbar_back` est maintenant un ImageButton
        // (anciennement MaterialToolbar), donc setOnClickListener et plus
        // setNavigationOnClickListener.
        binding.medicationsToolbarBack.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 back tap → navigateUp")
            findNavController().navigateUp()
        }

        adapter = MedicationsAdapter(
            lang = currentLang,
            kb = kb,
            scope = viewLifecycleOwner.lifecycleScope,
            onTap = { med, position ->
                openForm(MedicationFormMode.EDIT, existing = med, index = position)
            },
            onLongPress = { med, position ->
                confirmDelete(med, position)
            },
        )
        binding.medicationsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.medicationsRecycler.adapter = adapter

        binding.medicationsFabAdd.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 FAB add tap · opening assistant bottom sheet")
            // 🆕 Lot 14.4 — le FAB ouvre désormais le bottom sheet
            // d'Assistant Jemma (4 modes IA + 1 mode manuel). Le mode
            // "Saisir manuellement" rappelle openForm() en CREATE comme
            // avant pour ne pas casser le flow existant.
            AddItemWithAssistantBottomSheet
                .newInstance(AddItemWithAssistantBottomSheet.Companion.Pillar.MEDICATIONS)
                .setOnManualPicked {
                    Log.i(
                        TAG,
                        "[t=${System.currentTimeMillis()}] ✏️ manual mode → openForm CREATE (legacy path)",
                    )
                    openForm(MedicationFormMode.CREATE)
                }
                .show(parentFragmentManager, "add_medication_assistant")
        }

        parentFragmentManager.setFragmentResultListener(
            MedicationFormBottomSheet.RESULT_KEY, viewLifecycleOwner,
        ) { _, bundle ->
            handleFormResult(bundle)
        }

        loadAndRender()
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
            medications.clear()
            medications.addAll(current!!.md)
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 loaded ${medications.size} med(s) · " +
                "profileId=$pid · codes=${medications.mapNotNull { it.c }}")
            renderList()
        }
    }

    private fun renderList() {
        adapter.submitList(medications.toList())
        binding.medicationsEmptyState.visibility =
            if (medications.isEmpty()) View.VISIBLE else View.GONE
        binding.medicationsRecycler.visibility =
            if (medications.isEmpty()) View.GONE else View.VISIBLE
        binding.medicationsCount.text = resources.getQuantityString(
            R.plurals.medications_count, medications.size, medications.size,
        )
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 🎨 rendered list · size=${medications.size}")
    }

    private fun showEmptyStateNoProfile() {
        binding.medicationsEmptyState.visibility = View.VISIBLE
        binding.medicationsRecycler.visibility = View.GONE
        binding.medicationsFabAdd.isEnabled = false
        binding.medicationsEmptyText.setText(R.string.medications_empty_no_profile)
    }

    private fun openForm(
        mode: MedicationFormMode,
        existing: JMedication? = null,
        index: Int = -1,
    ) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 open form · mode=$mode · idx=$index · " +
            "existing.code=${existing?.c}")
        MedicationFormBottomSheet
            .newInstance(mode, currentLang, index, existing,
                profileId = argProfileId ?: profilesRepo.currentProfileId)
            .show(parentFragmentManager, "medication_form")
    }

    private fun handleFormResult(bundle: Bundle) {
        val mode = MedicationFormMode.valueOf(
            bundle.getString(MedicationFormBottomSheet.ARG_MODE) ?: MedicationFormMode.CREATE.name,
        )
        val idx = bundle.getInt(MedicationFormBottomSheet.ARG_INDEX, -1)
        val newMed = JMedication(
            c = bundle.getString(MedicationFormBottomSheet.ARG_CODE),
            t = bundle.getString(MedicationFormBottomSheet.ARG_TIMING),
            r = bundle.getString(MedicationFormBottomSheet.ARG_ROUTE),
            v = bundle.getString(MedicationFormBottomSheet.ARG_DOSE_VALUE),
            u = bundle.getString(MedicationFormBottomSheet.ARG_DOSE_UNIT),
            rs = bundle.getString(MedicationFormBottomSheet.ARG_REASON),
            rc = bundle.getString(MedicationFormBottomSheet.ARG_REASON_CODE),
            displayLabel = bundle.getString(MedicationFormBottomSheet.ARG_DISPLAY),
            // 🆕 v2.6.0k — IPS-FULL fields
            codeSystem = bundle.getString(MedicationFormBottomSheet.ARG_CODE_SYSTEM),
            status = bundle.getString(MedicationFormBottomSheet.ARG_STATUS),
            effective = bundle.getString(MedicationFormBottomSheet.ARG_EFFECTIVE),
        )

        when (mode) {
            MedicationFormMode.CREATE -> {
                medications.add(newMed)
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ➕ added med · " +
                    "code=${newMed.c} · r=${newMed.r} · " +
                    "status=${newMed.status} · effective=${newMed.effective} · " +
                    "rc=${newMed.rc} · cs=${newMed.codeSystem} · total=${medications.size}")
            }
            MedicationFormMode.EDIT -> {
                if (idx in medications.indices) {
                    // Keep what the form does not edit (effective absence reason).
                    medications[idx] = MedicationFormLogic.apply(medications[idx], newMed)
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✏ updated med · " +
                        "idx=$idx · code=${newMed.c} · r=${newMed.r} · " +
                        "status=${newMed.status} · rc=${newMed.rc}")
                } else {
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ EDIT with invalid idx=$idx · skipped")
                }
            }
        }
        renderList()
        persistMedications()
    }

    private fun confirmDelete(med: JMedication, position: Int) {
        val label = med.displayLabel?.takeIf { it.isNotBlank() }
            ?: med.c
            ?: getString(R.string.medications_unnamed)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 confirm delete dialog · pos=$position · label=$label")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.medications_delete_title)
            .setMessage(getString(R.string.medications_delete_message, label))
            .setNegativeButton(R.string.medications_delete_cancel) { d, _ -> d.dismiss() }
            .setPositiveButton(R.string.medications_delete_confirm) { d, _ ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 deleting · pos=$position · label=$label")
                if (position in medications.indices) {
                    medications.removeAt(position)
                    renderList()
                    persistMedications()
                }
                d.dismiss()
            }
            .show()
    }

    private fun persistMedications() {
        val baseProfile = current ?: run {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ persist called with no current profile · skipped")
            return
        }
        val updatedProfile = baseProfile.copy(md = medications.toList())
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 persist · " +
            "profileId=${baseProfile.sid} · md.size=${medications.size}")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = profilesRepo.saveProfile(updatedProfile, sourceFormat = "MANUAL_EDIT")
                current = updatedProfile
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ saved · id=${result.id}")
                Toast.makeText(requireContext(), R.string.medications_saved, Toast.LENGTH_SHORT).show()
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
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💊 onDestroyView")
        _binding = null
    }
}
