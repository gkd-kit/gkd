package li.gkd.app.ui.component

import li.gkd.app.text.UiStrings
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

data class RuleListFocus(
    val pending: Boolean,
    val missing: Boolean,
    val highlightedKey: Any?,
)

/** Keys follow the rendered list order, including its ordinary and sticky headers. */
@Composable
fun rememberRuleListFocus(
    requestKey: Any?,
    scrollState: ListScrollState,
    itemKeys: List<Any>,
    targetExists: Boolean,
    ready: Boolean = true,
    stickyHeaderKeys: Set<Any> = emptySet(),
    onRevealTarget: () -> Unit = {},
): RuleListFocus {
    var handled by rememberSaveable(requestKey) { mutableStateOf(requestKey == null) }
    var missing by rememberSaveable(requestKey) { mutableStateOf(false) }
    var positionedKey by remember(requestKey) { mutableStateOf<Any?>(null) }
    var highlightedKey by remember(requestKey) { mutableStateOf<Any?>(null) }
    val revealTarget by rememberUpdatedState(onRevealTarget)
    val currentStickyHeaderKeys by rememberUpdatedState(stickyHeaderKeys)
    val listState = scrollState.listState
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(requestKey, ready, itemKeys, targetExists, stickyHeaderKeys) {
        if (handled || !ready || requestKey == null) return@LaunchedEffect
        if (!targetExists) {
            missing = true
            handled = true
            return@LaunchedEffect
        }
        val index = itemKeys.indexOf(requestKey)
        if (index < 0) {
            revealTarget()
            return@LaunchedEffect
        }

        // Await a real list layout before positioning; a composed/prefetched card is not
        // proof of visibility. A changed key order cancels this attempt and resolves again.
        snapshotFlow { listState.layoutInfo }.first {
            it.totalItemsCount >= itemKeys.size && it.viewportSize.height > 0
        }
        scrollState.scrollToItemAndAwait(index)
        val layout = snapshotFlow { listState.layoutInfo }.first { info ->
            info.visibleItemsInfo.any { it.key == requestKey }
        }
        val target = layout.visibleItemsInfo.first { it.key == requestKey }
        val top = layout.unobscuredTop(stickyHeaderKeys)
        if (target.offset < top) {
            listState.scrollBy((target.offset - top).toFloat())
        }
        snapshotFlow { listState.layoutInfo }.first { info ->
            info.isUnobscured(requestKey, stickyHeaderKeys)
        }
        handled = true
        positionedKey = requestKey
    }

    // NavDisplay resumes a page only after its entrance settles. Locating is immediate;
    // only the visual pulse waits. List changes must not cancel a pulse already playing.
    LaunchedEffect(positionedKey, listState, lifecycle) {
        val key = positionedKey ?: return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        if (!listState.layoutInfo.isUnobscured(key, currentStickyHeaderKeys)) return@LaunchedEffect
        try {
            repeat(2) {
                highlightedKey = key
                delay(400)
                highlightedKey = null
                delay(400)
            }
        } finally {
            highlightedKey = null
        }
    }
    return RuleListFocus(pending = !handled, missing = missing, highlightedKey = highlightedKey)
}

private fun LazyListLayoutInfo.isUnobscured(key: Any, stickyHeaderKeys: Set<Any>): Boolean {
    val item = visibleItemsInfo.find { it.key == key } ?: return false
    val top = unobscuredTop(stickyHeaderKeys)
    return item.offset >= top && item.offset < viewportEndOffset &&
        (item.offset + item.size <= viewportEndOffset || item.size > viewportEndOffset - top)
}

private fun LazyListLayoutInfo.unobscuredTop(stickyHeaderKeys: Set<Any>): Int {
    val top = viewportStartOffset + beforeContentPadding
    return visibleItemsInfo.filter { it.key in stickyHeaderKeys && it.offset <= top }
        .maxOfOrNull { it.offset + it.size }?.coerceAtLeast(top) ?: top
}

@Composable
fun GkRuleFocusNotice() {
    Text(
        text = UiStrings.rule_focus_missing,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
