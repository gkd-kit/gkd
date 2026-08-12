package li.songe.gkd.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.priv.gkdPrivilegeUiConfig
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
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
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    val privilegeVm = viewModel {
        GkdPrivilegeUiViewModel(application) {
            mainVm.popPage()
        }
    }
    if (showInfoDialog) {
        PrivilegeServiceInfoDialog(
            onDismissRequest = { showInfoDialog = false },
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
                            showInfoDialog = true
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
    AlertDialog(
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
    override fun onBackClick(): Boolean {
        backAction()
        return true
    }
}
