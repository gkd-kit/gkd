package li.gkd.app.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.navigation.GkAppNavigation
import li.gkd.app.ui.style.AppTheme

@Composable
fun AppRoot() {
    val mainVm = MainViewModel.requireCurrent()
    val activity = androidx.activity.compose.LocalActivity.current as li.gkd.app.MainActivity
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            GkAppNavigation(
                homeNavigation = mainVm.homeNavigation,
                subsSheet = mainVm.subsSheet,
                subsLinks = mainVm.subsLinkDialog,
                backStack = mainVm.backStack,
                onBack = mainVm::popPage,
                window = activity,
                onNavigate = { mainVm.navigatePage(it) },
                replaceRoute = { mainVm.navigatePage(it, true) },
                showToast = li.gkd.app.util.ToastUtils::show,
                showText = mainVm.textDialog::showText,
                topRoute = { mainVm.topRoute },
                updateStatus = mainVm.updateStatus,
                onExportLogs = mainVm.shareLog::show,
                takeCrashDataList = mainVm::takeCrashDataList,
                dialogs = mainVm.dialogRequests,
                ruleGroups = mainVm.ruleGroupState,
                githubUpload = mainVm.githubUpload,
                confirmDelete = mainVm::confirmDelete,
                scope = mainVm.scope,
            )
            AppOverlayHost()
            mainVm.permissionRequests.Render(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
            )
        }
    }
}
