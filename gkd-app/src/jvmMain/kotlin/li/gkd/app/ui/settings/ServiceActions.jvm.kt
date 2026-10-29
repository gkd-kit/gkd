package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import li.gkd.app.DesktopRuntime
import li.gkd.app.SimulatedService
import li.gkd.app.network.NetworkAddresses
import li.gkd.app.simulatedServiceRunning
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.util.Constants

actual fun UiHost.setSnapshotButtonEnabled(enabled: Boolean) =
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Button, enabled)

actual fun UiHost.setDebugServiceEnabled(enabled: Boolean) =
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Debug, enabled)

actual fun UiHost.setActivityMonitorEnabled(enabled: Boolean) =
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Activity, enabled)

actual fun UiHost.setEventMonitorEnabled(enabled: Boolean) =
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Event, enabled)

actual fun UiHost.setTrackServiceEnabled(enabled: Boolean) =
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Track, enabled)

@Composable actual fun debugServiceRunning() = simulatedServiceRunning(SimulatedService.Debug)

@Composable
actual fun httpServerAddresses(): List<String> =
    remember(debugServiceRunning()) {
        listOf(Constants.loopbackHost) + NetworkAddresses.localIpv4Addresses()
    }

@Composable actual fun snapshotButtonRunning() = simulatedServiceRunning(SimulatedService.Button)

@Composable actual fun activityMonitorRunning() = simulatedServiceRunning(SimulatedService.Activity)

@Composable actual fun eventMonitorRunning() = simulatedServiceRunning(SimulatedService.Event)

@Composable actual fun trackServiceRunning() = simulatedServiceRunning(SimulatedService.Track)

actual fun httpServerPortChanged() = DesktopRuntime.requireCurrent().debugService.restartIfRunning()
