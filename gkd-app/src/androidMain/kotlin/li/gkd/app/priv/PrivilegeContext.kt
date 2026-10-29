package li.gkd.app.priv

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.IAccessibilityServiceClient
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.IBinder
import android.os.Process
import android.view.IWindowManager
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.aidl.ISystemSurfaceListener
import li.gkd.aidl.IUserService
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.input.AndroidForegroundState
import li.gkd.app.model.AppUser
import li.gkd.app.permission.AndroidPermissions
import li.gkd.app.permission.PermissionStates
import li.gkd.app.service.A11yService
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.util.AndroidTarget
import priv.kit.core.Privilege
import priv.kit.core.PrivilegeServerInfo
import priv.kit.core.PrivilegeUserServiceConnection
import priv.kit.core.binder.PrivilegeBinderWrapper
import priv.kit.input.PrivilegeInput

class PrivilegeContext
private constructor(
    val serverInfo: PrivilegeServerInfo,
    private val userServiceConnection: PrivilegeUserServiceConnection,
) {
    val serverLifecycleBinder
        get() = serverInfo.lifecycleBinder

    private val packageManager = CompatPackageManager()
    private val userManager = CompatUserManager()
    private val activityManager = CompatActivityManager()
    private val appOpsService = CompatAppOpsService()
    private val inputManager =
        PrivilegeInput(
            requireNotNull(PrivilegeBinderWrapper.fromSystemService(Context.INPUT_SERVICE)),
        )
    private val a11yManager = CompatAccessibilityManager()
    private val wmManager =
        IWindowManager.Stub.asInterface(
            requireNotNull(PrivilegeBinderWrapper.fromSystemService(Context.WINDOW_SERVICE)),
        )
    private val userService = IUserService.Stub.asInterface(userServiceConnection.binder)
    private var taskStackListenerRegistered = false
    private var surfaceJob: Job? = null

    fun observeSystemSurface() {
        if (android.os.Build.VERSION.SDK_INT < 34) return
        check(surfaceJob == null)
        surfaceJob =
            appScope.launch(Dispatchers.IO) {
                var subscription: SurfaceSubscription? = null
                fun clear() {
                    val previous = subscription ?: return
                    subscription = null
                    previous.deactivate()
                    userService.setSystemSurfaceListener(null)
                }
                try {
                    combine(SettingsRepository.settings, uiAutomationFlow, A11yService.isRunning) {
                            settings,
                            automation,
                            accessibility ->
                            settings.enableAutomator && (automation != null || accessibility)
                        }
                        .distinctUntilChanged()
                        .collect { enabled ->
                            try {
                                clear()
                                if (enabled) {
                                    val next = SurfaceSubscription()
                                    subscription = next
                                    userService.setSystemSurfaceListener(next)
                                }
                            } catch (e: Exception) {
                                subscription?.deactivate()
                                LogUtils.w("System window observation unavailable", e)
                            }
                        }
                } finally {
                    withContext(NonCancellable) {
                        try {
                            clear()
                        } catch (e: Exception) {
                            LogUtils.e("System window observation cleanup failed", e)
                        }
                    }
                }
            }
    }

    private inner class SurfaceSubscription : ISystemSurfaceListener.Stub() {
        private var active = true

        override fun onSurfaceChanged(state: Int, uptimeMillis: Long) =
            AndroidForegroundState.withTopActivityLock {
                if (
                    !active ||
                        privilegeContextFlow.value !== this@PrivilegeContext ||
                        !SettingsRepository.settings.value.enableAutomator
                )
                    return@withTopActivityLock
                AndroidForegroundState.updateSystemSurface(
                    when (state) {
                        0 -> false
                        1 -> true
                        else -> null
                    },
                    uptimeMillis,
                )
            }

        fun deactivate() = AndroidForegroundState.withTopActivityLock {
            active = false
            AndroidForegroundState.updateSystemSurface(null)
        }
    }

    private fun hasPermission(name: String): Boolean =
        Privilege.checkServerPermission(name) == PackageManager.PERMISSION_GRANTED

    private fun initialize() {
        activityManager.value.registerTaskStackListener(CompatTaskStackListener)
        taskStackListenerRegistered = true
        grantSelf()
    }

    suspend fun destroy() {
        try {
            surfaceJob?.cancelAndJoin()
            if (taskStackListenerRegistered && Privilege.pingServer()) {
                activityManager.value.unregisterTaskStackListener(CompatTaskStackListener)
                taskStackListenerRegistered = false
            }
        } finally {
            userServiceConnection.unbind()
        }
    }

    fun grantSelf() {
        try {
            if (hasAppOpsPermission()) allowAllSelfMode()
            if (hasPermission(AndroidPermissions.GRANT_RUNTIME_PERMISSIONS))
                allowAllSelfPermission()
        } catch (e: Exception) {
            LogUtils.e("Automatic permission grant failed", e)
        }
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

    fun topTask() =
        MatchingRuntime.coordinator.diagnostics
            .observeOperation("ActivityManager.getTasks") {
                activityManager.getTasks()
            }
            .firstOrNull()

    fun topCpn() = topTask()?.topActivity

    fun getUsers(excludeDying: Boolean = true): List<AppUser> {
        return userManager.getUsers(excludeDying)
    }

    fun getInstalledPackagesAsUser(
        flags: Int,
        userId: Int = currentUserId,
    ): List<PackageInfo> {
        return packageManager.appPackageManager.getInstalledPackagesAsUser(flags, userId)
    }

    // null means no input was attempted; false means an attempted input failed and must not be
    // replayed.
    private inline fun injectInputIfPermitted(block: () -> Unit): Boolean? {
        if (!hasPermission(AndroidPermissions.INJECT_EVENTS)) return null
        return try {
            block()
            true
        } catch (e: IOException) {
            LogUtils.w("Privileged input failed", e)
            false
        }
    }

    suspend fun tap(x: Float, y: Float, duration: Long = 0): Boolean? = injectInputIfPermitted {
        if (duration > 0) {
            inputManager.swipe(x, y, x, y, durationMillis = duration)
        } else {
            inputManager.tap(x, y)
        }
    }

    suspend fun swipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        duration: Long,
    ): Boolean? = injectInputIfPermitted { inputManager.swipe(x1, y1, x2, y2, duration) }

    suspend fun keyevent(keyCode: Int): Boolean? = injectInputIfPermitted {
        inputManager.keyEvent(keyCode)
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

    fun screenshot(): Bitmap? {
        return CompatScreenshot.capture(app, wmManager, userService)
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
        grantSelfPermission(AndroidPermissions.DUMP)
        if (!PermissionStates.queryPackages.value) {
            grantSelfPermission(AndroidPermissions.GET_INSTALLED_APPS)
        }
        grantSelfPermission(AndroidPermissions.GET_APP_OPS_STATS)
        grantSelfPermission(AndroidPermissions.WRITE_SECURE_SETTINGS)
        if (!AndroidTarget.Q) {
            grantSelfPermission(AndroidPermissions.WRITE_EXTERNAL_STORAGE)
        }
        if (AndroidTarget.TIRAMISU) {
            grantSelfPermission(AndroidPermissions.POST_NOTIFICATIONS)
        }
        if (AndroidTarget.CINNAMON_BUN) {
            grantSelfPermission(AndroidPermissions.ACCESS_LOCAL_NETWORK)
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
