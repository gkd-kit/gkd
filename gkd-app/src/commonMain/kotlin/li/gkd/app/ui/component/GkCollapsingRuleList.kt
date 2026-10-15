package li.gkd.app.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/** Collapses page controls above fixed tabs; the list keeps its own sticky section headers. */
@Composable
fun GkCollapsingRuleList(
    headerState: TopAppBarState,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    tabs: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = headerState,
        snapAnimationSpec = null,
    )
    Column(modifier.fillMaxSize().gkTopAppBarScroll(scrollBehavior)) {
        Column(
            Modifier.clipToBounds().layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                headerState.heightOffsetLimit = -placeable.height.toFloat()
                // Reapply the offset so its setter clamps it to the new header height.
                headerState.heightOffset = headerState.heightOffset
                val offset = headerState.heightOffset.roundToInt()
                layout(placeable.width, placeable.height + offset) {
                    placeable.placeRelative(0, offset)
                }
            }.animateContentSize()
                .scrollable(rememberScrollableState { 0f }, Orientation.Vertical),
        ) {
            header()
        }
        tabs()
        Box(Modifier.weight(1f)) {
            content()
        }
    }
}
