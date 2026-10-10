package li.gkd.app.ui.page

import androidx.compose.runtime.Composable
import li.gkd.app.resources.*
import li.gkd.app.ui.component.GkTextListDialog
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkShareAppDialog(
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onPlay: () -> Unit
) {
    GkTextListDialog(
        onDismiss = onDismiss, textList = listOf(
            stringResource(Res.string.action_share) to onShare,
            stringResource(Res.string.action_save_to_downloads) to onSave,
            stringResource(Res.string.google_play_label) to onPlay,
        )
    )
}
