package li.gkd.app.ui.snapshot

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.app
import li.gkd.app.permission.PermissionStates
import li.gkd.app.service.ScreenshotService
import li.gkd.app.ui.androidState
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.AndroidTarget

actual fun UiHost.setScreenshotServiceEnabled(enabled: Boolean) {
    mainVm.scope.launchUi {
        if (!enabled) {
            ScreenshotService.stop()
            return@launchUi
        }
        if (!mainVm.androidState.permissionRequests.ensurePermissions(PermissionStates.notification)) {
            return@launchUi
        }
        val activityResult = mainVm.androidState.activityResults.startActivity(
            app.mediaProjectionManager.createScreenCaptureIntent(),
        )
        val intent = activityResult.data
        if (activityResult.resultCode == Activity.RESULT_OK && intent != null) {
            ScreenshotService.start(intent)
        }
    }
}

@Composable
actual fun screenshotServiceRunning() =
    ScreenshotService.isRunning.collectAsStateWithLifecycle().value

actual fun nativeScreenshotAvailable() = AndroidTarget.R
actual suspend fun UiHost.ensureSnapshotSavePermission() =
    mainVm.androidState.permissionRequests.ensurePermissions(PermissionStates.writeExternalStorage)
