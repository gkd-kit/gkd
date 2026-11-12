package li.gkd.app.priv

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ServiceManager
import android.os.ServiceSpecificException
import android.view.accessibility.IAccessibilityManager
import androidx.annotation.Keep
import li.gkd.aidl.IInstallResultListener
import li.gkd.aidl.ISystemSurfaceListener
import li.gkd.aidl.IUserService

private const val SCREENSHOT_ERROR_CODE = 1
private const val SYSTEM_SURFACE_LISTENER_ERROR_CODE = 2
private const val INSTALL_RESULT_SENDER_ERROR_CODE = 3
private const val WINDOW_PACKAGE_ERROR_CODE = 4
private const val MAX_SCREENSHOT_ERROR_LENGTH = 16_384

@Keep
class UserService : IUserService.Stub() {
    private var systemSurfaceListener: PrivilegedSystemSurfaceListener? = null
    private val accessibilityManager by lazy {
        IAccessibilityManager.Stub.asInterface(
            requireNotNull(ServiceManager.getService(Context.ACCESSIBILITY_SERVICE)),
        )
    }

    @Synchronized
    override fun setSystemSurfaceListener(listener: ISystemSurfaceListener?) {
        try {
            systemSurfaceListener?.close()
            systemSurfaceListener = null
            if (listener != null && android.os.Build.VERSION.SDK_INT >= 34) {
                val next = PrivilegedSystemSurfaceListener(listener)
                systemSurfaceListener = next
                next.start()
            }
        } catch (e: Throwable) {
            throw ServiceSpecificException(
                SYSTEM_SURFACE_LISTENER_ERROR_CODE,
                e.stackTraceToString().take(MAX_SCREENSHOT_ERROR_LENGTH),
            )
        }
    }

    override fun takeScreenshot(crop: Rect, rotation: Int): Bitmap? {
        return try {
            CompatScreenshot.captureBySurfaceControl(crop, rotation)
        } catch (e: Throwable) {
            throw ServiceSpecificException(
                SCREENSHOT_ERROR_CODE,
                e.stackTraceToString().take(MAX_SCREENSHOT_ERROR_LENGTH),
            )
        }
    }

    override fun destroy() = setSystemSurfaceListener(null)

    override fun getAccessibilityWindowPackage(windowId: Int, userId: Int): String? {
        return try {
            val listener = synchronized(this) { systemSurfaceListener } ?: return null
            val token = accessibilityManager.getWindowToken(windowId, userId) ?: return null
            listener.windowPackage(token)
        } catch (e: Throwable) {
            if (e is VirtualMachineError || e is ThreadDeath) throw e
            throw ServiceSpecificException(
                WINDOW_PACKAGE_ERROR_CODE,
                e.stackTraceToString().take(MAX_SCREENSHOT_ERROR_LENGTH),
            )
        }
    }

    override fun createInstallResultSender(
        launchIntent: Intent,
        callingPackage: String,
        userId: Int,
        listener: IInstallResultListener,
    ): IntentSender =
        try {
            InstallRestartReceiver(launchIntent, callingPackage, userId, listener).intentSender()
        } catch (e: Throwable) {
            throw ServiceSpecificException(
                INSTALL_RESULT_SENDER_ERROR_CODE,
                e.stackTraceToString().take(MAX_SCREENSHOT_ERROR_LENGTH),
            )
        }
}
