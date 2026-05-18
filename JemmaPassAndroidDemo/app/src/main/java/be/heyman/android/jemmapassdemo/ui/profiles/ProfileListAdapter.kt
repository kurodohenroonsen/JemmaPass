/*
 * ProfileListAdapter.kt — v2.2.16.9
 *
 * v2.2.16.9 changes :
 *   • ⭐ button is now ALWAYS visible (gold when current, gray when not)
 *     and tappable to toggle the current profile (no more long-press
 *     menu needed for that)
 *   • 🗑 button bigger (52x52dp, 26sp) with explicit log on tap
 *   • Heavy logging in every callback for debugging discoverability
 *
 * Card layout :
 *   ┌────────────────────────────────────────────────────┐
 *   │ 👤 Haru Nakamura                          ⭐  🗑   │
 *   │ 1956-02-05 · A+                                     │
 *   │ 🩹 5 allergies · 💊 6 médicaments                   │
 *   └────────────────────────────────────────────────────┘
 *
 * Interactions :
 *   • Tap card body  → onProfileClick (open ProfileDetail)
 *   • Tap ⭐         → onSetCurrent (toggle current profile)
 *   • Tap 🗑         → onProfileDelete (confirm dialog then delete)
 *   • Long-press card → onProfileLongClick (action menu, optional)
 */
package be.heyman.android.jemmapassdemo.ui.profiles

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.profiles.ProfileSummary

class ProfileListAdapter(
    private val onProfileClick: (ProfileSummary) -> Unit,
    private val onProfileLongClick: (ProfileSummary) -> Unit,
    private val onProfileDelete: (ProfileSummary) -> Unit,
    private val onSetCurrent: (ProfileSummary) -> Unit,
) : RecyclerView.Adapter<ProfileListAdapter.ViewHolder>() {

    companion object {
        private const val TAG = "JEMMA-PROFILES"
    }

    private val items = mutableListOf<ProfileSummary>()
    private var currentId: String? = null

    fun submit(newItems: List<ProfileSummary>, currentId: String?) {
        items.clear()
        items.addAll(newItems)
        this.currentId = currentId
        Log.d(
            TAG,
            "[t=${System.currentTimeMillis()}] [Adapter] 📦 submit · ${newItems.size} profiles · currentId=$currentId"
        )
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile_summary, parent, false)
        Log.d(TAG, "[t=${System.currentTimeMillis()}] [Adapter] 🏗 onCreateViewHolder")
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isCurrent = item.id == currentId
        Log.d(
            TAG,
            "[t=${System.currentTimeMillis()}] [Adapter] 🔗 bind pos=$position · id=${item.id} · name=${item.displayName} · isCurrent=$isCurrent"
        )
        holder.bind(
            summary = item,
            isCurrent = isCurrent,
            onClick = onProfileClick,
            onLongClick = onProfileLongClick,
            onDelete = onProfileDelete,
            onSetCurrent = onSetCurrent,
        )
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.profile_card_name)
        private val details: TextView = itemView.findViewById(R.id.profile_card_details)
        private val counters: TextView = itemView.findViewById(R.id.profile_card_counters)
        private val starBtn: TextView = itemView.findViewById(R.id.profile_card_star)
        private val deleteBtn: TextView = itemView.findViewById(R.id.profile_card_delete)

        fun bind(
            summary: ProfileSummary,
            isCurrent: Boolean,
            onClick: (ProfileSummary) -> Unit,
            onLongClick: (ProfileSummary) -> Unit,
            onDelete: (ProfileSummary) -> Unit,
            onSetCurrent: (ProfileSummary) -> Unit,
        ) {
            name.text = "👤  ${summary.displayName}"

            val detailParts = buildList {
                summary.birthDate?.takeIf { it.isNotBlank() }?.let { add(it) }
                summary.bloodType?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            details.text = detailParts.joinToString(" · ")
            details.visibility = if (detailParts.isEmpty()) View.GONE else View.VISIBLE

            val ctx = itemView.context
            val counterParts = buildList {
                if (summary.allergiesCount > 0) {
                    add(ctx.getString(R.string.profile_card_allergies_count, summary.allergiesCount))
                }
                if (summary.medicationsCount > 0) {
                    add(ctx.getString(R.string.profile_card_medications_count, summary.medicationsCount))
                }
                if (summary.conditionsCount > 0) {
                    add(ctx.getString(R.string.profile_card_conditions_count, summary.conditionsCount))
                }
            }
            counters.text = counterParts.joinToString(" · ")
            counters.visibility = if (counterParts.isEmpty()) View.GONE else View.VISIBLE

            // ⭐ button : gold when current, gray when not. Tap toggles.
            starBtn.text = if (isCurrent) "⭐" else "☆"
            starBtn.alpha = if (isCurrent) 1f else 0.5f
            starBtn.setOnClickListener {
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] [Adapter] 👆 ⭐ tap · id=${summary.id} · wasCurrent=$isCurrent"
                )

                onSetCurrent(summary)
            }

            // 🗑 button : always visible, tap opens confirm dialog.
            deleteBtn.text = "🗑"
            deleteBtn.setOnClickListener {
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] [Adapter] 👆 🗑 tap · id=${summary.id} · name=${summary.displayName}"
                )

                onDelete(summary)
            }

            // Card body click → open detail.
            itemView.setOnClickListener {
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] [Adapter] 👆 card tap · id=${summary.id}"
                )

                onClick(summary)
            }
            itemView.setOnLongClickListener {
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] [Adapter] 👆 card long-press · id=${summary.id}"
                )
                onLongClick(summary)
                true
            }
        }
    }
}
