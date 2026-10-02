package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.resources.Res
import li.gkd.app.resources.unchanged
import li.gkd.app.resources.update_success
import li.gkd.app.settings.SettingsAppIds
import li.gkd.app.ui.navigation.EditorFrame
import li.gkd.app.ui.navigation.inputInsets
import li.gkd.app.ui.page.AppWhitelistScreen
import li.gkd.app.ui.text.getSync

@Composable
fun AppWhitelistPage(
    showToast: (String) -> Unit,
    editorFrame: EditorFrame,
) {
    val vm = viewModel { AppWhitelistViewModel() }
    var text by rememberSaveable {
        val names = AppInfoRepository.snapshot?.apps.orEmpty()
        mutableStateOf(vm.initialIds.sorted().sortedBy { if (it in names) 0 else 1 }
            .joinToString("\n\n", postfix = "\n\n") { id ->
                names[id]?.let { "$id\n# ${it.name}" } ?: id
            })
    }
    val indicatorSize by remember {
        snapshotFlow { text }.debounce(500).map { SettingsAppIds.decode(it).size }
    }.collectAsStateWithLifecycle(SettingsAppIds.decode(text).size)
    AppWhitelistScreen(
        text = text,
        onTextChange = { text = it },
        indicatorSize = indicatorSize,
        hasChanges = { vm.hasChanges(text) },
        onSave = { showToast((if (vm.saveChanges(text)) Res.string.update_success else Res.string.unchanged).getSync()) },
        frame = { editor, content -> editorFrame(editor, null, content) },
        inputInsets = inputInsets(),
    )
}
