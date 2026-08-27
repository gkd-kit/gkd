package li.gkd.db

import androidx.paging.PagingSource
import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

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
    fun everyExportedSchemaMigratesToVersion14() = runBlocking {
        // Protects the persisted schema compatibility contract for every released version.
        for (startVersion in 1 until 14) {
            val helper = migrationHelper("all-migrations-$startVersion.db")
            helper.createDatabase(startVersion).close()
            helper.runMigrationsAndValidate(14, emptyList()).close()
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
                    database.subsItemDao().insert(SubsItem(id = 43, order = 1))
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
