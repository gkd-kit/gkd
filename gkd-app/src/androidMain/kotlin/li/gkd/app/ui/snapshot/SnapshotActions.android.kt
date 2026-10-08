package li.gkd.app.ui.snapshot

import java.io.File
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.PlatformResult
import li.gkd.app.resources.Res
import li.gkd.app.resources.snapshot_share
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.saveToDownloads
import li.gkd.app.ui.androidState
import li.gkd.app.ui.platform.UiHost
import org.jetbrains.compose.resources.getString

actual fun UiHost.snapshotPlatformActions(): SnapshotPlatformActions {
    return object : SnapshotPlatformActions {
        override suspend fun ensureSavePermission() =
            mainVm.androidState.permissionRequests.ensurePermissions(PermissionStates.writeExternalStorage)

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
            mainVm.androidState.activityResults.pickImage()?.let { FileSource.Uri(it.toString()) }
    }
}
