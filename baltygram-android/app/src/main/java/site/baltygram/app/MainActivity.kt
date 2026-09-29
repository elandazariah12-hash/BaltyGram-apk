package site.baltygram.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONArray
import site.baltygram.app.data.SessionManager
import site.baltygram.app.network.SupabaseApi
import site.baltygram.app.ui.NotificationsActivity
import site.baltygram.app.ui.account.AccountFragment
import site.baltygram.app.ui.discover.DiscoverFragment
import site.baltygram.app.ui.downloads.DownloadsFragment
import site.baltygram.app.ui.home.HomeFragment
import site.baltygram.app.ui.uploads.UploadsFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Android 13+ requires runtime permission to show notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        if (savedInstanceState == null) {
            switchFragment(HomeFragment())
        }

        findViewById<BottomNavigationView>(R.id.bottom_nav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { switchFragment(HomeFragment()); true }
                R.id.nav_discover -> { switchFragment(DiscoverFragment()); true }
                R.id.nav_uploads -> { switchFragment(UploadsFragment()); true }
                R.id.nav_downloads -> { switchFragment(DownloadsFragment()); true }
                R.id.nav_account -> { switchFragment(AccountFragment()); true }
                else -> false
            }
        }

        findViewById<View>(R.id.bell_container).setOnClickListener {
            if (!SessionManager.isLoggedIn()) return@setOnClickListener
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationBadge()
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun refreshNotificationBadge() {
        val dot = findViewById<View>(R.id.bell_dot)
        val userId = SessionManager.userId
        if (userId == null) {
            dot.visibility = View.GONE
            return
        }
        SupabaseApi.select(
            "notifications", "id",
            "user_id=eq.$userId&read_at=is.null",
            object : SupabaseApi.Callback {
                override fun onSuccess(body: String) {
                    dot.visibility = if (JSONArray(body).length() > 0) View.VISIBLE else View.GONE
                }
                override fun onError(message: String) { dot.visibility = View.GONE }
            }
        )
    }
}
