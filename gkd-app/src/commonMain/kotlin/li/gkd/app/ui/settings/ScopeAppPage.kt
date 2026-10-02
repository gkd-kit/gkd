package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.ui.component.DialogRequests

@Composable
fun ScopeAppPage(
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    dialogs: DialogRequests,
    hideIme: () -> Boolean,
    appIcon: @Composable (String, Dp) -> Unit,
    blocked: Boolean,
) {
    if (blocked) {
        val vm = viewModel {
            BlockA11yAppListViewModel()
        }
        BlockA11yAppListPage(vm, onBack, showToast, dialogs, hideIme, appIcon)
    } else {
        val vm = viewModel {
            A11yScopeAppListViewModel()
        }
        A11yScopeAppListPage(vm, onBack, showToast, dialogs, hideIme, appIcon)
    }
}
