/*
 * AssistantPagesAdapter.kt — Lot 14.5b1 (PHASE 14)
 *
 * RecyclerView.Adapter horizontal pour les N pages scannées par
 * Document Scanner ou pickées en galerie. Combiné avec un
 * PagerSnapHelper, ça donne un viewer swipeable simple sans ajouter
 * la dépendance androidx.viewpager2 au build.
 *
 * Chaque item = un FrameLayout match_parent contenant une ImageView
 * fitCenter qui charge l'URI via setImageURI() (simple, suffisant
 * pour des images JPEG du Document Scanner ≤ ~1MB).
 */
package be.heyman.android.jemmapassdemo.ui.assistant

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R

class AssistantPagesAdapter(
    private val uris: List<Uri>,
) : RecyclerView.Adapter<AssistantPagesAdapter.PageVH>() {

    class PageVH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.page_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_assistant_page, parent, false)
        return PageVH(view)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        holder.image.setImageURI(uris[position])
    }

    override fun getItemCount(): Int = uris.size
}
