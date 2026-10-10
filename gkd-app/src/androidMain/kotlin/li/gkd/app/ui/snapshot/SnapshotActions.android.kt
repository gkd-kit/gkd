package li.gkd.app.ui.snapshot

import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
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

        override suspend fun share(files: List<File>): PlatformResult<Unit> {
            val uris = files.mapTo(ArrayList(files.size)) { file ->
                FileProvider.getUriForFile(this@snapshotPlatformActions, "$packageName.provider", file)
            }
            val intent = Intent().apply {
                type = "application/zip"
                if (uris.size == 1) {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uris.single())
                } else {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
                clipData = ClipData.newRawUri("", uris.first()).apply {
                    uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val title = getString(Res.string.snapshot_share)
            startActivity(Intent.createChooser(intent, title))
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
