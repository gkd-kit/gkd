package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.service.ServiceController
import li.gkd.app.service.ActivityService
import li.gkd.app.service.ButtonService
import li.gkd.app.service.EventService
import li.gkd.app.service.HttpService
import li.gkd.app.service.TrackService
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.share.launchUi


actual fun AppWindow.setSnapshotButtonEnabled(enabled: Boolean) {
    val mainVm = MainViewModel.requireCurrent()
    mainVm.scope.launchUi {
        if (!enabled || mainVm.permissionRequests.ensurePermissions(
                PermissionStates.foregroundServiceSpecialUse,
                PermissionStates.notification,
                PermissionStates.drawOverlays,
            )
        ) {
            ServiceController.setSnapshotButtonEnabled(enabled)
        }
    }
}

actual fun AppWindow.setHttpServiceEnabled(enabled: Boolean) {
    val mainVm = MainViewModel.requireCurrent()
    mainVm.scope.launchUi {
        if (!enabled || mainVm.permissionRequests.ensurePermissions(
                PermissionStates.foregroundServiceSpecialUse,
                PermissionStates.notification,
                PermissionStates.localNetwork,
            )
        ) {
            ServiceController.setHttpEnabled(enabled)
        }
    }
}

actual fun AppWindow.setActivityMonitorEnabled(enabled: Boolean) {
    val mainVm = MainViewModel.requireCurrent()
    mainVm.scope.launchUi {
        if (!enabled || mainVm.permissionRequests.ensurePermissions(
                PermissionStates.foregroundServiceSpecialUse,
                PermissionStates.notification,
                PermissionStates.drawOverlays,
            )
        ) {
            ServiceController.setActivityMonitorEnabled(enabled)
        }
    }
}

actual fun AppWindow.setEventMonitorEnabled(enabled: Boolean) {
    val mainVm = MainViewModel.requireCurrent()
    mainVm.scope.launchUi {
        if (!enabled || mainVm.permissionRequests.ensurePermissions(
                PermissionStates.foregroundServiceSpecialUse,
                PermissionStates.notification,
                PermissionStates.drawOverlays,
            )
        ) {
            ServiceController.setEventMonitorEnabled(enabled)
        }
    }
}

actual fun AppWindow.setTrackServiceEnabled(enabled: Boolean) {
    val mainVm = MainViewModel.requireCurrent()
    mainVm.scope.launchUi {
        if (!enabled || mainVm.permissionRequests.ensurePermissions(
                PermissionStates.foregroundServiceSpecialUse,
                PermissionStates.notification,
                PermissionStates.drawOverlays,
            )
        ) {
            if (enabled) TrackService.start() else TrackService.stop()
        }
    }
}

@Composable
actual fun AppWindow.httpRunning() =
    HttpService.httpServerFlow.collectAsStateWithLifecycle().value != null

@Composable
actual fun AppWindow.localNetworkIps() =
    HttpService.localNetworkIpsFlow.collectAsStateWithLifecycle().value

@Composable
actual fun AppWindow.snapshotButtonRunning() =
    ButtonService.isRunning.collectAsStateWithLifecycle().value

@Composable
actual fun AppWindow.activityMonitorRunning() =
    ActivityService.isRunning.collectAsStateWithLifecycle().value

@Composable
actual fun AppWindow.eventMonitorRunning() =
    EventService.isRunning.collectAsStateWithLifecycle().value

@Composable
actual fun AppWindow.trackServiceRunning() =
    TrackService.isRunning.collectAsStateWithLifecycle().value
