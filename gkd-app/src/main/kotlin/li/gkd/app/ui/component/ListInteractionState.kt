package li.songe.gkd.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Stable
class MultiSelectionState<K> {
    var selectedKeys by mutableStateOf<Set<K>>(emptySet())
        private set

    val active: Boolean
        get() = selectedKeys.isNotEmpty()

    fun selectOnly(key: K) {
        selectedKeys = setOf(key)
    }

    fun toggle(key: K) {
        selectedKeys = if (key in selectedKeys) {
            selectedKeys - key
        } else {
            selectedKeys + key
        }
    }

    fun selectAll(keys: Iterable<K>) {
        selectedKeys = keys.toSet()
    }

    fun invert(keys: Iterable<K>) {
        selectedKeys = keys.toSet() - selectedKeys
    }

    fun retain(keys: Set<K>) {
        val retainedKeys = selectedKeys intersect keys
        if (retainedKeys != selectedKeys) {
            selectedKeys = retainedKeys
        }
    }

    fun clear() {
        selectedKeys = emptySet()
    }
}

@Composable
fun <K> rememberMultiSelectionState(): MultiSelectionState<K> = remember {
    MultiSelectionState()
}

@Stable
data class ReorderFinishResult<T>(
    val moved: Boolean,
    val reorderedItems: List<T>?,
)

@Stable
class ReorderSession<T, K>(
    initialItems: List<T>,
    private val keyOf: (T) -> K,
) {
    private var sourceItems = initialItems

    var items by mutableStateOf(initialItems)
        private set

    private var dragging = false
    private var moved = false

    fun sync(items: List<T>) {
        if (items == sourceItems) return
        sourceItems = items
        if (!dragging) {
            this.items = items
        }
    }

    fun startDragging() {
        dragging = true
        moved = false
    }

    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex !in items.indices || toIndex !in items.indices) {
            return
        }
        items = items.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        moved = true
    }

    fun finishDragging(): ReorderFinishResult<T> {
        dragging = false
        val wasMoved = moved
        val mergedItems = if (wasMoved) {
            val latestByKey = sourceItems.associateBy(keyOf)
            val consumedKeys = mutableSetOf<K>()
            buildList {
                items.forEach { item ->
                    val key = keyOf(item)
                    latestByKey[key]?.let { latestItem ->
                        if (consumedKeys.add(key)) add(latestItem)
                    }
                }
                sourceItems.forEach { item ->
                    if (consumedKeys.add(keyOf(item))) add(item)
                }
            }
        } else {
            sourceItems
        }
        items = mergedItems
        val result = ReorderFinishResult(
            moved = wasMoved,
            reorderedItems = mergedItems.takeIf {
                wasMoved && it.map(keyOf) != sourceItems.map(keyOf)
            },
        )
        moved = false
        return result
    }

    fun cancelDragging() {
        dragging = false
        moved = false
        items = sourceItems
    }
}

@Composable
fun <T, K> rememberReorderSession(
    items: List<T>,
    keyOf: (T) -> K,
): ReorderSession<T, K> {
    val state = remember { ReorderSession(items, keyOf) }
    SideEffect { state.sync(items) }
    return state
}
