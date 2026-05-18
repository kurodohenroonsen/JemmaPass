/*
 * ContactFormBottomSheet.kt — JEMMA Pass · Plan B · v2.6.0 · L5b · L_BLINDAGE
 *
 * v2.6.0 BLINDAGE (2026-05-13) — 5 fixes :
 *   1. Phone normalize E.164 à la sauvegarde via PhoneNumberHelper
 *   2. Phone validation soft (toast warn si format suspect)
 *   3. "Dr X" / "Pr X" / "Prof. X" prénom → auto-pick relation MEDIC
 *   4. TalkBack a11y sur relation picker + email field
 *   5. lang.take(2) BCP-47 truncate
 *
 * Bottom sheet pour CREATE ou EDIT un JContact (FHIR Patient.contact[]).
 *
 * Champs :
 *   - Nom (n) : TextInputEditText, required
 *   - Relation (r) : Tappable card → IpsCodePickerDialog (39 V3-RoleCode)
 *   - Téléphone (p) : TextInputEditText inputType=phone, optional
 *   - Email (e) : TextInputEditText inputType=email, optional
 *   - Adresse (adr) : TextInputEditText multiline, optional
 *
 * Mode :
 *   - CREATE : args = mode=CREATE, no index
 *   - EDIT : args = mode=EDIT, index=position, + existing values
 *
 * Result :
 *   Envoie un Bundle via setFragmentResult(RESULT_KEY, bundle) avec :
 *     - ARG_MODE : "CREATE" | "EDIT"
 *     - ARG_INDEX : Int (EDIT only, -1 sinon)
 *     - ARG_NAME, ARG_RELATION, ARG_PHONE, ARG_EMAIL, ARG_ADDRESS
 *   Parent ContactsEditFragment écoute via setFragmentResultListener.
 *
 * Logging : tag JEMMA-CONTACTS-FORM
 */
package be.heyman.android.jemmapassdemo.ui.profile.contacts

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.BottomSheetContactFormBinding
import be.heyman.android.jemmapassdemo.pillars.IpsRelationshipCatalog
import be.heyman.android.jemmapassdemo.qr.JContact
import be.heyman.android.jemmapassdemo.ui.common.IpsCodePickerDialog
import be.heyman.android.jemmapassdemo.ui.common.IpsPickerItem
import be.heyman.android.jemmapassdemo.ui.profile.common.FormA11yHelpers
import be.heyman.android.jemmapassdemo.ui.profile.common.PhoneNumberHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

enum class ContactFormMode { CREATE, EDIT }

class ContactFormBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "JEMMA-CONTACTS-FORM"

        const val RESULT_KEY = "contact_form_result"
        const val ARG_MODE = "mode"
        const val ARG_INDEX = "index"
        const val ARG_NAME = "name"
        const val ARG_RELATION = "relation"
        const val ARG_PHONE = "phone"
        const val ARG_EMAIL = "email"
        const val ARG_ADDRESS = "address"
        const val ARG_LANG = "lang"
        // 🆕 L_BLINDAGE — country hint pour normaliser le phone E.164
        const val ARG_COUNTRY_HINT = "country_hint"

        // 🆕 L_BLINDAGE — Detection automatique "Dr X" / "Pr X" / "Prof X" → relation MEDIC
        // V3-RoleCode "MEDPROVR" est code IPS pour Medical provider/Doctor.
        // On commence par valider en regex insensible casse + accents.
        private val DOCTOR_TITLE_REGEX = Regex(
            "^(d[r]?\\.?|prof?\\.?|m[ée]decin)\\s+\\S+",
            RegexOption.IGNORE_CASE,
        )
        private const val MEDIC_ROLE_CODE = "MEDPROVR"

        fun newInstance(
            mode: ContactFormMode,
            lang: String,
            index: Int = -1,
            existing: JContact? = null,
            countryHint: String? = null,
        ): ContactFormBottomSheet = ContactFormBottomSheet().apply {
            arguments = bundleOf(
                ARG_MODE to mode.name,
                ARG_INDEX to index,
                ARG_LANG to lang,
                ARG_NAME to existing?.n,
                ARG_RELATION to existing?.r,
                ARG_PHONE to existing?.p,
                ARG_EMAIL to existing?.e,
                ARG_ADDRESS to existing?.adr,
                ARG_COUNTRY_HINT to countryHint,
            )
        }
    }

    private var _binding: BottomSheetContactFormBinding? = null
    private val binding get() = _binding!!

    private val mode: ContactFormMode by lazy {
        ContactFormMode.valueOf(arguments?.getString(ARG_MODE) ?: ContactFormMode.CREATE.name)
    }
    private val index: Int by lazy { arguments?.getInt(ARG_INDEX, -1) ?: -1 }
    // 🆕 L_BLINDAGE — truncate BCP-47 ("fr-BE" → "fr") so catalog lookups match
    private val lang: String by lazy {
        (arguments?.getString(ARG_LANG) ?: "en").take(2).lowercase()
    }
    /** 🆕 L_BLINDAGE — Country ISO 3166 hint for phone normalize ("BE", "JP"…). */
    private val countryHint: String? by lazy { arguments?.getString(ARG_COUNTRY_HINT) }

    /** Currently picked relation code (V3-RoleCode like "FTH"). */
    private var pickedRelationCode: String? = null

    /**
     * 🆕 L_BLINDAGE — Tracks whether we already auto-detected MEDIC from a
     * "Dr X" name. Prevents overwriting the user's manual relation choice
     * if they edit the name AFTER picking a relation.
     */
    private var autoDetectedMedic: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetContactFormBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView · mode=$mode · idx=$index · lang=$lang")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.contactFormTitle.setText(
            if (mode == ContactFormMode.CREATE) R.string.contact_form_title_create
            else R.string.contact_form_title_edit
        )

        // Pre-fill
        binding.contactFormName.setText(arguments?.getString(ARG_NAME).orEmpty())
        binding.contactFormPhone.setText(arguments?.getString(ARG_PHONE).orEmpty())
        binding.contactFormEmail.setText(arguments?.getString(ARG_EMAIL).orEmpty())
        binding.contactFormAddress.setText(arguments?.getString(ARG_ADDRESS).orEmpty())
        pickedRelationCode = arguments?.getString(ARG_RELATION)
        renderRelationLabel()

        // 🆕 L_BLINDAGE — TalkBack a11y: mark relation card as picker
        FormA11yHelpers.markAsPicker(
            view = binding.contactFormRelationCard,
            pickerHint = getString(R.string.contact_form_a11y_relation_picker,
                pickedRelationCode?.let { IpsRelationshipCatalog.getDisplay(it, lang) }
                    ?: getString(R.string.a11y_state_empty)),
            currentValue = pickedRelationCode,
        )

        // 🆕 L_BLINDAGE — phone field a11y hint
        binding.contactFormPhone.contentDescription =
            getString(R.string.contact_form_a11y_phone_hint)

        // 🆕 L_BLINDAGE — "Dr X" / "Pr X" auto-detect → pre-pick MEDIC role
        // Only triggers in CREATE mode and if no relation already set.
        binding.contactFormName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val name = s?.toString().orEmpty().trim()
                if (mode != ContactFormMode.CREATE) return
                if (name.isBlank()) return
                if (DOCTOR_TITLE_REGEX.matches(name)) {
                    // Only auto-set if user didn't already pick something OR we already auto-set
                    if (pickedRelationCode.isNullOrBlank() || autoDetectedMedic) {
                        pickedRelationCode = MEDIC_ROLE_CODE
                        autoDetectedMedic = true
                        renderRelationLabel()
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩺 auto-detected MEDIC from name '$name'")
                        FormA11yHelpers.announce(binding.contactFormRelationCard,
                            getString(R.string.contact_form_a11y_relation_picker,
                                IpsRelationshipCatalog.getDisplay(MEDIC_ROLE_CODE, lang)))
                    }
                } else if (autoDetectedMedic) {
                    // User removed the Dr prefix → unset our auto-pick
                    pickedRelationCode = null
                    autoDetectedMedic = false
                    renderRelationLabel()
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩺 auto-MEDIC cleared (name no longer matches)")
                }
            }
        })

        binding.contactFormRelationCard.setOnClickListener { openRelationPicker() }
        binding.contactFormCancelBtn.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ↩ cancel")
            dismiss()
        }
        binding.contactFormSaveBtn.setOnClickListener { trySubmit() }
    }

    /**
     * Affiche le display localisé du code relation actuel (ou hint si vide).
     */
    private fun renderRelationLabel() {
        val code = pickedRelationCode
        if (code.isNullOrBlank()) {
            binding.contactFormRelationCode.text = ""
            binding.contactFormRelationDisplay.setText(R.string.contact_form_relation_hint)
        } else {
            binding.contactFormRelationCode.text = code
            binding.contactFormRelationDisplay.text = IpsRelationshipCatalog.getDisplay(code, lang)
        }
    }

    /**
     * Ouvre le picker des 39 V3-RoleCode triés par display dans la lang
     * courante. Le code choisi est stocké dans pickedRelationCode et
     * l'UI re-rend.
     */
    private fun openRelationPicker() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 open relation picker · lang=$lang")
        val items = IpsRelationshipCatalog.sortedByDisplay(lang).map { entry ->
            IpsPickerItem(code = entry.code, display = entry.pick(lang))
        }
        IpsCodePickerDialog
            .newInstance(
                title = getString(R.string.contact_form_relation_picker_title),
                items = items,
            )
            .setOnPicked { picked ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ relation picked: ${picked.code}")
                pickedRelationCode = picked.code
                // 🆕 L_BLINDAGE — User manually overrode → disable auto-MEDIC tracking
                autoDetectedMedic = false
                renderRelationLabel()
                // 🆕 L_BLINDAGE — Refresh TalkBack hint + announce
                FormA11yHelpers.markAsPicker(
                    view = binding.contactFormRelationCard,
                    pickerHint = getString(R.string.contact_form_a11y_relation_picker, picked.display),
                    currentValue = picked.code,
                )
                FormA11yHelpers.announce(binding.contactFormRelationCard,
                    getString(R.string.contact_form_a11y_relation_picker, picked.display))
            }
            .show(childFragmentManager, "rel_picker")
    }

    /**
     * Valide les champs et envoie le résultat au parent via FragmentResult.
     * Requirements minimum :
     *   - name non-vide
     *   - au moins 1 des (phone, email, address) — sinon contact inutile
     *
     * Relation est optionnelle (peut être "—" si on ne sait pas).
     */
    private fun trySubmit() {
        val name = binding.contactFormName.text?.toString()?.trim().orEmpty()
        val phone = binding.contactFormPhone.text?.toString()?.trim().orEmpty()
        val email = binding.contactFormEmail.text?.toString()?.trim().orEmpty()
        val address = binding.contactFormAddress.text?.toString()?.trim().orEmpty()
        val relation = pickedRelationCode.orEmpty()

        if (name.isBlank()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: name empty")
            Toast.makeText(requireContext(),
                R.string.contact_form_validation_name, Toast.LENGTH_SHORT).show()
            binding.contactFormName.requestFocus()
            return
        }
        if (phone.isBlank() && email.isBlank() && address.isBlank()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: need at least 1 contact method")
            Toast.makeText(requireContext(),
                R.string.contact_form_validation_at_least_one, Toast.LENGTH_SHORT).show()
            return
        }
        // Email sanity if provided
        if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: email format")
            Toast.makeText(requireContext(),
                R.string.contact_form_validation_email, Toast.LENGTH_SHORT).show()
            binding.contactFormEmail.requestFocus()
            FormA11yHelpers.setErrorAnnounce(binding.contactFormEmail,
                getString(R.string.contact_form_validation_email))
            return
        }

        // 🆕 L_BLINDAGE — Phone validation soft + E.164 normalize
        // We don't BLOCK save on phone format errors (the user might use an
        // extension or a weird format we don't recognize) — we just warn and
        // try to normalize. The save proceeds with the best-effort normalized
        // form so the saved profile has a clean E.164 number when possible.
        val phoneToSave = if (phone.isNotBlank()) {
            val validation = PhoneNumberHelper.validate(phone, hasCountryHint = countryHint != null)
            when (validation) {
                PhoneNumberHelper.ValidationCode.TOO_SHORT,
                PhoneNumberHelper.ValidationCode.TOO_LONG,
                PhoneNumberHelper.ValidationCode.INVALID_CHARS -> {
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ phone validation soft fail: $validation")
                    val msgId = when (validation) {
                        PhoneNumberHelper.ValidationCode.TOO_SHORT -> R.string.phone_validation_too_short
                        PhoneNumberHelper.ValidationCode.TOO_LONG -> R.string.phone_validation_too_long
                        else -> R.string.phone_validation_invalid_chars
                    }
                    Toast.makeText(requireContext(), msgId, Toast.LENGTH_SHORT).show()
                    FormA11yHelpers.setErrorAnnounce(binding.contactFormPhone, getString(msgId))
                    // Soft : we still continue (no return) — let the user proceed
                    PhoneNumberHelper.normalize(phone, countryHint)
                }
                PhoneNumberHelper.ValidationCode.MISSING_PREFIX_WARNING -> {
                    // We can't normalize without a country hint — save as-is + log
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ℹ phone missing prefix · no hint · save as-is")
                    phone
                }
                else -> {
                    val normalized = PhoneNumberHelper.normalize(phone, countryHint)
                    if (normalized != phone) {
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📞 phone normalized: '$phone' → '$normalized'")
                    }
                    normalized
                }
            }
        } else null

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 submit · mode=$mode · idx=$index · " +
            "name='$name' · rel=$relation · p='$phoneToSave' · e='$email' · adr len=${address.length} · " +
            "autoMedic=$autoDetectedMedic")

        setFragmentResult(
            RESULT_KEY,
            bundleOf(
                ARG_MODE to mode.name,
                ARG_INDEX to index,
                ARG_NAME to name,
                ARG_RELATION to relation.ifBlank { null },
                ARG_PHONE to phoneToSave,
                ARG_EMAIL to email.ifBlank { null },
                ARG_ADDRESS to address.ifBlank { null },
            )
        )
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
