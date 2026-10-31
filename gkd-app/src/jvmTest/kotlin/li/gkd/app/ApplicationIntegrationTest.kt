package li.gkd.app

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModelStore
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.app.openAppCatalog
import li.gkd.app.backup.BackupManager
import li.gkd.app.backup.assertBackupSnapshotConsistency
import li.gkd.app.crash.assertCrashReports
import li.gkd.app.external.assertExternalCalls
import li.gkd.app.logging.LogUtils
import li.gkd.app.model.AppInfo
import li.gkd.app.model.AppInventory
import li.gkd.app.network.assertUpdateClient
import li.gkd.app.network.inspection.assertDesktopDebugServiceLifecycle
import li.gkd.app.network.inspection.assertInspectionProtocol
import li.gkd.app.network.isNetworkRequestAllowed
import li.gkd.app.record.assertRuntimeRecords
import li.gkd.app.resources.*
import li.gkd.app.rule.RuleSwitchPolicyTest
import li.gkd.app.settings.AutomatorMode
import li.gkd.app.settings.GithubCookieStore
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SettingsRepositoryChecks
import li.gkd.app.snapshot.SnapshotCaptureRepositoryTest
import li.gkd.app.snapshot.SnapshotStoreTest
import li.gkd.app.snapshot.assertSnapshotSharing
import li.gkd.app.state.Loadable
import li.gkd.app.storage.AppStorageLayout
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.LogArchiveChecks
import li.gkd.app.storage.appStorage
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.app.subscription.SubscriptionDefaults
import li.gkd.app.subscription.SubscriptionJson
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.subscription.SubscriptionResult
import li.gkd.app.subscription.assertCategorySaveResults
import li.gkd.app.subscription.assertUpdateErrorDismissal
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.ToastState
import li.gkd.app.ui.home.BottomNavItem
import li.gkd.app.ui.home.HomeState
import li.gkd.app.ui.home.HomeViewModel
import li.gkd.app.ui.navigation.AboutRoute
import li.gkd.app.ui.navigation.ActionLogRoute
import li.gkd.app.ui.navigation.AppConfigRoute
import li.gkd.app.ui.navigation.HomeRoute
import li.gkd.app.ui.navigation.SnapshotPageRoute
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.subscription.ActionLogViewModel
import li.gkd.app.ui.subscription.AppConfigViewModel
import li.gkd.app.ui.subscription.SubsAppGroupListViewModel
import li.gkd.app.ui.update.UpdateStatus
import li.gkd.db.ActionLog
import li.gkd.db.Db
import li.gkd.db.RuleGroupType
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigStore
import li.gkd.db.SubscriptionIds
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationIntegrationTest {
    @Test
    fun applicationImportsProfileEditsDataAndRestoresBackup() = runBlocking {
        System.setProperty(
            "gkd.projectRoot",
            System.getProperty("gkd.projectRoot", File("..").canonicalPath),
        )
        DesktopStorage.initialize(test = true)
        val session = DesktopStorage.sessionDirectory
        try {
            val profileDir = DesktopStorage.profile.apply { mkdirs() }
            profileDir
                .resolve("seed.json")
                .writeText(
                    """{"blocked":["example.blocked"],"subscriptions":{"92":false,"13":true}}""",
                )
            profileDir.resolve("settings.json").writeText("""{"showBlockApp":false}""")
            profileDir
                .resolve("apps.json")
                .writeText(
                    Json.encodeToString(
                        AppInventory(
                            apps =
                                listOf(
                                    AppInfo("test.alpha", "Alpha", 1, "1", false, 0, false, 0),
                                    AppInfo("test.beta", "Beta", 1, "1", false, 0, false, 0),
                                ),
                        ),
                    ),
                )
            profileDir.resolve("subscriptions").mkdirs()
            for (id in listOf(13, 92)) {
                profileDir
                    .resolve("subscriptions/$id.json")
                    .writeText("""{"id":$id,"name":"fixture-$id","version":1}""")
            }
            // Existing Android settings files are loaded exactly once at application startup.
            val store = DesktopStorage.data.resolve("files/store").apply { mkdirs() }
            store
                .resolve("store.json")
                .writeText("""{"appGroupType":7,"enableDynamicColor":false,"futureOption":123}""")
            store.resolve("overlay_position.json").writeText("""{"legacy":[12,34]}""")
            // Desktop must remain accepted even when an existing Android file says otherwise.
            store.resolve("terms_accepted.txt").writeText("false")
            // An Android recovery file without a database row must keep its user-supplied name.
            val subscriptions = DesktopStorage.data.resolve("files/subscription").apply { mkdirs() }
            subscriptions
                .resolve("${SubscriptionIds.LOCAL}.json.bak")
                .writeText(
                    """{"id":${SubscriptionIds.LOCAL},"name":"My saved rules","version":7}""",
                )
            val profile = DesktopProfile
            assertEquals(setOf("example.blocked"), profile.blocked)
            // Process-lifetime singletons follow the real application lifecycle, exactly once.
            DesktopRuntime(
                    SimulatorStore(
                        SimulatorSettings(
                            device =
                                SimulatedDevice(
                                    wifi = false,
                                    mobileSignal = 0,
                                ),
                        ),
                    ),
                )
                .use { runtime ->
                    assertDeferredModeHandoff(runtime)
                    assertLogCallerValues()
                    withTimeout(20_000) { runtime.subscriptionInitialization.join() }
                    withTimeout(5_000) { runtime.crashInitialization.join() }
                    assertDesktopDebugServiceLifecycle(runtime)
                    val items = Db.subsItemDao.queryAll().associateBy { it.id }
                    assertTrue(items.getValue(13).enable)
                    assertFalse(items.getValue(92).enable)
                    assertTrue(appStorage().subscription.resolve("92.json").isFile)
                    assertPlatformInputs(runtime.simulator)
                    assertSubscriptionCreationNames()
                    assertCategorySaveResults()
                    assertUpdateErrorDismissal(runtime.simulator)
                    assertHomeLifecycle()
                    assertActionLogSubscriptionLoadFailure()
                    assertRootNavigationLifecycle(runtime)
                    assertAppRuleParentVisibility()
                    assertAppRuleReorderPersistence()
                    SettingsRepositoryChecks.run(appStorage().store, META.appName)
                    assertUpdateClient(session.resolve("update-client"))
                    assertManualUpdateIgnoresStoredVersions(runtime.toast)
                    assertCrashReports()
                    assertCookiePersistence(appStorage().privateStore)
                    assertExternalCalls()
                    RuleSwitchPolicyTest()
                        .requestRejectsChangedSwitchesButAcceptsConcurrentPageAndOtherAppEdits()
                    SnapshotCaptureRepositoryTest(appStorage()).runAll()
                    SnapshotStoreTest(appStorage()).runAll()
                    assertSnapshotSharing(appStorage())
                    DesktopAppCatalogTest()
                        .profileReloadUsesSharedSelectionAndRecoversFromCorruptFile(
                            profileDir,
                        )
                    assertRuntimeRecords(appStorage().database)
                    LogArchiveChecks().apply {
                        archiveContainsMaterialsAndExpiredCacheIsCleanedByMaintenance()
                        materialFailurePreservesCauseAndLeavesFilesForCacheMaintenance()
                    }
                    assertBackupSnapshotConsistency()
                    assertBackupRoundTrip(appStorage())
                }
            assertFailsWith<IllegalStateException> { isNetworkRequestAllowed() }
            assertFailsWith<IllegalStateException> { openAppCatalog() }
            val logs = appStorage().log.listFiles()!!.associate { it.name to it.readText() }
            LogUtils.d("log-after-close")
            LogUtils.flush()
            LogUtils.close()
            assertEquals(
                logs,
                appStorage().log.listFiles()!!.associate { it.name to it.readText() },
            )
        } finally {
            session.deleteRecursively()
            DesktopStorage.initialize(test = false)
        }
    }

    private suspend fun assertPlatformInputs(simulator: SimulatorStore) {
        val previous = simulator.settings.value
        try {
            val beforeOfflineRefresh = SubscriptionRepository.snapshotFlow.value
            // The repository uses the active host's current network state on every operation.
            assertEquals(
                SubscriptionResult.Failure(
                    SubscriptionResult.FailureReason.NetworkUnavailable,
                ),
                SubscriptionRepository.refresh(),
            )
            // An offline preflight must not replace subscription data or update errors.
            assertEquals(beforeOfflineRefresh, SubscriptionRepository.snapshotFlow.value)
            simulator.update { it.copy(device = it.device.copy(wifi = true)) }
            // These imported subscriptions have no update URL: success requires no external
            // network.
            assertTrue(SubscriptionRepository.refresh() is SubscriptionResult.Success)
            simulator.update { it.copy(device = it.device.copy(wifi = false, mobileSignal = 0)) }
            assertEquals(
                SubscriptionResult.Failure(
                    SubscriptionResult.FailureReason.NetworkUnavailable,
                ),
                SubscriptionRepository.refresh(),
            )
            val query = openAppCatalog()
            assertTrue(query.isCurrent())
            simulator.update {
                it.copy(
                    permissions = it.permissions.copy(canQueryPackages = !query.canQueryPackages),
                )
            }
            assertFalse(query.isCurrent())
            AppInfoRepository.refresh()
            assertEquals(!query.canQueryPackages, AppInfoRepository.snapshot!!.canQueryPackages)
        } finally {
            simulator.update {
                it.copy(
                    device =
                        it.device.copy(
                            wifi = previous.device.wifi,
                            mobileSignal = previous.device.mobileSignal,
                        ),
                    permissions =
                        it.permissions.copy(
                            canQueryPackages = previous.permissions.canQueryPackages,
                        ),
                )
            }
            AppInfoRepository.refresh()
        }
    }

    private suspend fun assertSubscriptionCreationNames() {
        val localId = SubscriptionIds.LOCAL
        val memoryId = SubscriptionIds.LOCAL_HTTP
        assertEquals(
            "My saved rules",
            SubscriptionRepository.awaitSnapshot().subscriptions.getValue(localId).name,
        )
        val localFile = appStorage().subscription.resolve("$localId.json")
        val originalBytes = localFile.readBytes()
        // A retry with a different language creates only missing built-in data.
        Db.subsItemDao.upsert(SubsItem(memoryId, order = 0))
        SubscriptionRepository.initialize(SubscriptionDefaults("Local rules", "Memory rules"))
        assertTrue(originalBytes.contentEquals(localFile.readBytes()))
        val memoryFile = appStorage().subscription.resolve("$memoryId.json")
        assertEquals("Memory rules", RawSubscriptionParser.parse(memoryFile.readText()).name)
        val memoryBytes = memoryFile.readBytes()
        SubscriptionRepository.initialize(SubscriptionDefaults("本地订阅", "内存订阅"))
        assertTrue(originalBytes.contentEquals(localFile.readBytes()))
        assertTrue(memoryBytes.contentEquals(memoryFile.readBytes()))
        // A failed built-in file creation stays local to that subscription and can be retried.
        assertTrue(memoryFile.delete())
        assertTrue(memoryFile.mkdir())
        val obstruction = memoryFile.resolve("occupied").apply { writeText("blocked") }
        SubscriptionRepository.initialize(SubscriptionDefaults("Other", "Retry memory"))
        assertTrue(memoryId in SubscriptionRepository.awaitSnapshot().loadErrors)
        assertTrue(originalBytes.contentEquals(localFile.readBytes()))
        assertTrue(obstruction.delete())
        assertTrue(memoryFile.delete())
        SubscriptionRepository.initialize(SubscriptionDefaults("Other", "Retry memory"))
        assertFalse(memoryId in SubscriptionRepository.awaitSnapshot().loadErrors)
        assertEquals("Retry memory", RawSubscriptionParser.parse(memoryFile.readText()).name)
        // First local creation uses the operation's name; subsequent retries leave it untouched.
        Db.subsItemDao.deleteById(localId)
        assertTrue(localFile.delete())
        SubscriptionRepository.initialize(SubscriptionDefaults("Fresh local", "Other memory"))
        assertEquals("Fresh local", RawSubscriptionParser.parse(localFile.readText()).name)
        SubscriptionRepository.initialize(SubscriptionDefaults("Another locale", "Other memory"))
        assertEquals(
            "Fresh local",
            SubscriptionRepository.awaitSnapshot().subscriptions.getValue(localId).name,
        )
    }

    private suspend fun assertManualUpdateIgnoresStoredVersions(toast: ToastState) =
        kotlinx.coroutines.coroutineScope {
            val file = appStorage().store.resolve("ignore_version_list.json")
            val previous = file.takeIf { it.exists() }?.readBytes()
            val initialRevision = toast.revision
            assertFalse(isNetworkRequestAllowed())
            val networkUnavailable = getString(Res.string.network_unavailable)
            // This headless runtime has no composition to dispatch global snapshot changes.
            val snapshotChanges = Channel<Unit>(Channel.CONFLATED)
            val observer = Snapshot.registerGlobalWriteObserver { snapshotChanges.trySend(Unit) }
            val notificationJob =
                launch(Dispatchers.Main) {
                    for (change in snapshotChanges) Snapshot.sendApplyNotifications()
                }
            try {
                file.writeText("invalid-json")
                val update = UpdateStatus(this)
                suspend fun checkManually() {
                    val previousRevision = toast.revision
                    update.checkUpdate(manual = true)
                    withTimeout(5_000) { update.checkUpdatingFlow.first { !it } }
                    withTimeout(5_000) {
                        androidx.compose.runtime
                            .snapshotFlow { toast.revision }
                            .first { it > previousRevision }
                    }
                }
                checkManually()
                assertEquals(initialRevision + 1, toast.revision)
                assertEquals(networkUnavailable, toast.message)
                assertEquals("invalid-json", file.readText())
                file.delete()
                check(file.mkdir()) // Manual checks do not read the automatic-check ignore list.
                checkManually()
                assertEquals(initialRevision + 2, toast.revision)
                assertEquals(networkUnavailable, toast.message)
                assertTrue(file.isDirectory)
                check(file.delete())
                file.writeText("[10,20]")
                checkManually()
                // All three inputs reach the explicit network-unavailable outcome without changing
                // the file.
                assertEquals(networkUnavailable, toast.message)
                assertEquals("[10,20]", file.readText())
                assertEquals(initialRevision + 3, toast.revision)
            } finally {
                observer.dispose()
                snapshotChanges.close()
                notificationJob.cancel()
                if (file.isDirectory) check(file.delete())
                if (previous == null) file.delete() else file.writeBytes(previous)
            }
        }

    private fun assertLogCallerValues() {
        var value = "log-capture-before"
        val mutable =
            object {
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
        val store = GithubCookieStore
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

    // Reordering persists array positions while preserving keys, order values and latest content.
    private suspend fun assertAppRuleReorderPersistence() {
        val id = SubscriptionIds.LOCAL
        val previous = SubscriptionRepository.awaitSnapshot().subscriptions.getValue(id)
        val original =
            RawSubscriptionParser.parse(
                """{id:$id,name:'Reorder',version:1,apps:[{id:'test.alpha',groups:[
                {key:1,name:'First',order:9,rules:[{order:7,matches:'[text="Ad"]'}]},
                {key:2,name:'Second',order:-3,rules:[{matches:'[text="Ad"]'}]},
                {key:3,name:'Third',rules:[{matches:'[text="Ad"]'}]}
            ]}]}""",
            )
        val owner = ViewModelStore()
        try {
            SubscriptionRepository.update(id) { original }
            val vm =
                SubsAppGroupListViewModel(
                        SubsAppGroupListRoute(id, "test.alpha"),
                    )
                    .also { owner.put("reorder", it) }
            withTimeout(5_000) { vm.uiState.first { it is Loadable.Ready } }
            // A content edit after the drag snapshot must survive saving the requested keys.
            SubscriptionRepository.update(id) { current ->
                current.copy(
                    apps =
                        current.apps.map { app ->
                            app.copy(
                                groups =
                                    app.groups.map {
                                        if (it.key == 2) it.copy(name = "Edited") else it
                                    },
                            )
                        },
                )
            }
            val beforeReorder = SubscriptionRepository.awaitSnapshot().subscriptions.getValue(id)
            vm.reorderGroups(listOf(3, 1, 2))
            val saved = SubscriptionRepository.files.load(id)
            val expectedGroups =
                original.apps.single().groups.let {
                    listOf(it[2], it[0], it[1].copy(name = "Edited"))
                }
            assertEquals(
                Json.encodeToString(
                    beforeReorder.copy(
                        version = beforeReorder.version + 1,
                        apps = listOf(original.apps.single().copy(groups = expectedGroups)),
                    ),
                ),
                Json.encodeToString(saved),
            )
            assertEquals(
                Json.encodeToString(saved),
                Json.encodeToString(
                    SubscriptionRepository.awaitSnapshot().subscriptions.getValue(id),
                ),
            )
            vm.reorderGroups(listOf(2, 3, 1))
            assertEquals(
                listOf(2, 3, 1),
                SubscriptionRepository.files.load(id).apps.single().groups.map { it.key },
            )
        } finally {
            owner.clear()
            SubscriptionRepository.update(id) { previous }
        }
    }

    private suspend fun assertAppRuleParentVisibility() {
        val id = -991L
        val subscription =
            RawSubscriptionParser.parse(
                """{
            id: -991, name: 'Parent visibility', version: 1,
            globalGroups: [
                {key: 1, name: 'Default on', rules: [{matches: '[text="Ad"]'}]},
                {key: 2, name: 'Default off', enable: false, rules: [{matches: '[text="Ad"]'}]}
            ],
            apps: [{id: 'test.alpha', groups: [{key: 101, name: 'App rule', rules: [{matches: '[text="Ad"]'}]}]}]
        }""",
            )
        val owner = ViewModelStore()
        val originalShowDisabled = SettingsRepository.settings.value.showDisabledRule
        try {
            SubscriptionRepository.saveWithItem(
                subscription,
                SubsItem(id, order = 0, enable = true),
            )
            val vm =
                AppConfigViewModel(
                        AppConfigRoute("test.alpha"),
                    )
                    .also { owner.put("app-rules", it) }

            suspend fun expectGroups(vararg keys: Int) {
                withTimeout(5_000) {
                    vm.uiState.first { loaded ->
                        loaded is Loadable.Ready &&
                            loaded.value.subsPairs
                                .filter { it.first.subsItem.id == id }
                                .flatMap { it.second }
                                .map { it.key }
                                .toSet() == keys.toSet()
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
                SubscriptionConfigStore.capture().globalGroupConfigs.single {
                    it.subsId == id && it.groupKey == 1
                }
            assertEquals("!test.alpha", saved.exclude)
            assertEquals(subscription, SubscriptionRepository.awaitSnapshot().subscriptions[id])
        } finally {
            owner.clear()
            SettingsRepository.updateSettings { it.copy(showDisabledRule = originalShowDisabled) }
            SettingsRepository.awaitPersistence()
            SubscriptionRepository.delete(id)
        }
    }

    // Scenario reload replaces page state but must retain root tasks; closing the host cancels
    // them.
    private suspend fun assertRootNavigationLifecycle(runtime: DesktopRuntime) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            assertFailsWith<IllegalStateException> { MainViewModel.requireCurrent() }
            val state = DesktopState(runtime.toast, isolated = true, simulator = runtime.simulator)
            val vm = state.mainVm
            val rootJob = vm.scope.coroutineContext[kotlinx.coroutines.Job]!!
            try {
                kotlin.test.assertSame(vm, MainViewModel.requireCurrent())
                // Rebinding the same retained root must not create or replace its instance.
                vm.registerCurrent()
                kotlin.test.assertSame(vm, MainViewModel.requireCurrent())
                val retrieved =
                    androidx.lifecycle.ViewModelProvider.create(state)[MainViewModel::class]
                kotlin.test.assertSame(vm, retrieved)
                vm.navigator.navigate(AboutRoute)
                vm.navigator.navigate(SnapshotPageRoute, replaced = true)
                assertEquals(
                    listOf(HomeRoute, SnapshotPageRoute),
                    vm.navigator.backStack.map { it.route },
                )
                vm.navigator.pop()
                assertEquals(HomeRoute, vm.navigator.topRoute)
                val previousHome = HomeState()
                vm.homeNavigation.bind(previousHome)
                state.load(ScenarioRequest("settings"))
                vm.homeNavigation.unbind(previousHome)
                val nextHome = HomeState()
                vm.homeNavigation.bind(nextHome)
                assertEquals(BottomNavItem.Settings, nextHome.selectedTab.value)
                kotlin.test.assertSame(vm, state.mainVm)
                kotlin.test.assertSame(vm, MainViewModel.requireCurrent())
                assertTrue(rootJob.isActive)
                vm.homeNavigation.unbind(nextHome)
            } finally {
                state.close()
            }
            assertTrue(rootJob.isCancelled)
            assertFailsWith<IllegalStateException> { MainViewModel.requireCurrent() }
        }

    /**
     * A corrupt subscription must show a load failure, while an absent one is confirmed missing.
     */
    private suspend fun assertActionLogSubscriptionLoadFailure() {
        val id = -900003L
        val owner = ViewModelStore()
        try {
            SubscriptionRepository.files.restore(id, "invalid-json".toByteArray())
            Db.subsItemDao.upsert(SubsItem(id = id, order = 102))
            SubscriptionRepository.refresh(id)
            val failure = SubscriptionRepository.awaitSnapshot().loadErrors.getValue(id)
            val vm = ActionLogViewModel(ActionLogRoute()).also { owner.put("action-log", it) }
            val log =
                ActionLog(
                    ctime = 0,
                    appId = "test.alpha",
                    subsId = id,
                    subsVersion = 1,
                    groupKey = 1,
                    groupType = RuleGroupType.App,
                    ruleIndex = 0,
                )
            vm.showActionLog(log)
            val failed =
                withTimeout(5_000) {
                    vm.dialogStateFlow.first { it?.loadState is Loadable.Failure }
                }!!
            assertTrue((failed.loadState as Loadable.Failure).cause === failure)
            assertEquals(null, failed.subscription)

            vm.showActionLog(log.copy(subsId = id - 1))
            val missing =
                withTimeout(5_000) {
                    vm.dialogStateFlow.first {
                        it?.actionLog?.subsId == id - 1 && it.loadState is Loadable.Ready
                    }
                }!!
            assertEquals(null, missing.subscription)
            assertEquals(null, missing.group)

            vm.showActionLog(log.copy(subsId = 13))
            val ready =
                withTimeout(5_000) {
                    vm.dialogStateFlow.first {
                        it?.actionLog?.subsId == 13L && it.loadState is Loadable.Ready
                    }
                }!!
            assertNotNull(ready.subscription)
        } finally {
            owner.clear()
            SubscriptionRepository.delete(id)
        }
    }

    private suspend fun assertHomeLifecycle() {
        val owner = ViewModelStore()
        val originalPowerWarning = SettingsRepository.settings.value.subsPowerWarn
        val first = SubsItem(id = 900001, order = 100, enable = true)
        val second = SubsItem(id = 900002, order = 101)
        try {
            val vm = HomeViewModel().also { owner.put("home", it) }
            // A retained page keeps selection; both user and host tab changes clear it.
            vm.homeState.selectTab(BottomNavItem.SubsManage)
            vm.selection.select(first.id)
            vm.homeState.clickTab(BottomNavItem.SubsManage)
            assertEquals(setOf(first.id), vm.selection.state.value.selectedKeys)
            assertEquals(
                setOf(first.id),
                (owner["home"] as HomeViewModel).selection.state.value.selectedKeys,
            )
            vm.homeState.clickTab(BottomNavItem.Settings)
            assertFalse(vm.selection.state.value.active)
            vm.homeState.selectTab(BottomNavItem.SubsManage)
            vm.selection.select(second.id)
            vm.homeState.selectTab(BottomNavItem.AppList)
            assertFalse(vm.selection.state.value.active)
            withTimeout(5_000) { vm.appsState.first { it.value?.apps?.size == 2 } }
            withTimeout(5_000) { vm.latestState.first { it is Loadable.Ready } }
            withTimeout(5_000) { vm.subscriptionsState.first { it is Loadable.Ready } }
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
        val manager = BackupManager
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
        androidx.sqlite.driver.bundled
            .BundledSQLiteDriver()
            .open(layout.database.absolutePath)
            .use { connection ->
                connection
                    .prepare(
                        "CREATE TRIGGER test_backup_failure BEFORE INSERT ON subs_item BEGIN SELECT RAISE(FAIL, 'backup failure'); END",
                    )
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
                repository.awaitSnapshot().subscriptions.getValue(-2),
            ),
        )
        assertFailsWith<CancellationException> {
            manager.importData { throw CancellationException("cancelled selection") }
        }
        val malformedArchive = layout.sharedCache.resolve("invalid-subscription-directory.zip")
        try {
            ZipOutputStream(malformedArchive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("subscription"))
                zip.write("not a directory".toByteArray())
                zip.closeEntry()
            }
            assertFailsWith<IOException> {
                manager.importData(FileSource.Local(malformedArchive))
            }
            assertEquals(
                SubscriptionJson.json.encodeToString(original),
                SubscriptionJson.json.encodeToString(
                    repository.awaitSnapshot().subscriptions.getValue(-2),
                ),
            )
        } finally {
            malformedArchive.delete()
        }
        assertEquals("later", settings.settings.value.actionToast)
        manager.importData(FileSource.Uri(archive.toURI().toString()))
        assertEquals("archive", settings.settings.value.actionToast)
        assertEquals(
            SubscriptionJson.json.encodeToString(original.copy(version = original.version + 1)),
            SubscriptionJson.json.encodeToString(
                repository.awaitSnapshot().subscriptions.getValue(-2),
            ),
        )
        assertTrue(layout.tempCache.listFiles().orEmpty().isEmpty())
        assertInspectionProtocol(layout, repository)
    }
}

private fun assertDeferredModeHandoff(runtime: DesktopRuntime) {
    val original = SettingsRepository.settings.value
    val simulator = runtime.simulator.settings.value
    try {
        SettingsRepository.updateSettings {
            it.copy(
                enableAutomator = true,
                enableBlockA11yAppList = false,
                automatorMode = AutomatorMode.A11y.value,
            )
        }
        runtime.simulator.setPrivilegeAvailable(true)
        runtime.simulator.update {
            it.copy(permissions = it.permissions.copy(writeSecureSettings = true))
        }
        runtime.services.setEnabled(SimulatedService.Accessibility, true)
        SettingsRepository.updateAutomatorMode(AutomatorMode.Automation.value)
        assertTrue(runtime.simulator.settings.value.services.serviceEnabled)
        assertFalse(runtime.simulator.settings.value.services.automationRunning)
        runtime.simulator.update { it.copy(device = it.device.copy(topAppId = "handoff.first")) }
        assertTrue(runtime.simulator.settings.value.services.automationRunning)
        assertFalse(runtime.simulator.settings.value.services.serviceEnabled)
        SettingsRepository.updateAutomatorMode(AutomatorMode.A11y.value)
        runtime.simulator.update {
            it.copy(permissions = it.permissions.copy(writeSecureSettings = false))
        }
        runtime.simulator.update { it.copy(device = it.device.copy(topAppId = "handoff.denied")) }
        assertTrue(runtime.simulator.settings.value.services.automationRunning)
        runtime.simulator.update {
            it.copy(permissions = it.permissions.copy(writeSecureSettings = true))
        }
        runtime.simulator.update { it.copy(device = it.device.copy(topAppId = "handoff.second")) }
        assertTrue(runtime.simulator.settings.value.services.serviceEnabled)
        assertFalse(runtime.simulator.settings.value.services.automationRunning)
        runtime.services.setEnabled(SimulatedService.Accessibility, false)
        SettingsRepository.updateSettings { it.copy(enableAutomator = false) }
        SettingsRepository.updateAutomatorMode(AutomatorMode.Automation.value)
        runtime.simulator.update { it.copy(device = it.device.copy(topAppId = "handoff.disabled")) }
        assertFalse(runtime.simulator.settings.value.services.automationRunning)
    } finally {
        SettingsRepository.updateSettings { original }
        runtime.simulator.update { simulator }
    }
}
