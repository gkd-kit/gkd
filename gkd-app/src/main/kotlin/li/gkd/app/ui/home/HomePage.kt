package li.gkd.app.ui.home

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.share.LocalMainViewModel

sealed class BottomNavItem(
    val key: Int,
    val label: String,
    val icon: ImageVector,
) {
    object Dashboard : BottomNavItem(
        key = 0,
        label = "首页",
        icon = PerfIcon.Home,
    )

    object SubsManage : BottomNavItem(
        key = 1,
        label = "订阅",
        icon = PerfIcon.FormatListBulleted,
    )

    object AppList : BottomNavItem(
        key = 2,
        label = "应用",
        icon = PerfIcon.Apps,
    )

    object Settings : BottomNavItem(
        key = 3,
        label = "设置",
        icon = PerfIcon.Settings,
    )

    companion object {
        val allSubObjects by lazy { arrayOf(Dashboard, SubsManage, AppList, Settings) }
    }
}

@Serializable
data object HomeRoute : NavKey

@Composable
fun ResetPageScrollOnRequest(
    navItem: BottomNavItem,
    resetScroll: suspend () -> Unit,
) {
    val mainVm = LocalMainViewModel.current
    val request by mainVm.pageScrollResetRequestFlow.collectAsStateWithLifecycle()
    val currentRequest = request
    LaunchedEffect(currentRequest) {
        if (currentRequest?.navItem == navItem) {
            resetScroll()
            mainVm.consumePageScrollResetRequest(currentRequest)
        }
    }
}

@Composable
fun HomePage() {
    val mainVm = LocalMainViewModel.current
    viewModel<SubsManageVm>()
    val tab by mainVm.tabFlow.collectAsStateWithLifecycle()
    val selectedTab = BottomNavItem.allSubObjects.find { it.key == tab }
        ?: BottomNavItem.Dashboard
    val saveableStateHolder = rememberSaveableStateHolder()

    saveableStateHolder.SaveableStateProvider(selectedTab.key) {
        val page = when (selectedTab) {
            BottomNavItem.Dashboard -> useDashboardPage()
            BottomNavItem.SubsManage -> useSubsManagePage()
            BottomNavItem.AppList -> useAppListPage()
            BottomNavItem.Settings -> useSettingsPage()
        }
        Scaffold(
            modifier = page.modifier,
            topBar = page.topBar,
            floatingActionButton = page.floatingActionButton,
            bottomBar = {
                NavigationBar {
                    BottomNavItem.allSubObjects.forEach { navItem ->
                        NavigationBarItem(
                            selected = navItem == selectedTab,
                            modifier = Modifier,
                            onClick = { mainVm.handleClickTab(navItem) },
                            icon = {
                                PerfIcon(
                                    imageVector = navItem.icon,
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(text = navItem.label)
                            },
                        )
                    }
                }
            },
            content = page.content,
        )
    }
}
