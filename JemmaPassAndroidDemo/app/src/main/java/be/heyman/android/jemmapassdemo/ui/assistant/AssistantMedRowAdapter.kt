/*
 * AssistantMedRowAdapter.kt — Lot 14.5c (PHASE 14)
 *
 * RecyclerView.Adapter pour la liste de médocs détectés dans
 * AssistantMultiPreviewFragment. Réutilise le layout view_assistant_med_row
 * (créé au Lot 14.4) — checkbox + 💊 + nom + dose/timing + ATC.
 *
 * Le tap n'importe où dans la row toggle la checkbox. Le callback
 * `onSelectionChanged` est invoqué pour permettre au fragment de
 * recalculer le bouton "Enregistrer N" en temps réel.
 */
package be.heyman.android.jemmapassdemo.ui.assistant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.assistant.ExtractedMedication
import com.google.android.material.checkbox.MaterialCheckBox

class AssistantMedRowAdapter(
    private val items: MutableList<ExtractedMedication>,
    private val onSelectionChanged: () -> Unit,
) : RecyclerView.Adapter<AssistantMedRowAdapter.MedVH>() {

    class MedVH(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.med_row_root)
        val checkbox: MaterialCheckBox = view.findViewById(R.id.med_row_checkbox)
        val name: TextView = view.findViewById(R.id.med_row_name)
        val doseTiming: TextView = view.findViewById(R.id.med_row_dose_timing)
        val atc: TextView = view.findViewById(R.id.med_row_atc)
        val thumbnailCard: androidx.cardview.widget.CardView? = view.findViewById(R.id.med_row_thumbnail_card)
        val thumbnail: android.widget.ImageView? = view.findViewById(R.id.med_row_thumbnail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_assistant_med_row, parent, false)
        return MedVH(view)
    }

    override fun onBindViewHolder(holder: MedVH, position: Int) {
        val item = items[position]
        val ctx = holder.root.context

        holder.checkbox.isChecked = item.selected
        holder.name.text = item.displayName()

        // 🆕 Lot 14.5c3 — sub-line affiche page + dose KB + OCR snippet.
        val firstOcrLine = item.ocrSnippet.lines().firstOrNull()?.take(80) ?: ""
        val pageLabel = ctx.getString(R.string.assistant_multi_row_page_n, item.pageIndex + 1)
        val dose = item.firstDose
        holder.doseTiming.text = if (dose != null) {
            "$pageLabel · 💊 $dose · $firstOcrLine"
        } else {
            "$pageLabel · $firstOcrLine"
        }

        // 🆕 Lot 14.5c3 — 3 états visuels :
        //   • Non identifié (gemmaSkipped) → label gris "Non identifié"
        //   • Résolu avec ATC          → label vert "ATC · display"
        //   • Pas trouvé dans KB        → label orange "non trouvé dans la base"
        val kb = item.kbConcept()
        val visionPrefix = if (item.visionVerified) "👁️✨ [Vision] " else ""
        when {
            item.gemmaSkipped -> {
                holder.atc.setText(R.string.assistant_multi_row_skipped)
                holder.atc.setTextColor(
                    ctx.resources.getColor(android.R.color.darker_gray, ctx.theme),
                )
            }
            kb != null && kb.atcCode != null -> {
                holder.atc.text = visionPrefix + ctx.getString(
                    R.string.assistant_multi_row_resolved_atc,
                    kb.atcCode, kb.primaryDisplay,
                )
                holder.atc.setTextColor(
                    ctx.resources.getColor(R.color.jemma_primary, ctx.theme),
                )
            }
            else -> {
                val label = visionPrefix + ctx.getString(R.string.assistant_multi_row_resolved_none)
                holder.atc.text = label
                holder.atc.setTextColor(
                    ctx.resources.getColor(android.R.color.holo_orange_light, ctx.theme),
                )
            }
        }

        // Load thumbnail safely
        if (holder.thumbnailCard != null && holder.thumbnail != null) {
            val bmp = loadMiniThumbnail(ctx, item.imageUri)
            if (bmp != null) {
                holder.thumbnail.setImageBitmap(bmp)
                holder.thumbnailCard.visibility = View.VISIBLE
            } else {
                holder.thumbnailCard.visibility = View.GONE
            }
        }

        // ContentDescription dynamique pour TalkBack.
        holder.root.contentDescription = ctx.getString(
            R.string.assistant_multi_row_check_cd,
            item.displayName(),
        )

        // Tap row → toggle checkbox + notify.
        // Pour les gemmaSkipped, le tap permet quand même de cocher (au cas où
        // l'user veut quand même garder l'OCR snippet, mais le save filtrera).
        holder.root.setOnClickListener {
            item.selected = !item.selected
            holder.checkbox.isChecked = item.selected
            onSelectionChanged()
        }
    }

    override fun getItemCount(): Int = items.size

    private fun loadMiniThumbnail(context: android.content.Context, uriString: String?): android.graphics.Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = android.net.Uri.parse(uriString)
            val resolver = context.contentResolver
            val options = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = 8 // Very safe for heap allocation
            }
            resolver.openInputStream(uri).use {
                android.graphics.BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }
}
