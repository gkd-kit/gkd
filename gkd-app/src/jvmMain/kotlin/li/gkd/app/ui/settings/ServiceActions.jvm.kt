package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import li.gkd.app.ui.navigation.AppWindow

actual fun AppWindow.setSnapshotButtonEnabled(enabled: Boolean) {
    state.unsupported()
}

actual fun AppWindow.setHttpServiceEnabled(enabled: Boolean) {
    state.unsupported()
}

actual fun AppWindow.setActivityMonitorEnabled(enabled: Boolean) {
    state.unsupported()
}

actual fun AppWindow.setEventMonitorEnabled(enabled: Boolean) {
    state.unsupported()
}

actual fun AppWindow.setTrackServiceEnabled(enabled: Boolean) {
    state.unsupported()
}

@Composable
actual fun AppWindow.httpRunning() = false
@Composable
actual fun AppWindow.localNetworkIps() = emptyList<String>()
@Composable
actual fun AppWindow.snapshotButtonRunning() = false
@Composable
actual fun AppWindow.activityMonitorRunning() = false
@Composable
actual fun AppWindow.eventMonitorRunning() = false
@Composable
actual fun AppWindow.trackServiceRunning() = false
