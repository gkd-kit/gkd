package li.gkd.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.model.AppInfo
import li.gkd.app.model.AppInventory
import li.gkd.app.model.AppUser
import li.gkd.app.storage.appStorage
import li.gkd.app.util.LogUtils
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAppCatalogTest {
    suspend fun profileReloadUsesSharedSelectionAndRecoversFromCorruptFile(directory: File) {
        val original = directory.resolve("apps.json").readText()
        try {
            val file = directory.resolve("apps.json")
            val main = AppInfo("same", "primary", 1, "1", false, 1, false, 7)
            val hidden = main.copy(id = "hidden", hidden = true)
            val inventory = AppInventory(
                7,
                listOf(main, hidden),
                listOf(AppUser(10, "work")),
                listOf(main.copy(userId = 10))
            )
            file.writeText(Json.encodeToString(inventory))
            val repository = AppInfoRepository
            repository.refresh()
            assertEquals(7, repository.snapshot!!.apps.getValue("same").userId)
            assertTrue(repository.snapshot!!.inventory.othersApps.isEmpty())
            assertEquals(listOf("same"), repository.snapshot!!.visibleApps.map { it.id })
            assertTrue("hidden" in repository.snapshot!!.apps)
            // Package events use the production debounce and incremental query path.
            file.writeText(
                Json.encodeToString(
                    inventory.copy(
                        apps = listOf(
                            main.copy(name = "package-updated"),
                            hidden
                        )
                    )
                )
            )
            repository.packagesChanged(setOf("same"), source = "catalog-test.same.broadcast")
            delay(1_000)
            assertEquals("primary", repository.snapshot!!.apps.getValue("same").name)
            repository.packagesChanged(setOf("hidden"), source = "catalog-test.hidden.broadcast")
            delay(2_200)
            assertEquals("primary", repository.snapshot!!.apps.getValue("same").name)
            // A new package extends the 3s window; new/repeated sources for pending
            // packages must not extend it again (otherwise this 2s wait times out).
            repository.packagesChanged(setOf("same"), source = "catalog-test.same.launcher")
            repository.packagesChanged(setOf("same"), source = "catalog-test.same.launcher")
            repository.packagesChanged(setOf("hidden"), source = "catalog-test.hidden.broadcast")
            withTimeout(2_000) { repository.state.first { it.snapshot?.apps?.get("same")?.name == "package-updated" } }
            // Snapshot publication precedes the completion log; wait for that log too.
            var completionLog: String? = null
            withTimeout(2_000) {
                do {
                    LogUtils.flush()
                    completionLog = appStorage().log.listFiles().orEmpty()
                        .filter { it.extension == "log" }.joinToString("\n") { it.readText() }
                        .split("\n\n").singleOrNull {
                            it.contains("应用列表更新完成") && it.contains("catalog-test.same.broadcast")
                        }
                    if (completionLog == null) delay(25)
                } while (completionLog == null)
            }
            val updateLog = assertNotNull(completionLog)
            assertTrue(updateLog.contains("same=[catalog-test.same.broadcast, catalog-test.same.launcher]"), updateLog)
            assertTrue(updateLog.contains("hidden=[catalog-test.hidden.broadcast]"), updateLog)
            file.writeText("{broken")
            assertFails { repository.refresh() }
            assertEquals("package-updated", repository.snapshot!!.apps.getValue("same").name)
            assertNotNull(repository.state.value.failure)
            file.writeText(
                Json.encodeToString(
                    inventory.copy(
                        apps = listOf(
                            main.copy(
                                name = "renamed",
                                mtime = 2
                            )
                        )
                    )
                )
            )
            repository.refresh()
            assertEquals("renamed", repository.snapshot!!.apps.getValue("same").name)
            assertNull(repository.state.value.failure)
            file.writeText(Json.encodeToString(AppInventory(7, emptyList())))
            repository.refresh()
            assertTrue(repository.snapshot!!.apps.isEmpty())
        } finally {
            directory.resolve("apps.json").writeText(original)
            AppInfoRepository.refresh()
        }
    }

    @Test
    fun unrelatedPermissionChangesDoNotRefreshOrInvalidateCatalogQuery() = runTest {
        val root = File(System.getProperty("gkd.projectRoot", "..")).resolve(".local/tests/desktop")
            .apply { mkdirs() }
        val directory = Files.createTempDirectory(root.toPath(), "catalog-permissions-").toFile()
        try {
            val simulator = SimulatorStore()
            var refreshes = 0
            simulator.onAppCatalogChanged = { refreshes++ }
            val query = DesktopAppCatalog(simulator, directory).open()
            simulator.update { it.copy(permissions = it.permissions.copy(ignoreBatteryOptimizations = true)) }
            assertEquals(0, refreshes)
            assertTrue(query.isCurrent())
            simulator.update { it.copy(permissions = it.permissions.copy(canQueryPackages = false)) }
            assertEquals(1, refreshes)
            assertFalse(query.isCurrent())
        } finally {
            directory.deleteRecursively()
        }
    }
}
