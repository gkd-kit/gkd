package li.songe.gkd.ui.component

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first

@Stable
class LazyListAutoFollowState<K>(
    initialItemKey: K,
    val listState: LazyListState = LazyListState(),
) {
    var isAutoFollowEnabled by mutableStateOf(true)
        private set

    var pausedAtItemKey by mutableStateOf(initialItemKey)
        private set

    fun pause(latestItemKey: K) {
        if (isAutoFollowEnabled) {
            pausedAtItemKey = latestItemKey
        }
        isAutoFollowEnabled = false
    }

    fun resume() {
        isAutoFollowEnabled = true
    }
}

@Composable
fun <K> rememberLazyListAutoFollowState(
    itemCount: Int,
    latestItemKey: K,
): LazyListAutoFollowState<K> {
    val listState = rememberLazyListState()
    val state = remember {
        LazyListAutoFollowState(
            initialItemKey = latestItemKey,
            listState = listState,
        )
    }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) {
            state.pause(latestItemKey)
        } else {
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            if (listState.isAtBottom) {
                state.resume()
            }
        }
    }
    LaunchedEffect(itemCount, latestItemKey, state.isAutoFollowEnabled) {
        if (state.isAutoFollowEnabled && itemCount > 0) {
            listState.scrollToItem(itemCount - 1)
        }
    }
    return state
}

private val LazyListState.isAtBottom: Boolean
    get() {
        val layoutInfo = layoutInfo
        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
        return lastVisibleItem != null &&
                lastVisibleItem.index + 1 == layoutInfo.totalItemsCount &&
                lastVisibleItem.offset + lastVisibleItem.size <= layoutInfo.viewportEndOffset
    }
