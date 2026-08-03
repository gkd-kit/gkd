package li.songe.gkd.priv

import android.Manifest
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Process
import android.os.RemoteException
import li.songe.gkd.META
import li.songe.gkd.app
import li.songe.gkd.permission.Manifest_permission_GET_APP_OPS_STATS
import li.songe.gkd.permission.canQueryPkgState
import li.songe.gkd.util.AndroidTarget
import priv.kit.core.Privilege
import priv.kit.core.PrivilegeServerInfo
import priv.kit.core.PrivilegeUserServiceConnection
import priv.kit.core.binder.PrivilegeServerUnavailableException

class PrivilegeContext private constructor(
    val serverInfo: PrivilegeServerInfo,
    private val userServiceConnection: PrivilegeUserServiceConnection,
) {
    val serverLifecycleBinder = Privilege.getServerLifecycleBinder()
    val packageManager = CompatPackageManager()
    val userManager = CompatUserManager()
    val activityManager = CompatActivityManager()
    val appOpsService = CompatAppOpsService()
    val inputManager = CompatInputManager()
    val a11yManager = CompatAccessibilityManager()
    val wmManager = CompatWindowManager()
    private val userService = IUserService.Stub.asInterface(userServiceConnection.binder)
    private var taskStackListenerRegistered = false

    private fun initialize() {
        activityManager.value.registerTaskStackListener(CompatTaskStackListener)
        taskStackListenerRegistered = true
        grantSelf()
    }

    suspend fun destroy() {
        try {
            if (taskStackListenerRegistered && Privilege.pingServer()) {
                activityManager.value.unregisterTaskStackListener(CompatTaskStackListener)
                taskStackListenerRegistered = false
            }
        } finally {
            userServiceConnection.unbind()
        }
    }

    fun isCurrentServerAlive(): Boolean {
        if (!serverLifecycleBinder.pingBinder()) return false
        return try {
            Privilege.getServerLifecycleBinder() == serverLifecycleBinder
        } catch (_: PrivilegeServerUnavailableException) {
            false
        } catch (_: RemoteException) {
            false
        }
    }

    fun grantSelf() {
        if (Privilege.isPermissionRestricted()) return
        allowAllSelfMode()
        allowAllSelfPermission()
    }

    fun startForegroundService(intent: Intent) {
        // 被启动的服务必须设置 android:exported="true"
        // https://github.com/android-cs/16/blob/main/services/core/java/com/android/server/am/ActivityManagerShellCommand.java#L982
        activityManager.startService(
            intent = intent,
            requireForeground = true,
            callingPackage = "com.android.shell",
            callingFeatureId = null,
        )
    }

    fun topTask() = activityManager.getTasks().firstOrNull()
    fun topCpn() = topTask()?.topActivity

    fun screenshot(): Bitmap? {
        return CompatScreenshot.capture(app, wmManager.value, userService)
    }

    private fun setAllowSelfMode(code: Int) {
        val mode = appOpsService.value.checkOperation(code, Process.myUid(), META.appId)
        if (mode != AppOpsManager.MODE_ALLOWED) {
            appOpsService.value.setMode(
                code,
                Process.myUid(),
                META.appId,
                AppOpsManager.MODE_ALLOWED,
            )
        }
    }

    private fun allowAllSelfMode() {
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
        if (CompatAppOpsService.supportA11yOverlay) {
            setAllowSelfMode(AppOpsManagerHidden.OP_CREATE_ACCESSIBILITY_OVERLAY)
        }
    }

    private fun grantSelfPermission(name: String) {
        if (app.checkGrantedPermission(name)) return
        Privilege.grantRuntimePermission(
            packageName = META.appId,
            permissionName = name,
        )
    }

    private fun allowAllSelfPermission() {
        if (canUseGetInstalledApps && !canQueryPkgState.value) {
            grantSelfPermission("com.android.permission.GET_INSTALLED_APPS")
        }
        grantSelfPermission(Manifest_permission_GET_APP_OPS_STATS)
        grantSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
        if (AndroidTarget.TIRAMISU) {
            grantSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        suspend fun create(
            serverInfo: PrivilegeServerInfo,
            userServiceConnection: PrivilegeUserServiceConnection,
        ): PrivilegeContext {
            var context: PrivilegeContext? = null
            try {
                context = PrivilegeContext(serverInfo, userServiceConnection)
                context.initialize()
                return context
            } catch (e: Throwable) {
                try {
                    if (context == null) {
                        userServiceConnection.unbind()
                    } else {
                        context.destroy()
                    }
                } catch (cleanupError: Throwable) {
                    e.addSuppressed(cleanupError)
                }
                throw e
            }
        }

        private val canUseGetInstalledApps by lazy {
            try {
                app.packageManager.getPermissionInfo(
                    "com.android.permission.GET_INSTALLED_APPS",
                    0,
                )
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
