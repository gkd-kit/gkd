package li.gkd.app.snapshot

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.CountDownLatch

class SnapshotDirectoryTransactionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun commitPublishesCompleteDirectory() = runBlocking {
        val root = temporaryFolder.root
        val target = root.resolve("123")

        commitSnapshotDirectory(
            layout = SnapshotFileLayout(root),
            id = 123,
            write = { files -> files.snapshotFile.writeText("data") },
            publish = {},
        )

        assertEquals("data", target.resolve("123.json").readText())
        assertFalse(root.resolve(".123.tmp").exists())
    }

    @Test
    fun commitRemovesStagingDirectoryWhenWriteFails() {
        val root = temporaryFolder.root
        val target = root.resolve("123")

        assertThrows(IOException::class.java) {
            runBlocking {
                commitSnapshotDirectory(
                    layout = SnapshotFileLayout(root),
                    id = 123,
                    write = { files ->
                        files.snapshotFile.writeText("partial")
                        throw IOException("write failed")
                    },
                    publish = { error("publish must not run") },
                )
            }
        }

        assertFalse(target.exists())
        assertFalse(root.resolve(".123.tmp").exists())
    }

    @Test
    fun commitRemovesPublishedDirectoryWhenPublishFails() {
        val root = temporaryFolder.root
        val target = root.resolve("123")

        assertThrows(IOException::class.java) {
            runBlocking {
                commitSnapshotDirectory(
                    layout = SnapshotFileLayout(root),
                    id = 123,
                    write = { files -> files.snapshotFile.writeText("data") },
                    publish = { throw IOException("publish failed") },
                )
            }
        }

        assertFalse(target.exists())
        assertFalse(root.resolve(".123.tmp").exists())
    }

    @Test
    fun commitDoesNotOverwriteExistingSnapshot() {
        val root = temporaryFolder.root
        val target = temporaryFolder.newFolder("123")
        val existing = target.resolve("123.json").apply { writeText("existing") }

        assertThrows(IOException::class.java) {
            runBlocking {
                commitSnapshotDirectory(
                    layout = SnapshotFileLayout(root),
                    id = 123,
                    write = { error("write must not run") },
                    publish = { error("publish must not run") },
                )
            }
        }

        assertTrue(target.exists())
        assertEquals("existing", existing.readText())
    }

    @Test
    fun cancellationDuringPublishDoesNotRollBackCommittedDirectory() = runBlocking {
        val root = temporaryFolder.root
        val target = root.resolve("123")
        val publishStarted = CompletableDeferred<Unit>()
        val finishPublish = CompletableDeferred<Unit>()
        val task = async {
            commitSnapshotDirectory(
                layout = SnapshotFileLayout(root),
                id = 123,
                write = { files -> files.snapshotFile.writeText("data") },
                publish = {
                    publishStarted.complete(Unit)
                    finishPublish.await()
                },
            )
        }

        publishStarted.await()
        task.cancel()
        finishPublish.complete(Unit)
        task.join()

        assertEquals("data", target.resolve("123.json").readText())
        assertFalse(root.resolve(".123.tmp").exists())
    }

    @Test
    fun cancellationDuringWriteDoesNotPublishPartialDirectory() = runBlocking {
        val root = temporaryFolder.root
        val writeStarted = CountDownLatch(1)
        val finishWrite = CountDownLatch(1)
        var published = false
        val task = async(Dispatchers.Default) {
            commitSnapshotDirectory(
                layout = SnapshotFileLayout(root),
                id = 123,
                write = { files ->
                    files.snapshotFile.writeText("partial")
                    writeStarted.countDown()
                    finishWrite.await()
                },
                publish = { published = true },
            )
        }

        writeStarted.await()
        task.cancel()
        finishWrite.countDown()
        task.join()

        assertFalse(published)
        assertFalse(root.resolve("123").exists())
        assertFalse(root.resolve(".123.tmp").exists())
    }
}
