package li.gkd.app.settings

import androidx.lifecycle.ViewModelStore
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import li.gkd.app.subscription.SubscriptionJson.json
import li.gkd.app.ui.settings.AppIdListEditor
import li.gkd.app.ui.settings.AppListEditorState
import li.gkd.app.ui.settings.AppWhitelistViewModel

/** Invoked by ApplicationIntegrationTest after its one real application startup. */
object SettingsRepositoryChecks {
    suspend fun run(directory: File, appName: String) {
        val repo = SettingsRepository
        assertEquals(appName, repo.settings.value.actionToast)
        assertEquals(7, repo.settings.value.a11yAppGroupType)
        assertFalse(repo.settings.value.enableDynamicColor)
        assertEquals(listOf(12, 34), repo.overlayPositions.value["legacy"])
        val original = repo.exportBackupEntries()
        fun diskSettings() =
            json.decodeFromString<SettingsStore>(directory.resolve("store.json").readText())

        val owner = ViewModelStore()
        try {
            checkCorruptSettingsPreservation(directory.resolve("decode-checks").apply { mkdirs() })
            repo.updateSettings {
                it.copy(
                    enableMatch = false,
                    httpServerPort = 9123,
                    snapshotDisplayMode = 2,
                    appRuleSummaryDisplayMode = 1,
                    subsAppRuleDisplayMode = 0,
                )
            }
            repo.awaitPersistence()
            assertEquals(repo.settings.value, diskSettings())
            assertEquals(1, diskSettings().appRuleSummaryDisplayMode)
            assertEquals(0, diskSettings().subsAppRuleDisplayMode)
            repo.updateSettings { it.copy(subsAppRuleDisplayMode = 1) }
            repo.awaitPersistence()
            assertEquals(1, diskSettings().appRuleSummaryDisplayMode)
            assertEquals(1, diskSettings().subsAppRuleDisplayMode)
            val beforeCount = repo.actionCount.value
            coroutineScope {
                List(200) { async(Dispatchers.Default) { repo.incrementActionCount() } }.awaitAll()
            }
            repo.awaitPersistence()
            assertEquals(beforeCount + 200, repo.actionCount.value)
            assertEquals(
                repo.actionCount.value.toString(),
                directory.resolve("action_count.txt").readText(),
            )

            // Every imported file is decoded before writes or the database transaction begin.
            val beforeInvalidBackup = repo.exportBackupEntries()
            val beforeInvalidFiles =
                repo.backupFilenames.associateWith { filename ->
                    directory.resolve(filename).takeIf(File::exists)?.readText()
                }
            for ((filename, invalidText) in
                listOf(
                    "store.json" to "{",
                    "store.json" to "[]",
                    "store.json" to """{"httpServerPort":"invalid"}""",
                    "action_count.txt" to "not a count",
                    "action_count.txt" to "9223372036854775808",
                )) {
                var databaseStarted = false
                assertFailsWith<IllegalArgumentException> {
                    repo.withBackupRestore(
                        mapOf(
                            "store.json" to """{"httpServerPort":9000}""",
                            "action_count.txt" to "42",
                        ) + (filename to invalidText),
                    ) {
                        databaseStarted = true
                    }
                }
                assertFalse(databaseStarted)
                assertEquals(beforeInvalidBackup, repo.exportBackupEntries())
                assertEquals(
                    beforeInvalidFiles,
                    repo.backupFilenames.associateWith { name ->
                        directory.resolve(name).takeIf(File::exists)?.readText()
                    },
                )
            }

            // Single-segment package IDs must survive startup decoding and backup restore,
            // including IDs other than the Android system package.
            val platformPackageIds = setOf("android", "platform", "test.platform")
            assertEquals(
                platformPackageIds,
                SettingsAppIds.decode("android\nplatform\n# Android 系统\ninvalid-id\ntest.platform"),
            )
            val platformPackageLists =
                listOf(
                    "block_match_app_list.txt" to repo::saveBlockMatchAppList,
                    "block_a11y_app_list.txt" to repo::saveBlockA11yAppList,
                    "a11y_scope_app_list.txt" to repo::saveA11yScopeAppList,
                )
            for ((filename, save) in platformPackageLists) {
                save(platformPackageIds)
                assertEquals(
                    platformPackageIds,
                    SettingsAppIds.decode(directory.resolve(filename).readText()),
                )
            }
            val platformPackageBackup = repo.exportBackupEntries()
            platformPackageLists.forEach { (_, save) -> save(emptySet()) }
            // Invalid app IDs in backups are ignored while valid entries still restore to disk.
            val backupWithInvalidIds =
                platformPackageBackup.toMutableMap().apply {
                    platformPackageLists.forEach { (filename, _) ->
                        this[filename] = getValue(filename) + "\ninvalid-id"
                    }
                }
            repo.withBackupRestore(backupWithInvalidIds) {}
            assertEquals(platformPackageBackup, repo.exportBackupEntries())
            for ((filename, _) in platformPackageLists) {
                assertEquals(
                    platformPackageIds,
                    SettingsAppIds.decode(directory.resolve(filename).readText()),
                )
            }

            repo.updateSettings { it.copy(enableMatch = true, httpServerPort = 8000) }
            repo.saveBlockMatchAppList(setOf("local.app"))
            repo.awaitPersistence()
            val localCount = repo.actionCount.value
            assertFailsWith<IOException> {
                repo.withBackupRestore(
                    mapOf(
                        "store.json" to
                            json.encodeToString(
                                repo.settings.value.copy(
                                    enableMatch = false,
                                    httpServerPort = 9000,
                                ),
                            ),
                        "action_count.txt" to "100",
                        "block_match_app_list.txt" to "imported.app",
                    ),
                ) {
                    coroutineScope {
                        launch(Dispatchers.Default) {
                            repo.updateSettings { it.copy(httpServerPort = 8123) }
                            repo.updateBlockMatchAppList { it + "later.app" }
                        }
                        repeat(20) { launch(Dispatchers.Default) { repo.incrementActionCount() } }
                    }
                    throw IOException("database commit failed")
                }
            }
            assertTrue(repo.settings.value.enableMatch)
            assertEquals(8123, repo.settings.value.httpServerPort)
            assertEquals(localCount + 20, repo.actionCount.value)
            assertEquals(setOf("local.app", "later.app"), repo.blockMatchAppList.value)
            assertEquals(repo.settings.value, diskSettings())
            assertEquals(
                "committed",
                repo.withBackupRestore(mapOf("action_count.txt" to "100")) {
                    repo.incrementActionCount()
                    "committed"
                },
            )
            repo.awaitPersistence()
            assertEquals("101", directory.resolve("action_count.txt").readText())
            repo.withBackupRestore(mapOf("action_count.txt" to "200")) {}
            coroutineScope {
                val entered = CompletableDeferred<Unit>()
                val job = launch {
                    repo.withBackupRestore(mapOf("action_count.txt" to "100")) {
                        entered.complete(
                            Unit,
                        )
                        awaitCancellation()
                    }
                }
                withTimeout(5_000) { entered.await() }
                repo.incrementActionCount()
                withTimeout(5_000) { job.cancelAndJoin() }
            }
            assertEquals(201L, repo.actionCount.value)
            assertEquals("201", directory.resolve("action_count.txt").readText())
            var databaseStarted = false
            withBlockedWrite(directory, "action_count.txt") {
                val error =
                    assertFailsWith<SettingsWriteException> {
                        repo.withBackupRestore(mapOf("action_count.txt" to "100")) {
                            databaseStarted = true
                        }
                    }
                assertIs<IOException>(error.cause)
            }
            assertFalse(databaseStarted)
            assertEquals(201L, repo.actionCount.value)
            repo.incrementActionCount()
            repo.awaitPersistence()
            assertEquals("202", directory.resolve("action_count.txt").readText())

            repo.updateSettings {
                it.copy(
                    enableAutomator = true,
                    automatorMode = AutomatorMode.A11y.value,
                )
            }
            repo.updateAutomatorMode(AutomatorMode.A11y.value)
            assertTrue(repo.settings.value.enableAutomator)
            repo.updateAutomatorMode(AutomatorMode.Automation.value)
            assertFalse(repo.settings.value.enableAutomator)
            assertEquals(8123, repo.settings.value.httpServerPort)
            coroutineScope {
                repeat(30) { i ->
                    launch(Dispatchers.Default) {
                        repo.updateOverlayPosition(
                            "overlay-$i",
                            i,
                            -i,
                        )
                    }
                }
            }
            repo.awaitPersistence()
            val positions =
                json.decodeFromString<Map<String, List<Int>>>(
                    directory.resolve("overlay_position.json").readText(),
                )
            repeat(30) { i -> assertEquals(listOf(i, -i), positions["overlay-$i"]) }
            assertEquals(listOf(12, 34), positions["legacy"])
            val oldPositions = directory.resolve("overlay_position.json").readText()
            withBlockedWrite(directory, "overlay_position.json") {
                repo.updateOverlayPosition("overlay", 3, 4)
                assertEquals(
                    "overlay_position.json",
                    assertFailsWith<SettingsWriteException> { repo.awaitPersistence() }.filename,
                )
                assertEquals(oldPositions, directory.resolve("overlay_position.json").readText())

                // A failed overlay write must not make successful, unrelated editor saves fail.
                repo.saveActionToast(true, false, "Independent toast")
                repo.saveNotificationText(true, "Independent title", "Independent notification")
                assertEquals("Independent toast", diskSettings().actionToast)
                assertEquals("Independent title", diskSettings().customNotifTitle)
                assertEquals("Independent notification", diskSettings().customNotifText)
                val independentWhitelist =
                    AppWhitelistViewModel().also {
                        owner.put("independent-whitelist", it)
                    }
                assertTrue(independentWhitelist.saveChanges("test.independent"))
                assertEquals(
                    "test.independent",
                    directory.resolve("block_match_app_list.txt").readText(),
                )
                for ((filename, editor) in
                    listOf(
                        "block_a11y_app_list.txt" to
                            AppIdListEditor(
                                { repo.blockA11yAppList.value },
                                repo::saveBlockA11yAppList,
                            ),
                        "a11y_scope_app_list.txt" to
                            AppIdListEditor(
                                { repo.a11yScopeAppList.value },
                                repo::saveA11yScopeAppList,
                            ),
                    )) {
                    assertTrue(editor.save("test.independent"))
                    assertEquals("test.independent", directory.resolve(filename).readText())
                }
                assertEquals(
                    "overlay_position.json",
                    assertFailsWith<SettingsWriteException> { repo.awaitPersistence() }.filename,
                )
            }
            repo.updateOverlayPosition("overlay", 3, 4)
            repo.awaitPersistence()
            assertFailsWith<IOException> {
                repo.withBackupRestore(mapOf("store.json" to """{"enableMatch":false}""")) {
                    repo.updateOverlayPosition("overlay", 5, 6)
                    throw IOException("restore failed")
                }
            }
            repo.awaitPersistence()
            assertTrue(repo.settings.value.enableMatch)
            assertEquals(listOf(5, 6), repo.overlayPositions.value["overlay"])

            val settings = SettingsRepository
            val beforeSave = directory.resolve("store.json").readText()
            withBlockedWrite(directory, "store.json") {
                assertFailsWith<SettingsWriteException> {
                    settings.saveActionToast(
                        true,
                        false,
                        "My draft",
                    )
                }
                assertEquals(beforeSave, directory.resolve("store.json").readText())
            }
            settings.saveActionToast(true, false, "My draft")
            assertEquals("My draft", diskSettings().actionToast)
            assertFalse(diskSettings().enableDynamicColor)
            val beforeInvalid = repo.settings.value
            assertFalse(repo.saveHttpServerPort("65536"))
            assertFalse(repo.saveHttpServerPort("text"))
            assertEquals(
                ScreenshotConfigResult.InvalidApp,
                repo.saveCaptureScreenshotConfig("invalid-id", "[text='ok']"),
            )
            assertEquals(
                ScreenshotConfigResult.InvalidSelector,
                repo.saveCaptureScreenshotConfig("test.alpha", "["),
            )
            assertEquals(beforeInvalid, repo.settings.value)
            // Installed-app visibility must not decide whether a package ID can be saved.
            for (appId in listOf("missing.app", "android", "")) {
                assertEquals(
                    ScreenshotConfigResult.Saved,
                    repo.saveCaptureScreenshotConfig(appId, "[text='ok']"),
                )
                repo.awaitPersistence()
                assertEquals(appId, diskSettings().screenshotTargetAppId)
            }
            assertTrue(repo.saveHttpServerPort("17333"))
            assertEquals(
                ScreenshotConfigResult.Saved,
                repo.saveCaptureScreenshotConfig("test.alpha", "[text='ok']"),
            )
            repo.updateSettings {
                it.copy(
                    captureVolumeChange = true,
                    hideSnapshotStatusBar = true,
                )
            }
            repo.awaitPersistence()
            assertEquals(17333, diskSettings().httpServerPort)
            assertEquals("test.alpha", diskSettings().screenshotTargetAppId)
            assertTrue(diskSettings().captureVolumeChange)
            assertTrue(diskSettings().hideSnapshotStatusBar)

            val whitelist = AppWhitelistViewModel().also { owner.put("whitelist", it) }
            val beforeWhitelistFailure = repo.blockMatchAppList.value
            withBlockedWrite(directory, "block_match_app_list.txt") {
                assertFailsWith<SettingsWriteException> {
                    whitelist.saveChanges("test.alpha\n# Alpha\ntest.alpha\ninvalid-id")
                }
                assertTrue(whitelist.hasChanges("test.alpha"))
                assertEquals(beforeWhitelistFailure, repo.blockMatchAppList.value)
            }
            assertTrue(whitelist.saveChanges("test.alpha"))
            assertFalse(whitelist.hasChanges("test.alpha"))
            assertFalse(whitelist.saveChanges("test.alpha"))
            assertEquals("test.alpha", directory.resolve("block_match_app_list.txt").readText())
            val editors =
                listOf(
                    "block_a11y_app_list.txt" to
                        AppIdListEditor(
                            { repo.blockA11yAppList.value },
                            repo::saveBlockA11yAppList,
                        ),
                    "a11y_scope_app_list.txt" to
                        AppIdListEditor(
                            { repo.a11yScopeAppList.value },
                            repo::saveA11yScopeAppList,
                        ),
                )
            for ((filename, editor) in editors) {
                val beforeFailure = repo.exportBackupEntries().getValue(filename)
                withBlockedWrite(directory, filename) {
                    assertFailsWith<SettingsWriteException> {
                        editor.save("test.alpha\n# comment\ntest.alpha\ninvalid-id\ntest.absent")
                    }
                    assertTrue(editor.hasChanges("test.alpha\ntest.absent"))
                    assertEquals(beforeFailure, repo.exportBackupEntries().getValue(filename))
                }
                assertTrue(editor.save("test.alpha\ntest.absent"))
                assertFalse(editor.hasChanges("test.absent\ntest.alpha"))
                val editorState = AppListEditorState()
                editor.startEditing(editorState)
                assertEquals(
                    "test.alpha\n# Alpha\n\ntest.absent\n\n",
                    editorState.state.value.draft,
                )
                assertEquals(
                    setOf("test.alpha", "test.absent"),
                    SettingsAppIds.decode(directory.resolve(filename).readText()),
                )
            }
            editors.first().second.save("test.changed")
            assertEquals(setOf("test.alpha", "test.absent"), repo.a11yScopeAppList.value)
            // A queued replacement followed by concurrent additions must preserve every command.
            coroutineScope {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    repo.saveBlockMatchAppList(setOf("test.replaced"))
                }
                repeat(20) { index ->
                    launch(Dispatchers.Default) {
                        repo.updateBlockMatchAppList { it + "test.added$index" }
                    }
                }
            }
            val expectedIds = setOf("test.replaced") + (0 until 20).map { "test.added$it" }
            assertEquals(expectedIds, repo.blockMatchAppList.value)
            assertEquals(
                expectedIds,
                SettingsAppIds.decode(directory.resolve("block_match_app_list.txt").readText()),
            )
            // Leaving a page cancels its wait, including a save queued behind another update.
            // The accepted update must still reach both the file and the published list.
            for ((filename, state, update) in
                listOf(
                    Triple(
                        "block_match_app_list.txt",
                        repo.blockMatchAppList,
                        repo::updateBlockMatchAppList,
                    ),
                    Triple(
                        "block_a11y_app_list.txt",
                        repo.blockA11yAppList,
                        repo::updateBlockA11yAppList,
                    ),
                    Triple(
                        "a11y_scope_app_list.txt",
                        repo.a11yScopeAppList,
                        repo::updateA11yScopeAppList,
                    ),
                )) {
                coroutineScope {
                    val entered = CompletableDeferred<Unit>()
                    val release = CountDownLatch(1)
                    val preceding =
                        launch(Dispatchers.Default) {
                            update {
                                entered.complete(Unit)
                                check(release.await(10, TimeUnit.SECONDS))
                                setOf("test.beforeexit")
                            }
                        }
                    try {
                        withTimeout(5_000) { entered.await() }
                        var pageFeedback = false
                        val page =
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                update { it + "test.afterexit" }
                                pageFeedback = true
                            }
                        withTimeout(5_000) { page.cancelAndJoin() }
                        assertFalse(pageFeedback)
                    } finally {
                        release.countDown()
                    }
                    preceding.join()
                    val expected = setOf("test.beforeexit", "test.afterexit")
                    withTimeout(5_000) { state.first { it == expected } }
                    assertEquals(
                        expected,
                        SettingsAppIds.decode(directory.resolve(filename).readText()),
                    )
                }
            }
            assertTrue(repo.termsAccepted.value)
            withBlockedWrite(directory, "terms_accepted.txt") {
                repo.acceptTerms()
                assertTrue(repo.termsAccepted.value)
            }
            repo.acceptTerms()
            repo.acceptTerms()
            assertTrue(repo.termsAccepted.value)
            assertEquals("false", directory.resolve("terms_accepted.txt").readText())
        } finally {
            owner.clear()
            repo.withBackupRestore(original) {}
            repo.awaitPersistence()
        }
    }

    private fun checkCorruptSettingsPreservation(directory: File) {
        val storage = FileSettingsStorage(directory)
        val decode = { text: String? -> text?.let { json.decodeFromString<SettingsStore>(it) } }
        assertNull(storage.readValue("missing.json", decode))
        assertTrue(directory.listFiles()!!.isEmpty())
        for ((index, raw) in listOf("{", "[]", """{"httpServerPort":"invalid"}""").withIndex()) {
            val filename = "settings-$index.json"
            storage.writeAtomically(filename, raw)
            assertNull(storage.readValue(filename, decode))
            assertEquals(raw, storage.read(filename))
            val preserved =
                directory.listFiles()!!.single { it.name.startsWith("$filename.corrupt-") }
            assertEquals(raw, preserved.readText())
            storage.writeAtomically(filename, """{"httpServerPort":9123}""")
            assertEquals(9123, storage.readValue(filename, decode)?.httpServerPort)
            assertEquals(raw, preserved.readText())
        }
        storage.writeAtomically("blocked.json", "{")
        val blocked =
            object : SettingsStorage {
                override fun read(filename: String): String? = storage.read(filename)

                override fun writeAtomically(filename: String, text: String) {
                    throw IOException("cannot preserve settings")
                }
            }
        var usedDefaults = false
        assertFailsWith<IOException> {
            blocked.readValue("blocked.json") { text ->
                if (text == null) usedDefaults = true
                decode(text)
            }
        }
        assertFalse(usedDefaults)
        assertEquals("{", storage.read("blocked.json"))
        // Programming errors must not be converted into defaults or recovery files.
        assertFailsWith<IllegalStateException> {
            storage.readValue("blocked.json") { error("unexpected decoder failure") }
        }
        assertFalse(directory.listFiles()!!.any { it.name.startsWith("blocked.json.corrupt-") })
    }

    // A real nonempty directory prevents atomic writes; production needs no fault-injection API.
    private suspend fun withBlockedWrite(
        directory: File,
        filename: String,
        block: suspend () -> Unit,
    ) {
        SettingsRepository.awaitPersistence()
        val obstruction = directory.resolve("$filename.tmp")
        check(!obstruction.exists())
        check(obstruction.mkdir())
        val marker = obstruction.resolve("blocker").apply { writeText("test") }
        try {
            block()
        } finally {
            check(marker.delete())
            check(obstruction.delete())
        }
    }
}
