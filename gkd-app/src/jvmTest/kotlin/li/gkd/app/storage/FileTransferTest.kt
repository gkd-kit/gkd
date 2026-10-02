package li.gkd.app.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class FileTransferTest {
    private fun session(): File {
        val root = File("../.local/tests/desktop").apply { mkdirs() }
        return Files.createTempDirectory(root.toPath(), "file-transfer-").toFile()
    }

    @Test
    fun localAndUriSourcesUseTheSameLimit() = runTest {
        val root = session()
        try {
            val file = root.resolve("space name.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            assertContentEquals(file.readBytes(), readFileBytes(FileSource.Local(file), 3))
            assertContentEquals(
                file.readBytes(),
                readFileBytes(FileSource.Uri(file.toURI().toString()), 3)
            )
            assertFailsWith<FileTooLargeException> { readFileBytes(FileSource.Local(file), 2) }
            assertFailsWith<IOException> { readFileBytes(FileSource.Local(root.resolve("missing"))) }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingSourceKeepsExistingExportAndRemovesStagingFile() = runTest {
        val root = session()
        try {
            val source = root.resolve("source").apply { writeText("new") }
            val target = root.resolve("target").apply { writeText("old") }
            assertFailsWith<IOException> { FileExports.copyTo(root.resolve("missing"), target) }
            assertEquals("old", target.readText())
            assertEquals(setOf("source", "target"), root.list()!!.toSet())
            FileExports.copyTo(source, target)
            assertEquals("new", target.readText())
            FileExports.copyTo(source, source)
            assertEquals("new", source.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancelledExportCleansItsGeneratedArchive() = runTest {
        val root = session()
        try {
            val archive = root.resolve("export.zip").apply { writeText("archive") }
            assertFailsWith<CancellationException> {
                FileExports.withTemporaryFile(
                    create = { archive }, delete = { it.delete() },
                    consume = { throw CancellationException("cancelled picker") })
            }
            assertFalse(archive.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cleanupFailureDoesNotReplaceTheOperationFailure() = runTest {
        val original = IOException("export failed")
        val cleanup = IOException("cleanup failed")
        val thrown = assertFailsWith<IOException> {
            FileExports.withTemporaryFile(
                create = { File("not-created") }, delete = { throw cleanup },
                consume = { throw original })
        }
        assertSame(original, thrown)
        val suppressed = assertIs<IOException>(thrown.suppressed.single())
        assertEquals(cleanup.message, suppressed.message)
    }

    @Test
    fun zeroLengthReadDoesNotLoopOrSkipLimit() {
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2)) {
            override fun read(bytes: ByteArray, offset: Int, length: Int) = 0
        }
        val output = ByteArrayOutputStream()
        assertFailsWith<IOException> {
            BoundedStreams.copy(
                input,
                output,
                1
            ) { IOException("limit") }
        }
        assertContentEquals(byteArrayOf(1), output.toByteArray())
    }
}
