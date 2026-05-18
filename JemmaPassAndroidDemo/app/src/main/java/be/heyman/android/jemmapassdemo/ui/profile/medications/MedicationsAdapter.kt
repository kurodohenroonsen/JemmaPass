/*
 * MedicationsAdapter.kt — JEMMA Pass · Plan B · v2.6.0 · L5d
 *
 * ListAdapter (RecyclerView + DiffUtil) pour la liste des médicaments du
 * patient — pilier FHIR MedicationStatement IPS, stocké dans
 * JemmaProfileJ.md : List<JMedication>.
 *
 * Chaque row affiche :
 *   💊  Nom du médicament                           [emoji route]
 *       5 mg · 1x/jour · 💊 Orale
 *       Raison (rs) si présente
 *
 * Tap row → onTap (parent ouvre BottomSheet EDIT)
 * Long-press → onLongPress (parent confirm delete)
 *
 * Résolution :
 *   - JMedication.c (ATC/RxNorm) → label localisé via KnowledgeBaseService
 *     async (avec fallback sur displayLabel denormalisé pour rendu instantané)
 *   - JMedication.r (O/I/T/S) → display via IpsRouteCatalog sync
 *
 * Logging : tag JEMMA-MEDICATIONS-ADAPTER
 */
package be.heyman.android.jemmapassdemo.ui.profile.medications

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.databinding.ItemMedicationRowBinding
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.ResolvedConcept
import be.heyman.android.jemmapassdemo.pillars.IpsRouteCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
import be.heyman.android.jemmapassdemo.qr.JMedication
import kotlinx.coroutines.launch

class MedicationsAdapter(
    private val lang: String,
    private val kb: KnowledgeBaseService,
    private val scope: LifecycleCoroutineScope,
    private val onTap: (JMedication, Int) -> Unit,
    private val onLongPress: (JMedication, Int) -> Unit,
) : ListAdapter<JMedication, MedicationsAdapter.VH>(DIFF) {

    companion object {
        private const val TAG = "JEMMA-MEDICATIONS-ADAPTER"

        private val DIFF = object : DiffUtil.ItemCallback<JMedication>() {
            override fun areItemsTheSame(oldItem: JMedication, newItem: JMedication): Boolean {
                return oldItem.c == newItem.c &&
                    oldItem.v == newItem.v &&
                    oldItem.u == newItem.u &&
                    oldItem.t == newItem.t
            }
            override fun areContentsTheSame(oldItem: JMedication, newItem: JMedication): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class VH(val binding: ItemMedicationRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMedicationRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = getItem(position)
        val b = holder.binding

        // ─── Route emoji prefix ───────────────────────────────────
        val routeEntry = IpsRouteCatalog.byShortCode(m.r)
        b.medicationRowIcon.text = routeEntry?.emoji ?: "💊"

        // ─── Drug name : show denormalized first, then resolve async
        val initialLabel = m.displayLabel?.takeIf { it.isNotBlank() }
            ?: m.c?.takeIf { it.isNotBlank() }
            ?: "—"
        b.medicationRowName.text = IpsTranslationsRepository.cleanBilingual(initialLabel, lang)

        val code = m.c
        // 🆕 Lot 14.5c9.2 — Avoid overwriting a valid human-readable display name
        // with a generic/brand name matched from ATC code (e.g. overwriting
        // 'amoxicillin / clavulanate' with 'Biomox Pill'). We only resolve the
        // code async if the initial label is blank, is identical to the code,
        // or matches an ATC/SNOMED code shape (which means it's not a real drug name yet).
        val needsResolution = !code.isNullOrBlank() && (
            m.displayLabel.isNullOrBlank() ||
            m.displayLabel == code ||
            Regex("^[A-Za-z]\\d{2}[A-Za-z]{2}\\d{2}$").matches(m.displayLabel.trim()) ||
            Regex("^\\d+$").matches(m.displayLabel.trim())
        )

        if (needsResolution && !code.isNullOrBlank()) {
            // Mark this tag for recycle safety
            b.medicationRowName.tag = code
            scope.launch {
                val resolved = kb.resolveDrug(code)
                val display = when (resolved) {
                    is ResolvedConcept.Exact -> resolved.value.primaryDisplay
                    is ResolvedConcept.Prefix -> resolved.value.primaryDisplay
                    is ResolvedConcept.Contains -> resolved.value.primaryDisplay
                    is ResolvedConcept.NotFound -> null
                }
                if (!display.isNullOrBlank() && b.medicationRowName.tag == code) {
                    b.medicationRowName.text = IpsTranslationsRepository.cleanBilingual(display, lang)
                }
            }
        } else {
            b.medicationRowName.tag = null
        }

        // ─── Subtitle : "5 mg · 1x/jour · 💊 Orale" ─────────────
        val parts = mutableListOf<String>()
        if (!m.v.isNullOrBlank()) {
            val unit = m.u?.takeIf { it.isNotBlank() } ?: ""
            parts.add(if (unit.isBlank()) m.v else "${m.v} $unit")
        }
        m.t?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        routeEntry?.let { parts.add("${it.emoji} ${it.pick(lang)}") }
        b.medicationRowSubtitle.text = parts.joinToString(" · ").ifBlank { "—" }

        // ─── Reason line (rs/rc) ────────────────────────────────
        val reason = m.rs?.takeIf { it.isNotBlank() }
            ?: m.rc?.takeIf { it.isNotBlank() }
        if (reason != null) {
            b.medicationRowReason.text = IpsTranslationsRepository.cleanBilingual(reason, lang)
            b.medicationRowReason.visibility = android.view.View.VISIBLE
        } else {
            b.medicationRowReason.visibility = android.view.View.GONE
        }

        // ─── Tap handlers ────────────────────────────────────────
        // 🆕 Lot 14.5c9 — Fix delete bug : on lit bindingAdapterPosition
        // DYNAMIQUEMENT au moment du tap, pas via la closure. Sinon après
        // un DiffUtil-driven remove/shift, le ViewHolder reste à l'écran
        // sans rebind et sa closure garde une stale `position` du bind
        // précédent — d'où le bug "supprime le mauvais médoc".
        b.root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val item = getItem(pos)
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 👆 tap · pos=$pos · code=${item.c}")
            onTap(item, pos)
        }
        b.root.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener true
            val item = getItem(pos)
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 👆 long-press · pos=$pos · code=${item.c}")
            onLongPress(item, pos)
            true
        }
    }
}
