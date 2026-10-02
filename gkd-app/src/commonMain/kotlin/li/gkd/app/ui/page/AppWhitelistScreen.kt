package li.gkd.app.ui.page

import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import li.gkd.app.resources.Res
import li.gkd.app.resources.app_whitelist
import li.gkd.app.ui.component.GkMultiTextField
import li.gkd.app.ui.style.scaffoldPadding
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppWhitelistScreen(
    text: String, onTextChange: (String) -> Unit, indicatorSize: Int,
    hasChanges: () -> Boolean, onSave: suspend () -> Unit, frame: EditorFrame,
    inputInsets: Modifier = Modifier.imePadding(),
) {
    frame(
        EditorSession(
            stringResource(Res.string.app_whitelist),
            hasChanges,
            onSave = onSave
        )
    ) { padding ->
        GkMultiTextField(
            modifier = Modifier.scaffoldPadding(padding), text = text, onTextChange = onTextChange,
            immediateFocus = true, indicatorSize = indicatorSize, inputInsets = inputInsets,
        )
    }
}
