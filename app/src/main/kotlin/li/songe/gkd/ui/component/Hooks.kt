package li.songe.gkd.ui.component

import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.util.mapState
import li.songe.gkd.util.subsMapFlow

@Composable
fun useSubs(subsId: Long?): RawSubscription? {
    val scope = rememberCoroutineScope()
    return remember(subsId) { subsMapFlow.mapState(scope) { it[subsId] } }.collectAsState().value
}

@Composable
fun useSubsGroup(
    subs: RawSubscription?,
    groupKey: Int?,
    appId: String?,
): RawSubscription.RawGroupProps? {
    return remember(subs, groupKey, appId) {
        if (subs != null && groupKey != null) {
            if (appId != null) {
                subs.apps.find { it.id == appId }?.groups?.find { it.key == groupKey }
            } else {
                subs.globalGroups.find { it.key == groupKey }
            }
        } else {
            null
        }
    }
}

@Composable
fun Modifier.autoFocus(immediateFocus: Boolean = false): Modifier {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(null) {
        if (!immediateFocus) {
            delay(DefaultDurationMillis.toLong())
        }
        focusRequester.requestFocus()
    }
    return focusRequester(focusRequester)
}

@Composable
private fun getCompatStateValue(v: Any?): Any? = when (v) {
    is StateFlow<*> -> v.collectAsState().value
    is androidx.compose.runtime.State<*> -> v.value
    else -> v
}

@Composable
fun useListScrollState(
    v1: Any?,
    v2: Any? = null,
    v3: Any? = null,
    canScroll: () -> Boolean = { true },
): Pair<TopAppBarScrollBehavior, LazyListState> {
    val x1 = getCompatStateValue(v1)
    val x2 = getCompatStateValue(v2)
    val x3 = getCompatStateValue(v3)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        state = rememberSaveable(x1, x2, x3, saver = TopAppBarState.Saver) {
            TopAppBarState(-Float.MAX_VALUE, 0f, 0f)
        },
        canScroll = canScroll
    )
    val scrollState = rememberSaveable(x1, x2, x3, saver = LazyListState.Saver) {
        LazyListState(0, 0)
    }
    return scrollBehavior to scrollState
}

@Composable
fun usePinnedScrollBehaviorState(v1: Any?): Pair<TopAppBarScrollBehavior, LazyListState> {
    val x1 = getCompatStateValue(v1)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        state = rememberSaveable(x1, saver = TopAppBarState.Saver) {
            TopAppBarState(-Float.MAX_VALUE, 0f, 0f)
        },
    )
    val scrollState = rememberSaveable(x1, saver = LazyListState.Saver) {
        LazyListState(0, 0)
    }
    return scrollBehavior to scrollState
}

@Composable
fun useScrollBehaviorState(v1: Any?): Pair<TopAppBarScrollBehavior, ScrollState> {
    val x1 = getCompatStateValue(v1)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        state = rememberSaveable(x1, saver = TopAppBarState.Saver) {
            TopAppBarState(-Float.MAX_VALUE, 0f, 0f)
        },
    )
    val scrollState = rememberSaveable(x1, saver = ScrollState.Saver) { ScrollState(initial = 0) }
    return scrollBehavior to scrollState
}

@Composable
fun LazyListState.isAtBottom(): androidx.compose.runtime.State<Boolean> = remember(this) {
    derivedStateOf {
        val visibleItemsInfo = layoutInfo.visibleItemsInfo
        if (layoutInfo.totalItemsCount == 0) {
            false
        } else {
            val lastVisibleItem = visibleItemsInfo.last()
            val viewportHeight = layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset
            (lastVisibleItem.index + 1 == layoutInfo.totalItemsCount &&
                    lastVisibleItem.offset + lastVisibleItem.size <= viewportHeight)
        }
    }
}


val TopAppBarScrollBehavior.isFullVisible: Boolean
    @Composable
    @ReadOnlyComposable
    get() = state.collapsedFraction == 0f

@Composable
@ReadOnlyComposable
fun Modifier.textSize(
    style: TextStyle = LocalTextStyle.current,
    density: Density = LocalDensity.current,
): Modifier {
    val fontSizeDp = density.run { style.fontSize.toDp() }
    val lineHeightDp = density.run { style.lineHeight.toDp() }
    return height(lineHeightDp).width(fontSizeDp)
}