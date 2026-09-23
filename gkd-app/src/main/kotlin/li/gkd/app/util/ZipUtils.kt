package li.gkd.app.util

import li.gkd.app.text.UiStrings
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipUtils {
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
        comment: String?,
    ): Boolean {
        val rootPath =
            rawRootPath + (if (rawRootPath.isBlank()) "" else File.separator) + srcFile.getName()
        if (srcFile.isDirectory()) {
            val fileList = srcFile.listFiles()
            if (fileList == null || fileList.size <= 0) {
                val entry = ZipEntry("$rootPath/")
                entry.setComment(comment)
                zos.putNextEntry(entry)
                zos.closeEntry()
            } else {
                for (file in fileList) {
                    if (!zipFile(file, rootPath, zos, comment)) return false
                }
            }
        } else {
            var stream: InputStream? = null
            try {
                stream = BufferedInputStream(FileInputStream(srcFile))
                val entry = ZipEntry(rootPath)
                entry.setComment(comment)
                zos.putNextEntry(entry)
                val buffer: ByteArray? = ByteArray(BUFFER_LEN)
                var len: Int
                while ((stream.read(buffer, 0, BUFFER_LEN).also { len = it }) != -1) {
                    zos.write(buffer, 0, len)
                }
                zos.closeEntry()
            } finally {
                stream?.close()
            }
        }
        return true
    }

    fun zipFiles(srcFiles: Collection<File>, zipFile: File): Boolean {
        var zos: ZipOutputStream? = null
        try {
            zos = ZipOutputStream(FileOutputStream(zipFile))
            for (srcFile in srcFiles) {
                if (!zipFile(srcFile, "", zos, null)) return false
            }
            return true
        } finally {
            if (zos != null) {
                zos.finish()
                zos.close()
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
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw IOException(UiStrings.unzip_directory_create_failed(destDir.name))
        }
        val rootPath = destDir.canonicalFile.toPath()
        var entryCount = 0
        var totalBytes = 0L
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                entryCount += 1
                if (entryCount > limits.maxEntryCount) {
                    throw IOException(UiStrings.archive_file_count_exceeded(limits.maxEntryCount))
                }
                if (entry.name.indexOf('\\') >= 0) {
                    throw IOException(UiStrings.archive_path_invalid(entry.name))
                }
                val outPath = rootPath.resolve(entry.name).normalize()
                if (!outPath.startsWith(rootPath)) {
                    throw IOException(UiStrings.archive_path_outside_destination(entry.name))
                }
                val outFile = outPath.toFile()
                if (entry.isDirectory) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw IOException(UiStrings.unzip_directory_create_failed(entry.name))
                    }
                } else {
                    val declaredSize = entry.size
                    if (declaredSize > limits.maxEntryBytes) {
                        throw IOException(UiStrings.archive_file_too_large(entry.name))
                    }
                    outFile.parentFile?.let { parent ->
                        if (!parent.exists() && !parent.mkdirs()) {
                            throw IOException(UiStrings.unzip_directory_create_failed(parent.name))
                        }
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
                                    throw IOException(UiStrings.archive_file_too_large(entry.name))
                                }
                                if (totalBytes > limits.maxTotalBytes) {
                                    throw IOException(UiStrings.archive_total_size_exceeded)
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
