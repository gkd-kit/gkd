package li.gkd.app.backup

import java.io.File
import java.io.InputStream
import java.util.zip.ZipException
import li.gkd.app.storage.BackupIssue
import li.gkd.app.storage.BoundedStreams
import li.gkd.app.storage.StorageException
import li.gkd.app.storage.ZipArchive

object BackupArchiveReader {
    fun extract(open: () -> InputStream, archiveFile: File, destination: File) {
        copyArchive(open, archiveFile)
        try {
            ZipArchive.unzipFile(archiveFile, destination)
        } catch (e: ZipException) {
            throw StorageException(BackupIssue.InvalidArchive, cause = e)
        }
    }

    private fun copyArchive(open: () -> InputStream, archiveFile: File) {
        val input = try {
            open()
        } catch (e: SecurityException) {
            throw StorageException(BackupIssue.SourceAccessDenied, cause = e)
        }
        input.use {
            archiveFile.outputStream().use { output ->
                BoundedStreams.copy(input, output, MAX_ARCHIVE_BYTES) {
                    StorageException(BackupIssue.ArchiveTooLarge)
                }
            }
        }
    }

    private val MAX_ARCHIVE_BYTES = 64L * 1024 * 1024
}
