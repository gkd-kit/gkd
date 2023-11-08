package li.songe.gkd.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.util.ProfileTransitions

val BottomNavItems = listOf(
    subsNav, controlNav, settingsNav
)

data class BottomNavItem(
    val label: String,
    @DrawableRes val icon: Int,
    val route: String,
)

@RootNavGraph(start = true)
@Destination(style = ProfileTransitions::class)
@Composable
fun HomePage() {
    val vm = hiltViewModel<HomePageVm>()
    val tab by vm.tabFlow.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = {
            Text(
                text = tab.label,
            )
        })
    }, bottomBar = {
        NavigationBar {
            BottomNavItems.forEach { navItem ->
                NavigationBarItem(selected = tab == navItem, modifier = Modifier, onClick = {
                    vm.tabFlow.value = navItem
                }, icon = {
                    Icon(
                        painter = painterResource(id = navItem.icon),
                        contentDescription = navItem.label
                    )
                }, label = {
                    Text(text = navItem.label)
                })
            }
        }
    }, content = { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                subsNav -> SubsManagePage()
                controlNav -> ControlPage()
                settingsNav -> SettingsPage()
            }
        }
    })
}