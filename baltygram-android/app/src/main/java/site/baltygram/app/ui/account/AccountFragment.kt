package site.baltygram.app.ui.account

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import site.baltygram.app.R
import site.baltygram.app.data.SessionManager
import site.baltygram.app.network.SupabaseApi

class AccountFragment : Fragment(R.layout.fragment_account) {

    private var originalUsername: String? = null
    private var usernameCheckRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadAvatar(uri)
    }

    override fun onResume() {
        super.onResume()
        view?.let { renderState(it) }
    }

    private fun renderState(view: View) {
        val signupBlock = view.findViewById<View>(R.id.signup_block)
        val loginBlock = view.findViewById<View>(R.id.login_block)
        val profileBlock = view.findViewById<View>(R.id.profile_block)

        if (SessionManager.isLoggedIn()) {
            signupBlock.visibility = View.GONE
            loginBlock.visibility = View.GONE
            profileBlock.visibility = View.VISIBLE
            loadProfile(view)
            wireProfileActions(view)
        } else {
            signupBlock.visibility = View.VISIBLE
            loginBlock.visibility = View.GONE
            profileBlock.visibility = View.GONE
            wireAuthActions(view)
        }
    }

    private fun wireAuthActions(view: View) {
        view.findViewById<View>(R.id.show_login).setOnClickListener {
            view.findViewById<View>(R.id.signup_block).visibility = View.GONE
            view.findViewById<View>(R.id.login_block).visibility = View.VISIBLE
        }
        view.findViewById<View>(R.id.show_signup).setOnClickListener {
            view.findViewById<View>(R.id.login_block).visibility = View.GONE
            view.findViewById<View>(R.id.signup_block).visibility = View.VISIBLE
        }

        view.findViewById<View>(R.id.btn_signup).setOnClickListener {
            val email = view.findViewById<EditText>(R.id.signup_email).text.toString().trim()
            val password = view.findViewById<EditText>(R.id.signup_password).text.toString()
            if (email.isEmpty() || password.length < 8) {
                Toast.makeText(context, "Enter a valid email and an 8+ character password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            SupabaseApi.signUp(email, password, object : SupabaseApi.Callback {
                override fun onSuccess(body: String) {
                    Toast.makeText(context, "Account created! Check your email to verify.", Toast.LENGTH_LONG).show()
                }
                override fun onError(message: String) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            })
        }

        view.findViewById<View>(R.id.btn_login).setOnClickListener {
            val email = view.findViewById<EditText>(R.id.login_email).text.toString().trim()
            val password = view.findViewById<EditText>(R.id.login_password).text.toString()
            SupabaseApi.signIn(email, password, object : SupabaseApi.Callback {
                override fun onSuccess(body: String) {
                    val json = JSONObject(body)
                    SessionManager.accessToken = json.optString("access_token")
                    SessionManager.refreshToken = json.optString("refresh_token")
                    val user = json.optJSONObject("user")
                    SessionManager.userId = user?.optString("id")
                    SessionManager.userEmail = user?.optString("email")
                    Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                    if (isAdded) renderState(requireView())
                }
                override fun onError(message: String) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun loadProfile(view: View) {
        view.findViewById<TextView>(R.id.profile_email).text = SessionManager.userEmail ?: ""

        SupabaseApi.getUserProfile(SessionManager.userId ?: return, object : SupabaseApi.Callback {
            override fun onSuccess(body: String) {
                if (!isAdded) return
                val arr = JSONArray(body)
                val profile = if (arr.length() > 0) arr.getJSONObject(0) else JSONObject()

                originalUsername = profile.optString("username", null)
                view.findViewById<EditText>(R.id.input_username).setText(profile.optString("username", ""))
                view.findViewById<EditText>(R.id.input_full_name).setText(profile.optString("full_name", ""))
                view.findViewById<EditText>(R.id.input_display_name).setText(profile.optString("display_name", ""))
                view.findViewById<EditText>(R.id.input_bio).setText(profile.optString("bio", ""))

                val social = profile.optJSONObject("social_links") ?: JSONObject()
                view.findViewById<EditText>(R.id.input_instagram).setText(social.optString("instagram", ""))
                view.findViewById<EditText>(R.id.input_whatsapp).setText(social.optString("whatsapp", ""))
                view.findViewById<EditText>(R.id.input_telegram).setText(social.optString("telegram", ""))
                view.findViewById<EditText>(R.id.input_website).setText(social.optString("website", ""))

                view.findViewById<View>(R.id.badge_founder).visibility =
                    if (profile.optBoolean("is_founder", false)) View.VISIBLE else View.GONE
                view.findViewById<View>(R.id.badge_admin).visibility =
                    if (profile.optString("role") == "admin") View.VISIBLE else View.GONE

                val avatarUrl = profile.optString("avatar_url", null)
                if (!avatarUrl.isNullOrEmpty() && avatarUrl != "null") {
                    site.baltygram.app.util.ImageLoader.load(view.findViewById(R.id.avatar_preview), avatarUrl)
                }
            }
            override fun onError(message: String) {}
        })
    }

    private fun wireProfileActions(view: View) {
        view.findViewById<View>(R.id.btn_change_avatar).setOnClickListener { avatarPicker.launch("image/*") }

        val usernameInput = view.findViewById<EditText>(R.id.input_username)
        usernameInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) checkUsername(view) }
        usernameInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                usernameCheckRunnable?.let { handler.removeCallbacks(it) }
                val runnable = Runnable { checkUsername(view) }
                usernameCheckRunnable = runnable
                handler.postDelayed(runnable, 500)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        view.findViewById<View>(R.id.btn_save_profile).setOnClickListener { saveProfile(view) }
        view.findViewById<View>(R.id.btn_logout).setOnClickListener {
            SessionManager.clear()
            renderState(view)
        }
    }

    private fun checkUsername(view: View) {
        val value = view.findViewById<EditText>(R.id.input_username).text.toString().trim()
        val iconView = view.findViewById<ImageView>(R.id.username_check_icon)
        val msgView = view.findViewById<TextView>(R.id.username_msg)

        if (value.isEmpty()) { iconView.visibility = View.GONE; msgView.text = ""; return }

        if (value == originalUsername) {
            iconView.visibility = View.VISIBLE
            iconView.setImageResource(R.drawable.ic_check)
            msgView.text = "This is your current username."
            msgView.setTextColor(resources.getColor(R.color.slate_400))
            return
        }
        if (!Regex("^[A-Za-z0-9_]{3,30}$").matches(value)) {
            iconView.visibility = View.VISIBLE
            iconView.setImageResource(R.drawable.ic_close)
            msgView.text = "3-30 characters: letters, numbers, underscore only."
            msgView.setTextColor(resources.getColor(R.color.red_600))
            return
        }

        SupabaseApi.select("profiles", "id", "username=eq.$value", object : SupabaseApi.Callback {
            override fun onSuccess(body: String) {
                if (!isAdded) return
                val taken = JSONArray(body).length() > 0
                iconView.visibility = View.VISIBLE
                iconView.setImageResource(if (taken) R.drawable.ic_close else R.drawable.ic_check)
                msgView.text = if (taken) "Username is already taken." else "Username is available."
                msgView.setTextColor(resources.getColor(if (taken) R.color.red_600 else R.color.brand_600))
            }
            override fun onError(message: String) {}
        })
    }

    private fun saveProfile(view: View) {
        val username = view.findViewById<EditText>(R.id.input_username).text.toString().trim()
        if (!Regex("^[A-Za-z0-9_]{3,30}$").matches(username)) {
            Toast.makeText(context, "Username must be 3-30 characters: letters, numbers, underscore only.", Toast.LENGTH_SHORT).show()
            return
        }

        val social = JSONObject()
        social.put("instagram", view.findViewById<EditText>(R.id.input_instagram).text.toString())
        social.put("whatsapp", view.findViewById<EditText>(R.id.input_whatsapp).text.toString())
        social.put("telegram", view.findViewById<EditText>(R.id.input_telegram).text.toString())
        social.put("website", view.findViewById<EditText>(R.id.input_website).text.toString())

        val payload = JSONObject()
            .put("id", SessionManager.userId)
            .put("username", username)
            .put("full_name", view.findViewById<EditText>(R.id.input_full_name).text.toString())
            .put("display_name", view.findViewById<EditText>(R.id.input_display_name).text.toString())
            .put("bio", view.findViewById<EditText>(R.id.input_bio).text.toString())
            .put("social_links", social)

        SupabaseApi.upsert("profiles", "[$payload]", "id", object : SupabaseApi.Callback {
            override fun onSuccess(body: String) {
                originalUsername = username
                Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
            }
            override fun onError(message: String) {
                val msg = if (message.contains("duplicate", true)) "That username was just taken - try another." else message
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun uploadAvatar(uri: Uri) {
        val userId = SessionManager.userId ?: return
        val bytes = requireContext().contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val path = "$userId/avatar.jpg"

        SupabaseApi.uploadToStorage("avatars", path, bytes, "image/jpeg", object : SupabaseApi.Callback {
            override fun onSuccess(body: String) {
                val url = SupabaseApi.publicUrl("avatars", path) + "?t=" + System.currentTimeMillis()
                val payload = JSONObject().put("avatar_url", url).toString()
                SupabaseApi.update("profiles", "id=eq.$userId", payload, object : SupabaseApi.Callback {
                    override fun onSuccess(body2: String) {
                        if (!isAdded) return
                        site.baltygram.app.util.ImageLoader.load(requireView().findViewById(R.id.avatar_preview), url)
                        Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
                    }
                    override fun onError(message: String) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                })
            }
            override fun onError(message: String) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        })
    }
}
