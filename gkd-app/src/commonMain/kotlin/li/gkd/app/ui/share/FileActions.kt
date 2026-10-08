package li.gkd.app.ui.share

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import li.gkd.app.platform.PlatformResult
import li.gkd.app.storage.FileSource
import li.gkd.app.ui.platform.UiHost

expect fun UiHost.launchFileAction(block: suspend CoroutineScope.() -> Unit): Job
expect suspend fun UiHost.pickBackupFile(): FileSource?
expect suspend fun UiHost.shareExportFile(title: String, create: suspend () -> File): PlatformResult<Unit>
expect suspend fun UiHost.saveExportFile(file: File)
