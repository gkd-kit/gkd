package li.songe.gkd.ui.home

import android.webkit.URLUtil
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.MainActivity
import li.songe.gkd.util.ProfileTransitions

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
)

@RootNavGraph(start = true)
@Destination(style = ProfileTransitions::class)
@Composable
fun HomePage() {
    val context = LocalContext.current as MainActivity
    val vm = hiltViewModel<HomeVm>()
    val tab by vm.tabFlow.collectAsState()

    val intent = context.intent
    LaunchedEffect(key1 = intent, block = {
        if (intent != null) {
            context.intent = null
            val data = intent.data
            val url = data?.getQueryParameter("url")
            if (data?.scheme == "gkd" && data.host == "import" && URLUtil.isNetworkUrl(url)) {
                LogUtils.d(data, url)
            }
        }
    })

    val appListPage = useAppListPage()
    val subsPage = useSubsManagePage()
    val controlPage = useControlPage()
    val settingsPage = useSettingsPage()

    val pages = arrayOf(appListPage, subsPage, controlPage, settingsPage)

    val currentPage = pages.find { p -> p.navItem === tab }
        ?: controlPage

    Scaffold(
        modifier = currentPage.modifier,
        topBar = currentPage.topBar,
        floatingActionButton = currentPage.floatingActionButton,
        bottomBar = {
            NavigationBar {
                pages.forEach { page ->
                    NavigationBarItem(
                        selected = tab == page.navItem,
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