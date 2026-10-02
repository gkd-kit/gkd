package li.gkd.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimulatorStoreTest {
    @Test
    fun hostBatteryRefreshDoesNotPersistAndSurvivesConfigurationReplacement() {
        val writes = mutableListOf<SimulatorSettings>()
        val store = SimulatorStore(save = { writes.add(it) })
        store.updateHostBattery(42)
        assertEquals(42, store.settings.value.environment().android.batteryPercent)
        assertTrue(writes.isEmpty())
        store.replace(SimulatorSettings(device = SimulatedDevice(dark = true, batteryPercent = 12)))
        assertEquals(42, store.settings.value.device.batteryPercent)
        assertEquals(100, writes.last().device.batteryPercent)
        val writeCount = writes.size
        store.updateHostBattery(255)
        assertEquals(100, store.settings.value.device.batteryPercent)
        assertEquals(writeCount, writes.size)
    }

    @Test
    fun failedWritePreservesFileAndSnapshotAndCanBeRetried() {
        val directory = Files.createTempDirectory("simulator-store-").toFile()
        try {
            val storage = DesktopSimulatorStorage(directory.resolve("simulator.json"))
            val initial = SimulatorSettings()
            storage.save(initial)
            var fail = true
            val store = SimulatorStore(initial) {
                if (fail) throw IOException("disk unavailable")
                storage.save(it)
            }
            val error = assertFailsWith<IOException> { store.setRestricted(true) }
            assertEquals("disk unavailable", error.message)
            assertEquals(initial, store.settings.value)
            assertEquals(initial, storage.load(DesktopEnvironment()))
            fail = false
            store.setRestricted(true)
            assertTrue(storage.load(DesktopEnvironment()).permissions.restricted)
            assertTrue(store.settings.value.permissions.restricted)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun concurrentFieldPatchesPreserveOtherCommandsAndPersistLatestSnapshot() {
        val directory = Files.createTempDirectory("simulator-concurrent-").toFile()
        val executor = Executors.newFixedThreadPool(3)
        try {
            val storage = DesktopSimulatorStorage(directory.resolve("simulator.json"))
            val store = SimulatorStore(SimulatorSettings(), storage::save)
            val work = listOf(
                executor.submit { store.setRestricted(true) },
                executor.submit { store.patch(Json.parseToJsonElement("""{"device":{"fontScale":1.5}}""").jsonObject) },
                executor.submit { store.patch(Json.parseToJsonElement("""{"device":{"wifi":false,"mobileSignal":0}}""").jsonObject) },
            )
            work.forEach { it.get() }
            assertTrue(store.settings.value.permissions.restricted)
            assertEquals(1.5f, store.settings.value.device.fontScale)
            assertFalse(store.settings.value.networkAvailable)
            assertEquals(store.settings.value.persistent(), storage.load(DesktopEnvironment()))
            val before = store.settings.value
            assertFailsWith<IllegalArgumentException> {
                store.patch(Json.parseToJsonElement("""{"device":{"wfi":true}}""").jsonObject)
            }
            assertEquals(before, store.settings.value)
        } finally {
            executor.shutdownNow(); directory.deleteRecursively()
        }
    }

}
