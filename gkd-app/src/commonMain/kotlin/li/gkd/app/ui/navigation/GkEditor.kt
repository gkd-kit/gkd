package li.gkd.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import li.gkd.app.ui.component.GkEditorHost
import li.gkd.app.ui.page.EditorSession

@Composable
fun GkEditor(
    editor: EditorSession,
    onBack: () -> Unit,
    topRoute: () -> androidx.navigation3.runtime.NavKey,
    hideIme: () -> Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    onSaved: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val entry = remember { topRoute() }
    GkEditorHost(
        title = editor.titleContent,
        hasChanges = editor.hasChanges,
        onSave = editor.onSave,
        saveEnabled = editor.saveEnabled,
        onClose = { if (topRoute() === entry) onBack() },
        onSaved = {
            if (topRoute() === entry) {
                if (onSaved != null) onSaved() else onBack()
            }
        },
        beforeClose = { hideIme() },
        backHandler = { GkBackHandler(onBack = it) },
        actionScope = scope,
        content = content,
    )
}
