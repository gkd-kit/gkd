package li.gkd.app.crash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashRestartGuardTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun repeatedProcessStartsStopAtThirdCrashAndRecoverAfterWindow() {
        val file = directory.root.resolve("history")
        assertTrue(CrashRestartGuard(file).recordCrash(100_000))
        assertTrue(CrashRestartGuard(file).recordCrash(110_000))
        assertFalse(CrashRestartGuard(file).recordCrash(120_000))
        assertFalse(CrashRestartGuard(file).recordCrash(130_000))
        assertTrue(CrashRestartGuard(file).recordCrash(190_000))
    }

    @Test
    fun clockRollbackDoesNotKeepFutureCrashes() {
        val file = directory.root.resolve("history")
        CrashRestartGuard(file).recordCrash(100_000)
        CrashRestartGuard(file).recordCrash(110_000)
        assertTrue(CrashRestartGuard(file).recordCrash(10_000))
    }
}
