package li.gkd.app.ui.component

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
import androidx.compose.runtime.Stable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import li.gkd.app.data.RawSubscription
import li.gkd.app.util.mapState
import li.gkd.app.util.subsMapFlow

@Composable
fun useSubs(subsId: Long?): RawSubscription? {
    val scope = rememberCoroutineScope()
    return remember(subsId) { subsMapFlow.mapState(scope) { it[subsId] } }.collectAsStateWithLifecycle().value
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

private fun TopAppBarScrollBehavior.resetScroll() {
    state.heightOffset = 0f
    state.contentOffset = 0f
}

@Stable
class ListScrollState(
    val scrollBehavior: TopAppBarScrollBehavior,
    val listState: LazyListState,
    private val coroutineScope: CoroutineScope,
) {
    private var resetJob: Job? = null

    private suspend fun performScrollReset() {
        scrollBehavior.resetScroll()
        listState.scrollToItem(0)
    }

    fun resetScroll() {
        resetJob?.cancel()
        resetJob = coroutineScope.launch {
            performScrollReset()
        }
    }

    suspend fun resetScrollAndAwait() {
        resetJob?.cancelAndJoin()
        performScrollReset()
    }

    @Composable
    fun ResetOnChange(vararg keys: Any?) {
        val currentKeys = rememberUpdatedState(keys.toList())
        LaunchedEffect(this) {
            snapshotFlow { currentKeys.value }
                .drop(1)
                .collect { resetScroll() }
        }
    }
}

@Stable
class ColumnScrollState(
    val scrollBehavior: TopAppBarScrollBehavior,
    val scrollState: ScrollState,
    private val coroutineScope: CoroutineScope,
) {
    private var resetJob: Job? = null

    private suspend fun performScrollReset() {
        scrollBehavior.resetScroll()
        scrollState.scrollTo(0)
    }

    fun resetScroll() {
        resetJob?.cancel()
        resetJob = coroutineScope.launch {
            performScrollReset()
        }
    }

    suspend fun resetScrollAndAwait() {
        resetJob?.cancelAndJoin()
        performScrollReset()
    }
}

@Composable
fun rememberListScrollState(
    canScroll: () -> Boolean = { true },
): ListScrollState {
    val coroutineScope = rememberCoroutineScope()
    val currentCanScroll = rememberUpdatedState(canScroll)
    val stableCanScroll = remember { { currentCanScroll.value() } }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        state = rememberSaveable(saver = TopAppBarState.Saver) {
            TopAppBarState(-Float.MAX_VALUE, 0f, 0f)
        },
        canScroll = stableCanScroll,
    )
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState(0, 0) }
    return remember(scrollBehavior, listState, coroutineScope) {
        ListScrollState(scrollBehavior, listState, coroutineScope)
    }
}

@Composable
fun rememberPinnedListScrollState(): ListScrollState {
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        state = rememberSaveable(saver = TopAppBarState.Saver) {
            TopAppBarState(-Float.MAX_VALUE, 0f, 0f)
        },
    )
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState(0, 0) }
    return remember(scrollBehavior, listState, coroutineScope) {
        ListScrollState(scrollBehavior, listState, coroutineScope)
    }
}

@Composable
fun rememberColumnScrollState(): ColumnScrollState {
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        state = rememberSaveable(saver = TopAppBarState.Saver) {
            TopAppBarState(-Float.MAX_VALUE, 0f, 0f)
        },
    )
    val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(initial = 0) }
    return remember(scrollBehavior, scrollState, coroutineScope) {
        ColumnScrollState(scrollBehavior, scrollState, coroutineScope)
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
