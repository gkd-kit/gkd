package li.songe.gkd.ui.home

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AdvancedPageDestination
import com.ramcosta.composedestinations.generated.destinations.SnapshotPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import li.songe.gkd.MainActivity
import li.songe.gkd.OpenFileActivity
import li.songe.gkd.data.importData
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.toast

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
)

@Destination<RootGraph>(style = ProfileTransitions::class, start = true)
@Composable
fun HomePage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = viewModel<HomeVm>()
    val tab by vm.tabFlow.collectAsState()

    val controlPage = useControlPage()
    val subsPage = useSubsManagePage()
    val appListPage = useAppListPage()
    val settingsPage = useSettingsPage()

    val pages = arrayOf(controlPage, subsPage, appListPage, settingsPage)

    val currentPage = pages.find { p -> p.navItem.label == tab.label } ?: controlPage

    LaunchedEffect(key1 = null, block = {
        val intent = context.intent ?: return@LaunchedEffect
        context.intent = null
        LogUtils.d(intent)
        val uri = intent.data?.normalizeScheme() ?: return@LaunchedEffect
        val source = intent.getStringExtra("source")
        if (source == OpenFileActivity::class.qualifiedName) {
            vm.viewModelScope.launchTry(Dispatchers.IO) {
                toast("加载导入...")
                vm.tabFlow.value = subsPage.navItem
                importData(uri)
            }
        } else if (uri.scheme == "gkd" && uri.host == "page") {
            delay(200)
            when (uri.path) {
                "/1" -> {
                    navController.toDestinationsNavigator().navigate(AdvancedPageDestination)
                }

                "/2" -> {
                    navController.toDestinationsNavigator().navigate(SnapshotPageDestination)
                }
            }
        }
    })

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
                            vm.tabFlow.value = page.navItem
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
