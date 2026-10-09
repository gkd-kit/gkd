package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.DesktopRuntime
import li.gkd.app.SimulatedService
import li.gkd.app.simulatedServiceRunning

actual fun UiHost.setSnapshotButtonEnabled(enabled: Boolean) = DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Button, enabled)

actual fun UiHost.setHttpServiceEnabled(enabled: Boolean) = DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Http, enabled)

actual fun UiHost.setActivityMonitorEnabled(enabled: Boolean) = DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Activity, enabled)

actual fun UiHost.setEventMonitorEnabled(enabled: Boolean) = DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Event, enabled)

actual fun UiHost.setTrackServiceEnabled(enabled: Boolean) = DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Track, enabled)

@Composable
actual fun httpRunning() = simulatedServiceRunning(SimulatedService.Http)
@Composable
actual fun httpServerAddresses() = listOf(li.gkd.app.util.Constants.loopbackHost)
@Composable
actual fun snapshotButtonRunning() = simulatedServiceRunning(SimulatedService.Button)
@Composable
actual fun activityMonitorRunning() = simulatedServiceRunning(SimulatedService.Activity)
@Composable
actual fun eventMonitorRunning() = simulatedServiceRunning(SimulatedService.Event)
@Composable
actual fun trackServiceRunning() = simulatedServiceRunning(SimulatedService.Track)

actual fun UiHost.httpServerPortChanged() = DesktopRuntime.requireCurrent().httpService.restartIfRunning()
