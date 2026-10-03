package li.gkd.app.permission

import android.content.pm.PackageManager
import li.gkd.app.priv.PrivilegeCapabilities
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.os.Process
import android.provider.Settings
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import li.gkd.app.app
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.appScope
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.resources.Res
import li.gkd.app.resources.permission_external_storage
import li.gkd.app.resources.permission_external_storage_description
import li.gkd.app.resources.permission_go_authorize
import li.gkd.app.resources.permission_ignore_battery_optimization
import li.gkd.app.resources.permission_ignore_battery_optimization_description
import li.gkd.app.resources.permission_local_network
import li.gkd.app.resources.permission_local_network_description
import li.gkd.app.resources.permission_not_granted_description
import li.gkd.app.resources.permission_notifications
import li.gkd.app.resources.permission_notifications_description
import li.gkd.app.resources.permission_open_settings
import li.gkd.app.resources.permission_overlay
import li.gkd.app.resources.permission_overlay_description
import li.gkd.app.resources.permission_query_apps
import li.gkd.app.resources.permission_query_apps_description
import li.gkd.app.resources.permission_required
import li.gkd.app.resources.permission_special_foreground_service
import li.gkd.app.resources.permission_special_foreground_service_restricted
import li.gkd.app.resources.permission_start_operations
import li.gkd.app.resources.permission_write_secure_settings
import li.gkd.app.resources.privilege_service
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.mapState
import li.songe.codeorigin.CallSite
import org.jetbrains.compose.resources.StringResource
import priv.kit.core.Privilege

class PermissionState(
    private val nameResource: StringResource,
    private val check: () -> Boolean,
    val permission: IPermission? = null,
    private val purposeResource: StringResource? = null,
    val resolution: PermissionResolution? = null,
    private val onChanged: (() -> Unit)? = null,
    val recheckPolicy: PermissionRecheckPolicy = PermissionRecheckPolicy.Immediate,
) {
    val name: String get() = nameResource.getSync()
    val purpose: String? get() = purposeResource?.getSync()

    val stateFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
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

    fun checkOrToast(@CallSite loc: String = ""): Boolean {
        val granted = refresh()
        if (!granted) {
            ToastUtils.show(Res.string.permission_required.getSync(name), loc = loc)
        }
        return granted
    }
}

class PermissionResolution(
    private val messageProvider: () -> String,
    private val confirmResource: StringResource = Res.string.permission_open_settings,
    val navigateToPrivilegeService: Boolean = false,
) {
    val message: String get() = messageProvider()
    val confirmText: String get() = confirmResource.getSync()
}

private fun requestablePermissionState(
    nameResource: StringResource,
    purposeResource: StringResource,
    permission: IPermission,
    check: () -> Boolean = { XXPermissions.isGrantedPermission(app, permission) },
    onChanged: (() -> Unit)? = null,
    recheckPolicy: PermissionRecheckPolicy = PermissionRecheckPolicy.Immediate,
) = PermissionState(
    nameResource = nameResource,
    check = check,
    permission = permission,
    purposeResource = purposeResource,
    resolution = PermissionResolution(
        messageProvider = { Res.string.permission_not_granted_description.getSync(nameResource.getSync()) },
    ),
    onChanged = onChanged,
    recheckPolicy = recheckPolicy,
)

private fun checkAllowedOp(op: String): Boolean = app.appOpsManager.checkOpNoThrow(
    op,
    Process.myUid(),
    app.packageName
).let {
    it != AppOpsManager.MODE_IGNORED && it != AppOpsManager.MODE_ERRORED
}

object PermissionStates {
    // https://github.com/gkd-kit/gkd/issues/954
    // https://github.com/gkd-kit/gkd/issues/887
    val foregroundServiceSpecialUse by lazy {
        PermissionState(
            nameResource = Res.string.permission_special_foreground_service,
            check = {
                if (AndroidTarget.UPSIDE_DOWN_CAKE) {
                    checkAllowedOp(AppOpsManagerHidden.OPSTR_FOREGROUND_SERVICE_SPECIAL_USE)
                } else {
                    true
                }
            },
            resolution = PermissionResolution(
                messageProvider = { Res.string.permission_special_foreground_service_restricted.getSync() },
                confirmResource = Res.string.permission_go_authorize,
                navigateToPrivilegeService = true,
            ),
        )
    }

    // https://github.com/orgs/gkd-kit/discussions/1234
    private fun checkAccessA11y(): Boolean {
        return !AndroidTarget.Q ||
                checkAllowedOp(AppOpsManagerHidden.OPSTR_ACCESS_ACCESSIBILITY)
    }

    private var canRestrictsRead = true
    private fun checkAccessRestrictedSettings(): Boolean {
        return if (
            canRestrictsRead &&
            AndroidTarget.UPSIDE_DOWN_CAKE &&
            app.checkGrantedPermission(AndroidPermissions.GET_APP_OPS_STATS)
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

    private val accessRestrictions = MutableStateFlow<Set<AppPermissionRestriction>>(emptySet())

    private val appOpsAllowed by lazy {
        PermissionState(
            nameResource = Res.string.permission_start_operations,
            check = {
                val accessA11yAllowed = checkAccessA11y()
                val accessRestrictedSettingsAllowed = checkAccessRestrictedSettings()
                accessRestrictions.value = buildSet {
                    if (!accessA11yAllowed) add(AppPermissionRestriction.Accessibility)
                    if (!accessRestrictedSettingsAllowed) add(AppPermissionRestriction.RestrictedSettings)
                }
                accessA11yAllowed && accessRestrictedSettingsAllowed
            },
        )
    }

    val appRestrictionsFlow by lazy {
        combine(
            accessRestrictions,
            foregroundServiceSpecialUse.stateFlow,
        ) { access, foregroundServiceAllowed ->
            if (foregroundServiceAllowed) access else access + AppPermissionRestriction.ForegroundService
        }.stateIn(appScope, SharingStarted.Eagerly, emptySet())
    }

    val appOpsRestrictedFlow by lazy { appRestrictionsFlow.mapState(appScope) { it.isNotEmpty() } }

    val notification by lazy {
        requestablePermissionState(
            nameResource = Res.string.permission_notifications,
            purposeResource = Res.string.permission_notifications_description,
            permission = PermissionLists.getPostNotificationsPermission(),
        )
    }

    val localNetwork by lazy {
        requestablePermissionState(
            nameResource = Res.string.permission_local_network,
            purposeResource = Res.string.permission_local_network_description,
            permission = PermissionLists.getAccessLocalNetworkPermission(),
        )
    }

    val queryPackages by lazy {
        requestablePermissionState(
            nameResource = Res.string.permission_query_apps,
            purposeResource = Res.string.permission_query_apps_description,
            permission = PermissionLists.getGetInstalledAppsPermission(),
            onChanged = {
                AppInfoRepository.requestRefresh()
            },
        )
    }

    val drawOverlays by lazy {
        requestablePermissionState(
            nameResource = Res.string.permission_overlay,
            purposeResource = Res.string.permission_overlay_description,
            permission = PermissionLists.getSystemAlertWindowPermission(),
            check = {
                // https://developer.android.com/security/fraud-prevention/activities?hl=zh-cn#hide_overlay_windows
                Settings.canDrawOverlays(app)
            },
        )
    }

    val writeExternalStorage by lazy {
        requestablePermissionState(
            nameResource = Res.string.permission_external_storage,
            purposeResource = Res.string.permission_external_storage_description,
            permission = PermissionLists.getWriteExternalStoragePermission(),
            check = {
                if (AndroidTarget.Q) {
                    true
                } else {
                    app.checkGrantedPermission(AndroidPermissions.WRITE_EXTERNAL_STORAGE)
                }
            },
        )
    }

    val ignoreBatteryOptimizations by lazy {
        requestablePermissionState(
            nameResource = Res.string.permission_ignore_battery_optimization,
            purposeResource = Res.string.permission_ignore_battery_optimization_description,
            permission = PermissionLists.getRequestIgnoreBatteryOptimizationsPermission(),
            recheckPolicy = PermissionRecheckPolicy.Settings,
            check = {
                app.powerManager.isIgnoringBatteryOptimizations(app.packageName)
            },
        )
    }

    val writeSecureSettings by lazy {
        PermissionState(
            nameResource = Res.string.permission_write_secure_settings,
            check = { app.checkGrantedPermission(AndroidPermissions.WRITE_SECURE_SETTINGS) },
        )
    }

    val privilegeGranted by lazy {
        PermissionState(
            nameResource = Res.string.privilege_service,
            check = {
                privilegeContextFlow.value != null && Privilege.pingServer()
            },
        )
    }

    val all by lazy {
        listOf(
            notification,
            localNetwork,
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

    val privilegeCapabilities: StateFlow<PrivilegeCapabilities?>
        field = MutableStateFlow(null)

    fun refreshAll() {
        all.forEach {
            it.refresh()
        }
        privilegeCapabilities.value = if (privilegeGranted.value) {
            PrivilegeCapabilities(
                grantRuntimePermissions = Privilege.checkServerPermission(AndroidPermissions.GRANT_RUNTIME_PERMISSIONS) == PackageManager.PERMISSION_GRANTED,
                injectEvents = Privilege.checkServerPermission(AndroidPermissions.INJECT_EVENTS) == PackageManager.PERMISSION_GRANTED,
                writeSecureSettings = Privilege.checkServerPermission(AndroidPermissions.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED,
                updateAppOps = Privilege.checkServerPermission(AndroidPermissions.UPDATE_APP_OPS_STATS) == PackageManager.PERMISSION_GRANTED,
            )
        } else null
    }
}
