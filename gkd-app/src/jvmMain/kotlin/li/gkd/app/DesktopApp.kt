package li.gkd.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.Window
import java.io.File
import li.gkd.app.backup.BackupManager
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.createLogArchive
import li.gkd.app.ui.share.importBackup as importBackupArchive
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.ToastUtils

class DesktopSession(
    val state: DesktopState,
    val runtime: DesktopRuntime,
    fileDialogOwner: () -> Window,
    val browsersRunning: () -> Boolean,
    val onBrowserKey: (Int) -> Boolean,
) {
    val fileActions = DesktopFileActions(fileDialogOwner)
    val revision = state.revision
    // Controls expose archive paths; production pages use the shared host actions.
    var backupPath by mutableStateOf("")
    fun exportBackup() = state.mainVm.scope.launchUi {
        runtime.subscriptionInitialization.join()
        val file = BackupManager.exportData()
        backupPath = file.absolutePath
        state.record("backup-export:" + file.absolutePath)
        ToastUtils.show(file.absolutePath)
    }

    fun importBackup() = state.mainVm.scope.launchUi {
        runtime.subscriptionInitialization.join()
        val file = File(backupPath)
        importBackupArchive(FileSource.Local(file))
        state.record("backup-import:" + file.absolutePath)
    }

    fun exportLogs() = state.mainVm.scope.launchUi {
        val file = createLogArchive()
        state.record("logs-export:" + file.absolutePath)
        ToastUtils.show(file.absolutePath)
    }
}
