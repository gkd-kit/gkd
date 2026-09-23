package li.gkd.db

import androidx.room3.Room
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

class SubscriptionConfigStoreTest {
    @Test
    fun explicitAppAndGroupSettingsSurviveChangesToTheirDefaults() = withDatabase { db, store ->
        db.subsItemDao().upsert(SubsItem(7, order = 0))
        db.subsCategoryConfigDao().upsert(SubsCategoryConfig(true, 7, 3))
        store.setAppEnabled(7, "app.one", true)
        store.updateAppGroupConfig(7, "app.one", 4) { it.copy(enable = true) }
        store.updateGlobalGroupConfig(7, 5) { it.copy(enable = true) }
        db.subsCategoryConfigDao().upsert(SubsCategoryConfig(false, 7, 3))
        assertEquals(true, db.subsAppGroupConfigDao().getConfig(7, "app.one", 4)?.enable)
        assertEquals(true, db.subsGlobalGroupConfigDao().getConfig(7, 5)?.enable)
        assertEquals(true, store.capture().appConfigs.single().enable)
    }

    @Test
    fun resettingAppGateAndGroupSettingsPreservesOtherScopesAndPageExclusions() = withDatabase { db, store ->
        store.merge(sample())
        store.setAppEnabled(7, "app.one", null)
        store.updateAppGroupConfig(7, "app.one", 4) { it.copy(enable = null) }
        assertEquals(emptyList(), store.capture().appConfigs)
        assertEquals(SubsAppGroupConfig(7, "app.one", 4, null, "app.Activity"),
            db.subsAppGroupConfigDao().getConfig(7, "app.one", 4))
        assertEquals(sample().categoryConfigs, store.capture().categoryConfigs)
        assertEquals(sample().globalGroupConfigs, store.capture().globalGroupConfigs)
        store.updateAppGroupConfig(7, "app.one", 4) { it.copy(exclude = "") }
        assertEquals(null, db.subsAppGroupConfigDao().getConfig(7, "app.one", 4))
        store.updateGlobalGroupConfig(7, 4) { it.copy(enable = null, exclude = "") }
        assertEquals(null, db.subsGlobalGroupConfigDao().getConfig(7, 4))
    }

    @Test
    fun aFailedMixedScopeTransactionDoesNotLeavePartiallyChangedSwitches() = withDatabase { db, store ->
        store.merge(sample())
        val before = store.capture()
        assertFailsWith<IllegalStateException> {
            db.withWriteTransaction {
                store.setAppEnabled(7, "app.one", true)
                store.updateAppGroupConfig(7, "app.one", 4) { it.copy(enable = true) }
                store.updateGlobalGroupConfig(7, 4) { it.copy(enable = false) }
                error("write failed")
            }
        }
        assertEquals(before, store.capture())
    }

    private fun withDatabase(block: suspend (AppDb, SubscriptionConfigStore) -> Unit) = runBlocking {
        val directory = createTempDirectory("config-store-test")
        val database = Room.databaseBuilder<AppDb>(directory.resolve("test.db").toString())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            block(database, SubscriptionConfigStore(database))
        } finally {
            database.close()
            directory.toFile().deleteRecursively()
        }
    }

    private fun sample(subsId: Long = 7) = SubscriptionConfigSnapshot(
        subsItems = listOf(SubsItem(subsId, ctime = 1, mtime = 2, enable = true, order = 0)),
        appConfigs = listOf(SubsAppConfig(false, subsId, "app.one")),
        categoryConfigs = listOf(SubsCategoryConfig(null, subsId, 3)),
        appGroupConfigs = listOf(SubsAppGroupConfig(subsId, "app.one", 4, false, "app.Activity")),
        globalGroupConfigs = listOf(SubsGlobalGroupConfig(subsId, 4, true, "app.two")),
    )

    @Test
    fun importingTwiceKeepsLocalBusinessKeysAndAddsOnlyMissingOverrides() = withDatabase { _, store ->
        val local = sample()
        store.merge(local)
        val imported = local.copy(
            appConfigs = listOf(SubsAppConfig(true, 7, "app.one"), SubsAppConfig(true, 99, "orphan")),
            categoryConfigs = listOf(SubsCategoryConfig(false, 7, 3)),
            appGroupConfigs = listOf(SubsAppGroupConfig(7, "app.one", 4, true, "changed")),
            globalGroupConfigs = listOf(SubsGlobalGroupConfig(7, 4, false), SubsGlobalGroupConfig(7, 5, false)),
        )
        assertEquals(1, store.merge(imported))
        assertEquals(1, store.merge(imported))
        assertEquals(local.copy(globalGroupConfigs = local.globalGroupConfigs + SubsGlobalGroupConfig(7, 5, false)), store.capture())
    }

    @Test
    fun subscriptionUpsertPreservesOverridesAndDeletionCascadesToAllFourTables() = withDatabase { db, store ->
        val original = sample()
        store.merge(original)
        val updatedItem = original.subsItems.single().copy(mtime = 3, enable = false)
        db.subsItemDao().upsert(updatedItem)
        assertEquals(original.copy(subsItems = listOf(updatedItem)), store.capture())
        db.subsItemDao().deleteById(7)
        assertEquals(SubscriptionConfigSnapshot(), store.capture())
    }

    @Test
    fun checkpointRestoresDeletedAndChangedOverridesAndRemovesImportedRows() = withDatabase { db, store ->
        val original = sample()
        store.merge(original)
        val checkpoint = store.capture()
        store.merge(sample(8))
        db.subsCategoryConfigDao().upsert(SubsCategoryConfig(false, 7, 3))
        db.subsAppGroupConfigDao().delete(*original.appGroupConfigs.toTypedArray())
        store.restore(checkpoint)
        assertEquals(original, store.capture())

        db.subsItemDao().deleteById(7)
        store.restore(checkpoint)
        assertEquals(original, store.capture())
    }

    @Test
    fun observedSnapshotsContainCompleteTransactionsAcrossBothGroupTables() = withDatabase { db, store ->
        coroutineScope {
            val snapshots = Channel<SubscriptionConfigSnapshot>(Channel.UNLIMITED)
            val collector = launch { store.observe().collect { snapshots.send(it) } }
            try {
                withTimeout(5_000) {
                    assertEquals(SubscriptionConfigSnapshot(), snapshots.receive())
                    val original = sample()
                    store.merge(original)
                    assertEquals(original, snapshots.receive())
                    val app = original.appGroupConfigs.single().copy(enable = true)
                    val global = original.globalGroupConfigs.single().copy(enable = false)
                    db.withWriteTransaction {
                        db.subsAppGroupConfigDao().upsert(app)
                        yield()
                        db.subsGlobalGroupConfigDao().upsert(global)
                    }
                    assertEquals(
                        original.copy(appGroupConfigs = listOf(app), globalGroupConfigs = listOf(global)),
                        snapshots.receive(),
                    )
                }
            } finally {
                collector.cancelAndJoin()
                snapshots.close()
            }
        }
    }

    @Test
    fun failedImportTransactionRollsBackAllConfigurationTables() = withDatabase { db, store ->
        store.merge(sample())
        val before = store.capture()
        assertFailsWith<IllegalStateException> {
            db.withWriteTransaction {
                store.merge(sample(8))
                error("import failed")
            }
        }
        assertEquals(before, store.capture())
    }
    @Test
    fun concurrentGlobalGroupUpdatesPreserveEveryChangeToTheSameColumn() = withDatabase { db, store ->
        db.subsItemDao().upsert(SubsItem(7, order = 0))
        coroutineScope {
            List(40) { index ->
                async(Dispatchers.Default) {
                    store.updateGlobalGroupConfig(7, 4) { current ->
                        current.copy(exclude = current.exclude + "$index\n")
                    }
                }
            }.awaitAll()
        }
        val actual = db.subsGlobalGroupConfigDao().getConfig(7, 4)!!.exclude
            .lineSequence().filter { it.isNotEmpty() }.map { it.toInt() }.toSet()
        assertEquals((0 until 40).toSet(), actual)
    }

    @Test
    fun changingAnAppGroupSwitchKeepsTheLatestExclusionAndFailedEditsLeaveItUntouched() =
        withDatabase { db, store ->
            db.subsItemDao().upsert(SubsItem(7, order = 0))
            store.updateAppGroupConfig(7, "app.one", 4) { it.copy(exclude = "new.Activity") }
            store.updateAppGroupConfig(7, "app.one", 4) { it.copy(enable = false) }
            val expected = SubsAppGroupConfig(7, "app.one", 4, false, "new.Activity")
            assertEquals(expected, db.subsAppGroupConfigDao().getConfig(7, "app.one", 4))

            assertFailsWith<IllegalStateException> {
                store.updateAppGroupConfig(7, "app.one", 4) { current ->
                    check(current.exclude == "old.Activity") { "stale editor" }
                    current.copy(exclude = "edited.Activity")
                }
            }
            assertEquals(expected, db.subsAppGroupConfigDao().getConfig(7, "app.one", 4))
        }

    @Test
    fun failedImportDoesNotRollBackANormalWriteWaitingForTheTransaction() = withDatabase { db, store ->
        store.merge(sample())
        coroutineScope {
            val importing = CompletableDeferred<Unit>()
            val normalWriteStarted = CompletableDeferred<Unit>()
            val normalWrite = async(Dispatchers.Default) {
                importing.await()
                normalWriteStarted.complete(Unit)
                db.subsItemDao().updateEnable(7, false)
            }
            assertFailsWith<IllegalStateException> {
                db.withWriteTransaction {
                    store.merge(sample(8))
                    importing.complete(Unit)
                    normalWriteStarted.await()
                    error("restore failed")
                }
            }
            withTimeout(5_000) { normalWrite.await() }
            val expected = sample().let { original ->
                original.copy(subsItems = original.subsItems.map { it.copy(enable = false) })
            }
            assertEquals(expected, store.capture())
        }
    }

}
