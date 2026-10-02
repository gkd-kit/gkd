package li.gkd.app.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_save_to_downloads
import li.gkd.app.resources.action_share
import li.gkd.app.resources.backup_export
import li.gkd.app.resources.backup_import_label
import li.gkd.app.ui.component.GkTextListDialog
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkBackupDialogs(
    visible: Boolean, onDismiss: () -> Unit, onImport: () -> Unit,
    onShare: () -> Unit, onSaveDownloads: () -> Unit
) {
    var exporting by rememberSaveable { mutableStateOf(false) }
    if (visible) {
        GkTextListDialog(
            onDismiss = onDismiss, textList = listOf(
                stringResource(Res.string.backup_import_label) to onImport,
                stringResource(Res.string.backup_export) to { exporting = true },
            )
        )
    }
    if (exporting) {
        GkTextListDialog(
            onDismiss = { exporting = false }, textList = listOf(
                stringResource(Res.string.action_share) to onShare,
                stringResource(Res.string.action_save_to_downloads) to onSaveDownloads,
            )
        )
    }
}
