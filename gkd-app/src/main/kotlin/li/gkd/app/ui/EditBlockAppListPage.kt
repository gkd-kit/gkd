package li.gkd.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.component.GkEditorScaffold
import li.gkd.app.ui.component.GkMultiTextField
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.ToastUtils.toast

@Serializable
data object EditBlockAppListRoute : NavKey

@Composable
fun EditBlockAppListPage() {
    val vm = viewModel<EditBlockAppListVm>()
    val text by vm.textFlow.collectAsStateWithLifecycle()
    GkEditorScaffold(
        title = { Text(UiStrings.app_whitelist) },
        hasChanges = { vm.getChangedSet() != null },
        onSave = {
            toast(if (vm.saveChanges()) UiStrings.update_success else UiStrings.unchanged)
        },
    ) { padding ->
        GkMultiTextField(
            modifier = Modifier.scaffoldPadding(padding),
            text = text,
            onTextChange = vm::setText,
            immediateFocus = true,
            indicatorSize = vm.indicatorSizeFlow.collectAsStateWithLifecycle().value,
        )
    }
}
