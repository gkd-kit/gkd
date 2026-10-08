package li.gkd.app.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.priv.gkdPrivilegeUiConfig
import li.gkd.app.ui.page.GkPrivilegeTopBar
import li.gkd.app.ui.page.PrivilegeServiceInfoDialog
import li.gkd.app.ui.platform.UiHost
import priv.kit.ui.PrivilegeScaffold
import priv.kit.ui.PrivilegeUiViewModel

@Composable
actual fun PrivilegeServicePage(host: UiHost) {
    val mainVm = MainViewModel.requireCurrent()
    val application = LocalContext.current.applicationContext as Application
    val privilegeVm = viewModel {
        GkdPrivilegeUiViewModel(application) {
            mainVm.navigator.pop()
        }
    }
    val vm = viewModel { PrivilegeServiceViewModel() }
    val showInfo by vm.showInfo.collectAsStateWithLifecycle()
    if (showInfo) {
        PrivilegeServiceInfoDialog(
            onDismissRequest = { vm.setShowInfo(false) },
        )
    }
    PrivilegeScaffold(
        modifier = Modifier.fillMaxSize(),
        viewModel = privilegeVm,
        topBar = {
            GkPrivilegeTopBar(mainVm.navigator::pop) {
                vm.setShowInfo(true)
            }
        },
    )
}

private class GkdPrivilegeUiViewModel(
    application: Application,
    private val backAction: () -> Unit,
) : PrivilegeUiViewModel(
    application,
    gkdPrivilegeUiConfig,
) {
    override fun onBackClick(): Boolean {
        backAction()
        return true
    }
}
