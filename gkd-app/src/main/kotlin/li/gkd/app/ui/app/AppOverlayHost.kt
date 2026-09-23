package li.gkd.app.ui.app

import li.gkd.app.MainViewModel

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.text.UiStrings
import li.gkd.app.priv.AutomationService
import li.gkd.app.priv.uiAutomationOccupiedFlow
import li.gkd.app.service.A11yService
import li.gkd.app.ui.PrivilegeServiceRoute
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkTermsAcceptDialog
import li.gkd.app.util.ToastUtils.toast

@Composable
fun AppOverlayHost() {
    val mainVm = MainViewModel.requireCurrent()
    if (!mainVm.termsAcceptedFlow.collectAsStateWithLifecycle().value) {
        GkTermsAcceptDialog()
    } else {
        // Sheet
        mainVm.subsSheet.Render()

        // Dialog
        UiAutomationAlreadyRegisteredDlg()
        AccessRestrictedSettingsDlg()
        mainVm.dialogRequests.Render()
        mainVm.githubUpload.Render()
        mainVm.updateStatus?.UpgradeDialog()
        mainVm.subsLinkDialog.Render()
        mainVm.ruleGroupState.Render()
        mainVm.ruleControlDialog.Render()
        mainVm.textDialog.Render()
        mainVm.shareLog.Render()
    }
}

private val accessRestrictedSettingsShowFlow = MutableStateFlow(false)

fun showAccessRestrictedSettingsDialog() {
    accessRestrictedSettingsShowFlow.value = true
}

private fun dismissAccessRestrictedSettingsDialog() {
    accessRestrictedSettingsShowFlow.value = false
}

@Composable
private fun AccessRestrictedSettingsDlg() {
    val a11yRunning by A11yService.isRunning.collectAsStateWithLifecycle()
    LaunchedEffect(a11yRunning) {
        if (a11yRunning) {
            dismissAccessRestrictedSettingsDialog()
        }
    }
    val accessRestrictedSettingsShow by accessRestrictedSettingsShowFlow.collectAsStateWithLifecycle()
    val mainVm = MainViewModel.requireCurrent()
    val isPrivilegeServicePage = mainVm.topRoute is PrivilegeServiceRoute
    LaunchedEffect(isPrivilegeServicePage, accessRestrictedSettingsShow) {
        if (isPrivilegeServicePage && accessRestrictedSettingsShow && !a11yRunning) {
            toast(UiStrings.permission_reauthorize_to_unrestrict)
            dismissAccessRestrictedSettingsDialog()
        }
    }
    if (accessRestrictedSettingsShow && !isPrivilegeServicePage && !a11yRunning) {
        GkAlertDialog(
            title = {
                Text(text = UiStrings.permission_restricted)
            },
            text = {
                Text(text = UiStrings.restricted_settings_permission_description)
            },
            onDismissRequest = {
                dismissAccessRestrictedSettingsDialog()
            },
            confirmButton = {
                TextButton({
                    dismissAccessRestrictedSettingsDialog()
                    mainVm.navigatePage(PrivilegeServiceRoute)
                }) {
                    Text(text = UiStrings.permission_go_grant)
                }
            },
            dismissButton = {
                TextButton({
                    dismissAccessRestrictedSettingsDialog()
                }) {
                    Text(text = UiStrings.action_close)
                }
            },
        )
    }
}

@Composable
private fun UiAutomationAlreadyRegisteredDlg() {
    if (uiAutomationOccupiedFlow.collectAsStateWithLifecycle().value) {
        GkAlertDialog(
            onDismissRequest = {
                AutomationService.dismissOccupiedWarning()
            },
            title = { Text(text = UiStrings.startup_failed) },
            text = {
                Text(text = UiStrings.automation_service_occupied_description)
            },
            confirmButton = {
                TextButton(onClick = {
                    AutomationService.dismissOccupiedWarning()
                }) {
                    Text(text = UiStrings.action_understood)
                }
            }
        )
    }
}
