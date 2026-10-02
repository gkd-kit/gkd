package li.gkd.app.ui.home

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkScaffold

// A desktop device profile supplies Android's bottom inset; Android uses native defaults.
val LocalHomeNavigationInsets = staticCompositionLocalOf<WindowInsets?> { null }

@Composable
fun GkHomeScaffold(
    page: ScaffoldExt,
    selectedTab: BottomNavItem,
    onSelectTab: (BottomNavItem) -> Unit
) {
    GkScaffold(
        modifier = page.modifier, topBar = page.topBar,
        floatingActionButton = page.floatingActionButton,
        bottomBar = {
            NavigationBar(
                windowInsets = LocalHomeNavigationInsets.current
                    ?: NavigationBarDefaults.windowInsets
            ) {
                BottomNavItem.allSubObjects.forEach { item ->
                    NavigationBarItem(
                        selected = item == selectedTab, onClick = { onSelectTab(item) },
                        icon = { GkIcon(item.icon, contentDescription = null) },
                        label = { Text(item.label) })
                }
            }
        },
        content = page.content,
    )
}
