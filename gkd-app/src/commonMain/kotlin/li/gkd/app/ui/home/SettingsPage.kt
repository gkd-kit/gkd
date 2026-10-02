package li.gkd.app.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.rememberColumnScrollState

@Composable
fun settingsPage(
    bindScroll: @Composable (suspend () -> Unit) -> Unit = {},
    content: @Composable (PaddingValues, ScrollState) -> Unit,
): ScaffoldExt {
    val scroll = rememberColumnScrollState()
    bindScroll(scroll::resetScrollAndAwait)
    return ScaffoldExt(
        BottomNavItem.Settings,
        modifier = Modifier.nestedScroll(scroll.scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scroll.scrollBehavior,
                title = { Text(BottomNavItem.Settings.label) })
        },
        content = { content(it, scroll.scrollState) },
    )
}
