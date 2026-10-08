package li.gkd.app.ui.share

import java.io.File
import kotlinx.coroutines.CoroutineScope
import li.gkd.app.platform.PlatformResult
import li.gkd.app.storage.FileSource
import li.gkd.app.ui.androidState
import li.gkd.app.ui.platform.UiHost

actual fun UiHost.launchFileAction(block: suspend CoroutineScope.() -> Unit) =
    mainVm.scope.launchUi(block = block)
actual suspend fun UiHost.pickBackupFile(): FileSource? =
    mainVm.androidState.activityResults.openDocument("application/zip")?.let { FileSource.Uri(it.toString()) }

actual suspend fun UiHost.shareExportFile(title: String, create: suspend () -> File): PlatformResult<Unit> {
    // A native share target may read the file after returning; cache expiry releases it.
    shareFile(create(), title)
    return PlatformResult.Success(Unit)
}

actual suspend fun UiHost.saveExportFile(file: File) = saveFileToDownloads(file)
