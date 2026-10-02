package li.gkd.app.ui.snapshot

import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.PlatformResult
import li.gkd.app.resources.Res
import li.gkd.app.resources.snapshot_share
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.saveToDownloads
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.navigation.AppWindow
import org.jetbrains.compose.resources.getString
import java.io.File

actual fun AppWindow.snapshotPlatformActions(): SnapshotPlatformActions {
    val mainVm = MainViewModel.requireCurrent()
    return object : SnapshotPlatformActions {
        override suspend fun ensureSavePermission() =
            mainVm.permissionRequests.ensurePermissions(PermissionStates.writeExternalStorage)

        override suspend fun share(file: File): PlatformResult<Unit> {
            shareFile(file, getString(Res.string.snapshot_share))
            return PlatformResult.Success(Unit)
        }

        override suspend fun save(file: File): PlatformResult<Boolean> =
            when (saveToDownloads(file)) {
                is PlatformResult.Success -> PlatformResult.Success(true)
                PlatformResult.Unsupported -> PlatformResult.Unsupported
            }

        override suspend fun pickImage(): FileSource? =
            mainVm.activityResults.pickImage()?.let { FileSource.Uri(it.toString()) }
    }
}
