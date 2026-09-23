package li.gkd.app.ui.share

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSaveSessionTest {
    @Test
    fun repeatedSaveWhileWritingCommitsOnceAndReturnsTheCommittedResult() = runBlocking {
        val session = EditorSaveSession<Int>()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var writes = 0
        val first = async {
            session.save {
                writes++
                started.complete(Unit)
                finish.await()
                42
            }
        }
        started.await()
        val second = async { session.save { writes++; 99 } }
        finish.complete(Unit)
        assertEquals(42, first.await())
        assertEquals(42, second.await())
        assertEquals(1, writes)
    }

    @Test
    fun failedSaveCanBeRetriedWithoutTreatingFailureAsCompletion() = runBlocking {
        val session = EditorSaveSession<Int>()
        assertTrue(runCatching { session.save { error("write failed") } }.isFailure)
        assertEquals(7, session.save { 7 })
    }

    @Test
    fun cancelledSaveReleasesTheSessionForRetry() = runBlocking {
        val session = EditorSaveSession<Int>()
        val started = CompletableDeferred<Unit>()
        val write = launch {
            session.save {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        write.cancelAndJoin()
        assertEquals(7, session.save { 7 })
    }

    @Test
    fun successfulNullResultStillPreventsDuplicateCreation() = runBlocking {
        val session = EditorSaveSession<String?>()
        var writes = 0
        assertNull(session.save { writes++; null })
        assertNull(session.save { writes++; "duplicate" })
        assertEquals(1, writes)
    }
}
