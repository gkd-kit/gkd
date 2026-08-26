package li.gkd.app.snapshot

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SnapshotFileLayoutTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun committedEntryUsesCompatibleSnapshotPaths() {
        val root = temporaryFolder.root
        val files = SnapshotFileLayout(root).committed(123)
        val directory = root.resolve("123")

        assertEquals(directory, files.directory)
        assertEquals(directory.resolve("123.json"), files.snapshotFile)
        assertEquals(directory.resolve("123.min.json"), files.minSnapshotFile)
        assertEquals(directory.resolve("123.webp"), files.webpFile)
        assertEquals(directory.resolve("123.png"), files.legacyPngFile)
    }

    @Test
    fun screenshotFileFallsBackToLegacyPng() {
        val files = SnapshotFileLayout(temporaryFolder.root).committed(123)

        assertEquals(files.legacyPngFile, files.screenshotFile)

        files.directory.mkdirs()
        files.legacyPngFile.writeBytes(
            byteArrayOf(
                0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            )
        )
        files.webpFile.writeText("invalid")

        assertEquals(files.legacyPngFile, files.screenshotFile)

        files.webpFile.writeBytes(
            byteArrayOf(
                0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50,
            )
        )

        assertEquals(files.webpFile, files.screenshotFile)
    }
}
