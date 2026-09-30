/*
 * DevicesAdapter.kt — JEMMA Pass · IPS pillar "Medical Devices" (FHIR-native)
 *
 * ListAdapter for the patient's devices — FHIR R4 `DeviceUseStatement` + `Device`
 * resources read from the profile Bundle through ProfilesRepository.loadDevices().
 *
 *   ❤️  Cardiac pacemaker
 *       2021-03-15 · ✅ In use · Left chest
 *       Medtronic Azure XT · UDI (01)00643169007222(21)PJN1234567
 *
 * Logging : tag JEMMA-DEVICES-ADAPTER
 */
package be.heyman.android.jemmapassdemo.ui.profile.devices

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.ItemDeviceRowBinding
import be.heyman.android.jemmapassdemo.ips.IpsDevice
import be.heyman.android.jemmapassdemo.ips.IpsDeviceStatus
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.pillars.IpsDeviceCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsDeviceStatusCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
import kotlinx.coroutines.launch

class DevicesAdapter(
    private val lang: String,
    private val kb: KnowledgeBaseService,
    private val scope: LifecycleCoroutineScope,
    private val onTap: (IpsDevice, Int) -> Unit,
    private val onLongPress: (IpsDevice, Int) -> Unit,
) : ListAdapter<IpsDevice, DevicesAdapter.VH>(DIFF) {

    companion object {
        private const val TAG = "JEMMA-DEVICES-ADAPTER"
        private val DIFF = object : DiffUtil.ItemCallback<IpsDevice>() {
            override fun areItemsTheSame(oldItem: IpsDevice, newItem: IpsDevice) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: IpsDevice, newItem: IpsDevice) = oldItem == newItem
        }
    }

    inner class VH(val binding: ItemDeviceRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemDeviceRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val dv = getItem(position)
        val b = holder.binding
        val ctx = b.root.context

        b.deviceRowIcon.text = when (dv.status) {
            IpsDeviceStatus.ENTERED_IN_ERROR -> "⚠️"
            IpsDeviceStatus.INACTIVE -> "⏹"
            else -> IpsDeviceCatalog.byCode(dv.code)?.emoji ?: "📟"
        }

        // Label: curated catalog → stored display / free text → async KB (SNOMED) fallback
        val curated = IpsDeviceCatalog.getDisplay(dv.code, lang)
        val initial = curated ?: dv.label().takeIf { it.isNotBlank() } ?: "—"
        b.deviceRowName.text = IpsTranslationsRepository.cleanBilingual(initial, lang)
        val code = dv.code
        if (curated == null && !code.isNullOrBlank()) {
            // Localized IPS value-set translation first (procedures / devices free sets),
            // then the English primary display when nothing usable is stored.
            b.deviceRowName.tag = code
            val system = dv.system ?: KnowledgeBaseService.SYSTEM_SNOMED
            val needsAnyLabel = dv.display.isNullOrBlank() || dv.display == code
            scope.launch {
                val display = try {
                    kb.getLocalizedDisplay(code, system, lang)
                        ?: if (needsAnyLabel) kb.resolveByCode(code, system).concept?.primaryDisplay else null
                } catch (e: Throwable) { null }
                if (!display.isNullOrBlank() && b.deviceRowName.tag == code) {
                    b.deviceRowName.text = IpsTranslationsRepository.cleanBilingual(display, lang)
                }
            }
        } else {
            b.deviceRowName.tag = null
        }

        val parts = mutableListOf<String>()
        parts.add(dv.date?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.devices_date_unknown))
        IpsDeviceStatusCatalog.byCode(dv.status)?.let { parts.add("${it.emoji} ${it.pick(lang)}") }
        dv.bodySite?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        b.deviceRowSubtitle.text = parts.joinToString(" · ")

        val maker = listOfNotNull(
            dv.manufacturer?.takeIf { it.isNotBlank() },
            dv.model?.takeIf { it.isNotBlank() },
        ).joinToString(" ")
        val details = listOfNotNull(
            maker.takeIf { it.isNotBlank() },
            dv.serial?.takeIf { it.isNotBlank() }?.let { ctx.getString(R.string.devices_serial_short, it) },
            dv.udi?.takeIf { it.isNotBlank() }?.let { ctx.getString(R.string.devices_udi_short, it) },
            dv.note?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        b.deviceRowDetails.visibility = if (details.isBlank()) View.GONE else View.VISIBLE
        b.deviceRowDetails.text = details

        b.root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val item = getItem(pos)
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 👆 tap · pos=$pos · id=${item.id}")
            onTap(item, pos)
        }
        b.root.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener true
            val item = getItem(pos)
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 👆 long-press · pos=$pos · id=${item.id}")
            onLongPress(item, pos)
            true
        }
    }
}
