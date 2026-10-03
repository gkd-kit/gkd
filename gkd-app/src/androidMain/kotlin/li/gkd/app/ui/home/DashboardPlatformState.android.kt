package li.gkd.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.PrivilegeServiceStatus
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.priv.privilegeServiceStatusFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.service.A11yService
import li.gkd.app.service.ActivityService
import li.gkd.app.service.StatusService
import li.gkd.app.service.a11yPartDisabledFlow
import li.gkd.app.service.topAppIdFlow
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.navigation.AppWindow

@Composable
actual fun AppWindow.dashboardPlatformState(): DashboardPlatformState {
    val mainVm = MainViewModel.requireCurrent()
    val privilege by privilegeContextFlow.collectAsStateWithLifecycle()
    val privilegeStatus by privilegeServiceStatusFlow.collectAsStateWithLifecycle()
    val capabilities by PermissionStates.privilegeCapabilities.collectAsStateWithLifecycle()
    val a11y by A11yService.isRunning.collectAsStateWithLifecycle()
    val automation by uiAutomationFlow.collectAsStateWithLifecycle()
    val status by StatusService.isRunning.collectAsStateWithLifecycle()
    val secure by PermissionStates.writeSecureSettings.stateFlow.collectAsStateWithLifecycle()
    val a11yEnabled by mainVm.a11yServiceEnabledFlow.collectAsStateWithLifecycle()
    val partial by a11yPartDisabledFlow.collectAsStateWithLifecycle()
    val topApp by topAppIdFlow.collectAsStateWithLifecycle()
    val restrictions by PermissionStates.appRestrictionsFlow.collectAsStateWithLifecycle()
    val activity by ActivityService.isRunning.collectAsStateWithLifecycle()
    return DashboardPlatformState(
        a11y, automation != null, a11yEnabled, secure, partial, privilege != null,
        when (privilegeStatus) {
            PrivilegeServiceStatus.Connected -> DashboardPrivilegeStatus.Connected; PrivilegeServiceStatus.DisconnectedDesired -> DashboardPrivilegeStatus.DisconnectedDesired; PrivilegeServiceStatus.Disconnected -> DashboardPrivilegeStatus.Disconnected
        },
        status, activity, restrictions.isNotEmpty(), topApp, capabilities, restrictions
    )
}
