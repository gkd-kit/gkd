package li.songe.gkd.shizuku

import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.android.internal.app.IAppOpsService
import li.songe.gkd.META
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.checkExistClass

class SafeAppOpsService(
    private val value: IAppOpsService
) {
    companion object {
        val isAvailable: Boolean
            get() = checkExistClass("com.android.internal.app.IAppOpsService")

        fun newBinder() = getStubService(
            Context.APP_OPS_SERVICE,
            isAvailable,
        )?.let {
            SafeAppOpsService(IAppOpsService.Stub.asInterface(it))
        }

        private val a11yOverlayOk by lazy {
            AndroidTarget.UPSIDE_DOWN_CAKE && try {
                AppOpsManager::class.java.getField("OP_CREATE_ACCESSIBILITY_OVERLAY")
            } catch (_: NoSuchFieldException) {
                null
            } != null
        }

        @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        val supportCreateA11yOverlay get() = a11yOverlayOk
    }

    fun setMode(
        code: Int,
        uid: Int = currentUserId,
        packageName: String,
        mode: Int
    ) = safeInvokeMethod {
        value.setMode(code, uid, packageName, mode)
    }

    private fun setAllowSelfMode(code: Int) = setMode(
        code = code,
        packageName = META.appId,
        mode = AppOpsManager.MODE_ALLOWED,
    )

    fun allowAllSelfMode() {
        setAllowSelfMode(AppOpsManagerHidden.OP_POST_NOTIFICATION)
        setAllowSelfMode(AppOpsManagerHidden.OP_SYSTEM_ALERT_WINDOW)
        if (AndroidTarget.Q) {
            setAllowSelfMode(AppOpsManagerHidden.OP_ACCESS_ACCESSIBILITY)
        }
        if (AndroidTarget.TIRAMISU) {
            setAllowSelfMode(AppOpsManagerHidden.OP_ACCESS_RESTRICTED_SETTINGS)
        }
        if (AndroidTarget.UPSIDE_DOWN_CAKE) {
            setAllowSelfMode(AppOpsManagerHidden.OP_FOREGROUND_SERVICE_SPECIAL_USE)
        }
        if (supportCreateA11yOverlay) {
            setAllowSelfMode(AppOpsManagerHidden.OP_CREATE_ACCESSIBILITY_OVERLAY)
        }
    }
}