package li.songe.gkd.permission

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.shizuku.shizukuIsSafeOK
import li.songe.gkd.util.initOrResetAppInfoCache
import li.songe.gkd.util.launchTry

class PermissionState(
    val check: () -> Boolean,
    val request: (suspend (context: Activity) -> PermissionResult)? = null,
    /**
     * show it when user doNotAskAgain
     */
    val reason: AuthReason? = null,
) {
    val stateFlow = MutableStateFlow(check())
    fun updateAndGet(): Boolean {
        return stateFlow.updateAndGet { check() }
    }
}

private fun checkSelfPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(
        app,
        permission
    ) == PackageManager.PERMISSION_GRANTED
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

val canSaveToAlbumState by lazy {
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
                XXPermissions.startPermissionActivity(app, Permission.SYSTEM_ALERT_WINDOW)
            }
        ),
    )
}

val shizukuOkState by lazy {
    PermissionState(
        check = { shizukuIsSafeOK() },
    )
}

fun updatePermissionState() {
    arrayOf(
        notificationState,
        canDrawOverlaysState,
        canSaveToAlbumState,
        shizukuOkState
    ).forEach { it.updateAndGet() }

    if (canQueryPkgState.stateFlow.value != canQueryPkgState.updateAndGet()) {
        appScope.launchTry {
            initOrResetAppInfoCache()
        }
    }
}