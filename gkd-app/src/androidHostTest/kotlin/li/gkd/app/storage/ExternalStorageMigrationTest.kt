package li.gkd.app.storage

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExternalStorageMigrationTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun markerAndUnavailableVolumeNeverMigrateExternalData() {
        val internal = directory.newFolder("internal")
        val migration = migration(internal)
        assertEquals(
            StorageSelectionReason.ExternalNullFallback,
            migration.prepare { null }.reason,
        )
        assertTrue(internal.resolve(".gkd").isFile)
        assertEquals(
            StorageSelectionReason.InternalMarkerExists,
            migration
                .prepare { error("A marked installation must not probe external files") }
                .reason,
        )
    }

    @Test
    fun resumesWithoutOverwritingLiveCoreOrTodayLogAndDeletesOnlyVerifiedSources() {
        val external = directory.newFolder("external")
        val internal = directory.newFolder("internal")
        val db = external.write("db/gkd.db", "original-db")
        val wal = external.write("db/gkd.db-wal", "original-wal")
        val shm = external.write("db/gkd.db-shm", "temporary-index")
        val oldStore = external.write("store/store.json", "original-settings")
        val oldSubscription = external.write("subscription/1.json", "original-subscription")
        val oldSnapshot = external.write("snapshot/1/snapshot.json", "original-snapshot")
        val oldLog = external.write("log/gkd-20261110.log", "original-log")

        val migration = migration(internal)
        assertEquals(
            StorageSelectionReason.ExternalMigration,
            migration.prepare { external }.reason,
        )
        assertEquals("original-wal", internal.resolve("db/gkd.db-wal").readText())
        assertFalse(internal.resolve("db/gkd.db-shm").exists())
        assertEquals("original-subscription", internal.resolve("subscription/1.json").readText())
        assertFalse(internal.resolve("snapshot").exists())
        assertFalse(internal.resolve(".gkd").exists())
        internal.write("store/store.json", "new-settings")
        val newLog = internal.write("log/gkd-20261110.log", "new-log")

        val resumed = migration(internal)
        assertEquals(StorageSelectionReason.ExternalMigration, resumed.prepare { null }.reason)
        assertEquals("new-settings", internal.resolve("store/store.json").readText())
        runBlocking {
            assertTrue(resumed.migrateHistory({ external }) { _, result -> result.getOrThrow() })
        }
        assertEquals("original-snapshot", internal.resolve("snapshot/1/snapshot.json").readText())
        assertEquals("original-log", internal.resolve("log/gkd-20261110-migrated.log").readText())
        assertEquals("new-log", newLog.readText())
        val extra = external.write("snapshot/2/snapshot.json", "arrived-after-copy")

        oldLog.writeText("changed-after-copy")
        assertThrows(IOException::class.java) { resumed.cleanUp { external } }
        assertTrue(db.isFile)
        assertTrue(oldStore.isFile)
        oldLog.writeText("original-log")
        resumed.cleanUp { external }
        listOf(db, wal, oldStore, oldSubscription, oldSnapshot, oldLog).forEach {
            assertFalse(it.exists())
        }
        assertTrue(shm.isFile)
        assertTrue(extra.isFile)
        assertTrue(internal.resolve(".gkd").isFile)
    }

    @Test
    fun snapshotsBecomeReadyWhileOtherHistoryIsStillMigrating() = runBlocking {
        val external = directory.newFolder("external")
        val internal = directory.newFolder("internal")
        external.write("snapshot/1/snapshot.json", "snapshot")
        external.write("matching-diagnostics/1/diagnostic.json", "diagnostic")
        external.write("log/gkd-20261110.log", "old-log")
        val migration = migration(internal)
        migration.prepare { external }
        val snapshotsReady = CompletableDeferred<Unit>()
        val diagnosticsReady = CompletableDeferred<Unit>()
        val continueOtherHistory = CompletableDeferred<Unit>()

        val completed =
            async(Dispatchers.IO) {
                migration.migrateHistory({ external }) { directory, result ->
                    result.getOrThrow()
                    when (directory) {
                        HistoryDirectory.Snapshot -> {
                            snapshotsReady.complete(Unit)
                            diagnosticsReady.await()
                        }
                        HistoryDirectory.MatchDiagnostics -> {
                            diagnosticsReady.complete(Unit)
                            continueOtherHistory.await()
                        }
                        HistoryDirectory.Crash,
                        HistoryDirectory.Log -> Unit
                    }
                }
            }
        try {
            withTimeout(10_000) {
                snapshotsReady.await()
                diagnosticsReady.await()
            }
            assertEquals("snapshot", internal.resolve("snapshot/1/snapshot.json").readText())
            assertFalse(internal.resolve("log/gkd-20261110-migrated.log").exists())
        } finally {
            continueOtherHistory.complete(Unit)
            if (!diagnosticsReady.isCompleted) completed.cancel()
        }
        assertTrue(completed.await())
        assertEquals("old-log", internal.resolve("log/gkd-20261110-migrated.log").readText())
    }

    @Test
    fun resumeSkipsSnapshotFilesAlreadyReleasedToTheApp() = runBlocking {
        val external = directory.newFolder("external")
        val internal = directory.newFolder("internal")
        val oldSnapshot = external.write("snapshot/1/snapshot.json", "snapshot")
        val oldDiagnostic = external.write("matching-diagnostics/1/diagnostic.json", "diagnostic")
        val conflict = internal.write("matching-diagnostics/1/diagnostic.json", "conflict")
        val migration = migration(internal)
        migration.prepare { external }

        assertFalse(
            migration.migrateHistory({ external }) { directory, result ->
                if (directory == HistoryDirectory.MatchDiagnostics) assertTrue(result.isFailure)
                else result.getOrThrow()
            },
        )
        assertTrue(internal.resolve("snapshot/1/snapshot.json").delete())
        assertTrue(conflict.delete())
        assertTrue(oldSnapshot.isFile)
        assertTrue(oldDiagnostic.isFile)

        val resumed = migration(internal)
        resumed.prepare { external }
        assertTrue(resumed.migrateHistory({ external }) { _, result -> result.getOrThrow() })
        assertFalse(internal.resolve("snapshot/1/snapshot.json").exists())
        assertEquals("diagnostic", conflict.readText())
        resumed.cleanUp { external }
        assertFalse(oldSnapshot.exists())
        assertFalse(oldDiagnostic.exists())
    }

    @Test
    fun resumesPublishingAStagedCoreWithoutRequiringTheExternalVolume() {
        val external = directory.newFolder("external")
        val internal = directory.newFolder("internal")
        external.write("db/gkd.db", "original-db")
        external.write("store/store.json", "original-settings")
        val conflict = internal.write("store/store.json", "other-settings")

        assertThrows(IOException::class.java) {
            migration(internal).prepare { external }
        }
        assertEquals("other-settings", conflict.readText())
        assertFalse(internal.resolve(".gkd").exists())
        assertTrue(conflict.delete())
        migration(internal).prepare { error("The prepared core is already internal") }
        assertEquals("original-db", internal.resolve("db/gkd.db").readText())
        assertEquals("original-settings", internal.resolve("store/store.json").readText())
        assertTrue(external.resolve("db/gkd.db").isFile)
    }

    private fun migration(destination: File) =
        ExternalStorageMigration(destination, destination.resolve("migration"))

    private fun File.write(path: String, value: String): File =
        resolve(path).apply {
            checkNotNull(parentFile).mkdirs()
            writeText(value)
        }
}
