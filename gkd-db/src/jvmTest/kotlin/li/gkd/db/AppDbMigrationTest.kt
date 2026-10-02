package li.gkd.db

import androidx.paging.PagingSource
import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppDbMigrationTest {
    private val schemaDirectory =
        Path.of(checkNotNull(System.getProperty("room.schemaDirectory")))
    private val tempDirectory = createTempDirectory("app-db-migration-test")

    private fun databasePath(databaseName: String): Path = tempDirectory.resolve(databaseName)

    private fun migrationHelper(databaseName: String): MigrationTestHelper {
        return MigrationTestHelper(
            schemaDirectoryPath = schemaDirectory,
            databasePath = databasePath(databaseName),
            driver = BundledSQLiteDriver(),
            databaseClass = AppDb::class,
        )
    }

    private fun openDatabase(databaseName: String): AppDb =
        Room.databaseBuilder<AppDb>(name = databasePath(databaseName).toString())
            .addMigrations(Migration14To15)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private suspend fun AppDb.captureConfigurations() = SubscriptionConfigSnapshot(
        subsItems = subsItemDao().queryAll(),
        appConfigs = subsAppConfigDao().queryAll(),
        categoryConfigs = subsCategoryConfigDao().queryAll(),
        appGroupConfigs = subsAppGroupConfigDao().queryAll(),
        globalGroupConfigs = subsGlobalGroupConfigDao().queryAll(),
    )

    private fun SQLiteConnection.queryLong(sql: String): Long =
        prepare(sql).use { statement ->
            check(statement.step()) { "Query returned no rows: $sql" }
            statement.getLong(0)
        }

    private fun SQLiteConnection.queryText(sql: String): String =
        prepare(sql).use { statement ->
            check(statement.step()) { "Query returned no rows: $sql" }
            statement.getText(0)
        }

    @AfterTest
    fun deleteDatabases() {
        tempDirectory.toFile().deleteRecursively()
    }

    @Test
    fun everyExportedSchemaMigratesToVersion16() = runBlocking {
        // Protects the persisted schema compatibility contract for every released version.
        for (startVersion in 1 until 16) {
            val helper = migrationHelper("all-migrations-$startVersion.db")
            helper.createDatabase(startVersion).close()
            helper.runMigrationsAndValidate(16, listOf(Migration14To15)).close()
        }
    }

    @Test
    fun migration15To16PreservesConfigurationsLogsAndLastVisitOrdering() = runBlocking {
        val name = "rename-tables.db"
        val helper = migrationHelper(name)
        helper.createDatabase(15).use { connection ->
            connection.execSQL("INSERT INTO subs_item VALUES (7, 1, 2, 1, 1, 0, NULL)")
            connection.execSQL("INSERT INTO app_config VALUES (0, 7, 'app.one')")
            connection.execSQL("INSERT INTO category_config VALUES (NULL, 7, 3)")
            connection.execSQL("INSERT INTO app_group_config VALUES (7, 'app.one', 4, NULL, 'app-exclude')")
            connection.execSQL("INSERT INTO global_group_config VALUES (7, 4, 1, 'global-exclude')")
            connection.execSQL("INSERT INTO activity_log_v2 VALUES (41, 100, 'app.one', 'MainActivity')")
            connection.execSQL("INSERT INTO app_visit_log VALUES ('app.one', 100), ('app.two', 200)")
            connection.execSQL("""INSERT INTO a11y_event_log VALUES (5, 300, 32, 'app.one', 'Event', 'description', '["first","second"]')""")
        }
        helper.runMigrationsAndValidate(16, listOf(Migration14To15)).use { connection ->
            assertEquals(
                100L,
                connection.queryLong("SELECT last_visit_time FROM app_last_visit WHERE app_id = 'app.one'")
            )
            assertEquals(
                "app.one",
                connection.queryText("SELECT app_id FROM a11y_event_log WHERE id = 5")
            )
            assertEquals(
                "description",
                connection.queryText("SELECT desc FROM a11y_event_log WHERE id = 5")
            )
            assertEquals(
                """["first","second"]""",
                connection.queryText("SELECT text FROM a11y_event_log WHERE id = 5")
            )
            assertEquals(
                "MainActivity",
                connection.queryText("SELECT activity_id FROM activity_log WHERE id = 41")
            )
            connection.prepare("PRAGMA foreign_key_check").use { assertTrue(!it.step()) }
        }
        val database = openDatabase(name)
        try {
            val before = database.captureConfigurations()
            assertEquals(
                listOf(SubsItem(7, ctime = 1, mtime = 2, enable = true, order = 0)),
                before.subsItems
            )
            assertEquals(listOf(SubsAppConfig(false, 7, "app.one")), before.appConfigs)
            assertEquals(listOf(SubsCategoryConfig(null, 7, 3)), before.categoryConfigs)
            assertEquals(
                listOf(SubsAppGroupConfig(7, "app.one", 4, null, "app-exclude")),
                before.appGroupConfigs
            )
            assertEquals(
                listOf(SubsGlobalGroupConfig(7, 4, true, "global-exclude")),
                before.globalGroupConfigs
            )

            assertEquals(listOf("app.two", "app.one"), database.appLastVisitDao().query().first())
            database.appLastVisitDao().insert(AppLastVisit("app.one", 400))
            assertEquals(listOf("app.one", "app.two"), database.appLastVisitDao().query().first())
            assertEquals(
                listOf(42L),
                database.activityLogDao().insert(ActivityLog(ctime = 400, appId = "app.two"))
            )
            assertEquals(2, database.activityLogDao().count().first())
            assertEquals(1, database.a11yEventLogDao().count().first())

            // Renaming child tables must preserve both parent-update and cascading-delete behavior.
            val updatedItem = before.subsItems.single().copy(enable = false)
            database.subsItemDao().upsert(updatedItem)
            assertEquals(before.copy(subsItems = listOf(updatedItem)), database.captureConfigurations())
            database.subsItemDao().deleteById(7)
            assertEquals(SubscriptionConfigSnapshot(), database.captureConfigurations())
        } finally {
            database.close()
        }
    }

    @Test
    fun migration14To15DeduplicatesWholeConfigurationsAndPreservesTheirMeaning() = runBlocking {
        val name = "configuration-migration.db"
        val helper = migrationHelper(name)
        helper.createDatabase(14).apply {
            execSQL("INSERT INTO subs_item VALUES (7, 1, 1, 1, 1, 0, NULL)")
            execSQL("INSERT INTO subs_item VALUES (8, 1, 1, 1, 1, 1, NULL)")
            // IDs deliberately disagree with insertion order and configuration values.
            execSQL("INSERT INTO subs_config VALUES (20, 2, 1, 7, 'app.one', 4, 'new')")
            execSQL("INSERT INTO subs_config VALUES (10, 2, 0, 7, 'app.one', 4, 'old')")
            execSQL("INSERT INTO subs_config VALUES (21, 2, 1, 7, 'app.two', 4, '')")
            execSQL("INSERT INTO subs_config VALUES (22, 2, 1, 8, 'app.one', 4, '')")
            execSQL("INSERT INTO subs_config VALUES (40, 3, 1, 7, '', 4, 'new-global')")
            execSQL("INSERT INTO subs_config VALUES (30, 3, NULL, 7, '', 4, 'old-global')")
            execSQL("INSERT INTO app_config VALUES (60, 1, 7, 'app.one')")
            execSQL("INSERT INTO app_config VALUES (50, 0, 7, 'app.one')")
            execSQL("INSERT INTO category_config VALUES (80, 0, 7, 3)")
            execSQL("INSERT INTO category_config VALUES (70, NULL, 7, 3)")
            // Old subscription deletion could leave orphaned app overrides behind.
            execSQL("INSERT INTO app_config VALUES (90, 0, 99, 'orphan')")
            execSQL("INSERT INTO category_config VALUES (91, 0, 99, 3)")
            execSQL("INSERT INTO subs_config VALUES (92, 2, 0, 99, 'orphan', 4, '')")
            execSQL("INSERT INTO subs_config VALUES (93, 3, 0, 99, '', 4, '')")
            close()
        }
        helper.runMigrationsAndValidate(15, listOf(Migration14To15)).close()
        val database = openDatabase(name)
        try {
            val appDao = database.subsAppGroupConfigDao()
            assertEquals(3, appDao.queryAll().size)
            assertEquals(
                SubsAppGroupConfig(7, "app.one", 4, false, "old"),
                appDao.queryConfig(7, "app.one", 4).first()
            )
            assertEquals(
                listOf(SubsGlobalGroupConfig(7, 4, null, "old-global")),
                database.subsGlobalGroupConfigDao().queryAll()
            )
            assertEquals(
                listOf(SubsAppConfig(false, 7, "app.one")),
                database.subsAppConfigDao().queryAll()
            )
            assertEquals(
                listOf(SubsCategoryConfig(null, 7, 3)),
                database.subsCategoryConfigDao().queryAll()
            )

            // A switch update must affect the same sole row used by first-match UI and indexed runtime reads.
            appDao.upsert(SubsAppGroupConfig(7, "app.one", 4, true, "old"))
            val configs = appDao.queryByAppId(7, "app.one").first()
            assertEquals(1, configs.size)
            assertEquals(true, configs.find { it.groupKey == 4 }?.enable)
            assertEquals(true, appDao.queryUsedList().first().associateBy {
                Triple(it.subsId, it.appId, it.groupKey)
            }[Triple(7L, "app.one", 4)]?.enable)
        } finally {
            database.close()
        }
    }

    @Test
    fun migration9To10PreservesRenamedForeignIds() = runBlocking {
        val helper = migrationHelper("migration-9-10.db")
        helper.createDatabase(9).apply {
            execSQL(
                """
                INSERT INTO subs_config
                    (id, type, enable, subs_item_id, app_id, group_key, exclude)
                VALUES (101, 2, 1, 77, 'sample.app', 5, '')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO category_config
                    (id, enable, subs_item_id, category_key)
                VALUES (102, 1, 88, 6)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(10, emptyList()).use { connection ->
            assertEquals(
                77L,
                connection.queryLong("SELECT subs_id FROM subs_config WHERE id = 101"),
            )
            assertEquals(
                88L,
                connection.queryLong("SELECT subs_id FROM category_config WHERE id = 102"),
            )
        }
    }

    @Test
    fun migration10To11PreservesSnapshotDataOutsideDeletedColumns() = runBlocking {
        val helper = migrationHelper("migration-10-11.db")
        helper.createDatabase(10).apply {
            execSQL(
                """
                INSERT INTO snapshot
                    (id, app_id, activity_id, app_name, app_version_code,
                     app_version_name, screen_height, screen_width, is_landscape,
                     github_asset_id)
                VALUES
                    (201, 'sample.app', 'sample.Activity', 'Old name', 12,
                     '1.2', 1920, 1080, 0, 301)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(11, emptyList()).use { connection ->
            assertEquals(
                "sample.app",
                connection.queryText("SELECT app_id FROM snapshot WHERE id = 201"),
            )
            assertEquals(
                1920L,
                connection.queryLong("SELECT screen_height FROM snapshot WHERE id = 201"),
            )
            assertEquals(
                301L,
                connection.queryLong("SELECT github_asset_id FROM snapshot WHERE id = 201"),
            )
        }
    }

    @Test
    fun databaseOpensVersion14AndRollsBackFailedTransaction() = runBlocking {
        val databaseName = "version-14.db"
        val helper = migrationHelper(databaseName)
        helper.createDatabase(14).apply {
            execSQL(
                """
                INSERT INTO subs_item
                    (id, ctime, mtime, enable, enable_update, `order`, update_url)
                VALUES (42, 1, 1, 1, 1, 0, NULL)
                """.trimIndent()
            )
            close()
        }

        val database = openDatabase(databaseName)
        try {
            assertEquals(listOf(42L), database.subsItemDao().queryAll().map { it.id })

            var failed = false
            try {
                database.withWriteTransaction {
                    database.subsItemDao().upsert(SubsItem(id = 43, order = 1))
                    error("rollback")
                }
            } catch (_: IllegalStateException) {
                failed = true
            }

            assertTrue(failed)
            assertEquals(listOf(42L), database.subsItemDao().queryAll().map { it.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun flowPagingAndListConverterWorkOnJvm() = runBlocking {
        val databaseName = "room3-query-adapters.db"
        val database = openDatabase(databaseName)
        try {
            val expectedText = listOf("first", "second")
            database.a11yEventLogDao().insert(
                listOf(
                    A11yEventLog(
                        id = 1,
                        ctime = 2,
                        type = 3,
                        appId = "sample.app",
                        name = "sample.Event",
                        desc = null,
                        text = expectedText,
                    )
                )
            )

            assertEquals(1, database.a11yEventLogDao().count().first())
            when (
                val result = database.a11yEventLogDao().pagingSource().load(
                    PagingSource.LoadParams.Refresh(
                        key = null,
                        loadSize = 10,
                        placeholdersEnabled = false,
                    )
                )
            ) {
                is PagingSource.LoadResult.Page -> {
                    assertEquals(1, result.data.size)
                    assertEquals(expectedText, result.data.single().text)
                }

                is PagingSource.LoadResult.Error -> throw result.throwable
                is PagingSource.LoadResult.Invalid -> error("PagingSource became invalid before loading")
            }
        } finally {
            database.close()
        }
    }
}
