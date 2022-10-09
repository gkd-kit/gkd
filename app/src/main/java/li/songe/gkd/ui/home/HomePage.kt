package li.songe.gkd.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import li.songe.gkd.router.Page
import li.songe.gkd.router.Router
import li.songe.gkd.ui.component.StatusBar
import li.songe.gkd.ui.home.page.NativePage
import li.songe.gkd.ui.home.page.SettingsPage
import li.songe.gkd.ui.home.page.StatisticsPage
import li.songe.gkd.ui.home.page.SubscriptionManagePage
import li.songe.gkd.util.ModifierExt.noRippleClickable


object HomePage : Page<Unit, Unit> {
    override val path = "HomePage"
    override val defaultParams = Unit
    override val content: @Composable BoxScope.(
        params: Unit,
        router: Router<Unit>
    ) -> Unit = @Composable { _, _ ->
        var tabInt by remember { mutableStateOf(2) }
        Column(modifier = Modifier.fillMaxSize()) {
            StatusBar()
            Scaffold(
                bottomBar = { BottomNavigationBar(tabInt) { tabInt = it } },
                content = { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (tabInt == 0) 1f else 0f)
                                .zIndex(if (tabInt == 0) 1f else 0f)
                                .noRippleClickable { }) {
                            StatisticsPage()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (tabInt == 1) 1f else 0f)
                                .zIndex(if (tabInt == 1) 1f else 0f)
                                .noRippleClickable { }) {
                            NativePage()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (tabInt == 2) 1f else 0f)
                                .zIndex(if (tabInt == 2) 1f else 0f)
                                .noRippleClickable { }) {
                            SubscriptionManagePage()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (tabInt == 3) 1f else 0f)
                                .zIndex(if (tabInt == 3) 1f else 0f)
                                .noRippleClickable { }) {
                            SettingsPage()
                        }
                    }
                }
            )
        }
    }
}