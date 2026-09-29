package site.baltygram.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Tiny fire-and-forget image loader: no Glide/Picasso dependency, just an
 * HttpURLConnection + BitmapFactory + a simple in-memory cache. Good enough
 * for card icons and avatars; not meant to replace a real image pipeline at scale.
 */
object ImageLoader {
    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<ImageView, String>()

    fun load(imageView: ImageView, url: String?) {
        if (url.isNullOrBlank()) return
        pending[imageView] = url

        cache[url]?.let {
            imageView.setImageBitmap(it)
            return
        }

        val ref = WeakReference(imageView)
        executor.execute {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.doInput = true
                conn.connect()
                val bitmap = BitmapFactory.decodeStream(conn.inputStream)
                conn.disconnect()
                if (bitmap != null) {
                    cache[url] = bitmap
                    mainHandler.post {
                        val iv = ref.get()
                        // Only apply if this ImageView hasn't been recycled for another URL
                        if (iv != null && pending[iv] == url) {
                            iv.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently keep the placeholder on failure.
            }
        }
    }
}
