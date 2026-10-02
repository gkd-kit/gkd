package li.gkd.app.snapshot.platform

import li.gkd.app.platform.PlatformResult
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.io.FileOutputStream

actual fun prepareSnapshotReplacement(original: File, bytes: ByteArray, output: File): Boolean {
    val previous = try {
        Image.makeFromEncoded(original.readBytes())
    } catch (_: IllegalArgumentException) {
        return false
    }
    return previous.use {
        val replacement = try {
            Image.makeFromEncoded(bytes)
        } catch (_: IllegalArgumentException) {
            return false
        }
        replacement.use {
            if (previous.width != replacement.width || previous.height != replacement.height) false
            else {
                replacement.encodeToData(EncodedImageFormat.WEBP, 85)?.use { data ->
                    FileOutputStream(output).use { stream -> stream.write(data.bytes); stream.fd.sync() }
                } ?: error("Cannot encode screenshot")
                true
            }
        }
    }
}

actual suspend fun saveImageToAlbum(image: File): PlatformResult<Unit> = PlatformResult.Unsupported
