package li.songe.gkd.permission

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.shizuku.shizukuCheckGranted
import li.songe.gkd.util.initOrResetAppInfoCache
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.mayQueryPkgNoAccessFlow
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateAppMutex
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PermissionState(
    val check: () -> Boolean,
    val request: (suspend (context: Activity) -> PermissionResult)? = null,
    /**
     * show it when user doNotAskAgain
     */
    val reason: AuthReason? = null,
) {
    val stateFlow = MutableStateFlow(false)
    fun updateAndGet(): Boolean {
        return stateFlow.updateAndGet { check() }
    }

    fun checkOrToast(): Boolean {
        updateAndGet()
        if (!stateFlow.value) {
            reason?.text?.let { toast(it) }
        }
        return stateFlow.value
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
    vararg permissions: String,
): PermissionResult {
    if (XXPermissions.isGranted(context, *permissions)) {
        return PermissionResult.Granted
    }
    return suspendCoroutine { continuation ->
        XXPermissions.with(context)
            .unchecked()
            .permission(*permissions)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (allGranted) {
                        continuation.resume(PermissionResult.Granted)
                    } else {
                        continuation.resume(PermissionResult.Denied(false))
                    }
                }

                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                    continuation.resume(PermissionResult.Denied(doNotAskAgain))
                }
            })
    }
}

private val notificationPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE,
            // https://github.com/gkd-kit/gkd/issues/887
            Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
        )
    } else {
        arrayOf(
            Permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE
        )
    }
} else {
    arrayOf(Permission.POST_NOTIFICATIONS)
}

val notificationState by lazy {
    PermissionState(
        check = {
            XXPermissions.isGranted(app, notificationPermissions)
        },
        request = {
            asyncRequestPermission(it, *notificationPermissions)
        },
        reason = AuthReason(
            text = "当前操作需要「通知权限」n\n您需要前往应用权限设置打开此权限",
            confirm = {
                XXPermissions.startPermissionActivity(app, Permission.POST_NOTIFICATIONS)
            }
        ),
    )
}

val canQueryPkgState by lazy {
    PermissionState(
        check = {
            XXPermissions.isGranted(app, Permission.GET_INSTALLED_APPS)
        },
        request = {
            asyncRequestPermission(it, Permission.GET_INSTALLED_APPS)
        },
        reason = AuthReason(
            text = "当前操作需要「读取应用列表权限」n\n您需要前往应用权限设置打开此权限",
            confirm = {
                XXPermissions.startPermissionActivity(app, Permission.GET_INSTALLED_APPS)
            }
        ),
    )
}

val canDrawOverlaysState by lazy {
    PermissionState(
        check = {
            // 需要注意, 即使有悬浮权限, 在某些特殊的页面如 微信支付完毕 页面, 下面的方法会返回 false
            Settings.canDrawOverlays(app)
        },
        request = {
            // 无法直接请求悬浮窗权限
            if (!Settings.canDrawOverlays(app)) {
                PermissionResult.Denied(true)
            } else {
                PermissionResult.Granted
            }
        },
        reason = AuthReason(
            text = "当前操作需要「悬浮窗权限」n\n您需要前往应用权限设置打开此权限",
            confirm = {
                XXPermissions.startPermissionActivity(app, Manifest.permission.SYSTEM_ALERT_WINDOW)
            }
        ),
    )
}

val canWriteExternalStorage by lazy {
    PermissionState(
        check = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                true
            }
        },
        request = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                asyncRequestPermission(it, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                PermissionResult.Granted
            }
        },
        reason = AuthReason(
            text = "当前操作需要「写入外部存储权限」n\n您需要前往应用权限设置打开此权限",
            confirm = {
                XXPermissions.startPermissionActivity(
                    app,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
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

fun startQueryPkgSettingActivity(context: Activity) {
    XXPermissions.startPermissionActivity(context, Permission.GET_INSTALLED_APPS)
}

fun updatePermissionState() {
    arrayOf(
        notificationState,
        canDrawOverlaysState,
        canWriteExternalStorage,
        writeSecureSettingsState,
        shizukuOkState,
    ).forEach { it.updateAndGet() }
    val stateChanged = canQueryPkgState.stateFlow.value != canQueryPkgState.updateAndGet()
    if (!updateAppMutex.mutex.isLocked && (stateChanged || mayQueryPkgNoAccessFlow.value)) {
        appScope.launchTry(Dispatchers.IO) {
            initOrResetAppInfoCache()
        }
    }
}