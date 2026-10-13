package li.gkd.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.ui.page.EditorActionsState
import androidx.compose.runtime.remember
import li.gkd.app.ui.component.GkEditorDialogs
import li.gkd.app.ui.component.GkEditorLayout
import li.gkd.app.ui.page.EditorSession
import li.gkd.app.ui.platform.GkBackHandler

@Composable
fun GkEditor(
    editor: EditorSession,
    onSaved: (() -> Unit)? = null,
    navigator: AppNavigator,
    hideIme: () -> Boolean,
    actions: EditorActionsState,
    content: @Composable (PaddingValues) -> Unit,
) {
    val entry = remember { navigator.topEntry }
    val dialog by actions.dialog.collectAsStateWithLifecycle()
    val close: () -> Unit = { if (navigator.topEntry === entry) navigator.pop() }
    val requestClose: () -> Unit = { actions.requestClose(editor.hasChanges, { hideIme() }, close) }
    val save: () -> Unit = {
        actions.save(editor.onSave, { hideIme() }) {
            if (navigator.topEntry === entry) {
                if (onSaved != null) onSaved() else navigator.pop()
            }
        }
    }
    GkBackHandler(onBack = requestClose)
    GkEditorLayout(editor.titleContent, editor.saveEnabled, requestClose, save, content)
    GkEditorDialogs(dialog, actions::dismissDialog, close)
}
