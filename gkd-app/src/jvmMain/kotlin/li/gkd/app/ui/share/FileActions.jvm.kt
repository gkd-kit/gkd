package li.gkd.app.ui.share

import java.io.File
import kotlinx.coroutines.CoroutineScope
import li.gkd.app.platform.PlatformResult
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.appStorage
import li.gkd.app.ui.platform.UiHost

actual fun UiHost.launchFileAction(block: suspend CoroutineScope.() -> Unit) = state.mainVm.scope.launchUi {
    runtime.subscriptionInitialization.join()
    block()
}
actual suspend fun UiHost.pickBackupFile(): FileSource? {
    val file = fileActions.choose(appStorage().sharedCache) ?: return null
    return FileSource.Local(file)
}

actual suspend fun UiHost.shareExportFile(title: String, create: suspend () -> File): PlatformResult<Unit> =
    PlatformResult.Unsupported

actual suspend fun UiHost.saveExportFile(file: File) {
    fileActions.saveAs(file)
}
