package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import li.gkd.app.ui.platform.UiHost

actual fun UiHost.setSnapshotButtonEnabled(enabled: Boolean) {
    state.unsupported()
}

actual fun UiHost.setHttpServiceEnabled(enabled: Boolean) {
    state.unsupported()
}

actual fun UiHost.setActivityMonitorEnabled(enabled: Boolean) {
    state.unsupported()
}

actual fun UiHost.setEventMonitorEnabled(enabled: Boolean) {
    state.unsupported()
}

actual fun UiHost.setTrackServiceEnabled(enabled: Boolean) {
    state.unsupported()
}

@Composable
actual fun httpRunning() = false
@Composable
actual fun localNetworkIps() = emptyList<String>()
@Composable
actual fun snapshotButtonRunning() = false
@Composable
actual fun activityMonitorRunning() = false
@Composable
actual fun eventMonitorRunning() = false
@Composable
actual fun trackServiceRunning() = false
