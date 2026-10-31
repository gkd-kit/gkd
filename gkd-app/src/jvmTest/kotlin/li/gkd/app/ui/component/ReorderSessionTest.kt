package li.gkd.app.ui.component

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ReorderSessionTest {
    private data class Item(val id: Int, val name: String)

    @Test
    fun deletionAfterReorderIsVisibleBeforeCompositionReadsRelatedData() {
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(UnitApplier(), recomposer)
        val original = listOf(Item(1, "one"), Item(2, "two"), Item(3, "three"))
        var session: ReorderSession<Item, Int>? = null
        try {
            // The same composition must retain its drag session across source updates.
            for (source in listOf(original, listOf(original[2], original[0]))) {
                composition.setContent {
                    val current = rememberReorderSession(source) { it.id }
                    session?.let { assertSame(it, current) }
                    session = current
                    val details = source.associateBy { it.id }
                    assertEquals(source, current.items)
                    current.items.forEach { assertEquals(it, details.getValue(it.id)) }
                }
                if (source === original) {
                    val current = checkNotNull(session)
                    current.startDragging()
                    current.move(2, 0)
                    current.finishDragging()
                }
            }
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }

    @Test
    fun editsDuringDragKeepOrderButUseCurrentItemsAndDoNotRestoreDeletedItems() {
        val original = listOf(Item(1, "one"), Item(2, "two"), Item(3, "three"))
        val session = ReorderSession(original) { it.id }
        session.startDragging()
        session.move(2, 0)
        val edited = Item(3, "edited")
        val added = Item(4, "new")
        val latest = listOf(original[0], edited, added)
        session.sync(latest)
        val expected = listOf(edited, original[0], added)
        assertEquals(expected, session.items)
        assertEquals(expected, session.finishDragging().reorderedItems)
        session.reset()
        assertEquals(latest, session.items)
    }

    private class UnitApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit

        override fun insertBottomUp(index: Int, instance: Unit) = Unit

        override fun remove(index: Int, count: Int) = Unit

        override fun move(from: Int, to: Int, count: Int) = Unit

        override fun onClear() = Unit
    }
}
