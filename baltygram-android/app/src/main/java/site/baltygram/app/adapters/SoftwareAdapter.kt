package site.baltygram.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import site.baltygram.app.R
import site.baltygram.app.util.ImageLoader

/** Maps a software_type string to a category icon, matching the web app's icon set. */
private val TYPE_ICON = mapOf(
    "game" to R.drawable.ic_gamepad,
    "software" to R.drawable.ic_package,
    "app" to R.drawable.ic_package,
    "editing_tool" to R.drawable.ic_package,
    "ai_tool" to R.drawable.ic_package,
    "developer_tool" to R.drawable.ic_package,
    "other" to R.drawable.ic_package
)

class SoftwareAdapter(
    private var items: List<JSONObject>,
    private val onClick: (JSONObject) -> Unit
) : RecyclerView.Adapter<SoftwareAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.card_icon)
        val typeIcon: ImageView = view.findViewById(R.id.card_type_icon)
        val badge: TextView = view.findViewById(R.id.card_badge)
        val name: TextView = view.findViewById(R.id.card_name)
        val description: TextView = view.findViewById(R.id.card_description)
    }

    fun updateItems(newItems: List<JSONObject>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_software_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val sw = items[position]
        holder.name.text = sw.optString("name")
        holder.description.text = sw.optString("short_description")

        val iconUrl = sw.optString("icon_url", null)
        if (!iconUrl.isNullOrEmpty() && iconUrl != "null") {
            ImageLoader.load(holder.icon, iconUrl)
        } else {
            holder.icon.setImageResource(R.drawable.ic_package)
        }

        val type = sw.optString("software_type")
        holder.typeIcon.setImageResource(TYPE_ICON[type] ?: R.drawable.ic_package)

        holder.badge.visibility = if (sw.optBoolean("is_featured", false)) View.VISIBLE else View.GONE
        holder.badge.text = "FEATURED"

        holder.itemView.setOnClickListener { onClick(sw) }
    }

    override fun getItemCount(): Int = items.size
}
