package li.songe.gkd.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp


//@Composable
//fun NavHostContainer(
//    tabInt: Int,
//    padding:PaddingValues,
//) {
//    when(tabInt)
//    NavHost(
//        navController = navController,
//        startDestination = "statistics",
//        modifier = Modifier.padding(paddingValues = padding),
//        builder = {
//            composable("native") {
//                NativePage()
////                BackHandler(false) {}
//            }
//            composable("settings") {
//                SettingsPage()
//            }
//            composable("statistics") {
//                StatisticsPage()
//            }
//            composable("subscription") {
//                SubscriptionPage()
//            }
//        }
//    )
//}

@Composable
fun BottomNavigationBar(tabInt: Int, onTabChange: ((Int) -> Unit)? = null) {
    BottomNavigation(
        backgroundColor = Color.Transparent,
        elevation = 0.dp
    ) {
        BottomNavItems.forEachIndexed { i, navItem ->
            BottomNavigationItem(
                selected = i == tabInt,
                modifier = Modifier.background(Color.Transparent),
                onClick = {
                    onTabChange?.invoke(i)
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
                }
            )
        }
    }
}
