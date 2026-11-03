package li.gkd.app.a11y

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import li.gkd.app.DesktopRuntime
import li.gkd.app.META
import li.gkd.app.platform.PlatformResult
import li.gkd.app.privilegeCapabilities
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.getString

private const val gkdGestureServiceId = "gkd-gesture-service"

actual fun observeEnabledGestureServices(): Flow<List<GestureService>> =
    DesktopRuntime.requireCurrent()
        .simulator
        .settings
        .map { state ->
            val services = state.services.gestureServices
            if (state.services.a11yEnabled && state.privilegeCapabilities()?.injectEvents != true) {
                services +
                    GestureService(
                        id = gkdGestureServiceId,
                        appId = META.appId,
                        name = META.appName,
                        appName = META.appName,
                        isGkd = true,
                    )
            } else services
        }
        .distinctUntilChanged()

actual fun observeGestureServiceWritePermission(): Flow<Boolean> =
    DesktopRuntime.requireCurrent()
        .simulator
        .settings
        .map { it.permissions.writeSecureSettings }
        .distinctUntilChanged()

actual suspend fun closeGestureServices(ids: Set<String>): PlatformResult<Unit> {
    val runtime = DesktopRuntime.requireCurrent()
    if (!runtime.simulator.settings.value.permissions.writeSecureSettings) {
        throw SecurityException(getString(Res.string.secure_settings_permission_required))
    }
    runtime.simulator.update { state ->
        state.copy(
            services =
                state.services.copy(
                    gestureServices = state.services.gestureServices.filterNot { it.id in ids },
                ),
        )
    }
    return PlatformResult.Success(Unit)
}
