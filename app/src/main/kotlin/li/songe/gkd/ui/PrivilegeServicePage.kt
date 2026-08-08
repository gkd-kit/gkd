package li.songe.gkd.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.priv.gkdPrivilegeUiConfig
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.updateDialogOptions
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.util.throttle
import priv.kit.ui.PrivilegeScaffold
import priv.kit.ui.PrivilegeUiViewModel

@Serializable
data object PrivilegeServiceRoute : NavKey

@Composable
fun PrivilegeServicePage() {
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
                    Text(text = "特权服务")
                },
                actions = {
                    PerfIconButton(
                        imageVector = PerfIcon.Info,
                        contentDescription = "页面说明",
                        onClick = throttle {
                            mainVm.dialogFlow.updateDialogOptions(
                                title = "特权服务",
                                text = "此页面用于启动和管理特权服务。连接后，可为 GKD 提供自动化、必要权限授予等需要系统级能力的功能；断开后，依赖特权服务的功能将不可用。",
                                confirmText = "我知道了",
                            )
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
