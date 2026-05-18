/*
 * StubFragmentBase.kt — minimal base class for all "not yet implemented"
 * Fragment stubs that exist only to unblock the nav_graph.
 *
 * v2.2.7 fix : the constructor parameter that previously named `tag` was
 * shadowed by Fragment.getTag() (inherited from androidx.fragment.app.Fragment,
 * which returns a String?). The Kotlin compiler resolved `tag` as the
 * inherited property — type String? — instead of our String parameter,
 * which broke Log.i / Log.w at compile time. Renamed to `logTag`.
 */
package be.heyman.android.jemmapassdemo.ui.stubs

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

abstract class StubFragmentBase(
    private val logTag: String,
    private val emoji: String,
    private val displayName: String,
    private val comingInDelivery: String,
) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d(
            logTag,
            "[t=${System.currentTimeMillis()}] 📋 onCreateView (stub · ${this::class.simpleName})"
        )

        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F172A"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            val px = (32 * resources.displayMetrics.density).toInt()
            setPadding(px, px, px, px)
        }

        TextView(ctx).apply {
            text = emoji
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
            gravity = Gravity.CENTER
            root.addView(this)
        }

        TextView(ctx).apply {
            text = displayName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F1F5F9"))
            gravity = Gravity.CENTER
            val px = (16 * resources.displayMetrics.density).toInt()
            setPadding(0, px, 0, px / 2)
            root.addView(this)
        }

        TextView(ctx).apply {
            text = comingInDelivery
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            val px = (24 * resources.displayMetrics.density).toInt()
            setPadding(0, 0, 0, px)
            root.addView(this)
        }

        Button(ctx).apply {
            text = "← Retour"
            setBackgroundColor(Color.parseColor("#10B981"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                Log.i(logTag, "[t=${System.currentTimeMillis()}] 👆 stub back tap")
                try {
                    findNavController().navigateUp()
                } catch (e: Exception) {
                    Log.w(
                        logTag,
                        "[t=${System.currentTimeMillis()}] ⚠️ navigateUp failed : ${e.message}",
                    )
                }
            }
            root.addView(this)
        }

        return root
    }
}
