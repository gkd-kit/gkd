package li.songe.gkd.permission

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.provider.Settings
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import li.songe.gkd.MainActivity
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
    val request: (suspend (context: MainActivity) -> PermissionResult)? = null,
    /**
     * show it when user doNotAskAgain
     */
    val reason: AuthReason? = null,
    private val onChanged: (() -> Unit)? = null,
) {
    val stateFlow = MutableStateFlow(false)
    val value get() = stateFlow.value

    fun updateAndGet(): Boolean {
        return stateFlow.updateAndGet { check() }
    }

    fun refresh() {
        if (value != updateAndGet()) {
            onChanged?.invoke()
        }
    }

    fun checkOrToast(): Boolean = if (!updateAndGet()) {
        val r = updateAndGet()
        if (!r) {
            reason?.text?.let { toast(it()) }
        }
        r
    } else {
        true
    }
}

private suspend fun asyncRequestPermission(
    context: Activity,
    permission: IPermission,
): PermissionResult {
    if (XXPermissions.isGrantedPermission(context, permission)) {
        return PermissionResult.Granted
    }
    val deferred = CompletableDeferred<PermissionResult>()
    XXPermissions.with(context)
        .unchecked()
        .permission(permission)
        .request { grantedList, _ ->
            if (grantedList.contains(permission)) {
                PermissionResult.Granted
            } else {
                PermissionResult.Denied(
                    XXPermissions.isDoNotAskAgainPermissions(
                        context,
                        arrayOf(permission)
                    )
                )
            }.let { deferred.complete(it) }
        }
    return deferred.await()
}

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
            reason = AuthReason(
                text = { "当前操作权限「特殊用途的前台服务」已被限制，请前往特权服务重新授权" },
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
        val permission = PermissionLists.getNotificationServicePermission()
        PermissionState(
            name = "通知权限",
            check = {
                XXPermissions.isGrantedPermission(app, permission)
            },
            request = { asyncRequestPermission(it, permission) },
            reason = AuthReason(
                text = { "当前操作需要「通知权限」\n请先前往权限页面授权" },
                confirm = {
                    XXPermissions.startPermissionActivity(app, permission)
                }
            ),
        )
    }

    val queryPackages by lazy {
        val permission = PermissionLists.getGetInstalledAppsPermission()
        PermissionState(
            name = "读取应用列表权限",
            check = {
                XXPermissions.isGrantedPermission(app, permission)
            },
            request = {
                asyncRequestPermission(it, permission)
            },
            reason = AuthReason(
                text = { "当前操作需要「读取应用列表权限」\n请先前往权限页面授权" },
                confirm = {
                    XXPermissions.startPermissionActivity(app, permission)
                }
            ),
            onChanged = {
                if (!updateAppMutex.mutex.isLocked) {
                    updateAllAppInfo()
                }
            },
        )
    }

    val drawOverlays by lazy {
        PermissionState(
            name = "悬浮窗权限",
            check = {
                // https://developer.android.com/security/fraud-prevention/activities?hl=zh-cn#hide_overlay_windows
                Settings.canDrawOverlays(app)
            },
            reason = AuthReason(
                text = {
                    "当前操作需要「悬浮窗权限」\n请先前往权限页面授权"
                },
                confirm = {
                    XXPermissions.startPermissionActivity(
                        app,
                        PermissionLists.getSystemAlertWindowPermission()
                    )
                }
            ),
        )
    }

    val writeExternalStorage by lazy {
        PermissionState(
            name = "写入外部存储权限",
            check = {
                if (AndroidTarget.Q) {
                    true
                } else {
                    app.checkGrantedPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            },
            request = {
                if (AndroidTarget.Q) {
                    PermissionResult.Granted
                } else {
                    asyncRequestPermission(it, PermissionLists.getWriteExternalStoragePermission())
                }
            },
            reason = AuthReason(
                text = { "当前操作需要「写入外部存储权限」\n请先前往权限页面授权" },
                confirm = {
                    XXPermissions.startPermissionActivity(
                        app,
                        PermissionLists.getWriteExternalStoragePermission()
                    )
                }
            ),
        )
    }

    val ignoreBatteryOptimizations by lazy {
        val permission = PermissionLists.getRequestIgnoreBatteryOptimizationsPermission()
        PermissionState(
            name = "忽略电池优化权限",
            check = {
                app.powerManager.isIgnoringBatteryOptimizations(app.packageName)
            },
            request = {
                asyncRequestPermission(it, permission)
            },
            reason = AuthReason(
                text = { "当前操作需要「忽略电池优化权限」\n请先前往权限页面授权" },
                confirm = {
                    XXPermissions.startPermissionActivity(
                        app,
                        permission
                    )
                }
            ),
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
