package li.gkd.app.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_save
import li.gkd.app.resources.dialog_close
import org.jetbrains.compose.resources.stringResource

val LocalEditorWindowInsets = staticCompositionLocalOf<WindowInsets?> { null }

@Composable
fun GkEditorLayout(
    title: @Composable () -> Unit, saveEnabled: Boolean,
    onClose: () -> Unit, onSave: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    GkScaffold(
        contentWindowInsets = LocalEditorWindowInsets.current
            ?: ScaffoldDefaults.contentWindowInsets,
        topBar = {
            GkTopAppBar(
                title = title,
                navigationIcon = {
                    GkIconButton(
                        GkIcons.Close,
                        contentDescription = stringResource(Res.string.dialog_close),
                        onClick = onClose
                    )
                },
                actions = {
                    GkIconButton(
                        GkIcons.Check, contentDescription = stringResource(Res.string.action_save),
                        enabled = saveEnabled, onClick = onSave
                    )
                },
            )
        },
        content = content,
    )
}
