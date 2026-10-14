package li.gkd.app.ui.component

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

@Composable
actual fun Modifier.gkTopAppBarScroll(behavior: TopAppBarScrollBehavior): Modifier {
    val modifier = nestedScroll(behavior.nestedScrollConnection)
    if (behavior.isPinned) return modifier

    // A child at its scroll boundary rejects wheel input before dispatching nested scroll.
    // Receive that input here so the existing app bar connection can consume it.
    val state = rememberScrollableState { 0f }
    val appBarState = remember(state, behavior) {
        object : ScrollableState by state {
            override val canScrollForward: Boolean
                get() = behavior.state.heightOffset < 0f
            override val canScrollBackward: Boolean
                get() = behavior.state.heightOffset > behavior.state.heightOffsetLimit
        }
    }
    return modifier.scrollable(appBarState, Orientation.Vertical)
}
