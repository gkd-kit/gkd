package li.gkd.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import li.gkd.app.ui.component.GkEditorHost
import li.gkd.app.ui.page.EditorSession
import li.gkd.app.ui.platform.GkBackHandler

@Composable
fun GkEditor(
    editor: EditorSession,
    onSaved: (() -> Unit)? = null,
    navigator: AppNavigator,
    hideIme: () -> Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    content: @Composable (PaddingValues) -> Unit,
) {
    val entry = remember { navigator.topEntry }
    GkEditorHost(
        title = editor.titleContent,
        hasChanges = editor.hasChanges,
        onSave = editor.onSave,
        saveEnabled = editor.saveEnabled,
        onClose = { if (navigator.topEntry === entry) navigator.pop() },
        onSaved = {
            if (navigator.topEntry === entry) {
                if (onSaved != null) onSaved() else navigator.pop()
            }
        },
        beforeClose = { hideIme() },
        backHandler = { GkBackHandler(onBack = it) },
        actionScope = scope,
        content = content,
    )
}
