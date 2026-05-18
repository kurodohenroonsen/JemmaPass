/*
 * PatientEditFragment.kt — JEMMA Pass · Plan B · v2.6.0 · L5
 *
 * Édition manuelle du pilier FHIR Patient IPS (https://hl7.org/fhir/uv/ips/
 * StructureDefinition-Patient-uv-ips.html).
 *
 * Remplace l'ancien stub `PersoFragment` qui héritait de StubFragmentBase.
 *
 * Champs FHIR IPS couverts (MUST SUPPORT) :
 *   ⭐ Patient.name.given           → JPatient.gn
 *   ⭐ Patient.name.family          → JPatient.fn
 *   ⭐ Patient.gender               → JPatient.gs   (M|F|O|U mapped to administrative-gender)
 *   ⭐ Patient.birthDate            → JPatient.bd   (ISO YYYY-MM-DD, [1..1] required IPS)
 *   ⭐ Patient.address.country      → JPatient.nat  (ISO 3166-1 alpha-2)
 *      Patient.extension.bloodType  → JPatient.bt   (custom JEMMA extension)
 *
 * Champs FHIR IPS non-couverts dans ce sous-lot :
 *   - Patient.telecom  (couvert par pilier Contacts en 1b)
 *   - Patient.identifier  (auto-géré via JemmaProfileJ.sid)
 *   - Patient.generalPractitioner  (couvert par pilier Providers, info-only)
 *
 * Flow :
 *   1. onViewCreated : si arg `profileId` présent → load existant, sinon → new
 *   2. Form pré-rempli, validation à la frappe (birthDate ISO format)
 *   3. Tap "Enregistrer" : reconstruit JemmaProfileJ avec JPatient mis à jour,
 *      appelle ProfilesRepository.saveProfile()
 *   4. Si nouveau profil : setCurrent + navigate back to dest_profiles
 *      Si existant : Toast confirmation + navigate back
 *
 * Logging : tag JEMMA-PERSO-EDIT, emoji 👤 lifecycle / 💾 save / ⚠ validation
 */
package be.heyman.android.jemmapassdemo.ui.profile.perso

import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.patient.PatientDraft
import be.heyman.android.jemmapassdemo.ai.patient.PatientHandoff
import be.heyman.android.jemmapassdemo.databinding.FragmentPersoEditBinding
import be.heyman.android.jemmapassdemo.pillars.IpsIdentifierSystemCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsLanguageCatalog
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import be.heyman.android.jemmapassdemo.qr.JAddress
import be.heyman.android.jemmapassdemo.qr.JIdentifier
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JTelecom
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.ui.assistant.AddItemWithAssistantBottomSheet
import be.heyman.android.jemmapassdemo.ui.assistant.AssistantPhotoCaptureHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PatientEditFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-PERSO-EDIT"
        private const val ARG_PROFILE_ID = "profileId"
        private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        // FHIR administrative-gender (lowercase) ↔ JEMMA short codes
        private val GENDER_MAP = mapOf(
            "M" to "male",
            "F" to "female",
            "O" to "other",
            "U" to "unknown",
        )
        // ISO 3166-1 alpha-2 sample for the picker. Full list in res/values/jemma_countries.xml.
        // Order = JEMMA priority (BE first because dev location, then JP for Misako family,
        // then alpha sort of remaining 50 most populated countries — to expand later).
        private val COUNTRY_FALLBACK_LIST = listOf(
            "BE" to "Belgique",
            "JP" to "日本 / Japan",
            "FR" to "France",
            "DE" to "Deutschland",
            "ES" to "España",
            "IT" to "Italia",
            "NL" to "Nederland",
            "GB" to "United Kingdom",
            "US" to "United States",
            "CA" to "Canada",
            "CH" to "Schweiz",
        )
        // Blood type enum — used by JEMMA Live Scan + verdict UI elsewhere.
        // NOT a strict FHIR extension yet : it's a JEMMA convention persisted
        // in JPatient.bt.
        private val BLOOD_TYPES = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "—")
    }

    @Inject
    lateinit var profilesRepo: ProfilesRepository

    private var _binding: FragmentPersoEditBinding? = null
    private val binding get() = _binding!!

    /** Profile id from nav arg (null = creating a new profile). */
    private val argProfileId: String? by lazy { arguments?.getString(ARG_PROFILE_ID) }

    /** 🆕 c35 — true if we should consume the PatientHandoff at onViewCreated. */
    private val argConsumeHandoff: Boolean by lazy {
        arguments?.getBoolean("consumeHandoff", false) ?: false
    }

    /** The loaded profile (or fresh skeleton if creating new). */
    private var current: JemmaProfileJ? = null

    /** ISO YYYY-MM-DD birthDate currently chosen — single source of truth. */
    private var pickedBirthDateIso: String? = null

    // 🆕 v2.6.0i — IPS-FULL pickers state
    /** Selected identifier system short-code (BE-NRN | JP-MyNumber | ...). */
    private var pickedIdSystem: String? = null
    /** Selected language BCP-47 tag (fr-BE | ja-JP | ...). */
    private var pickedLanguageTag: String? = null
    /** Selected address.use (home | work | temp | old | billing). Default "home". */
    private var pickedAddressUse: String = "home"

    // 🆕 c35 — photo capture helper for vision pipeline.
    private lateinit var photoHelper: AssistantPhotoCaptureHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoHelper = AssistantPhotoCaptureHelper.register(this) { uris ->
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📷 captured ${uris.size} image(s) for patient pipeline")
            if (uris.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.error_no_image_captured), Toast.LENGTH_SHORT).show()
                return@register
            }
            val joined = uris.joinToString("|") { it.toString() }
            val args = androidx.core.os.bundleOf(
                "imageUris" to joined,
                "profileId" to argProfileId,
            )
            findNavController().navigate(R.id.action_perso_to_patient_pipeline, args)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPersoEditBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 👤 onCreateView · argProfileId=$argProfileId · consumeHandoff=$argConsumeHandoff")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 👤 onViewCreated")

        // 🆕 L_HERO_BAR — `perso_toolbar_back` est maintenant un ImageButton
        // (anciennement MaterialToolbar).
        binding.persoToolbarBack.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 back tap → navigateUp")
            findNavController().navigateUp()
        }

        // 🆕 Lot 14.5c35 — CTA banner opens the assistant bottom sheet.
        // Pillar PATIENT activates both photo and voice modes.
        //
        // 🐛 Lot 14.5c35c regression FIXED in c41 :
        // The layout uses <include android:id="@+id/assistant_cta_banner" ...>
        // Android OVERRIDES the included root's id with the include tag's id, so
        // R.id.assistant_cta_banner_root does NOT exist at runtime — the listener
        // would silently no-op. We use R.id.assistant_cta_banner and log if the
        // view is somehow missing (defensive).
        val ctaBanner = view.findViewById<View>(R.id.assistant_cta_banner)
        if (ctaBanner == null) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ FATAL : R.id.assistant_cta_banner not found · CTA tap will not work")
        }
        ctaBanner?.setOnClickListener {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 🚨🚨🚨 patient CTA banner tap → opening bottom sheet 🚨🚨🚨",
            )
            Toast.makeText(requireContext(),
                "Ouverture de l'assistant…", Toast.LENGTH_SHORT).show()
            try {
                AddItemWithAssistantBottomSheet
                    .newInstance(AddItemWithAssistantBottomSheet.Companion.Pillar.PATIENT)
                    .show(parentFragmentManager, "add_patient_assistant")
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ sheet show() called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ sheet show() threw", e)
                Toast.makeText(requireContext(),
                    "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // 🆕 c35 — listen to sheet's mode pick. Photo → open the picker.
        parentFragmentManager.setFragmentResultListener(
            AddItemWithAssistantBottomSheet.RESULT_KEY_MODE_PICKED,
            viewLifecycleOwner,
        ) { _, bundle ->
            val mode = bundle.getString(AddItemWithAssistantBottomSheet.RESULT_MODE)
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🤖 assistant mode picked · mode=$mode")
            when (mode) {
                "photo" -> {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 📷 photo mode → opening photo source chooser")
                    photoHelper.showSourceChooser()
                }
                "voice" -> {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎤 voice mode → navigating to dest_patient_chat (c40)")
                    val voiceArgs = androidx.core.os.bundleOf(
                        "profileId" to (argProfileId ?: ""),
                    )
                    try {
                        findNavController().navigate(
                            R.id.action_perso_to_patient_chat,
                            voiceArgs,
                        )
                    } catch (e: Throwable) {
                        Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ patient_chat navigation failed", e)
                        Toast.makeText(requireContext(),
                            "Erreur navigation voix : ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ unsupported mode for patient: $mode")
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            current = if (argProfileId != null) {
                profilesRepo.loadProfile(argProfileId!!).also {
                    if (it == null) {
                        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ profile $argProfileId not found · creating new")
                    } else {
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📥 loaded profile $argProfileId")
                    }
                }
            } else {
                null
            }
            // 🆕 c35 — if we navigated here from the patient pipeline, the
            // PatientHandoff holds an extracted PatientDraft. Merge it with
            // any existing JPatient (handoff wins on non-null fields) and
            // pre-fill the form with the result.
            val patientFromHandoff: JPatient? = if (argConsumeHandoff) {
                val draft = PatientHandoff.consume()
                if (draft != null) {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🤝 consuming handoff · " +
                        "filled=${draft.filledCount}")
                    mergeDraftIntoPatient(current?.p, draft)
                } else {
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ consumeHandoff=true but handoff empty")
                    current?.p
                }
            } else {
                current?.p
            }
            populateForm(patientFromHandoff)
            if (argConsumeHandoff && patientFromHandoff != null) {
                Toast.makeText(requireContext(),
                    "Champs pré-remplis par l'assistant · vérifie avant d'enregistrer",
                    Toast.LENGTH_LONG).show()
            }
        }

        wireSaveButton()
        wireGenderToggle()
        wireBloodTypePicker()
        wireCountryPicker()
        wireBirthDatePicker()
        // 🆕 v2.6.0i — IPS-FULL pickers
        wireAddressCountryPicker()
        wireAddressUsePicker()
        wireIdentifierSystemPicker()
        wireLanguagePicker()
    }

    /**
     * Pre-fill form fields with values from JPatient, or empty defaults
     * if patient == null (new profile).
     */
    private fun populateForm(patient: JPatient?) {
        binding.persoGivenName.setText(patient?.gn.orEmpty())
        binding.persoFamilyName.setText(patient?.fn.orEmpty())
        // Gender toggle
        val genderShort = patient?.gs?.uppercase() ?: ""
        binding.persoGenderToggle.check(
            when (genderShort) {
                "M" -> R.id.perso_gender_m
                "F" -> R.id.perso_gender_f
                "O" -> R.id.perso_gender_o
                else -> R.id.perso_gender_u
            }
        )
        // Birth date
        pickedBirthDateIso = patient?.bd
        binding.persoBirthDate.setText(patient?.bd.orEmpty())
        // Nationality
        binding.persoNationality.setText(patient?.nat.orEmpty())
        binding.persoNationalityLabel.text = countryLabel(patient?.nat)
        // Blood type
        binding.persoBloodType.text = patient?.bt ?: "—"

        // ─── 🆕 v2.6.0i — IPS-FULL : load structured with legacy fallback ───

        // [Address] : prefer adrs[0] structured, fallback on legacy `adr` text
        val addr0 = patient?.adrs?.firstOrNull()
        if (addr0 != null) {
            binding.persoAddressLine.setText(addr0.line.orEmpty())
            binding.persoAddressCity.setText(addr0.city.orEmpty())
            binding.persoAddressPostal.setText(addr0.postalCode.orEmpty())
            binding.persoAddressCountry.setText(addr0.country.orEmpty())
            binding.persoAddressCountryLabel.text = countryLabel(addr0.country)
            pickedAddressUse = addr0.use?.takeIf { it.isNotBlank() } ?: "home"
        } else {
            // Legacy fallback : dump old single-line `adr` into the line field
            binding.persoAddressLine.setText(patient?.adr.orEmpty())
            binding.persoAddressCity.setText("")
            binding.persoAddressPostal.setText("")
            // Use nationality as default country if address.country absent
            binding.persoAddressCountry.setText(patient?.nat.orEmpty())
            binding.persoAddressCountryLabel.text = countryLabel(patient?.nat)
            pickedAddressUse = "home"
        }
        binding.persoAddressUseLabel.text = addressUseDisplay(pickedAddressUse)

        // [Telecom] : prefer tels[] structured, fallback on legacy tel/eml
        val phoneEntry = patient?.tels?.firstOrNull { it.system == "phone" || it.system == "sms" }
        val emailEntry = patient?.tels?.firstOrNull { it.system == "email" }
        binding.persoTelecomPhone.setText(phoneEntry?.value ?: patient?.tel.orEmpty())
        binding.persoTelecomEmail.setText(emailEntry?.value ?: patient?.eml.orEmpty())

        // [Identifier] : prefer ids[0] structured, fallback on legacy idn
        val id0 = patient?.ids?.firstOrNull()
        if (id0 != null) {
            pickedIdSystem = id0.system?.takeIf { it.isNotBlank() } ?: "OTHER"
            binding.persoIdentifierValue.setText(id0.value.orEmpty())
        } else {
            pickedIdSystem = if (!patient?.idn.isNullOrBlank()) "OTHER" else null
            binding.persoIdentifierValue.setText(patient?.idn.orEmpty())
        }
        binding.persoIdentifierSystemLabel.text = identifierSystemDisplay(pickedIdSystem)

        // [GP]
        binding.persoGp.setText(patient?.gp.orEmpty())

        // [Language]
        pickedLanguageTag = patient?.lang
        binding.persoLanguageLabel.text = languageDisplay(pickedLanguageTag)

        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 form populated · " +
            "gn=${patient?.gn} · fn=${patient?.fn} · gs=$genderShort · " +
            "bd=${patient?.bd} · nat=${patient?.nat} · bt=${patient?.bt} · " +
            "addrs=${patient?.adrs?.size ?: 0} · tels=${patient?.tels?.size ?: 0} · " +
            "ids=${patient?.ids?.size ?: 0} · gp=${patient?.gp} · lang=${patient?.lang}")
    }

    // ─── Display helpers (lang-aware) ────────────────────────────

    private fun currentLang(): String = Locale.getDefault().language.lowercase().take(2)

    private fun identifierSystemDisplay(short: String?): String {
        if (short.isNullOrBlank()) return getString(R.string.perso_pick_hint)
        val entry = IpsIdentifierSystemCatalog.byShortCode(short) ?: return short
        return "${entry.emoji} ${entry.pick(currentLang())}"
    }

    private fun languageDisplay(tag: String?): String {
        if (tag.isNullOrBlank()) return getString(R.string.perso_pick_hint)
        val entry = IpsLanguageCatalog.byTag(tag) ?: return tag
        return "${entry.flag} ${entry.pick(currentLang())}"
    }

    private fun addressUseDisplay(use: String?): String {
        // Catalog lookup with i18n
        val entry = be.heyman.android.jemmapassdemo.pillars.IpsAddressUseCatalog.byCode(use)
            ?: return use.orEmpty()
        return "${entry.emoji} ${entry.pick(currentLang())}"
    }

    private fun wireGenderToggle() {
        binding.persoGenderToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val gs = when (checkedId) {
                    R.id.perso_gender_m -> "M"
                    R.id.perso_gender_f -> "F"
                    R.id.perso_gender_o -> "O"
                    else -> "U"
                }
                Log.d(TAG, "[t=${System.currentTimeMillis()}] 👤 gender tap → gs=$gs " +
                    "(FHIR administrative-gender=${GENDER_MAP[gs]})")
            }
        }
    }

    private fun wireBloodTypePicker() {
        binding.persoBloodTypeRow.setOnClickListener {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🩸 blood type tap")
            val items = BLOOD_TYPES.toTypedArray()
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.perso_blood_type_pick_title)
                .setItems(items) { _, which ->
                    val pick = items[which]
                    binding.persoBloodType.text = pick
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩸 blood type picked: $pick")
                }
                .show()
        }
    }

    private fun wireCountryPicker() {
        binding.persoNationalityRow.setOnClickListener {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🌍 country picker tap")
            val labels = COUNTRY_FALLBACK_LIST.map { "${it.first} · ${it.second}" }.toTypedArray()
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.perso_nationality_pick_title)
                .setItems(labels) { _, which ->
                    val pick = COUNTRY_FALLBACK_LIST[which]
                    binding.persoNationality.setText(pick.first)
                    binding.persoNationalityLabel.text = pick.second
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🌍 country picked: ${pick.first} (${pick.second})")
                }
                .show()
        }
    }

    // ─── 🆕 v2.6.0i — IPS-FULL pickers ───────────────────────────

    /** Address.country : reuse the same fallback country list as nationality. */
    private fun wireAddressCountryPicker() {
        binding.persoAddressCountryRow.setOnClickListener {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🌍 address country picker tap")
            val labels = COUNTRY_FALLBACK_LIST.map { "${it.first} · ${it.second}" }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.perso_address_country_pick_title)
                .setItems(labels) { _, which ->
                    val pick = COUNTRY_FALLBACK_LIST[which]
                    binding.persoAddressCountry.setText(pick.first)
                    binding.persoAddressCountryLabel.text = pick.second
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🌍 address country picked: ${pick.first} (${pick.second})")
                }
                .show()
        }
    }

    /** Address.use : home | work | temp | old | billing. */
    private fun wireAddressUsePicker() {
        binding.persoAddressUseRow.setOnClickListener {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🏠 address use picker tap")
            val catalog = be.heyman.android.jemmapassdemo.pillars.IpsAddressUseCatalog.ALL
            val lang = currentLang()
            val labels = catalog.map { "${it.emoji}  ${it.pick(lang)}" }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.perso_address_use_pick_title)
                .setItems(labels) { _, which ->
                    val pick = catalog[which]
                    pickedAddressUse = pick.code
                    binding.persoAddressUseLabel.text = "${pick.emoji} ${pick.pick(lang)}"
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏠 address use picked: ${pick.code}")
                }
                .show()
        }
    }

    /** Patient.identifier.system : BE-NRN | JP-MyNumber | FR-NSS | EU-EHIC | PASSPORT | OTHER. */
    private fun wireIdentifierSystemPicker() {
        binding.persoIdentifierSystemRow.setOnClickListener {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🆔 identifier system picker tap")
            val catalog = IpsIdentifierSystemCatalog.ALL
            val lang = currentLang()
            val labels = catalog.map { "${it.emoji}  ${it.pick(lang)}" }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.perso_identifier_system_pick_title)
                .setItems(labels) { _, which ->
                    val pick = catalog[which]
                    pickedIdSystem = pick.shortCode
                    binding.persoIdentifierSystemLabel.text = "${pick.emoji} ${pick.pick(lang)}"
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🆔 identifier system picked: ${pick.shortCode}")
                }
                .show()
        }
    }

    /** Patient.communication.language BCP-47. */
    private fun wireLanguagePicker() {
        binding.persoLanguageRow.setOnClickListener {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🌐 language picker tap")
            val catalog = IpsLanguageCatalog.ALL
            val lang = currentLang()
            val labels = catalog.map { "${it.flag}  ${it.pick(lang)}  ·  ${it.tag}" }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.perso_language_pick_title)
                .setItems(labels) { _, which ->
                    val pick = catalog[which]
                    pickedLanguageTag = pick.tag
                    binding.persoLanguageLabel.text = "${pick.flag} ${pick.pick(lang)}"
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🌐 language picked: ${pick.tag}")
                }
                .show()
        }
    }

    /**
     * Material DatePicker → ISO YYYY-MM-DD stored in pickedBirthDateIso.
     * FHIR Patient.birthDate is [1..1] required for IPS, so we don't allow
     * clearing once set. To "reset" the user must pick a new date.
     */
    private fun wireBirthDatePicker() {
        binding.persoBirthDateRow.setOnClickListener {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 📅 birth date picker tap")

            val constraints = CalendarConstraints.Builder()
                .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            val initialSelection = pickedBirthDateIso?.let { parseIsoDateUtc(it) }
                ?: MaterialDatePicker.todayInUtcMilliseconds()

            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.perso_birth_date_pick_title)
                .setSelection(initialSelection)
                .setCalendarConstraints(constraints)
                .build()

            picker.addOnPositiveButtonClickListener { utcMillis ->
                val iso = formatUtcMillisAsIso(utcMillis)
                pickedBirthDateIso = iso
                binding.persoBirthDate.setText(iso)
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 📅 birth date picked: $iso")
            }
            picker.show(childFragmentManager, "perso_birth_date_picker")
        }
    }

    private fun wireSaveButton() {
        binding.persoSaveBtn.setOnClickListener {
            saveProfile()
        }
    }

    private fun saveProfile() {
        val given = binding.persoGivenName.text?.toString()?.trim().orEmpty()
        val family = binding.persoFamilyName.text?.toString()?.trim().orEmpty()
        val bloodType = binding.persoBloodType.text?.toString()?.trim()?.takeIf { it != "—" }
        val nationality = binding.persoNationality.text?.toString()?.trim()
            ?.uppercase()?.takeIf { it.isNotBlank() }
        val gender = when (binding.persoGenderToggle.checkedButtonId) {
            R.id.perso_gender_m -> "M"
            R.id.perso_gender_f -> "F"
            R.id.perso_gender_o -> "O"
            else -> "U"
        }
        // 🆕 v2.6.0i — IPS-FULL : read structured address + telecom + identifier
        val addrLine = binding.persoAddressLine.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val addrCity = binding.persoAddressCity.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val addrPostal = binding.persoAddressPostal.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val addrCountry = binding.persoAddressCountry.text?.toString()?.trim()
            ?.uppercase()?.takeIf { it.isNotBlank() }

        val phone = binding.persoTelecomPhone.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val email = binding.persoTelecomEmail.text?.toString()?.trim()?.takeIf { it.isNotBlank() }

        val idValue = binding.persoIdentifierValue.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val idSystem = pickedIdSystem ?: if (idValue != null) "OTHER" else null

        val gp = binding.persoGp.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val languageTag = pickedLanguageTag

        // ─── Validation ────────────────────────────────────────────
        if (given.isBlank() && family.isBlank()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: name empty")
            Toast.makeText(requireContext(),
                R.string.perso_validation_name_required, Toast.LENGTH_SHORT).show()
            binding.persoGivenName.requestFocus()
            return
        }
        val birthIso = pickedBirthDateIso
        if (birthIso == null || !ISO_DATE_REGEX.matches(birthIso)) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: birthDate missing/invalid · $birthIso")
            Toast.makeText(requireContext(),
                R.string.perso_validation_birth_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (nationality != null && !nationality.matches(Regex("^[A-Z]{2}$"))) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: nationality not ISO-3166 alpha-2 · $nationality")
            Toast.makeText(requireContext(),
                R.string.perso_validation_nationality_format, Toast.LENGTH_SHORT).show()
            return
        }
        // Address country : same ISO-3166 alpha-2 check if provided
        if (addrCountry != null && !addrCountry.matches(Regex("^[A-Z]{2}$"))) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: address country not ISO-3166 alpha-2 · $addrCountry")
            Toast.makeText(requireContext(),
                R.string.perso_validation_nationality_format, Toast.LENGTH_SHORT).show()
            return
        }

        // ─── 🆕 v2.6.0i — Build IPS-FULL structured lists ─────

        // Address : structured if at least one field is filled
        val newAdrs = if (addrLine != null || addrCity != null || addrPostal != null || addrCountry != null) {
            listOf(
                JAddress(
                    use = pickedAddressUse,
                    line = addrLine,
                    city = addrCity,
                    postalCode = addrPostal,
                    country = addrCountry,
                ),
            )
        } else emptyList()

        // Telecom : list of phone + email + ... (skip nulls)
        val newTels = buildList {
            phone?.let { add(JTelecom(system = "phone", value = it, use = "mobile")) }
            email?.let { add(JTelecom(system = "email", value = it, use = "home")) }
        }

        // Identifier : single entry for v1
        val newIds = if (idValue != null) {
            listOf(JIdentifier(system = idSystem, value = idValue))
        } else emptyList()

        // Legacy mirror for backward compat (lecteurs anciens) : compose a flat
        // address line from structured fields if structured filled.
        val addressLegacy = if (newAdrs.isNotEmpty()) {
            listOfNotNull(addrLine, addrCity, addrPostal, addrCountry).joinToString(", ")
        } else null

        // ─── Build JPatient + persist ─────────────────────────────────
        val newPatient = JPatient(
            gn = given.takeIf { it.isNotBlank() },
            fn = family.takeIf { it.isNotBlank() },
            gs = gender,
            bd = birthIso,
            nat = nationality,
            bt = bloodType,
            // LEGACY simple fields — kept in sync with structured for compat
            adr = addressLegacy,
            tel = phone,
            eml = email,
            idn = idValue,
            // 🆕 IPS-FULL structured lists
            ids = newIds,
            adrs = newAdrs,
            tels = newTels,
            gp = gp,
            lang = languageTag,
            // Contacts preserved from existing profile if any — edited by 1b.
            ct = current?.p?.ct ?: emptyList(),
        )

        val base = current ?: JemmaProfileJ(j = "1.2")
        val updated = base.copy(p = newPatient)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 save patient · " +
            "gn=$given · fn=$family · gs=$gender · bd=$birthIso · " +
            "nat=$nationality · bt=$bloodType · " +
            "adrs=${newAdrs.size}(use=$pickedAddressUse) · tels=${newTels.size}(phone=${phone != null},email=${email != null}) · " +
            "ids=${newIds.size}(sys=$idSystem) · gp=$gp · lang=$languageTag · " +
            "isNew=${argProfileId == null} · sid=${updated.sid ?: "<new>"}")

        viewLifecycleOwner.lifecycleScope.launch {
            val result = profilesRepo.saveProfile(updated, sourceFormat = "MANUAL_EDIT")
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ saved · id=${result.id} · " +
                "wasExisting=${result.alreadyExisted}")
            Toast.makeText(requireContext(),
                if (result.alreadyExisted) R.string.perso_saved_updated
                else R.string.perso_saved_created,
                Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────

    /** "BE" → "Belgique"; null → "—". Reads our fallback list (in-memory). */
    private fun countryLabel(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return COUNTRY_FALLBACK_LIST.firstOrNull { it.first.equals(iso, ignoreCase = true) }
            ?.second ?: iso.uppercase()
    }

    /** "1956-02-05" → epoch UTC millis. Returns today if parse fails. */
    private fun parseIsoDateUtc(iso: String): Long {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(iso)?.time ?: MaterialDatePicker.todayInUtcMilliseconds()
        } catch (e: Exception) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ parseIsoDateUtc failed for '$iso': ${e.message}")
            MaterialDatePicker.todayInUtcMilliseconds()
        }
    }

    /** UTC epoch millis → "YYYY-MM-DD". */
    private fun formatUtcMillisAsIso(utcMillis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date(utcMillis))
    }

    /**
     * 🆕 c35 — Merge a PatientDraft (from the vision pipeline) into an
     * existing JPatient. Draft fields win on non-null values. If the
     * draft has structured address/identifier values, they are packed
     * into the IPS-FULL JAddress / JIdentifier sub-lists (replacing the
     * first entry if present).
     */
    private fun mergeDraftIntoPatient(existing: JPatient?, draft: PatientDraft): JPatient {
        val base = existing ?: JPatient()

        // Build new address[0] if draft has any address info, else keep existing.
        val newAdrs: List<JAddress> = if (
            !draft.addressLine.isNullOrBlank()
            || !draft.addressCity.isNullOrBlank()
            || !draft.addressPostalCode.isNullOrBlank()
            || !draft.addressCountry.isNullOrBlank()
        ) {
            val merged = JAddress(
                use = base.adrs.firstOrNull()?.use ?: "home",
                line = draft.addressLine ?: base.adrs.firstOrNull()?.line,
                city = draft.addressCity ?: base.adrs.firstOrNull()?.city,
                postalCode = draft.addressPostalCode ?: base.adrs.firstOrNull()?.postalCode,
                country = draft.addressCountry ?: base.adrs.firstOrNull()?.country,
            )
            listOf(merged) + base.adrs.drop(1)
        } else base.adrs

        // Build new telecoms if draft has phone or email.
        val newTels: List<JTelecom> = run {
            val keep = base.tels.filterNot { it.system == "phone" || it.system == "email" }
            val tels = mutableListOf<JTelecom>()
            if (!draft.phone.isNullOrBlank()) tels += JTelecom(system = "phone", value = draft.phone, use = "mobile")
            if (!draft.email.isNullOrBlank()) tels += JTelecom(system = "email", value = draft.email, use = "home")
            tels + keep
        }

        // Build new identifier[0] if draft has identifier value.
        val newIds: List<JIdentifier> = if (!draft.identifierValue.isNullOrBlank()) {
            val merged = JIdentifier(
                system = draft.identifierSystem ?: "OTHER",
                value = draft.identifierValue,
            )
            listOf(merged) + base.ids.drop(1)
        } else base.ids

        return base.copy(
            gn = draft.givenName ?: base.gn,
            fn = draft.familyName ?: base.fn,
            gs = draft.gender ?: base.gs,
            bd = draft.birthDate ?: base.bd,
            nat = draft.nationality ?: base.nat,
            bt = draft.bloodType ?: base.bt,
            adrs = newAdrs,
            tels = newTels,
            ids = newIds,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 👤 onDestroyView")
        _binding = null
    }
}
