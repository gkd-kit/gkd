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
            repository.packagesChanged(setOf("same"))
            delay(1_000)
            assertEquals("primary", repository.snapshot!!.apps.getValue("same").name)
            repository.packagesChanged(setOf("hidden"))
            delay(2_200)
            assertEquals("primary", repository.snapshot!!.apps.getValue("same").name)
            withTimeout(5_000) { repository.state.first { it.snapshot?.apps?.get("same")?.name == "package-updated" } }
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
