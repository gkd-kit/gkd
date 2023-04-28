package li.songe.gkd.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import li.songe.gkd.ui.component.StatusBar
import li.songe.gkd.util.ModifierExt.noRippleClickable
import li.songe.router.Page

val HomePage = Page {
    var tabInt by remember { mutableStateOf(0) }
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
                        SubscriptionManagePage()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (tabInt == 1) 1f else 0f)
                            .zIndex(if (tabInt == 1) 1f else 0f)
                            .noRippleClickable { }) {
                        SettingsPage()
                    }
                }
            }
        )
    }
}