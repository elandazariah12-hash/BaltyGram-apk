package site.baltygram.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Holds the current Supabase Auth session (access/refresh tokens + user id)
 * in SharedPreferences. Not encrypted-at-rest by default; for production
 * hardening, wrap this in EncryptedSharedPreferences (androidx.security).
 */
object SessionManager {
    private const val PREFS = "baltygram_session"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    var userId: String?
        get() = prefs.getString("user_id", null)
        set(value) = prefs.edit().putString("user_id", value).apply()

    var userEmail: String?
        get() = prefs.getString("user_email", null)
        set(value) = prefs.edit().putString("user_email", value).apply()

    fun isLoggedIn(): Boolean = !accessToken.isNullOrEmpty() && !userId.isNullOrEmpty()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
