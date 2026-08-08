package li.songe.gkd.permission

import android.Manifest
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.provider.Settings
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import li.songe.gkd.MainViewModel
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.priv.CompatAppOpsService
import li.songe.gkd.priv.privilegeContextFlow
import li.songe.gkd.ui.PrivilegeServiceRoute
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateAllAppInfo
import li.songe.gkd.util.updateAppMutex
import priv.kit.core.Privilege

class PermissionState(
    val name: String,
    private val check: () -> Boolean,
    val permission: IPermission? = null,
    val purpose: String? = null,
    val resolution: PermissionResolution? = null,
    private val onChanged: (() -> Unit)? = null,
) {
    val stateFlow = MutableStateFlow(false)
    val value get() = stateFlow.value

    fun updateAndGet(): Boolean {
        return stateFlow.updateAndGet { check() }
    }

    fun refresh(): Boolean {
        val oldValue = value
        val newValue = updateAndGet()
        if (oldValue != newValue) {
            onChanged?.invoke()
        }
        return newValue
    }

    fun checkOrToast(): Boolean {
        val granted = refresh()
        if (!granted) {
            toast("请先授予「$name」")
        }
        return granted
    }
}

data class PermissionResolution(
    val message: String,
    val confirmText: String = "去设置",
    val confirm: (() -> Unit)? = null,
)

private fun requestablePermissionState(
    name: String,
    purpose: String,
    permission: IPermission,
    check: () -> Boolean = { XXPermissions.isGrantedPermission(app, permission) },
    onChanged: (() -> Unit)? = null,
) = PermissionState(
    name = name,
    check = check,
    permission = permission,
    purpose = purpose,
    resolution = PermissionResolution(
        message = "未授予「$name」\n请前往系统权限设置开启",
    ),
    onChanged = onChanged,
)

private fun checkAllowedOp(op: String): Boolean = app.appOpsManager.checkOpNoThrow(
    op,
    android.os.Process.myUid(),
    app.packageName
).let {
    it != AppOpsManager.MODE_IGNORED && it != AppOpsManager.MODE_ERRORED
}


object PermissionStates {
    // https://github.com/gkd-kit/gkd/issues/954
    // https://github.com/gkd-kit/gkd/issues/887
    val foregroundServiceSpecialUse by lazy {
        PermissionState(
            name = "特殊用途的前台服务",
            check = {
                if (AndroidTarget.UPSIDE_DOWN_CAKE) {
                    checkAllowedOp(AppOpsManagerHidden.OPSTR_FOREGROUND_SERVICE_SPECIAL_USE)
                } else {
                    true
                }
            },
            resolution = PermissionResolution(
                message = "「特殊用途的前台服务」已被限制，请前往特权服务重新授权",
                confirmText = "去授权",
                confirm = {
                    MainViewModel.instance.navigatePage(PrivilegeServiceRoute)
                },
            ),
        )
    }

    // https://github.com/orgs/gkd-kit/discussions/1234
    private fun checkAccessA11y(): Boolean {
        return !AndroidTarget.Q ||
                checkAllowedOp(AppOpsManagerHidden.OPSTR_ACCESS_ACCESSIBILITY)
    }

    private fun checkCreateA11yOverlay(): Boolean {
        return !CompatAppOpsService.supportA11yOverlay ||
                checkAllowedOp(AppOpsManagerHidden.OPSTR_CREATE_ACCESSIBILITY_OVERLAY)
    }

    val Manifest_permission_GET_APP_OPS_STATS get() = "android.permission.GET_APP_OPS_STATS"

    private var canRestrictsRead = true
    private fun checkAccessRestrictedSettings(): Boolean {
        return if (
            canRestrictsRead &&
            AndroidTarget.UPSIDE_DOWN_CAKE &&
            app.checkGrantedPermission(Manifest_permission_GET_APP_OPS_STATS)
        ) {
            try {
                // https://cs.android.com/android/platform/superproject/+/android-14.0.0_r55:frameworks/base/services/core/java/com/android/server/appop/AppOpsService.java;l=4237
                checkAllowedOp(AppOpsManagerHidden.OPSTR_ACCESS_RESTRICTED_SETTINGS)
            } catch (_: SecurityException) {
                // https://cs.android.com/android/platform/superproject/+/android-14.0.0_r54:frameworks/base/services/core/java/com/android/server/appop/AppOpsService.java;l=4227
                canRestrictsRead = false
                true
            }
        } else {
            true
        }
    }

    private val appOpsAllowed by lazy {
        PermissionState(
            name = "启动相关操作权限",
            check = {
                val accessA11yAllowed = checkAccessA11y()
                val createA11yOverlayAllowed = checkCreateA11yOverlay()
                val accessRestrictedSettingsAllowed = checkAccessRestrictedSettings()
                accessA11yAllowed && createA11yOverlayAllowed && accessRestrictedSettingsAllowed
            },
        )
    }

    val appOpsRestrictedFlow by lazy {
        combine(
            appOpsAllowed.stateFlow,
            foregroundServiceSpecialUse.stateFlow,
        ) { appOpsAllowed, foregroundServiceSpecialUseAllowed ->
            !appOpsAllowed || !foregroundServiceSpecialUseAllowed
        }.stateIn(appScope, SharingStarted.Eagerly, false)
    }

    val notification by lazy {
        requestablePermissionState(
            name = "通知权限",
            purpose = "用于显示后台服务运行状态与必要通知",
            permission = PermissionLists.getPostNotificationsPermission(),
        )
    }

    val queryPackages by lazy {
        requestablePermissionState(
            name = "读取应用列表权限",
            purpose = "用于展示设备应用并匹配应用规则",
            permission = PermissionLists.getGetInstalledAppsPermission(),
            onChanged = {
                if (!updateAppMutex.mutex.isLocked) {
                    updateAllAppInfo()
                }
            },
        )
    }

    val drawOverlays by lazy {
        requestablePermissionState(
            name = "悬浮窗权限",
            purpose = "用于显示快照按钮、界面信息和事件提示等悬浮内容",
            permission = PermissionLists.getSystemAlertWindowPermission(),
            check = {
                // https://developer.android.com/security/fraud-prevention/activities?hl=zh-cn#hide_overlay_windows
                Settings.canDrawOverlays(app)
            },
        )
    }

    val writeExternalStorage by lazy {
        requestablePermissionState(
            name = "写入外部存储权限",
            purpose = "用于在 Android 9 及以下保存截图或文件到公共存储",
            permission = PermissionLists.getWriteExternalStoragePermission(),
            check = {
                if (AndroidTarget.Q) {
                    true
                } else {
                    app.checkGrantedPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            },
        )
    }

    val ignoreBatteryOptimizations by lazy {
        requestablePermissionState(
            name = "忽略电池优化权限",
            purpose = "用于降低后台服务被系统休眠或终止的概率",
            permission = PermissionLists.getRequestIgnoreBatteryOptimizationsPermission(),
            check = {
                app.powerManager.isIgnoringBatteryOptimizations(app.packageName)
            },
        )
    }

    val writeSecureSettings by lazy {
        PermissionState(
            name = "写入安全设置权限",
            check = { app.checkGrantedPermission(Manifest.permission.WRITE_SECURE_SETTINGS) },
        )
    }

    val privilegeGranted by lazy {
        PermissionState(
            name = "特权服务",
            check = {
                privilegeContextFlow.value != null && Privilege.pingServer()
            },
        )
    }

    val all by lazy {
        listOf(
            notification,
            foregroundServiceSpecialUse,
            appOpsAllowed,
            drawOverlays,
            writeExternalStorage,
            ignoreBatteryOptimizations,
            writeSecureSettings,
            queryPackages,
            privilegeGranted,
        )
    }

    fun refreshAll() {
        all.forEach {
            it.refresh()
        }
    }
}
