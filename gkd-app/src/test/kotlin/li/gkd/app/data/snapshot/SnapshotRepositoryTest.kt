package li.gkd.app.data.snapshot

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import li.gkd.db.Snapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class SnapshotRepositoryTest {
    @Test
    fun deleteRestoresDirectoryWhenDatabaseDeleteFails() = runBlocking {
        val root = Files.createTempDirectory("gkd-snapshot-delete-test").toFile()
        val snapshot = snapshot()
        val directory = root.resolve(snapshot.id.toString()).apply { mkdirs() }
        directory.resolve("data").writeText("value")
        try {
            val repository = SnapshotStore(FakeSnapshotDao(deleteFailure = IOException("db")), root)

            val result = runCatching { repository.delete(snapshot) }

            assertTrue(result.isFailure)
            assertTrue(directory.resolve("data").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deleteRemovesStagedDirectoryAfterDatabaseDeleteSucceeds() = runBlocking {
        val root = Files.createTempDirectory("gkd-snapshot-delete-success-test").toFile()
        val snapshot = snapshot()
        val directory = root.resolve(snapshot.id.toString()).apply { mkdirs() }
        directory.resolve("data").writeText("value")
        try {
            val repository = SnapshotStore(FakeSnapshotDao(), root)

            repository.delete(snapshot)

            assertFalse(directory.exists())
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deleteBatchRemovesDirectoriesAfterDatabaseDeleteSucceeds() = runBlocking {
        val root = Files.createTempDirectory("gkd-snapshot-batch-delete-success-test").toFile()
        val snapshot1 = snapshot(1)
        val snapshot2 = snapshot(2)
        val dir1 = root.resolve(snapshot1.id.toString()).apply { mkdirs() }
        val dir2 = root.resolve(snapshot2.id.toString()).apply { mkdirs() }
        dir1.resolve("data1").writeText("v1")
        dir2.resolve("data2").writeText("v2")
        try {
            val repository = SnapshotStore(FakeSnapshotDao(), root)

            repository.delete(listOf(snapshot1, snapshot2))

            assertFalse(dir1.exists())
            assertFalse(dir2.exists())
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deleteBatchRestoresDirectoriesWhenDatabaseDeleteFails() = runBlocking {
        val root = Files.createTempDirectory("gkd-snapshot-batch-delete-fail-test").toFile()
        val snapshot1 = snapshot(1)
        val snapshot2 = snapshot(2)
        val dir1 = root.resolve(snapshot1.id.toString()).apply { mkdirs() }
        val dir2 = root.resolve(snapshot2.id.toString()).apply { mkdirs() }
        dir1.resolve("data1").writeText("v1")
        dir2.resolve("data2").writeText("v2")
        try {
            val repository = SnapshotStore(FakeSnapshotDao(deleteFailure = IOException("db error")), root)

            val result = runCatching { repository.delete(listOf(snapshot1, snapshot2)) }

            assertTrue(result.isFailure)
            assertTrue(dir1.resolve("data1").isFile)
            assertTrue(dir2.resolve("data2").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun snapshot(id: Long = 1) = Snapshot(
        id = id,
        appId = "app.id",
        activityId = null,
        screenHeight = 1,
        screenWidth = 1,
        isLandscape = false,
    )

    private class FakeSnapshotDao(
        private val deleteFailure: Exception? = null,
    ) : Snapshot.SnapshotDao {
        override suspend fun update(vararg objects: Snapshot): Int = objects.size

        override suspend fun insert(vararg users: Snapshot): List<Long> = users.map { it.id }

        override suspend fun deleteAll() = Unit

        override suspend fun delete(vararg users: Snapshot): Int {
            deleteFailure?.let { throw it }
            return users.size
        }

        override fun query(): Flow<List<Snapshot>> = flowOf(emptyList())

        override suspend fun deleteGithubAssetId(id: Long) = Unit

        override fun count(): Flow<Int> = flowOf(0)
    }
}
