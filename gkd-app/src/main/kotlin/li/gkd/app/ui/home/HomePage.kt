package li.gkd.app.ui.home

import li.gkd.app.MainViewModel

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
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons

sealed class BottomNavItem(
    val key: Int,
    val label: String,
    val icon: ImageVector,
) {
    object Dashboard : BottomNavItem(
        key = 0,
        label = UiStrings.home_title,
        icon = GkIcons.Home,
    )

    object SubsManage : BottomNavItem(
        key = 1,
        label = UiStrings.subscription_title,
        icon = GkIcons.StackedDocuments,
    )

    object AppList : BottomNavItem(
        key = 2,
        label = UiStrings.apps_title,
        icon = GkIcons.Android,
    )

    object Settings : BottomNavItem(
        key = 3,
        label = UiStrings.settings_title,
        icon = GkIcons.Settings,
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
    val mainVm = MainViewModel.requireCurrent()
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
    val mainVm = MainViewModel.requireCurrent()
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
                                GkIcon(
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
