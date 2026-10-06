package li.gkd.app.backup

import li.gkd.app.storage.BackupIssue
import li.gkd.app.storage.StorageException
import li.gkd.app.testing.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException

class BackupArchiveReaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder(TestFiles.root)

    @Test
    fun deniedSourceOpenRequestsReselection() {
        val cause = SecurityException("source permission revoked")
        val error = assertThrows(StorageException::class.java) {
            BackupArchiveReader.extract(
                { throw cause },
                temporaryFolder.root.resolve("input.zip"),
                temporaryFolder.root.resolve("output"),
            )
        }
        assertEquals(BackupIssue.SourceAccessDenied, error.issue)
        assertSame(cause, error.cause)
    }

    @Test
    fun sourceReadFailuresKeepTheirOriginalCause() {
        for (cause in listOf(IOException("read failed"), SecurityException("read denied"), IllegalStateException("bug"))) {
            val error = assertThrows(cause.javaClass) {
                BackupArchiveReader.extract(
                    { object : InputStream() {
                        override fun read(): Int = throw cause
                    } },
                    temporaryFolder.root.resolve("input.zip"),
                    temporaryFolder.root.resolve("output"),
                )
            }
            assertSame(cause, error)
        }
    }

    @Test
    fun malformedZipReportsInvalidArchive() {
        val error = assertThrows(StorageException::class.java) {
            BackupArchiveReader.extract(
                { "not a zip".byteInputStream() },
                temporaryFolder.root.resolve("input.zip"),
                temporaryFolder.root.resolve("output"),
            )
        }
        assertEquals(BackupIssue.InvalidArchive, error.issue)
        org.junit.Assert.assertTrue(error.cause is ZipException)
    }
}
