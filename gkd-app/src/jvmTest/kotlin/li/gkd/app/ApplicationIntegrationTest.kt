package li.gkd.app

import androidx.lifecycle.ViewModelStore
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import li.gkd.app.backup.BackupManager
import li.gkd.app.crash.assertCrashReports
import li.gkd.app.model.AppInfo
import li.gkd.app.model.AppInventory
import li.gkd.app.network.assertInspectionProtocol
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SettingsRepositoryChecks
import li.gkd.app.state.Loadable
import li.gkd.app.storage.AppStorageLayout
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.appStorage
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionJson
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.home.HomeViewModel
import li.gkd.app.util.LogUtils
import li.gkd.db.Db
import li.gkd.db.SubscriptionConfigStore

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationIntegrationTest {
    @Test
    fun applicationImportsProfileEditsDataAndRestoresBackup() = runBlocking {
        System.setProperty(
            "gkd.projectRoot",
            System.getProperty("gkd.projectRoot", File("..").canonicalPath)
        )
        DesktopStorage.initialize(test = true)
        val session = DesktopStorage.sessionDirectory
        try {
            val profileDir = DesktopStorage.profile.apply { mkdirs() }
            profileDir.resolve("seed.json")
                .writeText("""{"blocked":["example.blocked"],"subscriptions":{"92":false,"13":true}}""")
            profileDir.resolve("settings.json").writeText("""{"showBlockApp":false}""")
            profileDir.resolve("apps.json").writeText(
                Json.encodeToString(
                    AppInventory(
                        apps = listOf(
                            AppInfo("test.alpha", "Alpha", 1, "1", false, 0, false, 0),
                            AppInfo("test.beta", "Beta", 1, "1", false, 0, false, 0),
                        )
                    )
                )
            )
            profileDir.resolve("subscriptions").mkdirs()
            for (id in listOf(13, 92)) {
                profileDir.resolve("subscriptions/$id.json")
                    .writeText("""{"id":$id,"name":"fixture-$id","version":1}""")
            }
            // Existing Android settings files are loaded exactly once at application startup.
            val store = DesktopStorage.data.resolve("files/store").apply { mkdirs() }
            store.resolve("store.json")
                .writeText("""{"appGroupType":7,"enableDynamicColor":false,"futureOption":123}""")
            store.resolve("overlay_position.json").writeText("""{"legacy":[12,34]}""")
            // An Android recovery file without a database row must keep its user-supplied name.
            val subscriptions = DesktopStorage.data.resolve("files/subscription").apply { mkdirs() }
            subscriptions.resolve("${li.gkd.db.LOCAL_SUBS_ID}.json.bak").writeText(
                """{"id":${li.gkd.db.LOCAL_SUBS_ID},"name":"My saved rules","version":7}"""
            )
            val profile = DesktopProfile
            assertEquals(setOf("example.blocked"), profile.blocked)
            // Process-lifetime singletons follow the real application lifecycle, exactly once.
            DesktopRuntime(
                SimulatorStore(
                    SimulatorSettings(
                        device = SimulatedDevice(
                            wifi = false,
                            mobileSignal = 0
                        )
                    )
                )
            ).use { runtime ->
                assertLogCallerValues()
                withTimeout(20_000) { runtime.subscriptionInitialization.join() }
                val items = Db.subsItemDao.queryAll().associateBy { it.id }
                assertTrue(items.getValue(13).enable)
                assertFalse(items.getValue(92).enable)
                assertTrue(appStorage().subscription.resolve("92.json").isFile)
                assertPlatformInputs(runtime.simulator)
                assertSubscriptionCreationNames()
                assertHomeLifecycle()
                assertAppRuleParentVisibility()
                SettingsRepositoryChecks.run(appStorage().store, profile.appName)
                li.gkd.app.network.assertUpdateClient(session.resolve("update-client"))
                assertIgnoredVersionsRetry()
                assertCrashReports()
                assertCookiePersistence(appStorage().privateStore)
                li.gkd.app.rule.RuleSwitchPolicyTest()
                    .requestRejectsChangedSwitchesButAcceptsConcurrentPageAndOtherAppEdits()
                li.gkd.app.snapshot.SnapshotCaptureRepositoryTest(appStorage()).runAll()
                li.gkd.app.snapshot.SnapshotStoreTest(appStorage()).runAll()
                DesktopAppCatalogTest().profileReloadUsesSharedSelectionAndRecoversFromCorruptFile(
                    profileDir
                )
                li.gkd.app.record.assertRuntimeRecords(appStorage().database)
                li.gkd.app.storage.LogArchiveChecks().apply {
                    archiveContainsHostMaterialsAndProductionLogsAndCleansStaging()
                    materialFailurePreservesCauseAndCleansAlreadyWrittenFiles()
                }
                assertBackupRoundTrip(appStorage())
            }
            assertFailsWith<IllegalStateException> { li.gkd.app.network.isNetworkAvailable() }
            assertFailsWith<IllegalStateException> { li.gkd.app.app.openAppCatalog() }
            val logs = appStorage().log.listFiles()!!.associate { it.name to it.readText() }
            LogUtils.d("log-after-close")
            LogUtils.flush()
            LogUtils.close()
            assertEquals(logs, appStorage().log.listFiles()!!.associate { it.name to it.readText() })
        } finally {
            session.deleteRecursively()
            DesktopStorage.initialize(test = false)
        }
    }

    private suspend fun assertPlatformInputs(simulator: SimulatorStore) {
        val previous = simulator.settings.value
        try {
            // The repository uses the active host's current network state on every operation.
            assertEquals(
                li.gkd.app.subscription.SubscriptionResult.Failure(
                    li.gkd.app.subscription.SubscriptionResult.FailureReason.NetworkUnavailable
                ),
                SubscriptionRepository.refresh(),
            )
            simulator.update { it.copy(device = it.device.copy(wifi = true)) }
            // These imported subscriptions have no update URL: success requires no external network.
            assertTrue(SubscriptionRepository.refresh() is li.gkd.app.subscription.SubscriptionResult.Success)
            simulator.update { it.copy(device = it.device.copy(wifi = false, mobileSignal = 0)) }
            assertEquals(
                li.gkd.app.subscription.SubscriptionResult.Failure(
                    li.gkd.app.subscription.SubscriptionResult.FailureReason.NetworkUnavailable
                ),
                SubscriptionRepository.refresh(),
            )
            val query = li.gkd.app.app.openAppCatalog()
            assertTrue(query.isCurrent())
            simulator.update {
                it.copy(permissions = it.permissions.copy(canQueryPackages = !query.canQueryPackages))
            }
            assertFalse(query.isCurrent())
            li.gkd.app.app.AppInfoRepository.refresh()
            assertEquals(!query.canQueryPackages, li.gkd.app.app.AppInfoRepository.snapshot!!.canQueryPackages)
        } finally {
            simulator.update {
                it.copy(
                    device = it.device.copy(wifi = previous.device.wifi, mobileSignal = previous.device.mobileSignal),
                    permissions = it.permissions.copy(canQueryPackages = previous.permissions.canQueryPackages),
                )
            }
            li.gkd.app.app.AppInfoRepository.refresh()
        }
    }

    private suspend fun assertSubscriptionCreationNames() {
        val localId = li.gkd.db.LOCAL_SUBS_ID
        val memoryId = li.gkd.db.LOCAL_HTTP_SUBS_ID
        assertEquals("My saved rules", SubscriptionRepository.awaitSnapshot().subscriptions.getValue(localId).name)
        val localFile = appStorage().subscription.resolve("$localId.json")
        val originalBytes = localFile.readBytes()
        // A retry with a different language creates only missing built-in data.
        Db.subsItemDao.upsert(li.gkd.db.SubsItem(memoryId, order = 0))
        SubscriptionRepository.initialize(li.gkd.app.subscription.SubscriptionDefaults("Local rules", "Memory rules"))
        assertTrue(originalBytes.contentEquals(localFile.readBytes()))
        val memoryFile = appStorage().subscription.resolve("$memoryId.json")
        assertEquals("Memory rules", RawSubscription.parse(memoryFile.readText()).name)
        val memoryBytes = memoryFile.readBytes()
        SubscriptionRepository.initialize(li.gkd.app.subscription.SubscriptionDefaults("本地订阅", "内存订阅"))
        assertTrue(originalBytes.contentEquals(localFile.readBytes()))
        assertTrue(memoryBytes.contentEquals(memoryFile.readBytes()))
        // A failed built-in file creation stays local to that subscription and can be retried.
        assertTrue(memoryFile.delete())
        assertTrue(memoryFile.mkdir())
        val obstruction = memoryFile.resolve("occupied").apply { writeText("blocked") }
        SubscriptionRepository.initialize(li.gkd.app.subscription.SubscriptionDefaults("Other", "Retry memory"))
        assertTrue(memoryId in SubscriptionRepository.awaitSnapshot().loadErrors)
        assertTrue(originalBytes.contentEquals(localFile.readBytes()))
        assertTrue(obstruction.delete())
        assertTrue(memoryFile.delete())
        SubscriptionRepository.initialize(li.gkd.app.subscription.SubscriptionDefaults("Other", "Retry memory"))
        assertFalse(memoryId in SubscriptionRepository.awaitSnapshot().loadErrors)
        assertEquals("Retry memory", RawSubscription.parse(memoryFile.readText()).name)
        // First local creation uses the operation's name; subsequent retries leave it untouched.
        Db.subsItemDao.deleteById(localId)
        assertTrue(localFile.delete())
        SubscriptionRepository.initialize(li.gkd.app.subscription.SubscriptionDefaults("Fresh local", "Other memory"))
        assertEquals("Fresh local", RawSubscription.parse(localFile.readText()).name)
        SubscriptionRepository.initialize(li.gkd.app.subscription.SubscriptionDefaults("Another locale", "Other memory"))
        assertEquals("Fresh local", SubscriptionRepository.awaitSnapshot().subscriptions.getValue(localId).name)
    }

    private suspend fun assertIgnoredVersionsRetry() = kotlinx.coroutines.coroutineScope {
        val file = appStorage().store.resolve("ignore_version_list.json")
        val previous = file.takeIf { it.exists() }?.readBytes()
        val messages = mutableListOf<String>()
        var networkChecks = 0
        try {
            file.writeText("invalid-json")
            val update = li.gkd.app.ui.update.UpdateStatus(
                this, 1, "test", messages::add,
                networkAvailable = { networkChecks++; false },
            )
            suspend fun checkManually() {
                update.checkUpdate(manual = true)
                withTimeout(5_000) { update.checkUpdatingFlow.first { !it } }
            }
            checkManually()
            assertEquals(0, networkChecks)
            assertEquals(1, messages.size)
            assertEquals("invalid-json", file.readText())
            file.delete()
            check(file.mkdir()) // A read failure must not be treated as an empty ignore list either.
            checkManually()
            assertEquals(0, networkChecks)
            assertEquals(2, messages.size)
            assertTrue(file.isDirectory)
            check(file.delete())
            file.writeText("[10,20]")
            checkManually()
            assertEquals(1, networkChecks) // The same workflow retries the repaired file.
            assertEquals("[10,20]", file.readText())
            assertEquals(3, messages.size)
        } finally {
            if (file.isDirectory) check(file.delete())
            if (previous == null) file.delete() else file.writeBytes(previous)
        }
    }

    private fun assertLogCallerValues() {
        var value = "log-capture-before"
        val mutable = object {
            override fun toString() = value
        }
        val caller = Thread.currentThread().name
        LogUtils.d(mutable)
        value = "log-capture-after"
        LogUtils.flush()
        val text = appStorage().log.listFiles()!!.joinToString("\n") { it.readText() }
        assertTrue(text.contains("\nlog-capture-before\n\n"))
        assertFalse(text.contains("log-capture-after"))
        assertTrue(text.contains(" ApplicationIntegrationTest, $caller, "), text)
        assertTrue(text.contains("assertLogCallerValues"), text)
    }

    private suspend fun assertCookiePersistence(directory: File) {
        val store = li.gkd.app.settings.GithubCookieStore
        val original = store.value.value
        val blocked = directory.resolve("github_cookie.txt.tmp")
        try {
            store.save("old")
            check(blocked.mkdir())
            assertFailsWith<IOException> { store.save("new") }
            assertEquals("old", store.value.value)
            blocked.delete()
            store.save("  new\r\n  ")
            assertEquals("new", store.value.value)
            assertEquals("new", directory.resolve("github_cookie.txt").readText())
        } finally {
            blocked.delete()
            store.save(original)
        }
    }

    private suspend fun assertAppRuleParentVisibility() {
        val id = -991L
        val subscription = RawSubscription.parse(
            """{
            id: -991, name: 'Parent visibility', version: 1,
            globalGroups: [
                {key: 1, name: 'Default on', rules: [{matches: '[text="Ad"]'}]},
                {key: 2, name: 'Default off', enable: false, rules: [{matches: '[text="Ad"]'}]}
            ],
            apps: [{id: 'test.alpha', groups: [{key: 101, name: 'App rule', rules: [{matches: '[text="Ad"]'}]}]}]
        }"""
        )
        val owner = ViewModelStore()
        val originalShowDisabled = SettingsRepository.settings.value.showDisabledRule
        try {
            SubscriptionRepository.saveWithItem(
                subscription,
                li.gkd.db.SubsItem(id, order = 0, enable = true)
            )
            val vm = li.gkd.app.ui.subscription.AppConfigViewModel(
                li.gkd.app.ui.navigation.AppConfigRoute("test.alpha")
            ).also { owner.put("app-rules", it) }

            suspend fun expectGroups(vararg keys: Int) {
                withTimeout(5_000) {
                    vm.uiState.first { loaded ->
                        loaded is Loadable.Ready && loaded.value.subsPairs
                            .filter { it.first.subsItem.id == id }
                            .flatMap { it.second }.map { it.key }.toSet() == keys.toSet()
                    }
                }
            }
            expectGroups(1, 101)
            // A per-app allow override must not bypass the global group's parent switch.
            SubscriptionConfigStore.updateGlobalGroupConfig(id, 1) {
                it.copy(enable = false, exclude = "!test.alpha")
            }
            expectGroups(101)
            vm.setShowDisabledRules(true)
            SettingsRepository.awaitPersistence()
            expectGroups(101)
            SubscriptionConfigStore.updateGlobalGroupConfig(id, 2) { it.copy(enable = true) }
            expectGroups(2, 101)
            SubscriptionConfigStore.setAppEnabled(id, "test.alpha", false)
            expectGroups(2)
            SubscriptionConfigStore.updateGlobalGroupConfig(id, 2) { it.copy(enable = null) }
            expectGroups()
            SubscriptionConfigStore.updateGlobalGroupConfig(id, 1) { it.copy(enable = null) }
            expectGroups(1)
            val saved =
                SubscriptionConfigStore.capture().globalGroupConfigs.single { it.subsId == id && it.groupKey == 1 }
            assertEquals("!test.alpha", saved.exclude)
            assertEquals(subscription, SubscriptionRepository.awaitSnapshot().subscriptions[id])
        } finally {
            owner.clear()
            SettingsRepository.updateSettings { it.copy(showDisabledRule = originalShowDisabled) }
            SettingsRepository.awaitPersistence()
            SubscriptionRepository.delete(id)
        }
    }

    private suspend fun assertHomeLifecycle() {
        val owner = ViewModelStore()
        val originalPowerWarning = SettingsRepository.settings.value.subsPowerWarn
        val first = li.gkd.db.SubsItem(id = 900001, order = 100, enable = true)
        val second = li.gkd.db.SubsItem(id = 900002, order = 101)
        try {
            val vm = HomeViewModel().also { owner.put("home", it) }
            withTimeout(5_000) { vm.appsState.first { it.value?.apps?.size == 2 } }
            withTimeout(5_000) { vm.latestState.first { it is li.gkd.app.state.Loadable.Ready } }
            withTimeout(5_000) { vm.subscriptionsState.first { it is li.gkd.app.state.Loadable.Ready } }
            SettingsRepository.updateSettings { it.copy(subsPowerWarn = true) }
            // Do not wait for the presentation flow: commands must consult current persisted items.
            Db.subsItemDao.upsert(first, second)
            vm.requestSubscriptionEnabled(second, true)
            assertEquals(second, vm.powerWarningItemFlow.value)
            assertFalse(Db.subsItemDao.queryAll().single { it.id == second.id }.enable)
            vm.confirmPowerWarning()
            assertTrue(Db.subsItemDao.queryAll().single { it.id == second.id }.enable)
            vm.requestSubscriptionEnabled(second, false)
            assertFalse(Db.subsItemDao.queryAll().single { it.id == second.id }.enable)
            assertTrue(vm.scope.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
            owner.clear()
            assertTrue(vm.scope.coroutineContext[kotlinx.coroutines.Job]!!.isCancelled)
        } finally {
            owner.clear()
            Db.subsItemDao.deleteById(first.id, second.id)
            SettingsRepository.updateSettings { it.copy(subsPowerWarn = originalPowerWarning) }
        }
    }

    private suspend fun assertBackupRoundTrip(layout: AppStorageLayout) {
        val repository = SubscriptionRepository
        val settings = SettingsRepository
        val manager =
            BackupManager
        settings.updateSettings { it.copy(actionToast = "archive") }
        settings.awaitPersistence()
        val original = repository.awaitSnapshot().subscriptions.getValue(-2)
        val archive = manager.exportData()
        ZipFile(archive).use { zip ->
            assertNotNull(zip.getEntry("store/store.json"))
            assertNotNull(zip.getEntry("subscription/-2.json"))
            assertNotNull(zip.getEntry("db.json"))
        }
        settings.updateSettings { it.copy(actionToast = "later") }
        settings.awaitPersistence()
        androidx.sqlite.driver.bundled.BundledSQLiteDriver().open(layout.database.absolutePath)
            .use { connection ->
                connection.prepare("CREATE TRIGGER test_backup_failure BEFORE INSERT ON subs_item BEGIN SELECT RAISE(FAIL, 'backup failure'); END")
                    .use { it.step() }
                try {
                    assertFails { manager.importData(FileSource.Local(archive)) }
                } finally {
                    connection.prepare("DROP TRIGGER test_backup_failure").use { it.step() }
                }
            }
        assertEquals("later", settings.settings.value.actionToast)
        assertEquals(
            SubscriptionJson.json.encodeToString(original),
            SubscriptionJson.json.encodeToString(
                repository.awaitSnapshot().subscriptions.getValue(-2)
            )
        )
        assertFailsWith<CancellationException> {
            manager.importData { throw CancellationException("cancelled selection") }
        }
        assertEquals("later", settings.settings.value.actionToast)
        manager.importData(FileSource.Uri(archive.toURI().toString()))
        assertEquals("archive", settings.settings.value.actionToast)
        assertEquals(
            SubscriptionJson.json.encodeToString(original.copy(version = original.version + 1)),
            SubscriptionJson.json.encodeToString(
                repository.awaitSnapshot().subscriptions.getValue(-2)
            )
        )
        assertTrue(layout.tempCache.listFiles().orEmpty().isEmpty())
        assertInspectionProtocol(layout, repository)
    }
}
