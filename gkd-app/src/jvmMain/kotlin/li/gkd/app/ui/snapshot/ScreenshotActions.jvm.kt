package li.gkd.app.ui.snapshot

import androidx.compose.runtime.Composable
import li.gkd.app.ui.navigation.AppWindow

@Composable
actual fun AppWindow.screenshotServiceRunning() = false
actual fun AppWindow.nativeScreenshotAvailable() = true
actual fun AppWindow.setScreenshotServiceEnabled(enabled: Boolean) {
    state.unsupported()
}

actual suspend fun AppWindow.ensureSnapshotSavePermission() = true
