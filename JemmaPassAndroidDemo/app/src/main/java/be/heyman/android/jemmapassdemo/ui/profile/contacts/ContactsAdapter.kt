/*
 * ContactsAdapter.kt — JEMMA Pass · Plan B · v2.6.0 · L5b
 *
 * ListAdapter (RecyclerView + DiffUtil) qui affiche la liste des
 * contacts d'un patient — sous-pilier de JPatient.ct.
 *
 * Chaque row affiche :
 *   📞  Nom du contact          [emoji relation]
 *       Relation · Téléphone
 *
 * Tap row → onTap callback (parent ouvre le BottomSheet form en mode EDIT)
 * Long-press row → onLongPress callback (parent affiche confirm delete)
 *
 * Le label de relation est résolu via IpsRelationshipCatalog (39 codes
 * V3-RoleCode FR/EN/JA hardcodés).
 *
 * Logging : tag JEMMA-CONTACTS-ADAPTER
 */
package be.heyman.android.jemmapassdemo.ui.profile.contacts

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.databinding.ItemContactRowBinding
import be.heyman.android.jemmapassdemo.pillars.ContactFormLogic
import be.heyman.android.jemmapassdemo.qr.CodeLabelResolver
import be.heyman.android.jemmapassdemo.qr.JContact

class ContactsAdapter(
    private val lang: String,
    private val onTap: (JContact, Int) -> Unit,
    private val onLongPress: (JContact, Int) -> Unit,
) : ListAdapter<JContact, ContactsAdapter.VH>(DIFF) {

    companion object {
        private const val TAG = "JEMMA-CONTACTS-ADAPTER"

        private val DIFF = object : DiffUtil.ItemCallback<JContact>() {
            override fun areItemsTheSame(oldItem: JContact, newItem: JContact): Boolean {
                // No stable id in JContact — use composite of name+phone as best effort.
                // Acceptable since we never mutate in place; we always replace whole list.
                return oldItem.n == newItem.n && oldItem.p == newItem.p
            }
            override fun areContentsTheSame(oldItem: JContact, newItem: JContact): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class VH(val binding: ItemContactRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemContactRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = getItem(position)
        val b = holder.binding

        // Display name — fallback to "—" if blank (shouldn't happen post-save validation)
        b.contactRowName.text = c.n?.takeIf { it.isNotBlank() } ?: "—"

        // Relation : resolve code → localized display via ContactFormLogic
        val relationDisplay = ContactFormLogic.relationDisplay(c.r, CodeLabelResolver.NONE, lang) ?: "—"

        // Phone + relation in subtitle row
        val subtitleParts = mutableListOf<String>()
        subtitleParts.add(relationDisplay)
        if (!c.p.isNullOrBlank()) subtitleParts.add(c.p)
        b.contactRowSubtitle.text = subtitleParts.joinToString(" · ")

        // Email + address show on a 3rd line if either present
        val line3 = listOfNotNull(
            c.e?.takeIf { it.isNotBlank() },
            c.adr?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        b.contactRowLine3.text = line3
        b.contactRowLine3.visibility = if (line3.isBlank())
            android.view.View.GONE else android.view.View.VISIBLE

        // 🆕 Lot 14.5c9 — Fix : bindingAdapterPosition lu dynamiquement.
        b.root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val item = getItem(pos)
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 👆 tap · pos=$pos · name=${item.n}")
            onTap(item, pos)
        }
        b.root.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener true
            val item = getItem(pos)
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 👆 long-press · pos=$pos · name=${item.n}")
            onLongPress(item, pos)
            true
        }
    }
}
