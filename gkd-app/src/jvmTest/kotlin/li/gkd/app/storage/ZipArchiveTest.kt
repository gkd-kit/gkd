package li.gkd.app.storage

import li.gkd.app.testing.TestFiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipArchiveTest {
    private val archive get() = ZipArchive

    @Test
    fun unzipRejectsEntryOutsideDestination() {
        val directory =
            Files.createTempDirectory(TestFiles.root.toPath(), "gkd-zip-path-test").toFile()
        try {
            val zipFile = directory.resolve("input.zip")
            ZipOutputStream(zipFile.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("../escaped.txt"))
                output.write("escaped".toByteArray())
                output.closeEntry()
            }

            assertThrows(IOException::class.java) {
                archive.unzipFile(zipFile, directory.resolve("output"))
            }
            assertFalse(directory.resolve("escaped.txt").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun unzipEnforcesActualEntrySize() {
        val directory =
            Files.createTempDirectory(TestFiles.root.toPath(), "gkd-zip-size-test").toFile()
        try {
            val zipFile = directory.resolve("input.zip")
            ZipOutputStream(zipFile.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("value.txt"))
                output.write("12345".toByteArray())
                output.closeEntry()
            }

            assertThrows(IOException::class.java) {
                archive.unzipFile(
                    zipFile,
                    directory.resolve("output"),
                    ZipArchive.ExtractLimits(maxEntryBytes = 4),
                )
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun unzipExtractsValidEntries() {
        val directory =
            Files.createTempDirectory(TestFiles.root.toPath(), "gkd-zip-valid-test").toFile()
        try {
            val zipFile = directory.resolve("input.zip")
            ZipOutputStream(zipFile.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("store/value.txt"))
                output.write("value".toByteArray())
                output.closeEntry()
            }
            val output = directory.resolve("output")

            archive.unzipFile(zipFile, output)

            assertEquals("value", output.resolve("store/value.txt").readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
