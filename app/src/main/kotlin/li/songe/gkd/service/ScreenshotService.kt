package li.songe.gkd.service

import android.app.Service
import android.content.Intent
import coil3.Bitmap
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import li.songe.gkd.app
import li.songe.gkd.notif.StopServiceReceiver
import li.songe.gkd.notif.screenshotNotif
import li.songe.gkd.util.OnSimpleLife
import li.songe.gkd.util.ScreenshotUtil
import li.songe.gkd.util.componentName
import li.songe.gkd.util.stopServiceByClass
import java.lang.ref.WeakReference

class ScreenshotService : Service(), OnSimpleLife {
    override val scope: CoroutineScope
        get() = throw NotImplementedError()

    override fun onBind(intent: Intent?) = null
    override fun onCreate() = onCreated()
    override fun onDestroy() = onDestroyed()

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

    private var screenshotUtil: ScreenshotUtil? = null

    init {
        useLogLifecycle()
        useAliveFlow(isRunning)
        useAliveToast("截屏服务")
        StopServiceReceiver.autoRegister()
        onCreated { screenshotNotif.notifyService() }
        onCreated { instance = WeakReference(this) }
        onDestroyed { instance = WeakReference(null) }
        onDestroyed { screenshotUtil?.destroy() }
    }

    companion object {
        private var instance = WeakReference<ScreenshotService>(null)
        val isRunning = MutableStateFlow(false)
        suspend fun screenshot(): Bitmap? {
            if (!isRunning.value) return null
            return withTimeoutOrNull(3_000) {
                instance.get()?.screenshotUtil?.execute()
            }
        }

        fun start(intent: Intent) {
            intent.component = ScreenshotService::class.componentName
            app.startForegroundService(intent)
        }

        fun stop() = stopServiceByClass(ScreenshotService::class)
    }
}