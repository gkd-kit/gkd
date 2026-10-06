package li.gkd.app.storage

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipArchive {
    private const val BUFFER_LEN = 8192

    data class ExtractLimits(
        val maxEntryCount: Int = 4_096,
        val maxEntryBytes: Long = 32L * 1024 * 1024,
        val maxTotalBytes: Long = 128L * 1024 * 1024,
    )

    private fun zipFile(
        srcFile: File,
        rawRootPath: String,
        zos: ZipOutputStream,
    ) {
        val rootPath =
            rawRootPath + (if (rawRootPath.isBlank()) "" else "/") + srcFile.getName()
        if (srcFile.isDirectory()) {
            val fileList = srcFile.listFiles()
                ?: throw IOException("Cannot list directory: $srcFile")
            if (fileList.isEmpty()) {
                val entry = ZipEntry("$rootPath/")
                zos.putNextEntry(entry)
                zos.closeEntry()
            } else {
                for (file in fileList) {
                    zipFile(file, rootPath, zos)
                }
            }
        } else {
            BufferedInputStream(FileInputStream(srcFile)).use { stream ->
                val entry = ZipEntry(rootPath)
                zos.putNextEntry(entry)
                val buffer = ByteArray(BUFFER_LEN)
                var len: Int
                while ((stream.read(buffer, 0, BUFFER_LEN).also { len = it }) != -1) {
                    zos.write(buffer, 0, len)
                }
                zos.closeEntry()
            }
        }
    }

    fun zipFiles(srcFiles: Collection<File>, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for (srcFile in srcFiles) {
                zipFile(srcFile, "", zos)
            }
        }
    }

    fun unzipFile(
        zipFile: File,
        destDir: File,
        limits: ExtractLimits = ExtractLimits(),
    ) {
        require(limits.maxEntryCount > 0)
        require(limits.maxEntryBytes > 0)
        require(limits.maxTotalBytes > 0)
        Files.createDirectories(destDir.toPath())
        val rootPath = destDir.canonicalFile.toPath()
        var entryCount = 0
        var totalBytes = 0L
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                entryCount += 1
                if (entryCount > limits.maxEntryCount) {
                    throw StorageException(
                        ArchiveIssue.FileCountExceeded,
                        limits.maxEntryCount
                    )
                }
                if (entry.name.indexOf('\\') >= 0) {
                    throw StorageException(ArchiveIssue.PathInvalid, entry.name)
                }
                val outPath = rootPath.resolve(entry.name).normalize()
                if (!outPath.startsWith(rootPath)) {
                    throw StorageException(
                        ArchiveIssue.PathOutsideDestination,
                        entry.name
                    )
                }
                val outFile = outPath.toFile()
                if (entry.isDirectory) {
                    Files.createDirectories(outPath)
                } else {
                    val declaredSize = entry.size
                    if (declaredSize > limits.maxEntryBytes) {
                        throw StorageException(
                            ArchiveIssue.FileTooLarge,
                            entry.name
                        )
                    }
                    outFile.parentFile?.let { parent ->
                        Files.createDirectories(parent.toPath())
                    }
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            val buffer = ByteArray(BUFFER_LEN)
                            var entryBytes = 0L
                            while (true) {
                                val size = input.read(buffer)
                                if (size < 0) break
                                entryBytes += size
                                totalBytes += size
                                if (entryBytes > limits.maxEntryBytes) {
                                    throw StorageException(
                                        ArchiveIssue.FileTooLarge,
                                        entry.name
                                    )
                                }
                                if (totalBytes > limits.maxTotalBytes) {
                                    throw StorageException(ArchiveIssue.TotalSizeExceeded)
                                }
                                output.write(buffer, 0, size)
                            }
                        }
                    }
                }
            }
        }
    }
}
