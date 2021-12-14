package li.songe.gkd.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import li.songe.gkd.ui.page.NativePage
import li.songe.gkd.ui.page.SettingsPage
import li.songe.gkd.ui.page.StatisticsPage
import li.songe.gkd.ui.page.SubscriptionPage


@Composable
fun NavHostContainer(
    navController: NavHostController,
    padding: PaddingValues
) {
    NavHost(
        navController = navController,
        startDestination = "statistics",
        modifier = Modifier.padding(paddingValues = padding),
        builder = {
            composable("native") {
                NativePage()
            }
            composable("settings") {
                SettingsPage()
            }
            composable("statistics") {
                StatisticsPage()
            }
            composable("subscription") {
                SubscriptionPage()
            }
        }
    )
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    BottomNavigation(
        backgroundColor = Color.White,
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        LaunchedEffect(Unit){
            navController.navigate(BottomNavItems[1].route)
        }
        BottomNavItems.forEach { navItem ->
            BottomNavigationItem(
                selected = currentRoute == navItem.route,
                onClick = {
                    navController.navigate(navItem.route)
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
