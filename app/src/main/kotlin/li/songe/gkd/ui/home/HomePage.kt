package li.songe.gkd.ui.home

import androidx.compose.material3.Icon
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
import li.songe.gkd.util.LocalMainViewModel
import li.songe.gkd.util.ProfileTransitions

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
)

@Destination<RootGraph>(style = ProfileTransitions::class, start = true)
@Composable
fun HomePage() {
    val mainVm = LocalMainViewModel.current
    viewModel<HomeVm>()
    val tab by mainVm.tabFlow.collectAsState()

    val controlPage = useControlPage()
    val subsPage = useSubsManagePage()
    val appListPage = useAppListPage()
    val settingsPage = useSettingsPage()

    val pages = arrayOf(controlPage, subsPage, appListPage, settingsPage)

    val currentPage = pages.find { p -> p.navItem.label == tab.label } ?: controlPage

    Scaffold(
        modifier = currentPage.modifier,
        topBar = currentPage.topBar,
        floatingActionButton = currentPage.floatingActionButton,
        bottomBar = {
            NavigationBar {
                pages.forEach { page ->
                    NavigationBarItem(
                        selected = tab.label == page.navItem.label,
                        modifier = Modifier,
                        onClick = {
                            mainVm.updateTab(page.navItem)
                        },
                        icon = {
                            Icon(
                                imageVector = page.navItem.icon,
                                contentDescription = page.navItem.label
                            )
                        },
                        label = {
                            Text(text = page.navItem.label)
                        })
                }
            }
        },
        content = currentPage.content
    )
}
