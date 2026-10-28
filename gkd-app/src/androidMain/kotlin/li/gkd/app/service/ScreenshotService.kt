package li.gkd.app.service

import android.content.Intent
import coil3.Bitmap
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import li.gkd.app.app
import li.gkd.app.logging.LogUtils
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.platform.screenshot.MediaProjectionScreenshotSession
import li.gkd.app.resources.*
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.ThreadUtils
import li.gkd.app.util.componentName

class ScreenshotService : LifecycleHookService() {
    @Volatile private var captureSession: MediaProjectionScreenshotSession? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            return super.onStartCommand(intent, flags, startId)
        } finally {
            intent?.let {
                val oldSession = captureSession
                captureSession = null
                oldSession?.close()
                captureSession = createCaptureSession(intent)
                LogUtils.i("screenshot restart")
            }
        }
    }

    private fun createCaptureSession(intent: Intent): MediaProjectionScreenshotSession {
        return MediaProjectionScreenshotSession(intent) { invalidatedSession ->
            ThreadUtils.runMainOrPost {
                if (captureSession === invalidatedSession) {
                    stopSelf()
                }
            }
        }
    }

    init {
        useLogLifecycle()
        useServicePresence(
            stateFlow = isRunning,
            name = Res.string.screenshot_service.getSync(),
        )
        useStopServiceReceiver()
        onCreated {
            NotificationCatalog.screenshot().startForeground()
            instance = this@ScreenshotService
        }
        onDestroyed {
            instance = null
            val oldSession = captureSession
            captureSession = null
            oldSession?.close()
        }
    }

    companion object {
        private var instance: ScreenshotService? = null
        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)

        suspend fun screenshot(): Bitmap? {
            if (!isRunning.value) return null
            return try {
                withTimeoutOrNull(5000.milliseconds) {
                    instance?.captureSession?.capture()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.e("截取屏幕失败", e)
                null
            }
        }

        fun start(intent: Intent) {
            intent.component = ScreenshotService::class.componentName
            app.startForegroundService(intent)
        }

        fun stop() = IntentUtils.stopService(ScreenshotService::class)
    }
}
