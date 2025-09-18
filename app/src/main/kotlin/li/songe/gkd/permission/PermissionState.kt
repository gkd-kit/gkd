package li.songe.gkd.permission

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import com.ramcosta.composedestinations.generated.destinations.AppOpsAllowPageDestination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import li.songe.gkd.MainActivity
import li.songe.gkd.app
import li.songe.gkd.isActivityVisible
import li.songe.gkd.shizuku.shizukuCheckGranted
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.mayQueryPkgNoAccessFlow
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateAllAppInfo
import li.songe.gkd.util.updateAppMutex

class PermissionState(
    val check: () -> Boolean,
    val request: (suspend (context: MainActivity) -> PermissionResult)? = null,
    /**
     * show it when user doNotAskAgain
     */
    val reason: AuthReason? = null,
) {
    val stateFlow = MutableStateFlow(false)
    val value: Boolean
        get() = stateFlow.value

    fun updateAndGet(): Boolean {
        return stateFlow.updateAndGet { check() }
    }

    fun checkOrToast(): Boolean {
        val r = updateAndGet()
        if (!r) {
            reason?.text?.let { toast(it()) }
        }
        return r
    }
}

private fun checkSelfPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(
        app,
        permission
    ) == PackageManager.PERMISSION_GRANTED
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

@Suppress("SameParameterValue")
private fun checkOpNoThrow(op: String): Int {
    if (AndroidTarget.Q) {
        try {
            return app.appOpsManager.checkOpNoThrow(
                op,
                android.os.Process.myUid(),
                app.packageName
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
    return AppOpsManager.MODE_ALLOWED
}

// https://github.com/gkd-kit/gkd/issues/954
// https://github.com/gkd-kit/gkd/issues/887
val foregroundServiceSpecialUseState by lazy {
    PermissionState(
        check = {
            checkOpNoThrow("android:foreground_service_special_use") != AppOpsManager.MODE_IGNORED
        },
        reason = AuthReason(
            text = { "当前操作权限「特殊用途的前台服务」已被限制, 请先解除限制" },
            renderConfirm = {
                val mainVm = LocalMainViewModel.current
                {
                    mainVm.navigatePage(AppOpsAllowPageDestination)
                }
            }
        ),
    )
}

val notificationState by lazy {
    val permission = PermissionLists.getNotificationServicePermission()
    PermissionState(
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

val canQueryPkgState by lazy {
    val permission = PermissionLists.getGetInstalledAppsPermission()
    PermissionState(
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
    )
}

val canDrawOverlaysState by lazy {
    PermissionState(
        check = {
            // https://developer.android.com/security/fraud-prevention/activities?hl=zh-cn#hide_overlay_windows
            Settings.canDrawOverlays(app)
        },
        reason = AuthReason(
            text = {
                if (isActivityVisible()) {
                    "当前操作需要「悬浮窗权限」\n请先前往权限页面授权"
                } else {
                    "缺少「悬浮窗权限」请先授权\n或当前应用拒绝显示悬浮窗"
                }
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

val canWriteExternalStorage by lazy {
    PermissionState(
        check = {
            if (AndroidTarget.Q) {
                true
            } else {
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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

val writeSecureSettingsState by lazy {
    PermissionState(
        check = { checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) },
    )
}

val shizukuOkState by lazy {
    PermissionState(
        check = { shizukuCheckGranted() },
    )
}

fun updatePermissionState() {
    val stateChanged = canQueryPkgState.stateFlow.value != canQueryPkgState.updateAndGet()
    if (!updateAppMutex.mutex.isLocked && (stateChanged || mayQueryPkgNoAccessFlow.value)) {
        updateAllAppInfo()
    }
    arrayOf(
        notificationState,
        foregroundServiceSpecialUseState,
        canDrawOverlaysState,
        canWriteExternalStorage,
        writeSecureSettingsState,
        shizukuOkState,
    ).forEach { it.updateAndGet() }
}