package li.gkd.app.backup

import li.gkd.app.storage.BoundedStreams
import li.gkd.app.storage.StorageException
import li.gkd.app.storage.StorageIssue
import li.gkd.app.storage.ZipArchive
import java.io.File
import java.io.InputStream

object BackupArchiveReader {
    fun extract(open: () -> InputStream, archiveFile: File, destination: File) {
        copyArchive(open, archiveFile)
        ZipArchive.unzipFile(archiveFile, destination)
    }

    private fun copyArchive(open: () -> InputStream, archiveFile: File) {
        val input = open()
        input.use {
            archiveFile.outputStream().use { output ->
                BoundedStreams.copy(input, output, MAX_ARCHIVE_BYTES) {
                    StorageException(StorageIssue.backup_archive_too_large)
                }
            }
        }
    }

    private val MAX_ARCHIVE_BYTES = 64L * 1024 * 1024
}
