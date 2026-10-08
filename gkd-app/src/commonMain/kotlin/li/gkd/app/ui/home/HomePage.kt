package li.gkd.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.platform.UiHost

@Composable
fun HomePage(host: UiHost) {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { HomeViewModel() }
    DisposableEffect(vm, mainVm.homeNavigation) {
        mainVm.homeNavigation.bind(vm.homeState)
        onDispose { mainVm.homeNavigation.unbind(vm.homeState) }
    }
    GkHomeHost(vm.homeState) { tab ->
        when (tab) {
            BottomNavItem.Dashboard -> dashboardPage(host, vm)
            BottomNavItem.AppList -> appListPage(host, vm)
            BottomNavItem.SubsManage -> subsManagePage(vm)
            BottomNavItem.Settings -> settingsPage(host, vm)
        }
    }
}
