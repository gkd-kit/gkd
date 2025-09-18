package li.songe.gkd.util

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipUtils {
    private const val BUFFER_LEN = 8192
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
    ) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = destDir.resolve(entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
}