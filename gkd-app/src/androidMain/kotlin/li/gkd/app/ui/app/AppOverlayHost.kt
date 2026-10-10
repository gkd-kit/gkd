package li.gkd.app.ui.app

import androidx.compose.runtime.getValue
import li.gkd.app.util.copyText

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.priv.AutomationService
import li.gkd.app.priv.uiAutomationOccupiedFlow
import li.gkd.app.resources.*
import li.gkd.app.service.A11yService
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.component.GkPlatformWarningDialogs
import li.gkd.app.ui.component.GkTermsAcceptDialog
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.ToastUtils

@Composable
fun AppOverlayHost() {
    val activity = LocalActivity.current as li.gkd.app.MainActivity
    val mainVm = activity.mainVm
    if (!SettingsRepository.termsAccepted.collectAsStateWithLifecycle().value) {
        GkTermsAcceptDialog(
            onAccept = SettingsRepository::acceptTerms,
            onError = { ToastUtils.show(it.message ?: it.toString()) },
            onDisagree = { activity.finish() },
        )
    } else {
        // Sheet
        mainVm.subsSheet.Render()

        // Dialog
        PlatformWarnings()
        mainVm.dialogRequests.Render()
        mainVm.githubUpload.Render(onCopy = ::copyText)
        mainVm.updateStatus?.UpgradeDialog()
        mainVm.subsLinkDialog.Render()
        mainVm.ruleGroupState.Render(
            navigator = mainVm.navigator, openSubscription = mainVm.subsSheet::show,
            ruleControl = mainVm.ruleControlDialog, confirmDelete = mainVm::confirmDelete,
        )
        mainVm.ruleControlDialog.Render()
        mainVm.textDialog.Render(onCopy = ::copyText)
        mainVm.shareLog.Render(activity)
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
private fun PlatformWarnings() {
    val mainVm = (androidx.activity.compose.LocalActivity.current as li.gkd.app.MainActivity).mainVm
    val a11y by A11yService.isRunning.collectAsStateWithLifecycle()
    val restricted by accessRestrictedSettingsShowFlow.collectAsStateWithLifecycle()
    val occupied by uiAutomationOccupiedFlow.collectAsStateWithLifecycle()
    GkPlatformWarningDialogs(
        restricted,
        a11y,
        mainVm.navigator.topRoute is PrivilegeServiceRoute,
        occupied,
        ::dismissAccessRestrictedSettingsDialog,
        { mainVm.navigator.navigate(PrivilegeServiceRoute) },
        { ToastUtils.show(Res.string.permission_reauthorize_to_unrestrict.getSync()) },
        AutomationService::dismissOccupiedWarning
    )
}
