package li.gkd.app.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSelectionStateTest {
    @Test
    fun selectingAndDeselectingOneGroupKeepsOtherSelections() {
        val state = MultiSelectionState<Long>()
        state.select(1)

        state.selectMany(listOf(2, 3))
        assertEquals(setOf(1L, 2L, 3L), state.selectedKeys)

        state.deselectMany(listOf(2, 3))
        assertTrue(state.active)
        assertEquals(setOf(1L), state.selectedKeys)
    }

    @Test
    fun deselectingLastItemKeepsModeUntilExplicitExit() {
        val state = MultiSelectionState<Int>()
        state.select(1)
        state.toggle(1)
        assertTrue(state.active)
        assertTrue(state.selectedKeys.isEmpty())

        state.clear()
        assertFalse(state.active)
        assertTrue(state.selectedKeys.isEmpty())
    }

    @Test
    fun longPressAddsToSelectionAndDoesNotToggleAnAlreadySelectedItem() {
        val state = MultiSelectionState<Int>()
        state.select(1)
        state.select(2)
        state.select(1)
        assertEquals(setOf(1, 2), state.selectedKeys)
    }

    @Test
    fun invertUsesEntireFilteredListAndCanBeRepeatedAfterSelectingNothing() {
        val state = MultiSelectionState<Int>()
        val filteredKeys = setOf(2, 4, 6)
        state.select(2)
        state.invert(filteredKeys)
        assertEquals(setOf(4, 6), state.selectedKeys)
        state.selectAll(filteredKeys)
        state.invert(filteredKeys)
        assertTrue(state.active)
        assertTrue(state.selectedKeys.isEmpty())
        state.invert(filteredKeys)
        assertEquals(filteredKeys, state.selectedKeys)
    }

    @Test
    fun refreshRemovesMissingTargetsWithoutAutomaticallySelectingNewOnes() {
        val state = MultiSelectionState<Int>()
        state.selectAll(listOf(1, 2))
        state.retain(setOf(2, 3))
        assertEquals(setOf(2), state.selectedKeys)
        state.retain(setOf(3))
        assertTrue(state.active)
        assertTrue(state.selectedKeys.isEmpty())
        state.retain(emptySet())
        assertFalse(state.active)
    }

    @Test
    fun reconcilingLoadedDataDoesNotEnterSelectionMode() {
        val state = MultiSelectionState<Int>()
        state.retain(setOf(1, 2))
        assertFalse(state.active)
        assertTrue(state.selectedKeys.isEmpty())
    }

    @Test
    fun deletionPreservesProtectedTargetsAndExitsWhenNoSelectionRemains() {
        val state = MultiSelectionState<Long>()
        state.selectAll(setOf(-1L, 10L, 20L))
        state.removeDeleted(setOf(10L, 20L))
        assertTrue(state.active)
        assertEquals(setOf(-1L), state.selectedKeys)
        state.removeDeleted(setOf(-1L))
        assertFalse(state.active)
    }

    @Test
    fun delayedDeletionCompletionDoesNotReenterAnExitedMode() {
        val state = MultiSelectionState<Int>()
        state.select(1)
        state.clear()
        state.removeDeleted(setOf(1))
        assertFalse(state.active)
    }

    @Test
    fun capturedTargetsDoNotChangeWhenSelectionChanges() {
        val state = MultiSelectionState<Int>()
        state.selectAll(setOf(1, 2))
        val operationTargets = state.selectedKeys
        state.toggle(1)
        state.select(3)
        assertEquals(setOf(1, 2), operationTargets)
        assertEquals(setOf(2, 3), state.selectedKeys)
    }
}
