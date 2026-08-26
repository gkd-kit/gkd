package li.gkd.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import li.gkd.app.priv.gkdPrivilegeUiConfig
import li.gkd.app.ui.component.AppAlertDialog
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.util.throttle
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
    val showInfoDialog by privilegeVm.showInfoDialogFlow.collectAsStateWithLifecycle()
    if (showInfoDialog) {
        PrivilegeServiceInfoDialog(
            onDismissRequest = { privilegeVm.setInfoDialogVisible(false) },
        )
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
                            privilegeVm.setInfoDialogVisible(true)
                        },
                    )
                },
            )
        },
    )
}

@Composable
private fun PrivilegeServiceInfoDialog(onDismissRequest: () -> Unit) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "特权服务")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "此页面用于启动和管理特权服务。连接后，可为 GKD 提供自动化、必要权限授予等需要系统级能力的功能；断开后，依赖特权服务的功能将不可用。",
                )
                Text(
                    text = buildAnnotatedString {
                        append("特权服务基于开源项目 ")
                        withLink(
                            LinkAnnotation.Url(
                                url = "https://github.com/priv-kit/priv-kit",
                                styles = linkStyles,
                            ),
                        ) {
                            append("priv-kit")
                        }
                        append(" (自有特权运行时) 实现不依赖外部授权器提权")
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "我知道了")
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
    val showInfoDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setInfoDialogVisible(visible: Boolean) {
        showInfoDialogFlow.value = visible
    }

    override fun onBackClick(): Boolean {
        backAction()
        return true
    }
}
