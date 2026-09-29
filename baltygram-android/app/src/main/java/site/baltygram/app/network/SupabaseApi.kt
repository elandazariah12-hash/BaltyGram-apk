package site.baltygram.app.network

import android.os.Handler
import android.os.Looper
import site.baltygram.app.data.SessionManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal, dependency-free client for Supabase's REST surface:
 *  - PostgREST (table reads/writes)   -> /rest/v1/<table>
 *  - GoTrue (auth)                    -> /auth/v1/...
 *  - Storage                          -> /storage/v1/object/<bucket>/<path>
 *  - Edge Functions                   -> /functions/v1/<name>
 *
 * Deliberately uses plain HttpURLConnection instead of Retrofit/OkHttp so the
 * project has zero risk of annotation-processor or dependency-resolution
 * headaches when built inside AIDE.
 */
object SupabaseApi {

    const val SUPABASE_URL = "https://atovuyugeqrbfhkpyhax.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_xH48K-h8jQ53WHqDmfX_KA_taBB6f81"

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    interface Callback {
        fun onSuccess(body: String)
        fun onError(message: String)
    }

    private fun runAsync(block: () -> Unit) {
        executor.execute {
            try {
                block()
            } catch (e: Exception) {
                // Safety net - individual request methods should already catch
                // and report their own errors via callback.
            }
        }
    }

    private fun postToMain(callback: Callback, success: Boolean, payload: String) {
        mainHandler.post {
            if (success) callback.onSuccess(payload) else callback.onError(payload)
        }
    }

    private fun bearerToken(): String = SessionManager.accessToken ?: PUBLISHABLE_KEY

    private fun readStream(conn: HttpURLConnection, isError: Boolean): String {
        val stream = if (isError) conn.errorStream else conn.inputStream
        if (stream == null) return ""
        val reader = BufferedReader(InputStreamReader(stream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) sb.append(line)
        reader.close()
        return sb.toString()
    }

    private fun extractErrorMessage(body: String, fallback: String): String {
        return try {
            val json = JSONObject(body)
            json.optString("message", json.optString("error_description", json.optString("error", fallback)))
        } catch (e: Exception) {
            if (body.isNotBlank()) body else fallback
        }
    }

    // ---------- Generic raw request ----------

    fun request(
        method: String,
        url: String,
        body: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        useAuth: Boolean = true,
        callback: Callback
    ) {
        runAsync {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.setRequestProperty("apikey", PUBLISHABLE_KEY)
                if (useAuth) conn.setRequestProperty("Authorization", "Bearer ${bearerToken()}")
                conn.setRequestProperty("Content-Type", "application/json")
                extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }

                if (body != null) {
                    conn.doOutput = true
                    val out: OutputStream = conn.outputStream
                    out.write(body.toByteArray(Charsets.UTF_8))
                    out.flush()
                    out.close()
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    postToMain(callback, true, readStream(conn, false))
                } else {
                    val errBody = readStream(conn, true)
                    postToMain(callback, false, extractErrorMessage(errBody, "Request failed ($code)"))
                }
            } catch (e: Exception) {
                postToMain(callback, false, e.message ?: "Network error")
            } finally {
                conn?.disconnect()
            }
        }
    }

    // ---------- PostgREST (table) helpers ----------

    /** e.g. select("software", "*", "status=eq.approved&order=trend_score.desc&limit=20") */
    fun select(table: String, columns: String, filterQuery: String?, callback: Callback) {
        val query = StringBuilder("select=$columns")
        if (!filterQuery.isNullOrBlank()) query.append("&").append(filterQuery)
        val url = "$SUPABASE_URL/rest/v1/$table?$query"
        request("GET", url, null, emptyMap(), true, callback)
    }

    fun insert(table: String, jsonBody: String, callback: Callback) {
        val url = "$SUPABASE_URL/rest/v1/$table"
        request("POST", url, jsonBody, mapOf("Prefer" to "return=representation"), true, callback)
    }

    fun upsert(table: String, jsonBody: String, onConflict: String, callback: Callback) {
        val url = "$SUPABASE_URL/rest/v1/$table?on_conflict=$onConflict"
        request("POST", url, jsonBody, mapOf("Prefer" to "resolution=merge-duplicates,return=representation"), true, callback)
    }

    fun update(table: String, filterQuery: String, jsonBody: String, callback: Callback) {
        val url = "$SUPABASE_URL/rest/v1/$table?$filterQuery"
        request("PATCH", url, jsonBody, mapOf("Prefer" to "return=representation"), true, callback)
    }

    fun delete(table: String, filterQuery: String, callback: Callback) {
        val url = "$SUPABASE_URL/rest/v1/$table?$filterQuery"
        request("DELETE", url, null, emptyMap(), true, callback)
    }

    // ---------- Auth (GoTrue) ----------

    fun signUp(email: String, password: String, callback: Callback) {
        val url = "$SUPABASE_URL/auth/v1/signup"
        val body = JSONObject().put("email", email).put("password", password).toString()
        request("POST", url, body, emptyMap(), false, callback)
    }

    fun signIn(email: String, password: String, callback: Callback) {
        val url = "$SUPABASE_URL/auth/v1/token?grant_type=password"
        val body = JSONObject().put("email", email).put("password", password).toString()
        request("POST", url, body, emptyMap(), false, callback)
    }

    fun getUserProfile(userId: String, callback: Callback) {
        select("profiles", "*", "id=eq.$userId", callback)
    }

    // ---------- Storage ----------

    /** Uploads raw bytes to a bucket path. Path should include the user id folder for owner-scoped RLS. */
    fun uploadToStorage(bucket: String, path: String, bytes: ByteArray, contentType: String, callback: Callback) {
        runAsync {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$SUPABASE_URL/storage/v1/object/$bucket/$path")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 20000
                conn.readTimeout = 60000
                conn.setRequestProperty("apikey", PUBLISHABLE_KEY)
                conn.setRequestProperty("Authorization", "Bearer ${bearerToken()}")
                conn.setRequestProperty("Content-Type", contentType)
                conn.setRequestProperty("x-upsert", "true")
                conn.outputStream.use { it.write(bytes) }

                val code = conn.responseCode
                if (code in 200..299) {
                    postToMain(callback, true, "$bucket/$path")
                } else {
                    postToMain(callback, false, extractErrorMessage(readStream(conn, true), "Upload failed ($code)"))
                }
            } catch (e: Exception) {
                postToMain(callback, false, e.message ?: "Upload error")
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun publicUrl(bucket: String, path: String): String =
        "$SUPABASE_URL/storage/v1/object/public/$bucket/$path"

    // ---------- Edge Functions ----------

    fun invokeFunction(name: String, jsonBody: String, callback: Callback) {
        val url = "$SUPABASE_URL/functions/v1/$name"
        request("POST", url, jsonBody, emptyMap(), true, callback)
    }
}
