package li.gkd.app.ui.snapshot

import androidx.compose.runtime.Composable
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.DesktopRuntime
import li.gkd.app.SimulatedService
import li.gkd.app.simulatedServiceRunning

@Composable
actual fun screenshotServiceRunning() = simulatedServiceRunning(SimulatedService.Screenshot)
actual fun nativeScreenshotAvailable() = false
actual fun UiHost.setScreenshotServiceEnabled(enabled: Boolean) {
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Screenshot, enabled)
}

actual suspend fun UiHost.ensureSnapshotSavePermission() = true
