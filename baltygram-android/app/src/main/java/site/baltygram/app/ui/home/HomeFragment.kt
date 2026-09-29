package site.baltygram.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONArray
import org.json.JSONObject
import site.baltygram.app.R
import site.baltygram.app.adapters.SoftwareAdapter
import site.baltygram.app.network.SupabaseApi

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val HOME_MIN_APPROVED = 6

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        swipeRefresh.setOnRefreshListener {
            loadData(view)
            swipeRefresh.isRefreshing = false
        }

        loadData(view)
    }

    private fun loadData(view: View) {
        val emptyState = view.findViewById<View>(R.id.empty_state)
        val contentState = view.findViewById<View>(R.id.content_state)
        val trendingRecycler = view.findViewById<RecyclerView>(R.id.recycler_trending)
        val newRecycler = view.findViewById<RecyclerView>(R.id.recycler_new)

        SupabaseApi.select(
            "software", "*",
            "status=eq.approved&deleted_at=is.null&order=trend_score.desc&limit=10",
            object : SupabaseApi.Callback {
                override fun onSuccess(body: String) {
                    if (!isAdded) return
                    val trending = JSONArray(body)

                    if (trending.length() < HOME_MIN_APPROVED) {
                        emptyState.visibility = View.VISIBLE
                        contentState.visibility = View.GONE
                        return
                    }

                    emptyState.visibility = View.GONE
                    contentState.visibility = View.VISIBLE

                    trendingRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                    trendingRecycler.adapter = SoftwareAdapter(toList(trending)) { openDetail(it) }

                    SupabaseApi.select(
                        "software", "*",
                        "status=eq.approved&deleted_at=is.null&order=created_at.desc&limit=10",
                        object : SupabaseApi.Callback {
                            override fun onSuccess(body2: String) {
                                if (!isAdded) return
                                newRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                                newRecycler.adapter = SoftwareAdapter(toList(JSONArray(body2))) { openDetail(it) }
                            }
                            override fun onError(message: String) { /* keep silent, non-critical section */ }
                        }
                    )
                }

                override fun onError(message: String) {
                    if (!isAdded) return
                    emptyState.visibility = View.VISIBLE
                    contentState.visibility = View.GONE
                }
            }
        )
    }

    private fun toList(arr: JSONArray): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
        return list
    }

    private fun openDetail(sw: JSONObject) {
        val intent = Intent(requireContext(), SoftwareDetailActivity::class.java)
        intent.putExtra("slug", sw.optString("slug"))
        startActivity(intent)
    }
}
