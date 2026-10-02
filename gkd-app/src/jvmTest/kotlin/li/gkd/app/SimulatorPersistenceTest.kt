package li.gkd.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatorPersistenceTest {
    private fun directory(): File {
        val root = File(System.getProperty("gkd.projectRoot", "..")).resolve(".local/tests/desktop")
            .apply { mkdirs() }
        return Files.createTempDirectory(root.toPath(), "simulator-persistence-").toFile()
    }

    @Test
    fun rapidChangesPreviewImmediatelyAndSaveLatestCompleteSnapshot() = runTest {
        val directory = directory()
        val file = directory.resolve("simulator.json")
        val persistence =
            SimulatorPersistence(backgroundScope, StandardTestDispatcher(testScheduler), file)
        try {
            val store = persistence.store
            repeat(100) { n ->
                store.updateEnvironment { it.copy(width = 400 + n) }
                runCurrent()
                advanceTimeBy(1)
            }
            store.setWifi(false)
            assertEquals(499, store.settings.value.device.width)
            assertFalse(file.exists())
            runCurrent()
            advanceTimeBy(200)
            runCurrent()
            assertEquals(
                store.settings.value.persistent(),
                DesktopSimulatorStorage(file).load(DesktopEnvironment())
            )
            assertFalse(persistence.status.value.pending)
        } finally {
            persistence.close(); directory.deleteRecursively()
        }
    }

    @Test
    fun flushSavesFinalValueWithoutWaitingForDebounce() = runTest {
        val directory = directory()
        val file = directory.resolve("simulator.json")
        val persistence =
            SimulatorPersistence(backgroundScope, StandardTestDispatcher(testScheduler), file)
        try {
            persistence.store.updateEnvironment { it.copy(height = 900) }
            val flushed = async { persistence.flush() }
            runCurrent()
            assertTrue(flushed.await())
            assertEquals(
                900,
                DesktopSimulatorStorage(file).load(DesktopEnvironment()).device.height
            )
            assertEquals(0, testScheduler.currentTime)
        } finally {
            persistence.close(); directory.deleteRecursively()
        }
    }

    @Test
    fun failedSaveKeepsPreviewAndExplicitRetryPersistsLatestValue() = runTest {
        val directory = directory()
        val file = directory.resolve("simulator.json")
        val persistence =
            SimulatorPersistence(backgroundScope, StandardTestDispatcher(testScheduler), file)
        try {
            file.mkdirs()
            val obstruction = file.resolve("occupied").apply { writeText("keep") }
            persistence.store.updateEnvironment { it.copy(width = 500) }
            val failed = async { persistence.flush() }
            runCurrent()
            assertFalse(failed.await())
            assertEquals(500, persistence.store.settings.value.device.width)
            assertTrue(persistence.status.value.pending)
            assertNotNull(persistence.status.value.error)
            assertEquals("keep", obstruction.readText())
            obstruction.delete()
            file.delete()
            advanceTimeBy(1_000)
            runCurrent()
            assertFalse(file.exists()) // Failure must not schedule an automatic retry.
            persistence.store.updateEnvironment { it.copy(width = 501) }
            val retry = async { persistence.flush() }
            runCurrent()
            assertTrue(retry.await())
            assertEquals(501, DesktopSimulatorStorage(file).load(DesktopEnvironment()).device.width)
            assertNull(persistence.status.value.error)
        } finally {
            persistence.close(); directory.deleteRecursively()
        }
    }

    @Test
    fun concurrentUpdatesAndFlushPersistLatestCompleteSnapshot() = kotlinx.coroutines.runBlocking {
        val directory = directory()
        val file = directory.resolve("simulator.json")
        val persistence = SimulatorPersistence(this, file = file)
        try {
            val width = async(kotlinx.coroutines.Dispatchers.Default) {
                repeat(100) { n -> persistence.store.updateEnvironment { it.copy(width = 400 + n) } }
            }
            val height = async(kotlinx.coroutines.Dispatchers.Default) {
                repeat(100) { n -> persistence.store.updateEnvironment { it.copy(height = 800 + n) } }
            }
            repeat(10) { assertTrue(persistence.flush()) }
            width.await()
            height.await()
            assertTrue(persistence.flush())
            val saved = DesktopSimulatorStorage(file).load(DesktopEnvironment())
            assertEquals(499, saved.device.width)
            assertEquals(899, saved.device.height)
            assertEquals(persistence.store.settings.value.persistent(), saved)
            assertFalse(persistence.status.value.pending)
        } finally {
            persistence.close(); directory.deleteRecursively()
        }
    }

}
