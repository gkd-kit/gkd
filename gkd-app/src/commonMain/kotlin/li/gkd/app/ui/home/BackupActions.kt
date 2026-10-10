package li.gkd.app.ui.home

import li.gkd.app.backup.BackupManager
import li.gkd.app.resources.*
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.share.importBackup
import li.gkd.app.ui.share.launchFileAction
import li.gkd.app.ui.share.pickBackupFile
import li.gkd.app.ui.share.saveArchive
import li.gkd.app.ui.share.shareArchive
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString

fun UiHost.importAppBackup() = launchFileAction {
    val source = pickBackupFile()
    if (source == null) ToastUtils.show(getString(Res.string.file_not_selected))
    else importBackup(source)
}

fun UiHost.shareAppBackup() = launchFileAction {
    shareArchive(getString(Res.string.backup_share), BackupManager::exportData)
}

fun UiHost.saveAppBackup() = launchFileAction {
    saveArchive(BackupManager::exportData)
}
