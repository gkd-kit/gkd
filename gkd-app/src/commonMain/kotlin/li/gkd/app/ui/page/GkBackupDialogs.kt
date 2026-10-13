package li.gkd.app.ui.page

import androidx.compose.runtime.Composable
import li.gkd.app.resources.*
import li.gkd.app.ui.component.GkTextListDialog
import org.jetbrains.compose.resources.stringResource

enum class BackupDialog { Main, Export }

@Composable
fun GkBackupDialogs(
    dialog: BackupDialog?,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onShare: () -> Unit,
    onSaveDownloads: () -> Unit,
) {
    if (dialog == null) return
    GkTextListDialog(
        onDismiss = onDismiss,
        textList = when (dialog) {
            BackupDialog.Main -> listOf(
                stringResource(Res.string.backup_import_label) to onImport,
                stringResource(Res.string.backup_export) to onExport,
            )
            BackupDialog.Export -> listOf(
                stringResource(Res.string.action_share) to onShare,
                stringResource(Res.string.action_save_to_downloads) to onSaveDownloads,
            )
        },
    )
}
