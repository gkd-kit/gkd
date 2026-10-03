package li.gkd.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.privilegeCapabilities
import li.gkd.app.permission.AppPermissionRestriction

@Composable
actual fun AppWindow.dashboardPlatformState(): DashboardPlatformState {
    val simulatorSettings by state.simulator.settings.collectAsStateWithLifecycle()
    val device = simulatorSettings.environment().android
    val capabilities = simulatorSettings.privilegeCapabilities()
    return DashboardPlatformState(
        device.serviceEnabled, device.automationRunning, device.a11yEnabled,
        device.writeSecureSettings, device.partiallyDisabled, device.privilegeAvailable,
        if (device.privilegeAvailable) DashboardPrivilegeStatus.Connected else if (simulatorSettings.privilege.desiredEnabled) DashboardPrivilegeStatus.DisconnectedDesired else DashboardPrivilegeStatus.Disconnected,
        device.statusEnabled, device.activityRunning, device.restricted, device.topAppId, capabilities,
        if (device.restricted) setOf(AppPermissionRestriction.Accessibility, AppPermissionRestriction.RestrictedSettings, AppPermissionRestriction.ForegroundService) else emptySet(),
    )
}
