package site.baltygram.app.ui.home

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import site.baltygram.app.R
import site.baltygram.app.data.SessionManager
import site.baltygram.app.network.SupabaseApi
import site.baltygram.app.util.ImageLoader

class SoftwareDetailActivity : AppCompatActivity() {

    private var softwareId: String? = null
    private var latestVersionId: String? = null
    private var isFavorited = false
    private var selectedRating = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_software_detail)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val slug = intent.getStringExtra("slug") ?: return
        loadSoftware(slug)
        buildStarInput()
    }

    private fun loadSoftware(slug: String) {
        SupabaseApi.select(
            "software", "*,developers(*,profiles(username,avatar_url))",
            "slug=eq.$slug&status=eq.approved&deleted_at=is.null",
            object : SupabaseApi.Callback {
                override fun onSuccess(body: String) {
                    val arr = JSONArray(body)
                    if (arr.length() == 0) {
                        Toast.makeText(this@SoftwareDetailActivity, "Software not found", Toast.LENGTH_SHORT).show()
                        finish()
                        return
                    }
                    val sw = arr.getJSONObject(0)
                    softwareId = sw.optString("id")
                    bindSoftware(sw)
                    loadLatestVersion(softwareId!!)
                    loadReviews(softwareId!!)
                    checkFavorited(softwareId!!)
                }
                override fun onError(message: String) {
                    Toast.makeText(this@SoftwareDetailActivity, message, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        )
    }

    private fun bindSoftware(sw: JSONObject) {
        findViewById<TextView>(R.id.detail_name).text = sw.optString("name")
        findViewById<TextView>(R.id.detail_description).text =
            sw.optString("description").ifBlank { sw.optString("short_description") }
        findViewById<TextView>(R.id.detail_stats).text = "${sw.optLong("download_count", 0)} downloads"

        val dev = sw.optJSONObject("developers")
        findViewById<TextView>(R.id.detail_developer).text = dev?.optString("developer_name") ?: "Independent"

        val iconUrl = sw.optString("icon_url", null)
        if (!iconUrl.isNullOrEmpty() && iconUrl != "null") {
            ImageLoader.load(findViewById(R.id.detail_icon), iconUrl)
        }

        findViewById<View>(R.id.btn_download).setOnClickListener { requestDownload() }
        findViewById<ImageButton>(R.id.btn_favorite).setOnClickListener { toggleFavorite() }
    }

    private fun loadLatestVersion(swId: String) {
        SupabaseApi.select(
            "software_versions", "*",
            "software_id=eq.$swId&order=created_at.desc&limit=1",
            object : SupabaseApi.Callback {
                override fun onSuccess(body: String) {
                    val arr = JSONArray(body)
                    if (arr.length() > 0) latestVersionId = arr.getJSONObject(0).optString("id")
                }
                override fun onError(message: String) {}
            }
        )
    }

    private fun checkFavorited(swId: String) {
        if (!SessionManager.isLoggedIn()) return
        SupabaseApi.select(
            "favorites", "*",
            "user_id=eq.${SessionManager.userId}&software_id=eq.$swId",
            object : SupabaseApi.Callback {
                override fun onSuccess(body: String) {
                    isFavorited = JSONArray(body).length() > 0
                    updateFavoriteIcon()
                }
                override fun onError(message: String) {}
            }
        )
    }

    private fun updateFavoriteIcon() {
        findViewById<ImageButton>(R.id.btn_favorite).setImageResource(
            if (isFavorited) R.drawable.ic_heart_filled else R.drawable.ic_heart
        )
    }

    private fun toggleFavorite() {
        if (!SessionManager.isLoggedIn()) {
            Toast.makeText(this, "Sign in to favorite software.", Toast.LENGTH_SHORT).show()
            return
        }
        val swId = softwareId ?: return
        if (isFavorited) {
            SupabaseApi.delete("favorites", "user_id=eq.${SessionManager.userId}&software_id=eq.$swId",
                object : SupabaseApi.Callback {
                    override fun onSuccess(body: String) { isFavorited = false; updateFavoriteIcon() }
                    override fun onError(message: String) {}
                })
        } else {
            val json = JSONObject().put("user_id", SessionManager.userId).put("software_id", swId).toString()
            SupabaseApi.insert("favorites", "[$json]", object : SupabaseApi.Callback {
                override fun onSuccess(body: String) { isFavorited = true; updateFavoriteIcon() }
                override fun onError(message: String) {}
            })
        }
    }

    /** Real download: gets a short-lived signed URL from the sign-download edge
     *  function (which server-side verifies the software is actually approved),
     *  then hands the URL to Android's own DownloadManager. No fake progress bars. */
    private fun requestDownload() {
        if (!SessionManager.isLoggedIn()) {
            Toast.makeText(this, "Sign in to download software.", Toast.LENGTH_SHORT).show()
            return
        }
        val versionId = latestVersionId
        if (versionId == null) {
            Toast.makeText(this, "No downloadable version yet.", Toast.LENGTH_SHORT).show()
            return
        }

        val body = JSONObject().put("version_id", versionId).toString()
        SupabaseApi.invokeFunction("sign-download", body, object : SupabaseApi.Callback {
            override fun onSuccess(responseBody: String) {
                val json = JSONObject(responseBody)
                if (!json.optBoolean("success", false)) {
                    Toast.makeText(this@SoftwareDetailActivity, json.optString("error", "Download failed"), Toast.LENGTH_SHORT).show()
                    return
                }
                val url = json.optString("url")
                val name = json.optString("software_name", "BaltyGram file")
                enqueueSystemDownload(url, name)
            }
            override fun onError(message: String) {
                Toast.makeText(this@SoftwareDetailActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun enqueueSystemDownload(url: String, displayName: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(displayName)
            .setDescription("Downloading via BaltyGram")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, displayName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(this, "Downloading $displayName...", Toast.LENGTH_SHORT).show()
    }

    // ---- Reviews ----

    private fun buildStarInput() {
        val row = findViewById<LinearLayout>(R.id.star_input_row)
        for (i in 1..5) {
            val star = ImageView(this)
            star.setImageResource(R.drawable.ic_star_outline)
            val size = (28 * resources.displayMetrics.density).toInt()
            val params = LinearLayout.LayoutParams(size, size)
            params.marginEnd = 6
            star.layoutParams = params
            star.setOnClickListener {
                selectedRating = i
                for (j in 0 until row.childCount) {
                    (row.getChildAt(j) as ImageView).setImageResource(
                        if (j < i) R.drawable.ic_star else R.drawable.ic_star_outline
                    )
                }
            }
            row.addView(star)
        }

        findViewById<View>(R.id.btn_submit_review).setOnClickListener { submitReview() }
    }

    private fun submitReview() {
        if (!SessionManager.isLoggedIn()) {
            Toast.makeText(this, "Sign in to leave a review.", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedRating == 0) {
            Toast.makeText(this, "Please select a star rating.", Toast.LENGTH_SHORT).show()
            return
        }
        val swId = softwareId ?: return
        val bodyText = findViewById<EditText>(R.id.review_body_input).text.toString()

        val json = JSONObject()
            .put("software_id", swId)
            .put("user_id", SessionManager.userId)
            .put("rating", selectedRating)
            .put("body", bodyText)

        SupabaseApi.insert("reviews", "[${json}]", object : SupabaseApi.Callback {
            override fun onSuccess(body: String) {
                Toast.makeText(this@SoftwareDetailActivity, "Review submitted!", Toast.LENGTH_SHORT).show()
                loadReviews(swId)
            }
            override fun onError(message: String) {
                Toast.makeText(this@SoftwareDetailActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadReviews(swId: String) {
        SupabaseApi.select(
            "reviews", "*,profiles(username)",
            "software_id=eq.$swId&order=created_at.desc&limit=20",
            object : SupabaseApi.Callback {
                override fun onSuccess(body: String) {
                    val container = findViewById<LinearLayout>(R.id.reviews_container)
                    container.removeAllViews()
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        val tv = TextView(this@SoftwareDetailActivity)
                        val stars = "*".repeat(r.optInt("rating", 0))
                        val username = r.optJSONObject("profiles")?.optString("username") ?: "User"
                        tv.text = "$stars  $username\n${r.optString("body", "")}"
                        tv.textSize = 13f
                        tv.setPadding(0, 12, 0, 12)
                        container.addView(tv)
                    }
                }
                override fun onError(message: String) {}
            }
        )
    }
}
