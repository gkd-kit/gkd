package li.gkd.app.ui.navigation

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

class OnRouteRemovedTest {
    @Test
    fun coveredEntryReturnsWithoutDuplicateCallbacks() = checkNavigation {
        val entry = push()
        repeat(3) {
            val covering = push()
            pop()
            assertEquals(1, calls.count { it == covering.id })
        }
        assertEquals(0, calls.count { it == entry.id })
        pop()
        assertEquals(1, calls.count { it == entry.id })
    }

    @Test
    fun repeatedRouteVisitsHaveIndependentCallbacks() = checkNavigation {
        val first = push()
        val second = push()
        pop()
        assertEquals(listOf(second.id), calls)
        pop()
        assertEquals(listOf(second.id, first.id), calls)
    }

    @Test
    fun coveredEntryCanBeRemovedWithoutReturningToComposition() = checkNavigation {
        val covered = push()
        push()
        stack.remove(covered)
        settle()
        assertEquals(listOf(covered.id), calls)
    }

    @Test
    fun recompositionReplacesCallbackWithLatestCapture() = checkNavigation {
        val entry = push()
        revision.value = 1
        settle()
        revision.value = 2
        settle()
        pop()
        assertEquals(listOf(entry.id), calls)
        assertEquals(listOf(2), capturedRevisions)
    }

    @Test
    fun hostDisposalDoesNotRemoveBackStackEntries() = checkNavigation {
        push()
        push()
        composition.dispose()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun hostDisposalCompletesAlreadyPoppedEntryRemoval() = checkNavigation {
        val entry = push()
        retained.value = displayed
        stack.removeAt(stack.lastIndex)
        settle()
        assertEquals(emptyList(), calls)
        composition.dispose()
        assertEquals(listOf(entry.id), calls)
    }
}

private fun checkNavigation(block: suspend NavigationComposition.() -> Unit) = runBlocking {
    withTimeout(10_000) {
        val harness = NavigationComposition(this)
        try {
            harness.composition.setContent { harness.Content() }
            harness.settle()
            harness.block()
        } finally {
            harness.composition.dispose()
            harness.recomposer.cancel()
        }
    }
}

/** Uses real Navigation3 decorators; retained content represents an outgoing transition. */
private class NavigationComposition(scope: CoroutineScope) {
    val stack = mutableStateListOf(AppNavEntry(HomeRoute))
    val calls = mutableListOf<String>()
    val capturedRevisions = mutableListOf<Int>()
    val revision = mutableStateOf(0)
    val retained = mutableStateOf<NavEntry<AppNavEntry>?>(null)
    var displayed: NavEntry<AppNavEntry>? = null
    private val frameClock =
        object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                yield()
                return onFrame(System.nanoTime())
            }
        }
    val recomposer = Recomposer(scope.coroutineContext + frameClock)
    val composition =
        Composition(
            object : AbstractApplier<Unit>(Unit) {
                override fun insertTopDown(index: Int, instance: Unit) = Unit

                override fun insertBottomUp(index: Int, instance: Unit) = Unit

                override fun remove(index: Int, count: Int) = Unit

                override fun move(from: Int, to: Int, count: Int) = Unit

                override fun onClear() = Unit
            },
            recomposer,
        )

    init {
        scope.launch(frameClock) { recomposer.runRecomposeAndApplyChanges() }
    }

    @Composable
    fun Content() {
        val entries =
            rememberDecoratedNavEntries(
                stack,
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberRouteRemovedNavEntryDecorator(),
                ),
            ) { entry ->
                NavEntry(entry, contentKey = entry.id) {
                    val capturedRevision = revision.value
                    OnRouteRemoved {
                        calls.add(entry.id)
                        capturedRevisions.add(capturedRevision)
                    }
                }
            }
        displayed = entries.last()
        val visible = listOfNotNull(displayed, retained.value).distinctBy { it.contentKey }
        visible.forEach { entry -> key(entry.contentKey) { entry.Content() } }
    }

    suspend fun settle() {
        Snapshot.sendApplyNotifications()
        yield()
        recomposer.awaitIdle()
    }

    suspend fun push(): AppNavEntry {
        val entry = AppNavEntry(BlockA11ySetupRoute)
        stack.add(entry)
        settle()
        return entry
    }

    suspend fun pop() {
        stack.removeAt(stack.lastIndex)
        settle()
    }
}
