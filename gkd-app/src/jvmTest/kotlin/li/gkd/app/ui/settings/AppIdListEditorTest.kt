package li.gkd.app.ui.settings

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class AppIdListEditorTest {
    @Test
    fun failedSaveKeepsDraftAndNewInputDuringSuccessfulSaveStaysOpen() = runTest {
        var ids = setOf("test.original")
        var fail = true
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val editor =
            AppIdListEditor({ ids }) {
                if (fail) throw IOException("disk full")
                entered.complete(Unit)
                release.await()
                ids = it
            }
        val state = AppListEditorState()
        editor.startEditing(state)
        state.setText("test.saved")
        var feedback = 0
        assertFailsWith<IOException> { editor.save(state) { feedback++ } }
        assertTrue(state.state.value.editing)
        assertEquals("test.saved", state.state.value.draft)
        assertEquals(setOf("test.original"), ids)
        assertTrue(editor.hasChanges(state.state.value.draft))
        var discardRequested = false
        editor.close(state) {
            discardRequested = true
            false
        }
        assertTrue(discardRequested)
        assertTrue(state.state.value.editing)
        assertEquals(0, feedback)
        fail = false
        val saving = launch {
            editor.save(state) { changed ->
                assertTrue(changed)
                feedback++
            }
        }
        runCurrent()
        entered.await()
        state.setText("test.newer")
        release.complete(Unit)
        saving.join()
        assertEquals(setOf("test.saved"), ids)
        assertTrue(state.state.value.editing)
        assertEquals("test.newer", state.state.value.draft)
        assertEquals(1, feedback)
        editor.save(state) { feedback++ }
        assertEquals(setOf("test.newer"), ids)
        assertFalse(state.state.value.editing)
    }

    @Test
    fun reopeningEditorUsesTheCurrentListAsItsNewBaseline() = runTest {
        var ids = setOf("test.original")
        val editor = AppIdListEditor({ ids }) { ids = it }
        val state = AppListEditorState()
        editor.startEditing(state)
        state.setText("test.saved")
        editor.save(state) { assertTrue(it) }

        ids = setOf("test.external")
        editor.startEditing(state)
        assertEquals("test.external\n\n", state.state.value.draft)
        assertFalse(editor.hasChanges(state.state.value.draft))
        editor.save(state) { assertFalse(it) }
        assertFalse(state.state.value.editing)
    }

    @Test
    fun discardAsksOnlyForSemanticChangesAndHonorsTheDecision() = runTest {
        val editor = AppIdListEditor({ setOf("test.original") }) { error("discard must not save") }
        val state = AppListEditorState()
        state.startEditing("test.original\n# comment\ntest.original")
        editor.close(state) { error("unchanged IDs do not require confirmation") }
        assertFalse(state.state.value.editing)
        state.startEditing("test.changed")
        editor.close(state) { false }
        assertTrue(state.state.value.editing)
        editor.close(state) { true }
        assertFalse(state.state.value.editing)
    }
}
