package li.gkd.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.ui.MainViewModel

@Composable
fun HomePage() {
    val navigator = MainViewModel.requireCurrent().navigator
    val vm = viewModel { HomeViewModel() }
    DisposableEffect(vm, navigator) {
        navigator.bindHomeState(vm.homeState)
        onDispose { navigator.unbindHomeState(vm.homeState) }
    }
    val latest = vm.latestState.collectAsStateWithLifecycle()
    val apps = vm.appsState.collectAsStateWithLifecycle()
    val subscriptions = vm.subscriptionsState.collectAsStateWithLifecycle()
    GkHomeHost(vm.homeState) { tab ->
        when (tab) {
            BottomNavItem.Dashboard -> dashboardPage(vm, latest.value)
            BottomNavItem.AppList -> appListPage(vm, apps.value)
            BottomNavItem.SubsManage -> subsManagePage(vm, subscriptions.value)
            BottomNavItem.Settings -> settingsPage(vm)
        }
    }
}
