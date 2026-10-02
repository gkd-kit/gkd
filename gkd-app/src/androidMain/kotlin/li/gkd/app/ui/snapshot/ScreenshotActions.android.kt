package li.gkd.app.ui.snapshot

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.app
import li.gkd.app.permission.PermissionStates
import li.gkd.app.service.ScreenshotService
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.AndroidTarget


actual fun AppWindow.setScreenshotServiceEnabled(enabled: Boolean) {
    val mainVm = MainViewModel.requireCurrent()
    mainVm.scope.launchUi {
        if (!enabled) {
            ScreenshotService.stop()
            return@launchUi
        }
        if (!mainVm.permissionRequests.ensurePermissions(PermissionStates.notification)) {
            return@launchUi
        }
        val activityResult = mainVm.activityResults.startActivity(
            app.mediaProjectionManager.createScreenCaptureIntent(),
        )
        val intent = activityResult.data
        if (activityResult.resultCode == Activity.RESULT_OK && intent != null) {
            ScreenshotService.start(intent)
        }
    }
}


@Composable
actual fun AppWindow.screenshotServiceRunning() =
    ScreenshotService.isRunning.collectAsStateWithLifecycle().value

actual fun AppWindow.nativeScreenshotAvailable() = AndroidTarget.R
actual suspend fun AppWindow.ensureSnapshotSavePermission() =
    MainViewModel.requireCurrent().permissionRequests.ensurePermissions(PermissionStates.writeExternalStorage)
