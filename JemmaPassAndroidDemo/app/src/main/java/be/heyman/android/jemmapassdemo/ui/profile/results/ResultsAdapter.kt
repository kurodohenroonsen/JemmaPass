/*
 * ResultsAdapter.kt — JEMMA Pass · IPS pillar "Results" (FHIR-native, sprint 3)
 *
 *   🧪  Potassium                               4.1 mmol/L  ✅
 *       2026-02-10 · ✅ Normal · ref 3.5-5.1
 *       Laboratory · Matsuyama Red Cross Hospital laboratory
 *
 * Logging : tag JEMMA-RESULTS-ADAPTER
 */
package be.heyman.android.jemmapassdemo.ui.profile.results

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.ItemResultRowBinding
import be.heyman.android.jemmapassdemo.ips.IpsBloodGroup
import be.heyman.android.jemmapassdemo.ips.IpsResult
import be.heyman.android.jemmapassdemo.ips.IpsResultCategory
import be.heyman.android.jemmapassdemo.ips.IpsResultStatus
import be.heyman.android.jemmapassdemo.pillars.IpsResultCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsResultCategoryCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsResultInterpretationCatalog

class ResultsAdapter(
    private val lang: String,
    private val onTap: (IpsResult, Int) -> Unit,
    private val onLongPress: (IpsResult, Int) -> Unit,
) : ListAdapter<IpsResult, ResultsAdapter.VH>(DIFF) {

    companion object {
        private const val TAG = "JEMMA-RESULTS-ADAPTER"
        private val DIFF = object : DiffUtil.ItemCallback<IpsResult>() {
            override fun areItemsTheSame(oldItem: IpsResult, newItem: IpsResult) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: IpsResult, newItem: IpsResult) = oldItem == newItem
        }

        /** Label shown everywhere in the UI: curated catalog → stored display / free text. */
        fun labelOf(rs: IpsResult, lang: String): String =
            IpsResultCatalog.getDisplay(rs.code, lang) ?: rs.label().ifBlank { "—" }

        /** Value shown in the UI (coded blood groups shown as "O+" when derivable). */
        fun valueOf(rs: IpsResult): String {
            if (rs.code == IpsBloodGroup.LOINC_ABO_RH && !rs.valueDisplay.isNullOrBlank()) {
                IpsBloodGroup.labelFromSnomed(rs.valueCode)?.let { return it }
            }
            return rs.valueLabel()
        }
    }

    inner class VH(val binding: ItemResultRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemResultRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val rs = getItem(position)
        val b = holder.binding
        val ctx = b.root.context

        b.resultRowIcon.text = when {
            rs.status == IpsResultStatus.ENTERED_IN_ERROR -> "⚠️"
            rs.category == IpsResultCategory.IMAGING -> "🩻"
            else -> IpsResultCatalog.byCode(rs.code)?.emoji ?: "🧪"
        }
        b.resultRowName.text = labelOf(rs, lang)
        b.resultRowValue.text = valueOf(rs)

        val parts = mutableListOf<String>()
        parts.add(rs.date?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.results_date_unknown))
        IpsResultInterpretationCatalog.byCode(rs.interpretation)?.let { parts.add("${it.emoji} ${it.pick(lang)}") }
        rs.referenceRangeLabel()?.let { parts.add(ctx.getString(R.string.results_ref_short, it)) }
        b.resultRowSubtitle.text = parts.joinToString(" · ")

        val details = listOfNotNull(
            IpsResultCategoryCatalog.byCode(rs.category)?.pick(lang),
            rs.performer?.takeIf { it.isNotBlank() },
            rs.note?.takeIf { it.isNotBlank() },
            if (IpsBloodGroup.isDerived(rs)) ctx.getString(R.string.results_derived_blood_group) else null,
        ).joinToString(" · ")
        b.resultRowDetails.visibility = if (details.isBlank()) View.GONE else View.VISIBLE
        b.resultRowDetails.text = details

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
