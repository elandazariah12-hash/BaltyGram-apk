package site.baltygram.app.ui.discover

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import site.baltygram.app.R
import site.baltygram.app.adapters.SoftwareAdapter
import site.baltygram.app.network.SupabaseApi
import site.baltygram.app.ui.home.SoftwareDetailActivity

class DiscoverFragment : Fragment(R.layout.fragment_discover) {

    private val types = listOf(
        "" to "All", "game" to "Games", "software" to "Software", "app" to "Apps",
        "editing_tool" to "Editing", "ai_tool" to "AI Tools", "developer_tool" to "Dev Tools"
    )
    private var activeType = ""
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.recycler_results)
        emptyState = view.findViewById(R.id.empty_state)
        recycler.layoutManager = GridLayoutManager(context, 2)

        buildChips(view.findViewById(R.id.chip_row))

        val searchInput = view.findViewById<EditText>(R.id.search_input)
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(searchInput.text.toString())
                true
            } else false
        }

        search("")
    }

    private fun buildChips(container: LinearLayout) {
        container.removeAllViews()
        types.forEach { (value, label) ->
            val chip = TextView(context)
            chip.text = label
            chip.textSize = 12f
            chip.setPadding(28, 14, 28, 14)
            chip.setTextColor(resources.getColor(if (value == activeType) R.color.white else R.color.slate_600))
            chip.setBackgroundResource(if (value == activeType) R.drawable.bg_button_primary else R.drawable.bg_button_secondary)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.marginEnd = 8
            chip.layoutParams = params
            chip.setOnClickListener {
                activeType = value
                buildChips(container)
                search("")
            }
            container.addView(chip)
        }
    }

    private fun search(query: String) {
        val filters = StringBuilder("status=eq.approved&deleted_at=is.null&order=trend_score.desc&limit=30")
        if (query.isNotBlank()) filters.append("&name=ilike.*").append(query).append("*")
        if (activeType.isNotBlank()) filters.append("&software_type=eq.").append(activeType)

        SupabaseApi.select("software", "*", filters.toString(), object : SupabaseApi.Callback {
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
                recycler.adapter = SoftwareAdapter(list) { sw ->
                    val intent = Intent(requireContext(), SoftwareDetailActivity::class.java)
                    intent.putExtra("slug", sw.optString("slug"))
                    startActivity(intent)
                }
            }
            override fun onError(message: String) {
                if (!isAdded) return
                recycler.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            }
        })
    }
}
