package li.gkd.app.service

import android.content.Intent
import coil3.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import li.gkd.app.app
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.platform.lifecycle.ResourceSlot
import li.gkd.app.platform.screenshot.MediaProjectionScreenshotSession
import li.gkd.app.resources.Res
import li.gkd.app.resources.screenshot_service
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ThreadUtils
import li.gkd.app.util.componentName
import kotlin.time.Duration.Companion.milliseconds

class ScreenshotService : LifecycleHookService() {
    private val captureSessionSlot = ResourceSlot<MediaProjectionScreenshotSession>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            return super.onStartCommand(intent, flags, startId)
        } finally {
            intent?.let {
                captureSessionSlot.replace { createCaptureSession(intent) }
                LogUtils.d("screenshot restart")
            }
        }
    }

    private fun createCaptureSession(intent: Intent): MediaProjectionScreenshotSession {
        return MediaProjectionScreenshotSession(intent) { invalidatedSession ->
            ThreadUtils.runMainOrPost {
                if (captureSessionSlot.get() === invalidatedSession) {
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
            captureSessionSlot.close()
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
                    instance?.captureSessionSlot?.get()?.capture()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.d("截取屏幕失败", e)
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
