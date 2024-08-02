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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.shizuku.newActivityTaskManager
import li.songe.gkd.shizuku.safeGetTasks
import li.songe.gkd.shizuku.shizukuIsSafeOK
import li.songe.gkd.util.initOrResetAppInfoCache
import li.songe.gkd.util.launchTry
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PermissionState(
    val check: suspend () -> Boolean,
    val request: (suspend (context: Activity) -> PermissionResult)? = null,
    /**
     * show it when user doNotAskAgain
     */
    val reason: AuthReason? = null,
) {
    val stateFlow = MutableStateFlow(false)
    suspend fun updateAndGet(): Boolean {
        return stateFlow.updateAndGet { check() }
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
    permission: String,
): PermissionResult {
    if (XXPermissions.isGranted(context, permission)) {
        return PermissionResult.Granted
    }
    return suspendCoroutine { continuation ->
        XXPermissions.with(context)
            .unchecked()
            .permission(permission)
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

val notificationState by lazy {
    PermissionState(
        check = {
            checkSelfPermission(Permission.POST_NOTIFICATIONS)
        },
        request = {
            asyncRequestPermission(it, Permission.POST_NOTIFICATIONS)
        },
        reason = AuthReason(
            text = "当前操作需要[通知权限]\n\n您需要前往应用权限设置打开此权限",
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
            text = "当前操作需要[读取应用列表权限]\n\n您需要前往应用权限设置打开此权限",
            confirm = {
                XXPermissions.startPermissionActivity(app, Permission.GET_INSTALLED_APPS)
            }
        ),
    )
}

val canDrawOverlaysState by lazy {
    PermissionState(
        check = {
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
            text = "当前操作需要[悬浮窗权限]\n\n您需要前往应用权限设置打开此权限",
            confirm = {
                XXPermissions.startPermissionActivity(app, Permission.SYSTEM_ALERT_WINDOW)
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
                asyncRequestPermission(it, Permission.WRITE_EXTERNAL_STORAGE)
            } else {
                PermissionResult.Granted
            }
        },
        reason = AuthReason(
            text = "当前操作需要[写入外部存储权限]\n\n您需要前往应用权限设置打开此权限",
            confirm = {
                XXPermissions.startPermissionActivity(app, Permission.WRITE_EXTERNAL_STORAGE)
            }
        ),
    )
}

val shizukuOkState by lazy {
    PermissionState(
        check = {
            shizukuIsSafeOK() && (try {
                // 打开 shizuku 点击右上角停止, 此时 shizukuIsSafeOK() == true, 因此需要二次检查状态
                newActivityTaskManager()?.safeGetTasks(log = false)?.isNotEmpty() == true
            } catch (e: Exception) {
                false
            })
        },
    )
}

private val checkAuthLoading = MutableStateFlow(false)
suspend fun updatePermissionState() {
    if (checkAuthLoading.value) return
    checkAuthLoading.value = true
    arrayOf(
        notificationState,
        canDrawOverlaysState,
        canWriteExternalStorage,
        shizukuOkState
    ).forEach { it.updateAndGet() }
    if (canQueryPkgState.stateFlow.value != canQueryPkgState.updateAndGet()) {
        appScope.launchTry {
            initOrResetAppInfoCache()
        }
    }
    checkAuthLoading.value = false
}