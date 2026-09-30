/*
 * ProceduresAdapter.kt — JEMMA Pass · IPS pillar "History of Procedures" (FHIR-native)
 *
 * ListAdapter for the patient's procedures — FHIR R4 `Procedure` resources read
 * from the profile Bundle through ProfilesRepository.loadProcedures().
 *
 *   🔪  Appendectomy
 *       1995-07-12 · ✅ Completed
 *       CHU Namur · Dr. Dupont · Laparoscopic
 *
 * Logging : tag JEMMA-PROCEDURES-ADAPTER
 */
package be.heyman.android.jemmapassdemo.ui.profile.procedures

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.ItemProcedureRowBinding
import be.heyman.android.jemmapassdemo.ips.IpsProcedure
import be.heyman.android.jemmapassdemo.ips.IpsProcedureStatus
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.pillars.IpsProcedureCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsProcedureStatusCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
import kotlinx.coroutines.launch

class ProceduresAdapter(
    private val lang: String,
    private val kb: KnowledgeBaseService,
    private val scope: LifecycleCoroutineScope,
    private val onTap: (IpsProcedure, Int) -> Unit,
    private val onLongPress: (IpsProcedure, Int) -> Unit,
) : ListAdapter<IpsProcedure, ProceduresAdapter.VH>(DIFF) {

    companion object {
        private const val TAG = "JEMMA-PROCEDURES-ADAPTER"
        private val DIFF = object : DiffUtil.ItemCallback<IpsProcedure>() {
            override fun areItemsTheSame(oldItem: IpsProcedure, newItem: IpsProcedure) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: IpsProcedure, newItem: IpsProcedure) = oldItem == newItem
        }
    }

    inner class VH(val binding: ItemProcedureRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemProcedureRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val pr = getItem(position)
        val b = holder.binding
        val ctx = b.root.context

        b.procedureRowIcon.text = when (pr.status) {
            IpsProcedureStatus.NOT_DONE -> "🚫"
            IpsProcedureStatus.ENTERED_IN_ERROR -> "⚠️"
            else -> IpsProcedureCatalog.byCode(pr.code)?.emoji ?: "🏥"
        }

        // Label: curated catalog → stored display / free text → async KB (SNOMED) fallback
        val curated = IpsProcedureCatalog.getDisplay(pr.code, lang)
        val initial = curated ?: pr.label().takeIf { it.isNotBlank() } ?: "—"
        b.procedureRowName.text = IpsTranslationsRepository.cleanBilingual(initial, lang)
        val code = pr.code
        if (curated == null && !code.isNullOrBlank()) {
            // Localized IPS value-set translation first (procedures / devices free sets),
            // then the English primary display when nothing usable is stored.
            b.procedureRowName.tag = code
            val system = pr.system ?: KnowledgeBaseService.SYSTEM_SNOMED
            val needsAnyLabel = pr.display.isNullOrBlank() || pr.display == code
            scope.launch {
                val display = try {
                    kb.getLocalizedDisplay(code, system, lang)
                        ?: if (needsAnyLabel) kb.resolveByCode(code, system).concept?.primaryDisplay else null
                } catch (e: Throwable) { null }
                if (!display.isNullOrBlank() && b.procedureRowName.tag == code) {
                    b.procedureRowName.text = IpsTranslationsRepository.cleanBilingual(display, lang)
                }
            }
        } else {
            b.procedureRowName.tag = null
        }

        val parts = mutableListOf<String>()
        parts.add(pr.date?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.procedures_date_unknown))
        IpsProcedureStatusCatalog.byCode(pr.status)?.let { parts.add("${it.emoji} ${it.pick(lang)}") }
        pr.bodySite?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        b.procedureRowSubtitle.text = parts.joinToString(" · ")

        val details = listOfNotNull(
            pr.location?.takeIf { it.isNotBlank() },
            pr.performer?.takeIf { it.isNotBlank() },
            pr.outcome?.takeIf { it.isNotBlank() },
            pr.note?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        b.procedureRowDetails.visibility = if (details.isBlank()) View.GONE else View.VISIBLE
        b.procedureRowDetails.text = details

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
