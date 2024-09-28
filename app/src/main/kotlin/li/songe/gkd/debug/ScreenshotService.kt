package li.songe.gkd.debug

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.app
import li.songe.gkd.notif.notifyService
import li.songe.gkd.notif.screenshotNotif
import li.songe.gkd.util.ScreenshotUtil

class ScreenshotService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        isRunning.value = true
        screenshotNotif.notifyService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            return super.onStartCommand(intent, flags, startId)
        } finally {
            intent?.let {
                screenshotUtil?.destroy()
                screenshotUtil = ScreenshotUtil(this, intent)
                LogUtils.d("screenshot restart")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.value = false
        screenshotUtil?.destroy()
        screenshotUtil = null
    }

    companion object {
        suspend fun screenshot() = screenshotUtil?.execute()

        @SuppressLint("StaticFieldLeak")
        private var screenshotUtil: ScreenshotUtil? = null

        fun start(context: Context = app, intent: Intent) {
            intent.component = ComponentName(context, ScreenshotService::class.java)
            context.startForegroundService(intent)
        }

        val isRunning = MutableStateFlow(false)
        fun stop(context: Context = app) {
            context.stopService(Intent(context, ScreenshotService::class.java))
        }
    }
}