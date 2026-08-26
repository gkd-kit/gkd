package li.gkd.app.service

import android.app.Service
import android.content.Intent
import coil3.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import li.gkd.app.app
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.notif.StopServiceReceiver
import li.gkd.app.util.DefaultSimpleLifeImpl
import li.gkd.app.util.LogUtils
import li.gkd.app.util.OnSimpleLife
import li.gkd.app.util.componentName
import li.gkd.app.util.runMainPost
import li.gkd.app.util.stopServiceByClass
import kotlin.time.Duration.Companion.milliseconds

class ScreenshotService : Service(), OnSimpleLife by DefaultSimpleLifeImpl() {
    override fun onBind(intent: Intent?) = null
    override fun onCreate() = onCreated()
    override fun onDestroy() = onDestroyed()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            return super.onStartCommand(intent, flags, startId)
        } finally {
            intent?.let {
                captureSession?.close()
                captureSession = createCaptureSession(intent)
                LogUtils.d("screenshot restart")
            }
        }
    }

    private var captureSession: MediaProjectionScreenshotSession? = null

    private fun createCaptureSession(intent: Intent): MediaProjectionScreenshotSession {
        lateinit var created: MediaProjectionScreenshotSession
        created = MediaProjectionScreenshotSession(intent) {
            runMainPost {
                if (captureSession === created) {
                    stopSelf()
                }
            }
        }
        return created
    }

    init {
        useLogLifecycle()
        useAliveFlow(isRunning)
        useAliveToast("截屏服务")
        StopServiceReceiver.autoRegister()
        onCreated {
            NotificationCatalog.screenshot().startForeground()
        }
        onCreated { instance = this }
        onDestroyed {
            captureSession?.close()
            instance = null
        }
    }

    companion object {
        private var instance: ScreenshotService? = null
        val isRunning = MutableStateFlow(false)
        suspend fun screenshot(): Bitmap? {
            if (!isRunning.value) return null
            return try {
                withTimeoutOrNull(5000.milliseconds) {
                    instance?.captureSession?.capture()
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

        fun stop() = stopServiceByClass(ScreenshotService::class)
    }
}
