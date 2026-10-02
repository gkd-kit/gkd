package li.gkd.app.snapshot

import kotlinx.coroutines.test.runTest
import li.gkd.app.platform.PlatformResult
import li.gkd.app.snapshot.platform.prepareSnapshotReplacement
import li.gkd.app.snapshot.platform.saveImageToAlbum
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotImagesTest {
    private fun png(width: Int, height: Int) = ByteArrayOutputStream().use {
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", it)
        it.toByteArray()
    }

    @Test
    fun replacementEncodesWebpAndPreservesDimensions() {
        val root = File("../.local/tests/desktop").apply { mkdirs() }
        val directory = Files.createTempDirectory(root.toPath(), "snapshot-codec-").toFile()
        try {
            val original = directory.resolve("original.png").apply { writeBytes(png(4, 3)) }
            val output = directory.resolve("replacement.webp")
            assertTrue(prepareSnapshotReplacement(original, png(4, 3), output))
            assertEquals("WEBP", output.readBytes().copyOfRange(8, 12).decodeToString())
            Image.makeFromEncoded(output.readBytes()).use {
                assertEquals(4, it.width)
                assertEquals(3, it.height)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidOrDifferentSizeDoesNotOverwriteOutput() {
        val root = File("../.local/tests/desktop").apply { mkdirs() }
        val directory = Files.createTempDirectory(root.toPath(), "snapshot-codec-").toFile()
        try {
            val original = directory.resolve("original.png").apply { writeBytes(png(4, 3)) }
            val output = directory.resolve("replacement.webp").apply { writeText("unchanged") }
            assertFalse(prepareSnapshotReplacement(original, png(3, 4), output))
            assertEquals("unchanged", output.readText())
            assertFalse(prepareSnapshotReplacement(original, byteArrayOf(1, 2, 3), output))
            assertEquals("unchanged", output.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun desktopAlbumWriteIsExplicitlyUnsupported() = runTest {
        val image = File("not-created-by-unsupported-action.webp")
        assertEquals(PlatformResult.Unsupported, saveImageToAlbum(image))
        assertFalse(image.exists())
    }
}
