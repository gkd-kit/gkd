package li.gkd.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import li.gkd.app.resources.Res
import li.gkd.app.resources.update_success
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.ui.navigation.EditorFrame
import li.gkd.app.ui.page.ActionToastScreen
import li.gkd.app.ui.text.getSync

@Composable
fun ActionToastPage(
    showToast: (String) -> Unit,
    editorFrame: EditorFrame,
    onPreview: (String, Boolean) -> Unit
) {
    val initial = remember { settings.value }
    ActionToastScreen(
        initialEnabled = initial.toastWhenClick,
        initialSystemStyle = initial.useSystemToast,
        initialText = initial.actionToast,
        onPreview = onPreview,
        onSave = { enabled, system, text ->
            if (SettingsRepository.saveActionToast(
                    enabled,
                    system,
                    text
                )
            ) showToast(Res.string.update_success.getSync())
        },
        frame = { editor, content -> editorFrame(editor, null, content) },
    )
}
