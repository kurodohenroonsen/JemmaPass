/*
 * RescuerBadgeAdapter.kt — JEMMA Pass · JemmaAppDemo · L5 v2.5.6
 *
 * RecyclerView adapter for the rescuer list shown to a broadcasting
 * victim. Each row tells the victim: a rescuer is here, this is their
 * language, this is how recently they were heard.
 *
 * Compact row: ⛑ name · langCode · last-seen
 */
package be.heyman.android.jemmapassdemo.ui.radar

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.ItemRescuerBadgeBinding

class RescuerBadgeAdapter : RecyclerView.Adapter<RescuerBadgeAdapter.VH>() {

    data class Rescuer(
        val sessionIdHex: String,
        val name: String,
        val langCode: String,
        val lastSeenAgoMs: Long?,
        val latitude: Double?,
        val longitude: Double?,
        val isStale: Boolean,
    )

    private var items: List<Rescuer> = emptyList()

    fun submit(newItems: List<Rescuer>) {
        val old = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                old[oldPos].sessionIdHex == newItems[newPos].sessionIdHex
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                old[oldPos] == newItems[newPos]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemRescuerBadgeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    class VH(private val b: ItemRescuerBadgeBinding) : RecyclerView.ViewHolder(b.root) {
        @SuppressLint("SetTextI18n")
        fun bind(r: Rescuer) {
            val res = b.root.resources
            b.rescuerName.text = "⛑ ${r.name.ifBlank { res.getString(R.string.rescuer_anon) }}"
            b.rescuerSid.text = "#${r.sessionIdHex}"
            b.rescuerLang.text = if (r.langCode.isNotBlank()) r.langCode.uppercase() else "—"
            b.rescuerSeen.text = r.lastSeenAgoMs?.let {
                when {
                    it < 5_000  -> res.getString(R.string.victim_seen_now)
                    it < 60_000 -> res.getString(R.string.victim_seen_secs, it / 1000)
                    else        -> res.getString(R.string.victim_seen_mins, it / 60_000)
                }
            } ?: "—"
            b.root.alpha = if (r.isStale) 0.5f else 1.0f
        }
    }
}
