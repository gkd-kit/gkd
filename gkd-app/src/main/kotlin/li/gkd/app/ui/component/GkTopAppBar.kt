package li.gkd.app.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import li.gkd.app.MainActivity

@Composable
fun GkTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    canScroll: Boolean = true,
) {
    val actualScrollBehavior = if (canScroll || scrollBehavior == null) {
        scrollBehavior
    } else {
        remember(scrollBehavior) {
            object : TopAppBarScrollBehavior by scrollBehavior {
                // disable inner scroll effect
                override val isPinned: Boolean
                    get() = true
            }
        }
    }
    // SingleRowTopAppBar internally containerColor+scrolledContainerColor are composited into one animation
    // App theme color update creates a superimposed animation, causing a disjoint visual transition compared to surrounding normal components.
    key(MaterialTheme.colorScheme.primary) {
        TopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            expandedHeight = expandedHeight,
            windowInsets = (LocalActivity.current as MainActivity).topBarWindowInsets,
            colors = colors,
            scrollBehavior = actualScrollBehavior,
        )
    }
}
