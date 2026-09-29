package site.baltygram.app

import android.app.Application
import site.baltygram.app.data.SessionManager

class BaltyGramApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionManager.init(this)
    }
}
