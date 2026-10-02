package li.gkd.app.state

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutexStateTest {
    @Test
    fun tryWithStateLockSkipsAtomicallyWhileLocked() = runBlocking {
        val mutexState = MutexState()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = launch {
            mutexState.withStateLock {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()

        var secondExecuted = false
        val secondAcquired = mutexState.tryWithStateLock {
            secondExecuted = true
        }

        assertTrue(mutexState.state.value)
        assertFalse(secondAcquired)
        assertFalse(secondExecuted)

        release.complete(Unit)
        first.join()

        assertFalse(mutexState.state.value)
        assertTrue(mutexState.tryWithStateLock { secondExecuted = true })
        assertTrue(secondExecuted)
        assertFalse(mutexState.state.value)
    }

    @Test
    fun withStateLockUnlocksAfterFailure() = runBlocking {
        val mutexState = MutexState()

        val result = runCatching {
            mutexState.withStateLock { error("failed") }
        }

        assertEquals("failed", result.exceptionOrNull()?.message)
        assertFalse(mutexState.state.value)
        assertTrue(mutexState.tryWithStateLock {})
    }
}
