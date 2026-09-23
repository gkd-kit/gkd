package li.gkd.app.permission

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class PermissionRecheckPolicyTest {
    @Test
    fun delayedGrantSucceedsBeforeReportingDenial() = runBlocking {
        var reads = 0
        val granted = PermissionRecheckPolicy(listOf(10L, 10L, 10L)).awaitGranted(
            MutableStateFlow(true),
        ) { ++reads >= 3 }
        assertTrue(granted)
        assertEquals(3, reads)
    }

    @Test
    fun persistentDenialFinishesAfterBoundedRetries() = runBlocking {
        var reads = 0
        val granted = PermissionRecheckPolicy(listOf(10L, 10L)).awaitGranted(
            MutableStateFlow(true),
        ) { reads++; false }
        assertFalse(granted)
        assertEquals(3, reads)
    }

    @Test
    fun grantedPermissionDoesNotWaitForRetryWindow() = runBlocking {
        assertTrue(withTimeout(1_000L) {
            PermissionRecheckPolicy(listOf(60_000L)).awaitGranted(MutableStateFlow(true)) { true }
        })
    }

    @Test
    fun losingFocusCancelsOldRetryAndReturnStartsFreshCheck() = runBlocking {
        val interactive = MutableStateFlow(false)
        val firstRead = CompletableDeferred<Unit>()
        var reads = 0
        val result = async {
            PermissionRecheckPolicy(listOf(60_000L)).awaitGranted(interactive) {
                reads++
                firstRead.complete(Unit)
                reads > 1
            }
        }
        delay(20L)
        assertEquals(0, reads)
        interactive.value = true
        withTimeout(1_000L) { firstRead.await() }
        interactive.value = false
        delay(20L)
        assertEquals(1, reads)
        assertFalse(result.isCompleted)
        interactive.value = true
        assertTrue(withTimeout(1_000L) { result.await() })
        assertEquals(2, reads)
    }

    @Test
    fun cancellationStopsChecksAndDoesNotLeakIntoNextRequest() = runBlocking {
        val interactive = MutableStateFlow(true)
        val firstRead = CompletableDeferred<Unit>()
        var reads = 0
        val oldRequest = async {
            PermissionRecheckPolicy(listOf(20L, 20L)).awaitGranted(interactive) {
                reads++
                firstRead.complete(Unit)
                false
            }
        }
        withTimeout(1_000L) { firstRead.await() }
        oldRequest.cancelAndJoin()
        assertTrue(PermissionRecheckPolicy.Immediate.awaitGranted(interactive) { true })
        delay(60L)
        assertEquals(1, reads)
    }

    @Test
    fun ordinaryPermissionDenialDoesNotRetry() = runBlocking {
        var reads = 0
        assertFalse(PermissionRecheckPolicy.Immediate.awaitGranted(MutableStateFlow(true)) {
            reads++
            false
        })
        assertEquals(1, reads)
    }
}
