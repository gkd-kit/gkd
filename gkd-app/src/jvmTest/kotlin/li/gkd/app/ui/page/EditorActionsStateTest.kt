package li.gkd.app.ui.page

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class EditorActionsStateTest {
    @Test
    fun changedDraftRequiresConfirmationAndDismissDoesNotCloseEditor() = runBlocking {
        val state = EditorActionsState(this)
        var closes = 0
        state.requestClose({ true }, {}, { closes++ })
        yield()
        assertSame(EditorDialog.Discard, state.dialog.value)
        assertEquals(0, closes)
        state.dismissDialog()
        assertNull(state.dialog.value)
        assertEquals(0, closes)
        state.requestClose({ false }, {}, { closes++ })
        yield()
        assertEquals(1, closes)
    }

    @Test
    fun failedSavePreservesCauseAndDoesNotRunSuccessCallbacks() = runBlocking {
        val state = EditorActionsState(this)
        val failure = IOException("write failed")
        val events = mutableListOf<String>()
        state.save({ throw failure }, { events.add("close") }, { events.add("saved") })
        yield()
        assertSame(failure, (state.dialog.value as EditorDialog.Error).cause)
        assertEquals(emptyList(), events)
        state.dismissDialog()
        state.save({ events.add("write") }, { events.add("close") }, { events.add("saved") })
        yield()
        assertNull(state.dialog.value)
        assertEquals(listOf("write", "close", "saved"), events)
    }

    @Test
    fun cancellingOwnerStopsSaveWithoutShowingAnErrorOrClosingEditor() = runBlocking {
        val owner = Job()
        val state = EditorActionsState(CoroutineScope(coroutineContext + owner))
        val started = CompletableDeferred<Unit>()
        var closes = 0
        try {
            state.save({ started.complete(Unit); awaitCancellation() }, { closes++ }, { closes++ })
            started.await()
            owner.cancelAndJoin()
            assertNull(state.dialog.value)
            assertEquals(0, closes)
        } finally {
            owner.cancelAndJoin()
        }
    }
}
