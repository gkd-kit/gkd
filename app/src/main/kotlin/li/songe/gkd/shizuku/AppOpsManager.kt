package li.songe.gkd.shizuku

import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.Context
import com.android.internal.app.IAppOpsService
import li.songe.gkd.META
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.checkExistClass

class SafeAppOpsManager(
    private val value: IAppOpsService
) {
    companion object {
        val isAvailable: Boolean
            get() = checkExistClass("com.android.internal.app.IAppOpsService")

        fun newBinder() = getStubService(
            Context.APP_OPS_SERVICE,
            isAvailable,
        )?.let {
            SafeAppOpsManager(IAppOpsService.Stub.asInterface(it))
        }
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
    }
}