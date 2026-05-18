/*
 * AllergiesAdapter.kt — JEMMA Pass · Plan B · v2.6.0 · L5c
 *
 * ListAdapter (RecyclerView + DiffUtil) pour la liste des allergies du
 * patient — sous-pilier FHIR AllergyIntolerance IPS, stocké dans
 * JemmaProfileJ.al : List<JAllergy>.
 *
 * Chaque row affiche :
 *   🩹  Substance SNOMED                       [emoji criticality]
 *       Sévérité · Statut · Mécanisme
 *       Notes / manifestations (line3, gone si vide)
 *
 * Tap row → onTap callback (parent ouvre le BottomSheet en mode EDIT)
 * Long-press → onLongPress callback (parent affiche confirm delete)
 *
 * Resolution :
 *   - JAllergy.c (SNOMED code) → label via IpsTranslationsRepository (suspend)
 *   - JAllergy.s (H/L/U) → display via IpsCriticalityCatalog (sync)
 *   - JAllergy.st (A/I/R) → display via IpsClinicalStatusCatalog (sync)
 *
 * Logging : tag JEMMA-ALLERGIES-ADAPTER
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.databinding.ItemAllergyRowBinding
import be.heyman.android.jemmapassdemo.pillars.IpsClinicalStatusCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsCriticalityCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
import be.heyman.android.jemmapassdemo.qr.JAllergy
import kotlinx.coroutines.launch

class AllergiesAdapter(
    private val lang: String,
    private val translations: IpsTranslationsRepository,
    private val scope: LifecycleCoroutineScope,
    private val onTap: (JAllergy, Int) -> Unit,
    private val onLongPress: (JAllergy, Int) -> Unit,
) : ListAdapter<JAllergy, AllergiesAdapter.VH>(DIFF) {

    companion object {
        private const val TAG = "JEMMA-ALLERGIES-ADAPTER"

        private val DIFF = object : DiffUtil.ItemCallback<JAllergy>() {
            override fun areItemsTheSame(oldItem: JAllergy, newItem: JAllergy): Boolean {
                // Composite key (code + notes) — JAllergy has no stable id.
                // Acceptable since list is rebuilt full on every change.
                return oldItem.c == newItem.c && oldItem.d == newItem.d
            }
            override fun areContentsTheSame(oldItem: JAllergy, newItem: JAllergy): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class VH(val binding: ItemAllergyRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAllergyRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = getItem(position)
        val b = holder.binding

        // ─── Criticality emoji prefix on the row icon ─────────────
        val critEntry = IpsCriticalityCatalog.byShortCode(a.s)
        b.allergyRowIcon.text = critEntry?.emoji ?: "🩹"

        // ─── Substance name ─ resolved async from SNOMED catalog ──
        // Initial state : show denormalized label if any, fallback to code,
        // fallback to "—". Then if we have an SNOMED code, query the repo
        // and update when ready.
        val initialLabel = a.displayLabel?.takeIf { it.isNotBlank() }
            ?: a.c?.takeIf { it.isNotBlank() }
            ?: "—"
        b.allergyRowSubstance.text = IpsTranslationsRepository.cleanBilingual(initialLabel, lang)

        val code = a.c
        if (!code.isNullOrBlank()) {
            scope.launch {
                val resolved = translations.get(code, lang)
                if (!resolved.isNullOrBlank()) {
                    val cleaned = IpsTranslationsRepository.cleanBilingual(resolved, lang)
                    // Verify we're still bound to the same item (RecyclerView recycle safety)
                    if (b.allergyRowSubstance.tag == code) {
                        b.allergyRowSubstance.text = cleaned
                    }
                }
            }
            b.allergyRowSubstance.tag = code
        } else {
            b.allergyRowSubstance.tag = null
        }

        // ─── Subtitle : "Sévère · Active · Mécanisme" ────────────
        val parts = mutableListOf<String>()
        critEntry?.let { parts.add(it.pick(lang)) }
        IpsClinicalStatusCatalog.byShortCode(a.st)?.let { parts.add(it.pick(lang)) }
        a.m?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        b.allergyRowSubtitle.text = parts.joinToString(" · ").ifBlank { "—" }

        // ─── Line 3 : notes (allergyRowNotes) ─────────────────────
        val notes = a.d?.takeIf { it.isNotBlank() }
        b.allergyRowNotes.text = notes.orEmpty()
        b.allergyRowNotes.visibility = if (notes == null)
            android.view.View.GONE else android.view.View.VISIBLE

        // ─── Tap handlers ────────────────────────────────────────
        // 🆕 Lot 14.5c9 — Fix : bindingAdapterPosition lu dynamiquement
        // (sinon stale après DiffUtil-driven remove).
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
