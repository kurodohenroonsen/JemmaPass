/*
 * ImmunizationsAdapter.kt — JEMMA Pass · IPS pillar "Immunizations" (FHIR-native)
 *
 * ListAdapter (RecyclerView + DiffUtil) for the patient's immunization history —
 * FHIR R4 `Immunization` resources read from the profile Bundle through
 * ProfilesRepository.loadImmunizations() (domain type IpsImmunization).
 *
 * Each row shows :
 *   💉  Vaccine label (curated catalog → asset translations → stored display)
 *       2022-05-17 · dose 2/3 · lot AC52B213BC
 *       Manufacturer / performer / note (muted)
 *
 * Tap row → onTap (parent opens the EDIT bottom sheet)
 * Long-press → onLongPress (parent confirms delete)
 *
 * Logging : tag JEMMA-IMMUNIZATIONS-ADAPTER
 */
package be.heyman.android.jemmapassdemo.ui.profile.immunizations

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.ItemImmunizationRowBinding
import be.heyman.android.jemmapassdemo.ips.IpsImmunization
import be.heyman.android.jemmapassdemo.ips.IpsImmunizationStatus
import be.heyman.android.jemmapassdemo.pillars.IpsImmunizationStatusCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
import be.heyman.android.jemmapassdemo.pillars.IpsVaccineCatalog
import kotlinx.coroutines.launch

class ImmunizationsAdapter(
    private val lang: String,
    private val translations: IpsTranslationsRepository,
    private val scope: LifecycleCoroutineScope,
    private val onTap: (IpsImmunization, Int) -> Unit,
    private val onLongPress: (IpsImmunization, Int) -> Unit,
) : ListAdapter<IpsImmunization, ImmunizationsAdapter.VH>(DIFF) {

    companion object {
        private const val TAG = "JEMMA-IMMUNIZATIONS-ADAPTER"

        private val DIFF = object : DiffUtil.ItemCallback<IpsImmunization>() {
            override fun areItemsTheSame(oldItem: IpsImmunization, newItem: IpsImmunization): Boolean {
                return oldItem.id == newItem.id
            }
            override fun areContentsTheSame(oldItem: IpsImmunization, newItem: IpsImmunization): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class VH(val binding: ItemImmunizationRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemImmunizationRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val im = getItem(position)
        val b = holder.binding
        val ctx = b.root.context

        // ─── Icon : status-aware ───────────────────────────────────
        b.immunizationRowIcon.text = when (im.status) {
            IpsImmunizationStatus.NOT_DONE -> "🚫"
            IpsImmunizationStatus.ENTERED_IN_ERROR -> "⚠️"
            else -> IpsVaccineCatalog.byCode(im.code)?.emoji ?: "💉"
        }

        // ─── Vaccine name : curated catalog → stored display → async asset translation
        val curated = IpsVaccineCatalog.getDisplay(im.code, lang)
        val initialLabel = curated
            ?: im.label().takeIf { it.isNotBlank() }
            ?: "—"
        b.immunizationRowName.text = IpsTranslationsRepository.cleanBilingual(initialLabel, lang)

        val code = im.code
        if (curated == null && !code.isNullOrBlank()) {
            b.immunizationRowName.tag = code
            scope.launch {
                val localized = try { translations.get(code, lang) } catch (e: Throwable) { null }
                if (!localized.isNullOrBlank() && b.immunizationRowName.tag == code) {
                    b.immunizationRowName.text = localized
                }
            }
        } else {
            b.immunizationRowName.tag = null
        }

        // ─── Subtitle : "2022-05-17 · dose 2/3 · lot AC52B213BC" ──
        val parts = mutableListOf<String>()
        parts.add(im.date?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.immunizations_date_unknown))
        im.doseNumber?.let { dn ->
            val sd = im.seriesDoses
            parts.add(
                if (sd != null && sd > 0) ctx.getString(R.string.immunizations_dose_of_series, dn, sd)
                else ctx.getString(R.string.immunizations_dose_number, dn)
            )
        }
        im.lotNumber?.takeIf { it.isNotBlank() }?.let { parts.add(ctx.getString(R.string.immunizations_lot_short, it)) }
        if (im.status != IpsImmunizationStatus.COMPLETED) {
            val entry = IpsImmunizationStatusCatalog.byCode(im.status)
            entry?.let { parts.add("${it.emoji} ${it.pick(lang)}") }
        }
        b.immunizationRowSubtitle.text = parts.joinToString(" · ")

        // ─── Details line : manufacturer / performer / note ───────
        val details = listOfNotNull(
            im.manufacturer?.takeIf { it.isNotBlank() },
            im.performer?.takeIf { it.isNotBlank() },
            im.note?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (details.isNotBlank()) {
            b.immunizationRowDetails.text = details
            b.immunizationRowDetails.visibility = View.VISIBLE
        } else {
            b.immunizationRowDetails.visibility = View.GONE
        }

        // ─── Tap handlers — read bindingAdapterPosition at tap time (DiffUtil-safe)
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
