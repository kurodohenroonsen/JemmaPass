/*
 * DevicesEditFragment.kt — JEMMA Pass · IPS pillar "Medical Devices" (FHIR-native)
 *
 * List + CRUD of the patient's implants / assistive devices. Persistence goes
 * through ProfilesRepository.loadDevices() / saveDevices(): the FHIR Bundle is
 * rewritten (source of truth — one `Device` + one `DeviceUseStatement` per entry)
 * and `_j.dv` is re-projected for QR / mesh. Same pattern as Immunizations.
 *
 * Logging : tag JEMMA-DEVICES-EDIT
 */
package be.heyman.android.jemmapassdemo.ui.profile.devices

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
import be.heyman.android.jemmapassdemo.databinding.FragmentDevicesEditBinding
import be.heyman.android.jemmapassdemo.ips.IpsDevice
import be.heyman.android.jemmapassdemo.ips.IpsDeviceStatus
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.pillars.IpsDeviceCatalog
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DevicesEditFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-DEVICES-EDIT"
        private const val ARG_PROFILE_ID = "profileId"
    }

    @Inject lateinit var profilesRepo: ProfilesRepository
    @Inject lateinit var kb: KnowledgeBaseService

    private var _binding: FragmentDevicesEditBinding? = null
    private val binding get() = _binding!!

    private val argProfileId: String? by lazy { arguments?.getString(ARG_PROFILE_ID) }
    private var profileId: String? = null
    private var loaded = false
    private val devices = mutableListOf<IpsDevice>()
    private lateinit var adapter: DevicesAdapter
    private val currentLang: String by lazy { Locale.getDefault().language.lowercase().take(2) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDevicesEditBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView · argProfileId=$argProfileId")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.devicesToolbarBack.setOnClickListener { findNavController().navigateUp() }

        adapter = DevicesAdapter(
            lang = currentLang,
            kb = kb,
            scope = viewLifecycleOwner.lifecycleScope,
            onTap = { dv, position -> openForm(DeviceFormMode.EDIT, existing = dv, index = position) },
            onLongPress = { dv, position -> confirmDelete(dv, position) },
        )
        binding.devicesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.devicesRecycler.adapter = adapter
        binding.devicesFabAdd.setOnClickListener { openForm(DeviceFormMode.CREATE) }

        parentFragmentManager.setFragmentResultListener(DeviceFormBottomSheet.RESULT_KEY, viewLifecycleOwner) { _, bundle ->
            handleFormResult(bundle)
        }

        if (loaded) renderList() else loadAndRender()
    }

    private fun loadAndRender() {
        val pid = argProfileId ?: profilesRepo.currentProfileId
        if (pid == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ no profile id")
            showEmptyStateNoProfile(); return
        }
        profileId = pid
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = profilesRepo.loadProfile(pid)
            if (profile == null) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ profile $pid not found")
                showEmptyStateNoProfile(); return@launch
            }
            val list = profilesRepo.loadDevices(pid)
            devices.clear(); devices.addAll(list); loaded = true
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📂 loaded ${list.size} devices for $pid")
            if (_binding != null) renderList()
        }
    }

    private fun renderList() {
        val sorted = devices.sortedWith(
            compareByDescending<IpsDevice> { it.status == IpsDeviceStatus.ACTIVE }
                .thenByDescending { it.date != null }
                .thenByDescending { it.date ?: "" }
        )
        adapter.submitList(sorted)
        val empty = devices.isEmpty()
        binding.devicesEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.devicesRecycler.visibility = if (empty) View.GONE else View.VISIBLE
        binding.devicesEmptyText.setText(R.string.devices_empty_default)
        binding.devicesFabAdd.isEnabled = true
        binding.devicesCount.text = resources.getQuantityString(R.plurals.devices_count, devices.size, devices.size)
    }

    private fun showEmptyStateNoProfile() {
        binding.devicesEmptyState.visibility = View.VISIBLE
        binding.devicesRecycler.visibility = View.GONE
        binding.devicesEmptyText.setText(R.string.devices_empty_no_profile)
        binding.devicesFabAdd.isEnabled = false
        binding.devicesCount.text = resources.getQuantityString(R.plurals.devices_count, 0, 0)
    }

    private fun openForm(mode: DeviceFormMode, existing: IpsDevice? = null, index: Int = -1) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 openForm · mode=$mode · idx=$index · id=${existing?.id}")
        DeviceFormBottomSheet.newInstance(mode, currentLang, existing).show(parentFragmentManager, "device_form")
    }

    private fun handleFormResult(bundle: Bundle) {
        val mode = DeviceFormMode.valueOf(bundle.getString(DeviceFormBottomSheet.ARG_MODE) ?: DeviceFormMode.CREATE.name)
        if (bundle.getBoolean(DeviceFormBottomSheet.ARG_DELETE, false)) {
            val targetId = bundle.getString(DeviceFormBottomSheet.ARG_ID)
            devices.firstOrNull { it.id == targetId }?.let { confirmDelete(it, -1) }
                ?: Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ delete requested for unknown id=$targetId")
            return
        }
        val id = bundle.getString(DeviceFormBottomSheet.ARG_ID)?.takeIf { it.isNotBlank() } ?: IpsDevice.newId()
        val code = bundle.getString(DeviceFormBottomSheet.ARG_CODE)?.takeIf { it.isNotBlank() }
        val freeText = bundle.getString(DeviceFormBottomSheet.ARG_TEXT)?.takeIf { it.isNotBlank() }
        val display = bundle.getString(DeviceFormBottomSheet.ARG_DISPLAY)?.takeIf { it.isNotBlank() }
            ?: IpsDeviceCatalog.byCode(code)?.displayEn
        val updated = IpsDevice(
            id = id,
            code = code,
            system = bundle.getString(DeviceFormBottomSheet.ARG_CODE_SYSTEM)?.takeIf { it.isNotBlank() } ?: IpsDeviceCatalog.CODE_SYSTEM,
            display = if (code != null) display else null,
            text = if (code == null) freeText else null,
            udi = bundle.getString(DeviceFormBottomSheet.ARG_UDI)?.takeIf { it.isNotBlank() },
            manufacturer = bundle.getString(DeviceFormBottomSheet.ARG_MANUFACTURER)?.takeIf { it.isNotBlank() },
            model = bundle.getString(DeviceFormBottomSheet.ARG_MODEL)?.takeIf { it.isNotBlank() },
            serial = bundle.getString(DeviceFormBottomSheet.ARG_SERIAL)?.takeIf { it.isNotBlank() },
            date = bundle.getString(DeviceFormBottomSheet.ARG_DATE)?.takeIf { it.isNotBlank() },
            status = IpsDeviceStatus.normalize(bundle.getString(DeviceFormBottomSheet.ARG_STATUS)),
            bodySite = bundle.getString(DeviceFormBottomSheet.ARG_BODY_SITE)?.takeIf { it.isNotBlank() },
            note = bundle.getString(DeviceFormBottomSheet.ARG_NOTE)?.takeIf { it.isNotBlank() },
        )
        when (mode) {
            DeviceFormMode.CREATE -> devices.add(updated)
            DeviceFormMode.EDIT -> {
                val idx = devices.indexOfFirst { it.id == updated.id }
                if (idx >= 0) devices[idx] = updated else devices.add(updated)
            }
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ form result · mode=$mode · id=${updated.id} · code=${updated.code} · udi=${updated.udi != null} · date=${updated.date}")
        renderList()
        persist()
    }

    private fun confirmDelete(dv: IpsDevice, position: Int) {
        val resId = dv.code?.let { code ->
            val system = dv.system ?: be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService.SYSTEM_SNOMED
            be.heyman.android.jemmapassdemo.qr.codeLabelResourceName(system, code)
                ?.let { name -> resources.getIdentifier(name, "string", requireContext().packageName) }
        } ?: 0
        val resLabel = if (resId != 0) getString(resId) else null
        val label = resLabel
            ?: IpsDeviceCatalog.getDisplay(dv.code, currentLang)
            ?: dv.label().takeIf { it.isNotBlank() }
            ?: getString(R.string.devices_unnamed)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.devices_delete_title)
            .setMessage(getString(R.string.devices_delete_message, label))
            .setNegativeButton(R.string.devices_delete_cancel) { d, _ -> d.dismiss() }
            .setPositiveButton(R.string.devices_delete_confirm) { d, _ ->
                val removed = devices.removeAll { it.id == dv.id }
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 delete · pos=$position · id=${dv.id} · removed=$removed")
                renderList(); persist(); d.dismiss()
            }
            .show()
    }

    private fun persist() {
        val pid = profileId ?: return
        val snapshot = devices.toList()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ok = profilesRepo.saveDevices(pid, snapshot)
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 persist · profileId=$pid · count=${snapshot.size} · ok=$ok")
                if (_binding != null) {
                    Toast.makeText(requireContext(), if (ok) R.string.devices_saved else R.string.devices_save_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ persist failed : ${e.message}", e)
                if (_binding != null) Toast.makeText(requireContext(), R.string.devices_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
