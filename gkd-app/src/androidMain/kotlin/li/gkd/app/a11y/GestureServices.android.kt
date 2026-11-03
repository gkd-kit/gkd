package li.gkd.app.a11y

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import li.gkd.app.app
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.PlatformResult
import li.gkd.app.resources.*
import li.gkd.app.service.A11yService
import li.gkd.app.service.withServiceControl
import org.jetbrains.compose.resources.getString

actual fun observeEnabledGestureServices(): Flow<List<GestureService>> =
    observeEnabledA11yServices()
        .map { enabled ->
            val manager = app.getSystemService(AccessibilityManager::class.java)
            val pm = app.packageManager
            manager.installedAccessibilityServiceList
                .mapNotNull { info ->
                    val serviceInfo = info.resolveInfo.serviceInfo
                    val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
                    if (
                        component !in enabled ||
                            info.capabilities and
                                AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES == 0
                    )
                        return@mapNotNull null
                    GestureService(
                        id = component.flattenToString(),
                        appId = serviceInfo.packageName,
                        name = info.resolveInfo.loadLabel(pm).toString(),
                        appName = serviceInfo.applicationInfo.loadLabel(pm).toString(),
                        isGkd = component == A11yService.standardComponent,
                        description = info.loadDescription(pm).orEmpty(),
                    )
                }
                .sortedWith(compareBy({ it.appName }, { it.name }, { it.id }))
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

actual fun observeGestureServiceWritePermission(): Flow<Boolean> =
    PermissionStates.writeSecureSettings.stateFlow

actual suspend fun closeGestureServices(ids: Set<String>): PlatformResult<Unit> =
    withServiceControl {
        if (!PermissionStates.writeSecureSettings.updateAndGet()) {
            throw SecurityException(getString(Res.string.secure_settings_permission_required))
        }
        val enabled = app.getSecureA11yServices()
        val selected =
            ids.mapNotNull(ComponentName::unflattenFromString).filter {
                it != A11yService.standardComponent && it in enabled
            }
        if (selected.isNotEmpty() && !app.putSecureA11yServices(enabled - selected.toSet())) {
            error(getString(Res.string.touch_optimization_close_failed))
        }
        PlatformResult.Success(Unit)
    }
