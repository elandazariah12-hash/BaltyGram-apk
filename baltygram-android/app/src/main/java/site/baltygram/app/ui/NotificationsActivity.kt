package site.baltygram.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import site.baltygram.app.R
import site.baltygram.app.data.SessionManager
import site.baltygram.app.network.SupabaseApi

class NotificationsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.recycler_notifications)
        val emptyState = findViewById<View>(R.id.empty_state)
        recycler.layoutManager = LinearLayoutManager(this)

        val userId = SessionManager.userId ?: return

        SupabaseApi.select(
            "notifications", "*",
            "user_id=eq.$userId&order=created_at.desc&limit=30",
            object : SupabaseApi.Callback {
                override fun onSuccess(body: String) {
                    val arr = JSONArray(body)
                    if (arr.length() == 0) {
                        recycler.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                        return
                    }
                    val list = mutableListOf<JSONObject>()
                    val unreadIds = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val n = arr.getJSONObject(i)
                        list.add(n)
                        if (n.isNull("read_at")) unreadIds.add(n.optString("id"))
                    }
                    recycler.adapter = NotificationsAdapter(list)

                    // Mark all fetched-unread as read
                    unreadIds.forEach { id ->
                        val payload = JSONObject().put("read_at", isoNow()).toString()
                        SupabaseApi.update("notifications", "id=eq.$id", payload, object : SupabaseApi.Callback {
                            override fun onSuccess(body: String) {}
                            override fun onError(message: String) {}
                        })
                    }
                }
                override fun onError(message: String) {
                    recycler.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                }
            }
        )
    }

    private fun isoNow(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }
}

private class NotificationsAdapter(private val items: List<JSONObject>) :
    RecyclerView.Adapter<NotificationsAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.notif_title)
        val body: TextView = view.findViewById(R.id.notif_body)
        val time: TextView = view.findViewById(R.id.notif_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification_row, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val n = items[position]
        holder.title.text = n.optString("title")
        holder.body.text = n.optString("body", "")
        holder.time.text = n.optString("created_at", "").take(10)
    }

    override fun getItemCount(): Int = items.size
}
