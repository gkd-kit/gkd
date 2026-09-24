package li.gkd.app.priv

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.IAccessibilityServiceClient
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.Intent
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.os.IBinder
import android.os.Process
import com.hjq.permissions.permission.dangerous.GetInstalledAppsPermission
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.data.UserInfo
import li.gkd.app.permission.PermissionStates
import li.gkd.app.util.AndroidTarget
import priv.kit.core.Privilege
import priv.kit.core.PrivilegeServerInfo
import priv.kit.core.PrivilegeUserServiceConnection

class PrivilegeContext private constructor(
    val serverInfo: PrivilegeServerInfo,
    private val userServiceConnection: PrivilegeUserServiceConnection,
) {
    val serverLifecycleBinder = serverInfo.lifecycleBinder
    private val packageManager = CompatPackageManager()
    private val userManager = CompatUserManager()
    private val activityManager = CompatActivityManager()
    private val appOpsService = CompatAppOpsService()
    private val inputManager = CompatInputManager()
    private val a11yManager = CompatAccessibilityManager()
    private val wmManager = CompatWindowManager()
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

    fun grantSelf() {
        if (Privilege.isPermissionRestricted()) return
        allowAllSelfMode()
        allowAllSelfPermission()
    }

    fun startForegroundService(intent: Intent) {
        // Started services must set android:exported="true"
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

    fun getUsers(excludeDying: Boolean = true): List<UserInfo> {
        return userManager.getUsers(excludeDying)
    }

    fun getInstalledPackagesAsUser(
        flags: Int,
        userId: Int = currentUserId,
    ): List<PackageInfo> {
        return packageManager.appPackageManager.getInstalledPackagesAsUser(flags, userId)
    }

    fun tap(x: Float, y: Float, duration: Long = 0): Boolean {
        return inputManager.tap(x, y, duration)
    }

    fun swipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        duration: Long,
    ): Boolean {
        return inputManager.swipe(x1, y1, x2, y2, duration)
    }

    fun keyevent(keyCode: Int): Boolean {
        return inputManager.keyevent(keyCode)
    }

    fun registerUiTestAutomationService(
        owner: IBinder,
        client: IAccessibilityServiceClient,
        info: AccessibilityServiceInfo,
        userId: Int,
        flags: Int,
    ) {
        a11yManager.registerUiTestAutomationService(owner, client, info, userId, flags)
    }

    fun unregisterUiTestAutomationService(client: IAccessibilityServiceClient) {
        a11yManager.value.unregisterUiTestAutomationService(client)
    }

    fun isUiAutomationRunning(): Boolean = a11yManager.isUiAutomationRunning()

    fun isRotationFrozen(): Boolean = wmManager.value.isRotationFrozen

    fun getDefaultDisplayRotation(): Int = wmManager.value.defaultDisplayRotation

    fun freezeRotation(rotation: Int, caller: String) {
        wmManager.freezeRotation(rotation, caller)
    }

    fun thawRotation(caller: String) {
        wmManager.thawRotation(caller)
    }

    fun isFocusedWindowSecure(appId: String): Boolean? {
        return wmManager.isFocusedWindowSecure(appId)
    }

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
    }

    private fun grantSelfPermission(name: String) {
        if (app.checkGrantedPermission(name)) return
        Privilege.grantRuntimePermission(
            packageName = META.appId,
            permissionName = name,
        )
    }

    private fun allowAllSelfPermission() {
        if (!PermissionStates.queryPackages.value) {
            grantSelfPermission(GetInstalledAppsPermission.PERMISSION_NAME)
        }
        grantSelfPermission(PermissionStates.Manifest_permission_GET_APP_OPS_STATS)
        grantSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
        if (!AndroidTarget.Q) {
            grantSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (AndroidTarget.TIRAMISU) {
            grantSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (AndroidTarget.CINNAMON_BUN) {
            grantSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
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
    }
}
