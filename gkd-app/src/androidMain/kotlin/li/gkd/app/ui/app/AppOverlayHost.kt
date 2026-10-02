package li.gkd.app.ui.app

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.priv.AutomationService
import li.gkd.app.priv.uiAutomationOccupiedFlow
import li.gkd.app.resources.Res
import li.gkd.app.resources.permission_reauthorize_to_unrestrict
import li.gkd.app.service.A11yService
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkPlatformWarningDialogs
import li.gkd.app.ui.component.GkTermsAcceptDialog
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.ToastUtils

@Composable
fun AppOverlayHost() {
    val mainVm = MainViewModel.requireCurrent()
    val activity = LocalActivity.current
    if (!SettingsRepository.termsAccepted.collectAsStateWithLifecycle().value) {
        GkTermsAcceptDialog(
            onAccept = SettingsRepository::acceptTerms,
            onError = { ToastUtils.show(it.message ?: it.toString()) },
            onDisagree = { activity?.finish() },
        )
    } else {
        // Sheet
        mainVm.subsSheet.Render()

        // Dialog
        PlatformWarnings()
        mainVm.dialogRequests.Render()
        mainVm.githubUpload.Render()
        mainVm.updateStatus?.UpgradeDialog()
        mainVm.subsLinkDialog.Render()
        mainVm.ruleGroupState.Render(
            onNavigate = { mainVm.navigatePage(it) }, showToast = ToastUtils::show,
            topRoute = { mainVm.topRoute }, openSubscription = mainVm.subsSheet::show,
            ruleControl = mainVm.ruleControlDialog, confirmDelete = mainVm::confirmDelete,
            copyText = { li.gkd.app.ui.navigation.copyText(it, ToastUtils::show) },
        )
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
private fun PlatformWarnings() {
    val mainVm = MainViewModel.requireCurrent()
    val a11y by A11yService.isRunning.collectAsStateWithLifecycle()
    val restricted by accessRestrictedSettingsShowFlow.collectAsStateWithLifecycle()
    val occupied by uiAutomationOccupiedFlow.collectAsStateWithLifecycle()
    GkPlatformWarningDialogs(
        restricted,
        a11y,
        mainVm.topRoute is PrivilegeServiceRoute,
        occupied,
        ::dismissAccessRestrictedSettingsDialog,
        { mainVm.navigatePage(PrivilegeServiceRoute) },
        { ToastUtils.show(Res.string.permission_reauthorize_to_unrestrict.getSync()) },
        AutomationService::dismissOccupiedWarning
    )
}
