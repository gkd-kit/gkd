package li.songe.gkd.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.priv.gkdPrivilegeUiConfig
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.icon.GitHub
import li.songe.gkd.ui.share.LocalMainViewModel
import priv.kit.ui.PrivilegeScaffold
import priv.kit.ui.PrivilegeUiViewModel
import priv.kit.ui.R as PrivilegeUiR

@Serializable
data object PrivilegePageRoute : NavKey

private const val PRIV_KIT_REPOSITORY_URL = "https://github.com/priv-kit/priv-kit"

@Composable
fun PrivilegePage() {
    val mainVm = LocalMainViewModel.current
    val application = LocalContext.current.applicationContext as Application
    val privilegeVm = viewModel {
        GkdPrivilegeUiViewModel(application) {
            mainVm.popPage()
        }
    }
    PrivilegeScaffold(
        modifier = Modifier.fillMaxSize(),
        viewModel = privilegeVm,
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = mainVm::popPage,
                    )
                },
                title = {
                    Text(text = stringResource(PrivilegeUiR.string.priv_ui_title))
                },
                actions = {
                    PerfIconButton(
                        imageVector = GitHub,
                        contentDescription = "GitHub",
                        onClick = {
                            mainVm.openUrl(PRIV_KIT_REPOSITORY_URL)
                        },
                    )
                },
            )
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
