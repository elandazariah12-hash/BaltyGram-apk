package site.baltygram.app.ui.uploads

import android.content.Intent
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

class UploadsFragment : Fragment(R.layout.fragment_uploads) {

    private val MAX_UPLOADS_PER_MONTH = 20

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_new_upload).setOnClickListener {
            if (!SessionManager.isLoggedIn()) {
                android.widget.Toast.makeText(context, "Sign in to upload.", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(requireContext(), UploadWizardActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { loadUploads(it) }
    }

    private fun loadUploads(view: View) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_uploads)
        val emptyState = view.findViewById<View>(R.id.empty_state)
        val capBanner = view.findViewById<TextView>(R.id.cap_banner)

        if (!SessionManager.isLoggedIn()) {
            recycler.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            (emptyState as? TextView)?.text = "Sign in to see your uploads."
            capBanner.visibility = View.GONE
            return
        }

        recycler.layoutManager = LinearLayoutManager(context)

        SupabaseApi.select(
            "uploads", "*",
            "uploader_id=eq.${SessionManager.userId}&order=submitted_at.desc",
            object : SupabaseApi.Callback {
                override fun onSuccess(body: String) {
                    if (!isAdded) return
                    val arr = JSONArray(body)

                    // Monthly cap banner
                    val used = arr.length().coerceAtMost(MAX_UPLOADS_PER_MONTH)
                    if (used >= 5) {
                        val remaining = (MAX_UPLOADS_PER_MONTH - used).coerceAtLeast(0)
                        capBanner.visibility = View.VISIBLE
                        capBanner.text = if (remaining > 0)
                            "$remaining upload${if (remaining == 1) "" else "s"} left this month ($used/$MAX_UPLOADS_PER_MONTH used)."
                        else
                            "You've used all $MAX_UPLOADS_PER_MONTH uploads this month. Resets on the 1st."
                    } else {
                        capBanner.visibility = View.GONE
                    }

                    if (arr.length() == 0) {
                        recycler.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                        return
                    }
                    recycler.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE

                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    recycler.adapter = UploadsAdapter(list)
                }
                override fun onError(message: String) {}
            }
        )
    }
}

private class UploadsAdapter(private val items: List<JSONObject>) :
    RecyclerView.Adapter<UploadsAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.row_name)
        val meta: TextView = view.findViewById(R.id.row_meta)
        val status: TextView = view.findViewById(R.id.row_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_upload_row, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = items[position]
        holder.name.text = u.optString("project_name", "Untitled submission")
        holder.meta.text = "${u.optString("platform", "-")} - ${u.optString("file_name", "no file")}"
        holder.status.text = u.optString("status", "pending").uppercase()
    }

    override fun getItemCount(): Int = items.size
}
