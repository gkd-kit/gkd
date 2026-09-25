package li.gkd.app.data.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.IOException
import li.gkd.app.util.json
import li.gkd.app.util.SnapshotDisplayModeOption
import org.junit.Test
import java.nio.file.Files

class SettingsRepositoryTest {
    @Test
    fun explicitUpdateChangesStateAndSurvivesRepositoryRecreation() = runBlocking {
        val directory = Files.createTempDirectory("gkd-settings-test").toFile()
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val readScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = SettingsRepository(
                directory,
                writeScope,
                ::testDefaults,
                ::emptySet,
            )

            repository.updateSettings {
                it.copy(enableMatch = false, httpServerPort = 9123, snapshotDisplayMode = SnapshotDisplayModeOption.ByApp.value)
            }

            assertFalse(repository.settings.value.enableMatch)
            assertEquals(9123, repository.settings.value.httpServerPort)
            assertEquals(2, repository.settings.value.snapshotDisplayMode)
            withTimeout(5_000) {
                while (!directory.resolve("store.json").isFile) delay(10)
            }

            val recreated = SettingsRepository(
                directory,
                readScope,
                ::testDefaults,
                ::emptySet,
            )
            assertFalse(recreated.settings.value.enableMatch)
            assertEquals(9123, recreated.settings.value.httpServerPort)
            assertEquals(2, recreated.settings.value.snapshotDisplayMode)
        } finally {
            writeScope.cancel()
            readScope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun concurrentUpdatesPersistTheLatestState() = runBlocking {
        val directory = Files.createTempDirectory("gkd-settings-concurrent-test").toFile()
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = SettingsRepository(
                directory,
                writeScope,
                ::testDefaults,
                ::emptySet,
            )

            coroutineScope {
                List(200) {
                    async(Dispatchers.Default) { repository.incrementActionCount() }
                }.awaitAll()
            }

            assertEquals(200L, repository.actionCount.value)
            awaitFileText(directory.resolve("action_count.txt"), "200")
        } finally {
            writeScope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun writerContinuesAfterOnePersistenceFailure() = runBlocking {
        val parent = Files.createTempDirectory("gkd-settings-recovery-test").toFile()
        val directory = parent.resolve("missing")
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = SettingsRepository(
                directory,
                writeScope,
                ::testDefaults,
                ::emptySet,
            )

            repository.incrementActionCount()
            val failure = runCatching {
                withTimeout(5_000) {
                    repository.withBackupRestore(mapOf("action_count.txt" to "100")) { }
                }
            }.exceptionOrNull()
            assertTrue(failure is IOException)

            directory.mkdirs()
            repository.incrementActionCount()
            awaitFileText(directory.resolve("action_count.txt"), "2")
        } finally {
            writeScope.cancel()
            parent.deleteRecursively()
        }
    }

    @Test
    fun failedRestoreKeepsLaterEditsAndIncrementsWithoutKeepingImportedValues() = runBlocking {
        withRepository { repository, directory ->
            repository.updateSettings { it.copy(enableMatch = true, httpServerPort = 8000) }
            repository.incrementActionCount()
            repository.replaceBlockMatchAppList(setOf("local.app"))
            val entries = mapOf(
                "store.json" to json.encodeToString(testDefaults().copy(enableMatch = false, httpServerPort = 9000)),
                "action_count.txt" to "100",
                "block_match_app_list.txt" to "imported.app",
            )

            val failure = runCatching {
                repository.withBackupRestore(entries) {
                    coroutineScope {
                        val settings = async(Dispatchers.Default) {
                            repository.updateSettings { it.copy(httpServerPort = 8123) }
                            repository.updateBlockMatchAppList { it + "later.app" }
                        }
                        val increments = List(20) {
                            async(Dispatchers.Default) { repository.incrementActionCount() }
                        }
                        settings.await()
                        increments.awaitAll()
                    }
                    throw IOException("database commit failed")
                }
            }.exceptionOrNull()

            assertTrue(failure is IOException)
            assertTrue(repository.settings.value.enableMatch)
            assertEquals(8123, repository.settings.value.httpServerPort)
            assertEquals(21L, repository.actionCount.value)
            assertEquals(setOf("local.app", "later.app"), repository.blockMatchAppList.value)
            assertEquals("21", directory.resolve("action_count.txt").readText())
            val persisted = json.decodeFromString<SettingsStore>(directory.resolve("store.json").readText())
            assertEquals(repository.settings.value, persisted)
        }
    }

    @Test
    fun successfulRestoreKeepsImportedValuesAndLaterCommands() = runBlocking {
        withRepository { repository, directory ->
            val result = repository.withBackupRestore(mapOf("action_count.txt" to "100")) {
                repository.incrementActionCount()
                "committed"
            }
            awaitFileText(directory.resolve("action_count.txt"), "101")

            assertEquals("committed", result)
            assertEquals(101L, repository.actionCount.value)
            assertEquals("101", directory.resolve("action_count.txt").readText())
            // The rollback shadow must be released after success so another restore can start.
            repository.withBackupRestore(mapOf("action_count.txt" to "200")) { }
            assertEquals(200L, repository.actionCount.value)
        }
    }

    @Test
    fun cancelledRestorePersistsRollbackAndRetainsUpdatesMadeWhileSuspended() = runBlocking {
        withRepository { repository, directory ->
            repository.incrementActionCount()
            val entered = CompletableDeferred<Unit>()
            val restore = launch {
                repository.withBackupRestore(mapOf("action_count.txt" to "100")) {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
            withTimeout(5_000) { entered.await() }
            repository.incrementActionCount()
            withTimeout(5_000) { restore.cancelAndJoin() }

            assertEquals(2L, repository.actionCount.value)
            assertEquals("2", directory.resolve("action_count.txt").readText())
        }
    }

    @Test
    fun failedRestoreWriteDoesNotStartDatabaseWorkAndWriterCanRecover() = runBlocking {
        withRepository { repository, directory ->
            // A directory at the target path makes the atomic file replacement fail.
            directory.resolve("action_count.txt").mkdir()
            var databaseWorkStarted = false
            val result = runCatching {
                withTimeout(5_000) {
                    repository.withBackupRestore(mapOf("action_count.txt" to "100")) {
                        databaseWorkStarted = true
                    }
                }
            }
            assertTrue(result.exceptionOrNull() is IOException)
            assertFalse(databaseWorkStarted)
            assertEquals(0L, repository.actionCount.value)

            directory.resolve("action_count.txt").delete()
            repository.incrementActionCount()
            awaitFileText(directory.resolve("action_count.txt"), "1")
        }
    }

    private suspend fun withRepository(block: suspend (SettingsRepository, java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("gkd-settings-restore-test").toFile()
        val writer = SupervisorJob()
        try {
            val repository = SettingsRepository(
                directory, CoroutineScope(writer + Dispatchers.Default), ::testDefaults, ::emptySet,
            )
            withTimeout(10_000) { block(repository, directory) }
        } finally {
            writer.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    private suspend fun awaitFileText(file: java.io.File, expected: String) {
        withTimeout(5_000) {
            while (file.takeIf { it.isFile }?.readText() != expected) delay(10)
        }
    }

    private fun testDefaults() = SettingsStore(
        actionToast = "GKD",
        customNotifTitle = "GKD",
        updateChannel = 0,
    )
}
