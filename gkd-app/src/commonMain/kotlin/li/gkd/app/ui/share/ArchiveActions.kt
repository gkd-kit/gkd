package li.gkd.app.ui.share

import java.io.File
import li.gkd.app.backup.BackupManager
import li.gkd.app.platform.PlatformResult
import li.gkd.app.resources.*
import li.gkd.app.storage.FileExports
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.StorageMaintenance
import li.gkd.app.storage.appStorage
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.settings.SettingsFeedback
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString

suspend fun importBackup(source: FileSource) {
    ToastUtils.show(getString(Res.string.backup_import_progress))
    val skipped = BackupManager.importData(source)
    ToastUtils.show(SettingsFeedback.backupImported(skipped))
}

suspend fun UiHost.saveArchive(create: suspend () -> File) {
    FileExports.withTemporaryFile(
        create = create,
        delete = { StorageMaintenance.deleteSharedFile(appStorage(), it) },
        consume = { saveExportFile(it) },
    )
}

suspend fun UiHost.shareArchive(title: String, create: suspend () -> File) {
    if (shareExportFile(title, create) == PlatformResult.Unsupported) {
        ToastUtils.show(getString(Res.string.platform_action_unsupported))
    }
}
