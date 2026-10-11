package li.gkd.app.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import li.gkd.app.error.BusinessError
import li.gkd.app.error.ErrorMessage
import li.gkd.app.resources.*

/** A selected source, not a file-picker or a permission owner. */
sealed interface FileSource {
    data class Local(val file: File) : FileSource
    data class Uri(val value: String) : FileSource
}

expect fun openFileSource(source: FileSource): InputStream

class FileTooLargeException : IOException("File exceeds the size limit"), BusinessError {
    override val errorCode: String get() = "FileTooLarge"
    override val errorMessage: ErrorMessage get() = ErrorMessage.Resource(Res.string.file_too_large)
}

suspend fun readFileBytes(source: FileSource, maxBytes: Int = 32 * 1024 * 1024): ByteArray =
    withContext(Dispatchers.IO) {
        openFileSource(source).use { input ->
            ByteArrayOutputStream(minOf(maxBytes, 8192)).use { output ->
                BoundedStreams.copy(input, output, maxBytes.toLong()) { FileTooLargeException() }
                output.toByteArray()
            }
        }
    }
