package li.gkd.app.ui.home

import li.gkd.app.backup.BackupManager
import li.gkd.app.resources.Res
import li.gkd.app.resources.backup_import_progress
import li.gkd.app.resources.backup_share
import li.gkd.app.resources.file_not_selected
import li.gkd.app.storage.FileExports
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.StorageMaintenance
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.settings.SettingsFeedback
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.AndroidStorage
import li.gkd.app.util.ToastUtils

actual fun AppWindow.importAppBackup() {
    val mainVm = MainViewModel.requireCurrent()
    mainVm.scope.launchUi {
        val uri = mainVm.activityResults.openDocument("application/zip")
        if (uri == null) ToastUtils.show(Res.string.file_not_selected.getSync())
        else {
            ToastUtils.show(Res.string.backup_import_progress.getSync())
            val skipped = BackupManager.importData(FileSource.Uri(uri.toString()))
            ToastUtils.show(SettingsFeedback.backupImported(skipped))
        }
    }
}

actual fun AppWindow.shareAppBackup() {
    val mainVm = MainViewModel.requireCurrent()
    mainVm.scope.launchUi {
        shareFile(BackupManager.exportData(), Res.string.backup_share.getSync())
    }
}

actual fun AppWindow.saveAppBackup() {
    val mainVm = MainViewModel.requireCurrent()
    mainVm.scope.launchUi {
        FileExports.withTemporaryFile(
            create = BackupManager::exportData,
            delete = { StorageMaintenance.deleteSharedFile(AndroidStorage.storage, it) }
        ) {
            saveFileToDownloads(it)
        }
    }
}
