package li.gkd.app.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.priv.gkdPrivilegeUiConfig
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.page.GkPrivilegeTopBar
import li.gkd.app.ui.page.PrivilegeServiceInfoDialog
import priv.kit.ui.PrivilegeScaffold
import priv.kit.ui.PrivilegeUiViewModel

@Composable
actual fun PrivilegeServicePage(window: AppWindow) {
    val mainVm = MainViewModel.requireCurrent()
    val application = LocalContext.current.applicationContext as Application
    val privilegeVm = viewModel {
        GkdPrivilegeUiViewModel(application) {
            mainVm.popPage()
        }
    }
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    if (showInfoDialog) {
        PrivilegeServiceInfoDialog(
            onDismissRequest = { showInfoDialog = false },
        )
    }
    PrivilegeScaffold(
        modifier = Modifier.fillMaxSize(),
        viewModel = privilegeVm,
        topBar = {
            GkPrivilegeTopBar(mainVm::popPage) {
                showInfoDialog = true
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
