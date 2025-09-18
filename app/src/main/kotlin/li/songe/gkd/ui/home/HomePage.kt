package li.songe.gkd.ui.home

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.ProfileTransitions

sealed class BottomNavItem(
    val key: Int,
    val label: String,
    val icon: ImageVector,
) {
    object Control : BottomNavItem(
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
        val allSubObjects by lazy { arrayOf(Control, SubsManage, AppList, Settings) }
    }
}

@Destination<RootGraph>(style = ProfileTransitions::class, start = true)
@Composable
fun HomePage() {
    val mainVm = LocalMainViewModel.current
    viewModel<HomeVm>() // init state
    val tab by mainVm.tabFlow.collectAsState()
    val pages = arrayOf(useControlPage(), useSubsManagePage(), useAppListPage(), useSettingsPage())
    val page = pages.find { p -> p.navItem.key == tab } ?: pages.first()

    Scaffold(
        modifier = page.modifier,
        topBar = page.topBar,
        floatingActionButton = page.floatingActionButton,
        bottomBar = {
            NavigationBar {
                pages.forEach { page ->
                    NavigationBarItem(
                        selected = page.navItem.key == tab,
                        modifier = Modifier,
                        onClick = {
                            mainVm.updateTab(page.navItem)
                        },
                        icon = {
                            PerfIcon(
                                imageVector = page.navItem.icon,
                            )
                        },
                        label = {
                            Text(text = page.navItem.label)
                        })
                }
            }
        },
        content = page.content
    )
}
