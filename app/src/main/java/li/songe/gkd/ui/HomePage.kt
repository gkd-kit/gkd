package li.songe.gkd.ui

import android.app.Activity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.getImportUrl

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
    val context = LocalContext.current as Activity
    val vm = hiltViewModel<HomePageVm>()
    val tab by vm.tabFlow.collectAsState()
    val intent by vm.intentFlow.collectAsState()
    LaunchedEffect(key1 = Unit, block = {
        vm.intentFlow.value = context.intent
    })
    LaunchedEffect(intent, block = {
        if (getImportUrl(intent) != null) {
            vm.tabFlow.value = subsNav
        }
    })

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