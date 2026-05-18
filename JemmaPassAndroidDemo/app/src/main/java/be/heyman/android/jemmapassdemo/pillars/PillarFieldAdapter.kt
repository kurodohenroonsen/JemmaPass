/*
 * PillarFieldAdapter.kt — JEMMA Pass · Plan B · L4 v2.5.3
 *
 * Lightweight RecyclerView adapter for the IPS field list shown inside
 * fragment_pillar_stub.xml. Each row uses item_ips_field_row.xml and
 * displays :
 *   • the field key (monospace, primary color)   "given_name"
 *   • the cardinality                              "1..1"
 *   • the FHIR datatype                            "string"
 *   • a one-line localized description
 *
 * No DiffUtil — the list is fixed for a given pillar key, set once at
 * fragment creation and never updated.
 */
package be.heyman.android.jemmapassdemo.pillars

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R

class PillarFieldAdapter(
    private val fields: List<PillarField>,
) : RecyclerView.Adapter<PillarFieldAdapter.FieldVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FieldVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ips_field_row, parent, false)
        return FieldVH(view)
    }

    override fun getItemCount(): Int = fields.size

    override fun onBindViewHolder(holder: FieldVH, position: Int) {
        holder.bind(fields[position])
    }

    class FieldVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTv: TextView = itemView.findViewById(R.id.ips_field_name)
        private val cardTv: TextView = itemView.findViewById(R.id.ips_field_cardinality)
        private val typeTv: TextView = itemView.findViewById(R.id.ips_field_type)
        private val descTv: TextView = itemView.findViewById(R.id.ips_field_description)

        fun bind(field: PillarField) {
            val ctx = itemView.context
            nameTv.text = field.key
            cardTv.text = field.cardinality
            typeTv.text = field.fhirType
            descTv.text = ctx.getString(field.descRes)
        }
    }
}
