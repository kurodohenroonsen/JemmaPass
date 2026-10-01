/*
 * PastProblemsAdapter.kt — JEMMA Pass · IPS pillar "History of Past Illness" (FHIR-native)
 *
 * ListAdapter for the patient's past illnesses — FHIR R4 `Condition` resources read
 * from the profile Bundle through ProfilesRepository.loadPastProblems().
 *
 *   📜  Appendicitis
 *       1995-07-10 → 1995-07-12 · ✅ Resolved
 *       🟠 Moderate · Treated by appendectomy
 *
 * Labels: stored English term, replaced by the KB free-set translation in the UI language.
 *
 * Logging : tag JEMMA-PASTPROBLEMS-ADAPTER
 */
package be.heyman.android.jemmapassdemo.ui.profile.pastproblems

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.ItemPastProblemRowBinding
import be.heyman.android.jemmapassdemo.ips.IpsPastProblem
import be.heyman.android.jemmapassdemo.ips.IpsPastProblemStatus
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
import kotlinx.coroutines.launch

class PastProblemsAdapter(
    private val lang: String,
    private val kb: KnowledgeBaseService,
    private val scope: LifecycleCoroutineScope,
    private val onTap: (IpsPastProblem, Int) -> Unit,
    private val onLongPress: (IpsPastProblem, Int) -> Unit,
) : ListAdapter<IpsPastProblem, PastProblemsAdapter.VH>(DIFF) {

    companion object {
        private const val TAG = "JEMMA-PASTPROBLEMS-ADAPTER"
        private val DIFF = object : DiffUtil.ItemCallback<IpsPastProblem>() {
            override fun areItemsTheSame(oldItem: IpsPastProblem, newItem: IpsPastProblem) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: IpsPastProblem, newItem: IpsPastProblem) = oldItem == newItem
        }

        /** "1995-07-10 → 1995-07-12", "→ 1963", "1962", or null when both are unknown. */
        fun period(onset: String?, abatement: String?): String? {
            val o = onset?.takeIf { it.isNotBlank() }
            val a = abatement?.takeIf { it.isNotBlank() }
            return when {
                o != null && a != null -> "$o → $a"
                o != null -> o
                a != null -> "→ $a"
                else -> null
            }
        }
    }

    inner class VH(val binding: ItemPastProblemRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPastProblemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val pp = getItem(position)
        val b = holder.binding
        val ctx = b.root.context

        b.pastProblemRowIcon.text = when (pp.clinicalStatus) {
            IpsPastProblemStatus.REMISSION -> "🌗"
            IpsPastProblemStatus.INACTIVE -> "💤"
            else -> "📜"
        }

        val initial = pp.label().takeIf { it.isNotBlank() } ?: ctx.getString(R.string.past_problems_unnamed)
        b.pastProblemRowName.text = IpsTranslationsRepository.cleanBilingual(initial, lang)
        val code = pp.code
        if (!code.isNullOrBlank() && lang != "en") {
            b.pastProblemRowName.tag = code
            val system = pp.system ?: KnowledgeBaseService.SYSTEM_SNOMED
            scope.launch {
                val display = try { kb.getLocalizedDisplay(code, system, lang) } catch (e: Throwable) { null }
                if (!display.isNullOrBlank() && b.pastProblemRowName.tag == code) {
                    b.pastProblemRowName.text = IpsTranslationsRepository.cleanBilingual(display, lang)
                }
            }
        } else {
            b.pastProblemRowName.tag = null
        }

        val parts = mutableListOf<String>()
        parts.add(period(pp.onset, pp.abatement) ?: ctx.getString(R.string.past_problems_date_unknown))
        parts.add(ctx.getString(PastProblemFormBottomSheet.statusLabelRes(pp.clinicalStatus)))
        b.pastProblemRowSubtitle.text = parts.joinToString(" · ")

        val details = listOfNotNull(
            pp.severity?.let { ctx.getString(PastProblemFormBottomSheet.severityLabelRes(it)) },
            pp.note?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        b.pastProblemRowDetails.visibility = if (details.isBlank()) View.GONE else View.VISIBLE
        b.pastProblemRowDetails.text = details

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
