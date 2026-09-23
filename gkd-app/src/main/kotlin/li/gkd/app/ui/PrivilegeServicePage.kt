package li.gkd.app.ui

import li.gkd.app.MainViewModel

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
import li.gkd.app.text.UiStrings
import li.gkd.app.priv.gkdPrivilegeUiConfig
import li.gkd.app.util.TimeUtils.throttle
import priv.kit.ui.PrivilegeScaffold
import priv.kit.ui.PrivilegeUiViewModel
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTopAppBar

@Serializable
data object PrivilegeServiceRoute : NavKey

@Composable
fun PrivilegeServicePage() {
    val mainVm = MainViewModel.requireCurrent()
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
            GkTopAppBar(
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = mainVm::popPage,
                    )
                },
                title = {
                    Text(text = UiStrings.privilege_service)
                },
                actions = {
                    GkIconButton(
                        imageVector = GkIcons.Info,
                        contentDescription = UiStrings.page_help,
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
    GkAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = UiStrings.privilege_service)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = UiStrings.privilege_service_help_description,
                )
                Text(
                    text = buildAnnotatedString {
                        append(UiStrings.privilege_service_project_prefix)
                        withLink(
                            LinkAnnotation.Url(
                                url = "https://github.com/priv-kit/priv-kit",
                                styles = linkStyles,
                            ),
                        ) {
                            append(UiStrings.privilege_project_name)
                        }
                        append(UiStrings.privilege_service_project_suffix)
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = UiStrings.action_understood)
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
