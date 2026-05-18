/*
 * ContactsEditFragment.kt — JEMMA Pass · Plan B · v2.6.0 · L5b
 *
 * Édition manuelle du pilier FHIR Patient.contact[*] (IPS).
 *
 * Persistés dans JPatient.ct : List<JContact> (n/r/p/e/adr).
 * Pas un Resource FHIR standalone — c'est un sous-élément de Patient,
 * cohérent avec le QR _j 1.2 où "ct" est sous "p" (= patient).
 *
 * Couverture FHIR :
 *   Patient.contact.name.text                → JContact.n
 *   Patient.contact.relationship.coding[0]   → JContact.r  (V3-RoleCode)
 *   Patient.contact.telecom (phone)          → JContact.p
 *   Patient.contact.telecom (email)          → JContact.e
 *   Patient.contact.address                  → JContact.adr (1 ligne libre)
 *
 * Flow :
 *   1. onViewCreated : load profile via argProfileId / currentProfileId
 *   2. RecyclerView affiche current.p.ct
 *   3. FAB + → ContactFormBottomSheet (mode CREATE)
 *   4. Tap item → ContactFormBottomSheet (mode EDIT) avec values pré-remplies
 *   5. Long-press item → MaterialAlertDialog confirm delete → persist
 *   6. FragmentResult listener reçoit le submit du bottom sheet → mise à jour
 *      liste + reconstruction JemmaProfileJ + saveProfile()
 *
 * Tag log : JEMMA-CONTACTS-EDIT · emoji 📞 lifecycle / 📋 list / 💾 save / 🗑 delete / ⚠ validation
 */
package be.heyman.android.jemmapassdemo.ui.profile.contacts

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
import be.heyman.android.jemmapassdemo.databinding.FragmentContactsEditBinding
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import be.heyman.android.jemmapassdemo.qr.JContact
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ContactsEditFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-CONTACTS-EDIT"
        private const val ARG_PROFILE_ID = "profileId"
    }

    @Inject
    lateinit var profilesRepo: ProfilesRepository

    private var _binding: FragmentContactsEditBinding? = null
    private val binding get() = _binding!!

    private val argProfileId: String? by lazy { arguments?.getString(ARG_PROFILE_ID) }

    /** Loaded profile — null = no active profile (must create patient first). */
    private var current: JemmaProfileJ? = null

    /** Working copy of contacts list. Mutated by add/edit/delete callbacks. */
    private val contacts = mutableListOf<JContact>()

    private lateinit var adapter: ContactsAdapter

    /** Resolved at first onViewCreated — 2-letter lang code. */
    private val currentLang: String by lazy {
        Locale.getDefault().language.lowercase().take(2)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentContactsEditBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📞 onCreateView · argProfileId=$argProfileId")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📞 onViewCreated · lang=$currentLang")

        // 🆕 L_HERO_BAR — `contacts_toolbar_back` est maintenant un ImageButton
        // (anciennement MaterialToolbar). Plus besoin du dual listener.
        binding.contactsToolbarBack.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 back tap → navigateUp")
            findNavController().navigateUp()
        }

        adapter = ContactsAdapter(
            lang = currentLang,
            onTap = { contact, position ->
                openForm(ContactFormMode.EDIT, existing = contact, index = position)
            },
            onLongPress = { contact, position ->
                confirmDelete(contact, position)
            },
        )
        binding.contactsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.contactsRecycler.adapter = adapter

        binding.contactsFabAdd.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 FAB add tap")
            openForm(ContactFormMode.CREATE)
        }

        // Listen for form results (works for both CREATE and EDIT)
        parentFragmentManager.setFragmentResultListener(
            ContactFormBottomSheet.RESULT_KEY, viewLifecycleOwner,
        ) { _, bundle ->
            handleFormResult(bundle)
        }

        loadAndRender()
    }

    /**
     * Load the active profile, populate the contacts working copy,
     * submit to adapter. If no profile id available, show inline
     * empty-state telling the user to create the patient first.
     */
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
            contacts.clear()
            contacts.addAll(current!!.p?.ct ?: emptyList())
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 loaded ${contacts.size} contact(s) · " +
                "profileId=$pid")
            renderList()
        }
    }

    private fun renderList() {
        adapter.submitList(contacts.toList())  // toList() = defensive copy for DiffUtil
        binding.contactsEmptyState.visibility =
            if (contacts.isEmpty()) View.VISIBLE else View.GONE
        binding.contactsRecycler.visibility =
            if (contacts.isEmpty()) View.GONE else View.VISIBLE
        binding.contactsCount.text = resources.getQuantityString(
            R.plurals.contacts_count, contacts.size, contacts.size,
        )
    }

    private fun showEmptyStateNoProfile() {
        binding.contactsEmptyState.visibility = View.VISIBLE
        binding.contactsRecycler.visibility = View.GONE
        binding.contactsFabAdd.isEnabled = false
        binding.contactsEmptyText.setText(R.string.contacts_empty_no_profile)
    }

    // ─── Form open dispatchers ────────────────────────────────────

    private fun openForm(
        mode: ContactFormMode,
        existing: JContact? = null,
        index: Int = -1,
    ) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 open form · mode=$mode · idx=$index")
        ContactFormBottomSheet
            .newInstance(mode, currentLang, index, existing)
            .show(parentFragmentManager, "contact_form")
    }

    // ─── Form result handler ──────────────────────────────────────

    private fun handleFormResult(bundle: Bundle) {
        val mode = ContactFormMode.valueOf(
            bundle.getString(ContactFormBottomSheet.ARG_MODE) ?: ContactFormMode.CREATE.name
        )
        val idx = bundle.getInt(ContactFormBottomSheet.ARG_INDEX, -1)
        val newContact = JContact(
            n = bundle.getString(ContactFormBottomSheet.ARG_NAME),
            r = bundle.getString(ContactFormBottomSheet.ARG_RELATION),
            p = bundle.getString(ContactFormBottomSheet.ARG_PHONE),
            e = bundle.getString(ContactFormBottomSheet.ARG_EMAIL),
            adr = bundle.getString(ContactFormBottomSheet.ARG_ADDRESS),
        )

        when (mode) {
            ContactFormMode.CREATE -> {
                contacts.add(newContact)
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ➕ added contact · " +
                    "name=${newContact.n} · rel=${newContact.r} · total=${contacts.size}")
            }
            ContactFormMode.EDIT -> {
                if (idx in contacts.indices) {
                    contacts[idx] = newContact
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✏ updated contact · " +
                        "idx=$idx · name=${newContact.n} · rel=${newContact.r}")
                } else {
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ EDIT with invalid idx=$idx · skipped")
                }
            }
        }
        renderList()
        persistContacts()
    }

    // ─── Delete confirm ───────────────────────────────────────────

    private fun confirmDelete(contact: JContact, position: Int) {
        val name = contact.n?.takeIf { it.isNotBlank() } ?: getString(R.string.contacts_unnamed)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.contacts_delete_title)
            .setMessage(getString(R.string.contacts_delete_message, name))
            .setNegativeButton(R.string.contacts_delete_cancel) { d, _ -> d.dismiss() }
            .setPositiveButton(R.string.contacts_delete_confirm) { d, _ ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 deleting contact · pos=$position · name=$name")
                if (position in contacts.indices) {
                    contacts.removeAt(position)
                    renderList()
                    persistContacts()
                }
                d.dismiss()
            }
            .show()
    }

    // ─── Persist ──────────────────────────────────────────────────

    /**
     * Reconstruit JemmaProfileJ.p.ct = current contacts list, save via
     * ProfilesRepository. Idempotent (peut être appelé après chaque CRUD).
     */
    private fun persistContacts() {
        val baseProfile = current ?: run {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ persist called with no current profile · skipped")
            return
        }
        val basePatient = baseProfile.p ?: JPatient()
        val updatedPatient = basePatient.copy(ct = contacts.toList())
        val updatedProfile = baseProfile.copy(p = updatedPatient)

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 persist · " +
            "profileId=${baseProfile.sid} · ct.size=${contacts.size}")

        viewLifecycleOwner.lifecycleScope.launch {
            val result = profilesRepo.saveProfile(updatedProfile, sourceFormat = "MANUAL_EDIT")
            current = updatedProfile
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ saved · id=${result.id}")
            Toast.makeText(requireContext(), R.string.contacts_saved, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📞 onDestroyView")
        _binding = null
    }
}
