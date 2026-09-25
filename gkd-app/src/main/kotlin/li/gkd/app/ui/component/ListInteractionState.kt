package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Stable
class MultiSelectionState<K> {
    private data class Selection<K>(
        val active: Boolean = false,
        val keys: Set<K> = emptySet(),
    )

    private var selection by mutableStateOf(Selection<K>())

    val selectedKeys: Set<K>
        get() = selection.keys

    val active: Boolean
        get() = selection.active

    fun select(key: K) {
        selection = Selection(active = true, keys = selectedKeys + key)
    }

    fun selectMany(keys: Iterable<K>) {
        selection = Selection(active = true, keys = selectedKeys + keys)
    }

    fun deselectMany(keys: Iterable<K>) {
        selection = selection.copy(keys = selectedKeys - keys.toSet())
    }

    fun toggle(key: K) {
        val keys = if (key in selectedKeys) {
            selectedKeys - key
        } else {
            selectedKeys + key
        }
        selection = Selection(active = true, keys = keys)
    }

    fun selectAll(keys: Iterable<K>) {
        selection = Selection(active = true, keys = keys.toSet())
    }

    fun invert(keys: Iterable<K>) {
        selection = Selection(active = true, keys = keys.toSet() - selectedKeys)
    }

    // Only reconcile a loaded list. An empty selection alone does not end selection mode.
    fun retain(keys: Set<K>) {
        if (keys.isEmpty()) {
            clear()
        } else {
            selection = selection.copy(keys = selectedKeys intersect keys)
        }
    }

    fun removeDeleted(keys: Set<K>) {
        val remaining = selectedKeys - keys
        selection = Selection(active = active && remaining.isNotEmpty(), keys = remaining)
    }

    fun clear() {
        selection = Selection()
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

    var dragging by mutableStateOf(false)
        private set
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
