package li.songe.gkd.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

val BottomNavItems = listOf(
    subsNav, controlNav, settingsNav
)

data class BottomNavItem(
    val label: String,
    @DrawableRes val icon: Int,
    val route: String,
)

@HiltViewModel
class HomePageVm @Inject constructor() : ViewModel() {
    val tabFlow = MutableStateFlow(controlNav)
}

@RootNavGraph(start = true)
@Destination
@Composable
fun HomePage() {
    val vm = hiltViewModel<HomePageVm>()
    val tab by vm.tabFlow.collectAsState()

    Scaffold(bottomBar = {
        BottomNavigation(
            backgroundColor = Color.Transparent, elevation = 0.dp
        ) {
            BottomNavItems.forEach { navItem ->
                BottomNavigationItem(selected = tab == navItem,
                    modifier = Modifier.background(Color.Transparent),
                    onClick = {
                        vm.tabFlow.value = navItem
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = navItem.icon),
                            contentDescription = navItem.label,
                            modifier = Modifier.padding(2.dp)
                        )
                    },
                    label = {
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