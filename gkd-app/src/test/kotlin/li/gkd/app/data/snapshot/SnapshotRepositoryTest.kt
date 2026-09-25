package li.gkd.app.data.snapshot

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import li.gkd.db.Snapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class SnapshotRepositoryTest {
    @Test
    fun uploadLinkIsRecordedWhenScreenshotIsUnchanged() = runBlocking {
        val root = Files.createTempDirectory("gkd-snapshot-upload-test").toFile()
        try {
            val screenshot = screenshot(root)
            val dao = FakeSnapshotDao()
            val repository = SnapshotStore(dao, root)

            assertTrue(repository.markUploaded(1, 42, screenshot.lastModified()))
            assertEquals(42, dao.markedAssetId)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun uploadLinkIsNotRecordedAfterScreenshotChanges() = runBlocking {
        val root = Files.createTempDirectory("gkd-snapshot-upload-changed-test").toFile()
        try {
            val screenshot = screenshot(root)
            val originalModifiedAt = screenshot.lastModified()
            assertTrue(screenshot.setLastModified(originalModifiedAt + 10_000))
            val dao = FakeSnapshotDao()
            val repository = SnapshotStore(dao, root)

            assertFalse(repository.markUploaded(1, 42, originalModifiedAt))
            assertEquals(null, dao.markedAssetId)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun screenshot(root: java.io.File) = root.resolve("1").apply { mkdirs() }
        .resolve("1.png").apply {
            writeBytes(byteArrayOf(
                0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            ))
            check(setLastModified(10_000))
        }

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

    private fun snapshot() = Snapshot(
        id = 1,
        appId = "app.id",
        activityId = null,
        screenHeight = 1,
        screenWidth = 1,
        isLandscape = false,
    )

    private class FakeSnapshotDao(
        private val deleteFailure: Exception? = null,
    ) : Snapshot.SnapshotDao {
        var markedAssetId: Int? = null

        override suspend fun insert(vararg users: Snapshot): List<Long> = users.map { it.id }

        override suspend fun delete(vararg users: Snapshot): Int {
            deleteFailure?.let { throw it }
            return users.size
        }

        override fun query(): Flow<List<Snapshot>> = flowOf(emptyList())

        override suspend fun deleteGithubAssetId(id: Long) = Unit

        override suspend fun markUploadedIfPending(id: Long, assetId: Int): Int {
            markedAssetId = assetId
            return 1
        }

        override fun count(): Flow<Int> = flowOf(0)
    }
}
