/*
 * VictimCardAdapter.kt — JEMMA Pass · JemmaAppDemo · v2.5.12 (UX compact)
 *
 * RecyclerView adapter for the radar list AND the SOS broadcast peer list.
 *
 * v2.5.12 changes :
 *   • Layout compacted from 5 rows to 2 (Row 1 emoji+name+sid+range+lang
 *     +crit+counts+seen on TWO compact horizontal lines, Row 2 SALT).
 *   • New emoji slot at the start of Row 1 (was nowhere) for sex/age
 *     pictogram, mirroring the radar dots.
 *   • New `isRescuer: Boolean` flag in [Victim] — rescuer rows hide the
 *     SALT row and show a ⛑️ accent strip instead of the criticality
 *     traffic light. Used by SosBroadcastFragment so its unified list
 *     can mix rescuers and other victims.
 *   • Kept the `showInlineTriage` constructor flag — when false (SOS
 *     broadcast list) all SALT buttons are hidden regardless of the
 *     row's isRescuer state.
 */
package be.heyman.android.jemmapassdemo.ui.radar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.ItemVictimCardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class VictimCardAdapter(
    private val showInlineTriage: Boolean = true,
    private val onSalt: (Victim, String, Boolean) -> Unit = { _, _, _ -> },
    private val onClick: (Victim) -> Unit,
) : RecyclerView.Adapter<VictimCardAdapter.VH>() {

    companion object {
        private const val TAG = "JEMMA-TRIAGE-UI"

        private val SALT_META = listOf(
            SaltMeta("WAIT", "⏳", "#9E9E9E", false),
            SaltMeta("EVAL", "🔍", "#FFC107", false),
            SaltMeta("STAB", "✅", "#4CAF50", false),
            SaltMeta("HELP", "🆘", "#F44336", false),
            SaltMeta("EVAC", "🚑", "#2196F3", true),
            SaltMeta("DCD",  "🕊\uFE0F", "#FFFFFF", true),
        )

        private data class SaltMeta(
            val code: String,
            val emoji: String,
            val colorHex: String,
            val needsConfirm: Boolean,
        )

        /** Same sex/age emoji table as RadarOverlayView. */
        private fun sexAgeEmoji(sex: Char, ageDecade: Int): String {
            return when {
                ageDecade == 0 -> "\uD83D\uDC76"
                ageDecade == 1 -> when (sex) {
                    'F', 'f' -> "\uD83D\uDC67"
                    'M', 'm' -> "\uD83D\uDC66"
                    else      -> "\uD83E\uDDD2"
                }
                ageDecade in 2..6 -> when (sex) {
                    'F', 'f' -> "\uD83D\uDC69"
                    'M', 'm' -> "\uD83D\uDC68"
                    else      -> "\uD83E\uDDD1"
                }
                ageDecade >= 7 -> when (sex) {
                    'F', 'f' -> "\uD83D\uDC75"
                    'M', 'm' -> "\uD83D\uDC74"
                    else      -> "\uD83E\uDDD3"
                }
                else -> "\uD83E\uDDD1"
            }
        }
    }

    data class Victim(
        val sessionIdHex: String,
        val name: String,
        val sex: Char,
        val ageDecade: Int,
        val bloodType: String,
        val criticality: Int,
        val rssi: Int,
        val chunksReceived: Int,
        val chunksTotal: Int,
        val complete: Boolean,
        val distanceM: Double?,
        val bearingDeg: Double?,
        val hasGps: Boolean,
        val nAllergies: Int,
        val nMedications: Int,
        val langCode: String,
        val lastSeenAgoMs: Long?,
        val isStale: Boolean,
        val saltCode: String? = null,
        /** v2.5.12 — true for rescuer rows (no SALT, ⛑️ accent strip). */
        val isRescuer: Boolean = false,
    )

    private var items: List<Victim> = emptyList()

    fun submit(newItems: List<Victim>) {
        val old = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                old[oldPos].sessionIdHex == newItems[newPos].sessionIdHex &&
                old[oldPos].isRescuer == newItems[newPos].isRescuer
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                old[oldPos] == newItems[newPos]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemVictimCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(items[position], onClick, onSalt, showInlineTriage)

    class VH(private val b: ItemVictimCardBinding) : RecyclerView.ViewHolder(b.root) {

        @SuppressLint("SetTextI18n")
        fun bind(
            v: Victim,
            onClick: (Victim) -> Unit,
            onSalt: (Victim, String, Boolean) -> Unit,
            showInlineTriage: Boolean,
        ) {
            val ctx = b.root.context
            val res = b.root.resources

            // ── Strip color : SALT priority > rescuer > criticality ──
            val critRes = when (v.criticality) {
                3 -> Triple(R.color.jemma_crit_red, R.string.crit_red, "🔴")
                2 -> Triple(R.color.jemma_crit_yellow, R.string.crit_yellow, "🟡")
                1 -> Triple(R.color.jemma_crit_green, R.string.crit_green, "🟢")
                else -> Triple(R.color.jemma_crit_unknown, R.string.crit_unknown, "⚪")
            }
            val saltSpec = SaltUi.resolve(v.saltCode)
            when {
                saltSpec != null -> {
                    b.victimCritStrip.setBackgroundColor(saltSpec.colorInt)
                    b.victimCritLabel.text = "${saltSpec.emoji} ${res.getString(saltSpec.labelRes)}"
                }
                v.isRescuer -> {
                    b.victimCritStrip.setBackgroundResource(R.color.jemma_primary)
                    b.victimCritLabel.text = "⛑️ ${v.langCode.uppercase().ifBlank { "RESCUE" }}"
                }
                else -> {
                    b.victimCritStrip.setBackgroundResource(critRes.first)
                    b.victimCritLabel.text = "${critRes.third} ${res.getString(critRes.second)}"
                }
            }

            // ── Identity row ──
            b.victimEmoji.text = sexAgeEmoji(v.sex, v.ageDecade)
            val displayName = v.name.ifBlank { res.getString(R.string.victim_anon) }
            b.victimName.text = displayName
            b.victimSid.text = "#${v.sessionIdHex}"

            // ── Range : compact "Nm·CARDINAL" ──
            b.victimRange.text = when {
                v.hasGps && v.distanceM != null -> {
                    val d = v.distanceM
                    val dStr = if (d < 1000) "${d.toInt()}m" else String.format("%.1fkm", d / 1000.0)
                    val bearing = v.bearingDeg?.let { bearingToCardinal(it) } ?: ""
                    if (bearing.isNotEmpty()) "$dStr·$bearing" else dStr
                }
                else -> "rssi${v.rssi}"
            }

            // ── Demo chip ──
            val sexStr = when (v.sex) { 'M', 'm' -> "♂"; 'F', 'f' -> "♀"; else -> "•" }
            val age = if (v.ageDecade > 0) "${v.ageDecade * 10}s" else "—"
            val blood = v.bloodType.ifBlank { "—" }
            b.victimChipDemo.text = "$sexStr·$age·$blood"

            // ── Language chip ──
            b.victimChipLang.text = if (v.langCode.isNotBlank()) v.langCode.uppercase() else ""
            b.victimChipLang.visibility = if (v.langCode.isNotBlank()) View.VISIBLE else View.GONE

            // ── Counts compact ──
            b.victimCounts.text = if (v.isRescuer) {
                ""
            } else {
                "⚠${v.nAllergies}·💊${v.nMedications}"
            }
            b.victimCounts.visibility = if (v.isRescuer) View.GONE else View.VISIBLE

            // ── Chunks + last seen compact ──
            val chunksLabel = if (v.complete) "✓${v.chunksReceived}/${v.chunksTotal}"
                              else "${v.chunksReceived}/${v.chunksTotal}"
            val seenLabel = v.lastSeenAgoMs?.let {
                when {
                    it < 5_000  -> "now"
                    it < 60_000 -> "${it / 1000}s"
                    else        -> "${it / 60_000}m"
                }
            } ?: "—"
            b.victimMeta.text = "$chunksLabel·$seenLabel"

            // ── Stale dim ──
            b.root.alpha = if (v.isStale) 0.5f else 1.0f

            // ── Card click ──
            b.root.setOnClickListener { onClick(v) }

            // ── SALT row : hidden if showInlineTriage=false OR isRescuer=true ──
            if (!showInlineTriage || v.isRescuer) {
                b.victimSaltRow.visibility = View.GONE
                b.victimSaltBtnCancelDcd.visibility = View.GONE
                return
            }
            b.victimSaltRow.visibility = View.VISIBLE

            bindSaltButton(ctx, b.victimSaltBtnWait, v, SALT_META[0], onSalt)
            bindSaltButton(ctx, b.victimSaltBtnEval, v, SALT_META[1], onSalt)
            bindSaltButton(ctx, b.victimSaltBtnStab, v, SALT_META[2], onSalt)
            bindSaltButton(ctx, b.victimSaltBtnHelp, v, SALT_META[3], onSalt)
            bindSaltButton(ctx, b.victimSaltBtnEvac, v, SALT_META[4], onSalt)
            bindSaltButton(ctx, b.victimSaltBtnDcd,  v, SALT_META[5], onSalt)

            if (v.saltCode == "DCD") {
                b.victimSaltBtnCancelDcd.visibility = View.VISIBLE
                b.victimSaltBtnCancelDcd.text = res.getString(R.string.triage_btn_cancel_deceased)
                b.victimSaltBtnCancelDcd.background = pillBackground(
                    ctx, fillHex = "#1E293B", strokeHex = "#F44336", strokeDp = 2,
                )
                b.victimSaltBtnCancelDcd.setOnClickListener {
                    showConfirmDialog(
                        ctx = ctx,
                        title = res.getString(R.string.triage_confirm_title),
                        message = res.getString(
                            R.string.triage_confirm_msg,
                            "STAB", "✅", v.name.ifBlank { v.sessionIdHex },
                        ),
                        onConfirm = {
                            Log.i(TAG, "[t=${System.currentTimeMillis()}] " +
                                "🚑 inline · victim=${v.sessionIdHex} status=STAB " +
                                "override=true (cancel-deceased)")
                            onSalt(v, "STAB", true)
                        },
                    )
                }
            } else {
                b.victimSaltBtnCancelDcd.visibility = View.GONE
                b.victimSaltBtnCancelDcd.setOnClickListener(null)
            }
        }

        private fun bindSaltButton(
            ctx: Context,
            btn: TextView,
            v: Victim,
            meta: SaltMeta,
            onSalt: (Victim, String, Boolean) -> Unit,
        ) {
            val isActive = (v.saltCode == meta.code)
            btn.text = meta.emoji
            btn.background = pillBackground(
                ctx,
                fillHex   = if (isActive) meta.colorHex else "#1E293B",
                strokeHex = meta.colorHex,
                strokeDp  = if (isActive) 2 else 1,
            )
            btn.setOnClickListener {
                if (meta.needsConfirm) {
                    showConfirmDialog(
                        ctx = ctx,
                        title = ctx.getString(R.string.triage_confirm_title),
                        message = ctx.getString(
                            R.string.triage_confirm_msg,
                            meta.code, meta.emoji, v.name.ifBlank { v.sessionIdHex },
                        ),
                        onConfirm = {
                            Log.i(TAG, "[t=${System.currentTimeMillis()}] " +
                                "🚑 inline · victim=${v.sessionIdHex} status=${meta.code} " +
                                "override=false (confirmed)")
                            onSalt(v, meta.code, false)
                        },
                    )
                } else {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] " +
                        "🚑 inline · victim=${v.sessionIdHex} status=${meta.code} override=false")
                    onSalt(v, meta.code, false)
                }
            }
        }

        private fun pillBackground(
            ctx: Context,
            fillHex: String,
            strokeHex: String,
            strokeDp: Int,
        ): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(fillHex))
            setStroke(dp(ctx, strokeDp), Color.parseColor(strokeHex))
            cornerRadius = dp(ctx, 8).toFloat()
        }

        private fun showConfirmDialog(
            ctx: Context,
            title: String,
            message: String,
            onConfirm: () -> Unit,
        ) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.triage_confirm_yes) { d, _ ->
                    d.dismiss()
                    onConfirm()
                }
                .setNegativeButton(R.string.triage_confirm_no) { d, _ -> d.dismiss() }
                .show()
        }

        private fun dp(ctx: Context, v: Int): Int =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                v.toFloat(),
                ctx.resources.displayMetrics,
            ).toInt()

        private fun bearingToCardinal(deg: Double): String {
            val d = ((deg % 360.0) + 360.0) % 360.0
            return when {
                d < 22.5   -> "N"
                d < 67.5   -> "NE"
                d < 112.5  -> "E"
                d < 157.5  -> "SE"
                d < 202.5  -> "S"
                d < 247.5  -> "SW"
                d < 292.5  -> "W"
                d < 337.5  -> "NW"
                else       -> "N"
            }
        }
    }
}
