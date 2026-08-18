package li.songe.gkd.ui.app

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.priv.uiAutomationOccupiedFlow
import li.songe.gkd.service.A11yService
import li.songe.gkd.ui.PrivilegeServiceRoute
import li.songe.gkd.ui.component.AppAlertDialog
import li.songe.gkd.ui.component.TermsAcceptDialog
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.util.toast

@Composable
fun AppOverlayHost() {
    val mainVm = LocalMainViewModel.current
    if (!mainVm.termsAcceptedFlow.collectAsStateWithLifecycle().value) {
        TermsAcceptDialog()
    } else {
        UiAutomationAlreadyRegisteredDlg()
        AccessRestrictedSettingsDlg()
        mainVm.dialogRequests.Render()
        mainVm.githubUpload.Render()
        mainVm.updateStatus?.UpgradeDialog()
        mainVm.subsSheet.Render()
        mainVm.subsLinkDialog.Render()
        mainVm.ruleGroupState.Render()
        mainVm.textDialog.Render()
        mainVm.shareLog.Render()
    }
}

val accessRestrictedSettingsShowFlow = MutableStateFlow(false)

@Composable
private fun AccessRestrictedSettingsDlg() {
    val a11yRunning by A11yService.isRunning.collectAsStateWithLifecycle()
    LaunchedEffect(a11yRunning) {
        if (a11yRunning) {
            accessRestrictedSettingsShowFlow.value = false
        }
    }
    val accessRestrictedSettingsShow by accessRestrictedSettingsShowFlow.collectAsStateWithLifecycle()
    val mainVm = LocalMainViewModel.current
    val isPrivilegeServicePage = mainVm.topRoute is PrivilegeServiceRoute
    LaunchedEffect(isPrivilegeServicePage, accessRestrictedSettingsShow) {
        if (isPrivilegeServicePage && accessRestrictedSettingsShow && !a11yRunning) {
            toast("请重新授权以解除限制")
            accessRestrictedSettingsShowFlow.value = false
        }
    }
    if (accessRestrictedSettingsShow && !isPrivilegeServicePage && !a11yRunning) {
        AppAlertDialog(
            title = {
                Text(text = "权限受限")
            },
            text = {
                Text(text = "当前操作权限「访问受限设置」已被限制，请前往特权服务重新授权")
            },
            onDismissRequest = {
                accessRestrictedSettingsShowFlow.value = false
            },
            confirmButton = {
                TextButton({
                    accessRestrictedSettingsShowFlow.value = false
                    mainVm.navigatePage(PrivilegeServiceRoute)
                }) {
                    Text(text = "前往授权")
                }
            },
            dismissButton = {
                TextButton({
                    accessRestrictedSettingsShowFlow.value = false
                }) {
                    Text(text = "关闭")
                }
            },
        )
    }
}

@Composable
private fun UiAutomationAlreadyRegisteredDlg() {
    if (uiAutomationOccupiedFlow.collectAsStateWithLifecycle().value) {
        AppAlertDialog(
            onDismissRequest = {
                uiAutomationOccupiedFlow.value = false
            },
            title = { Text(text = "启动失败") },
            text = {
                Text(text = "自动化服务启动失败，检测到自动化服务已被其他应用占用，请先关闭已有服务后重试\n\n注：自动化服务只能同时运行一个，请确保没有其他应用或测试框架占用后再启动")
            },
            confirmButton = {
                TextButton(onClick = {
                    uiAutomationOccupiedFlow.value = false
                }) {
                    Text(text = "我知道了")
                }
            }
        )
    }
}
