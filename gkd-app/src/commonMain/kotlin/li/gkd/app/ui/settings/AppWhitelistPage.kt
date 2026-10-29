package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.resources.*
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkMultiTextField
import li.gkd.app.ui.navigation.GkEditor
import li.gkd.app.ui.page.EditorSaveResult
import li.gkd.app.ui.page.EditorSession
import li.gkd.app.ui.platform.LocalUiHost
import li.gkd.app.ui.platform.hideIme
import li.gkd.app.ui.platform.inputInsets
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppWhitelistPage() {
    val host = LocalUiHost.current
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { AppWhitelistViewModel() }
    val text by vm.text.collectAsStateWithLifecycle()
    val draft = text
    val indicatorSize by vm.indicatorSize.collectAsStateWithLifecycle()
    val inputInsets: Modifier = inputInsets()

    GkEditor(
        EditorSession(
            stringResource(Res.string.app_whitelist),
            { vm.hasChanges(text) },
            onSave = {
                ToastUtils.show(
                    (if (vm.saveChanges(draft)) Res.string.update_success else Res.string.unchanged)
                        .getSync(),
                )
                EditorSaveResult.Saved
            },
        ),
        onSaved = {
            if (vm.text.value == draft) mainVm.navigator.pop()
        },
        navigator = mainVm.navigator,
        hideIme = host::hideIme,
        actions = vm.editorActions,
    ) { padding ->
        GkMultiTextField(
            modifier = Modifier.scaffoldPadding(padding),
            text = text,
            onTextChange = vm::setText,
            immediateFocus = true,
            indicatorSize = indicatorSize,
            inputInsets = inputInsets,
        )
    }
}
