package li.gkd.app.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class CoroutineExtTest {
    @Test
    fun launchLoggedPreservesCancellation() = runBlocking {
        val job = launchLogged {
            throw CancellationException("stop")
        }

        job.join()

        assertTrue(job.isCancelled)
    }
}
