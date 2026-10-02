package li.gkd.app.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/** A selected source, not a file-picker or a permission owner. */
sealed interface FileSource {
    data class Local(val file: File) : FileSource
    data class Uri(val value: String) : FileSource
}

expect fun openFileSource(source: FileSource): InputStream

class FileTooLargeException : IOException("File exceeds the size limit")

suspend fun readFileBytes(source: FileSource, maxBytes: Int = 32 * 1024 * 1024): ByteArray =
    withContext(Dispatchers.IO) {
        require(maxBytes > 0)
        openFileSource(source).use { input ->
            ByteArrayOutputStream(minOf(maxBytes, 8192)).use { output ->
                BoundedStreams.copy(input, output, maxBytes.toLong()) { FileTooLargeException() }
                output.toByteArray()
            }
        }
    }
