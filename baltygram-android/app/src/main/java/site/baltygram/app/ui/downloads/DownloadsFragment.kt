package site.baltygram.app.ui.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import site.baltygram.app.R
import site.baltygram.app.data.SessionManager
import site.baltygram.app.network.SupabaseApi

class DownloadsFragment : Fragment(R.layout.fragment_downloads) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateConnectivity(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { loadDownloads(it) }
    }

    private fun updateConnectivity(view: View) {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val statusView = view.findViewById<TextView>(R.id.connectivity_status)
        statusView.text = if (online) "Online" else "Offline"
        statusView.setTextColor(resources.getColor(if (online) R.color.brand_600 else R.color.red_500))
    }

    private fun loadDownloads(view: View) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_downloads)
        val emptyState = view.findViewById<View>(R.id.empty_state)

        if (!SessionManager.isLoggedIn()) {
            recycler.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            (emptyState as? TextView)?.text = "Sign in to see your downloads."
            return
        }

        recycler.layoutManager = LinearLayoutManager(context)

        SupabaseApi.select(
            "downloads", "*,software(name),software_versions(version)",
            "user_id=eq.${SessionManager.userId}&deleted_from_history=eq.false&order=downloaded_at.desc&limit=50",
            object : SupabaseApi.Callback {
                override fun onSuccess(body: String) {
                    if (!isAdded) return
                    val arr = JSONArray(body)
                    if (arr.length() == 0) {
                        recycler.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                        return
                    }
                    recycler.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    recycler.adapter = DownloadsAdapter(list)
                }
                override fun onError(message: String) {}
            }
        )
    }
}

private class DownloadsAdapter(private val items: List<JSONObject>) :
    RecyclerView.Adapter<DownloadsAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.row_name)
        val meta: TextView = view.findViewById(R.id.row_meta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download_row, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = items[position]
        val swName = d.optJSONObject("software")?.optString("name") ?: "Unknown"
        val version = d.optJSONObject("software_versions")?.optString("version") ?: "-"
        holder.name.text = swName
        val bytes = d.optLong("bytes_downloaded", 0)
        holder.meta.text = "v$version - ${d.optString("status")} - ${String.format("%.1f", bytes / 1e6)} MB"
    }

    override fun getItemCount(): Int = items.size
}
