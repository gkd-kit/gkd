package li.gkd.app.ui.snapshot

import androidx.compose.runtime.Composable
import li.gkd.app.ui.platform.UiHost

@Composable
actual fun screenshotServiceRunning() = false
actual fun nativeScreenshotAvailable() = true
actual fun UiHost.setScreenshotServiceEnabled(enabled: Boolean) {
    state.unsupported()
}

actual suspend fun UiHost.ensureSnapshotSavePermission() = true
