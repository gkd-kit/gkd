package li.gkd.app.ui.home

import androidx.compose.runtime.Composable
import li.gkd.app.network.AppLinks
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.GkBackHandler
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.settings.appVersion
import li.gkd.app.ui.subscription.SubsLinkDialogState
import li.gkd.app.ui.subscription.SubsSheetState

@Composable
fun HomePage(
    window: AppWindow,
    vm: HomeViewModel,
    onNavigate: (AppRoute) -> Unit,
    toast: (String) -> Unit,
    dialogs: DialogRequests,
    subsSheet: SubsSheetState,
    subsLinks: SubsLinkDialogState,
) {
    GkHomeHost(vm.homeState) { tab ->
        when (tab) {
            BottomNavItem.Dashboard -> dashboardPage(window, vm, onNavigate)
            BottomNavItem.AppList -> appListPage(window, vm, onNavigate, toast, dialogs)
            BottomNavItem.SubsManage ->
                subsManagePage(
                    vm,
                    vm.homeState,
                    SubsManageHost(
                        window.appVersion().appName,
                        onNavigate,
                        subsSheet::show,
                        { subsLinks.request() },
                        { title, text, error -> dialogs.confirm(title, text, error = error) },
                        toast,
                        { onNavigate(WebViewRoute(AppLinks.BackgroundRunningHelp)) },
                    ),
                    selectionBackHandler = { enabled, back -> GkBackHandler(enabled, back) },
                )

            BottomNavItem.Settings -> settingsPage(window, vm, onNavigate, toast, dialogs)
        }
    }
}
