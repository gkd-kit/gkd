package li.gkd.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ExportFileNamesTest {
    @Test
    fun repeatedExportsReserveDifferentFilesWithoutReplacingTheFirst() {
        val directory = Files.createTempDirectory("gkd-export-names-test").toFile()
        try {
            val first = ExportFileNames.reserve(directory, "gkd-backup-20260923_143205", "zip")
            first.writeText("original")

            val second = ExportFileNames.reserve(directory, "gkd-backup-20260923_143205", "zip")

            assertEquals("gkd-backup-20260923_143205-2.zip", second.name)
            assertEquals("original", first.readText())
            assertTrue(second.isFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun snapshotNameSkipsExistingArchivesInTheSameSecond() {
        val taken = setOf("App-20260923_143205.zip", "App-20260923_143205-2.zip")

        assertEquals(
            "App-20260923_143205-3.zip",
            ExportFileNames.availableName("App-20260923_143205", "zip", taken::contains),
        )
    }
}
