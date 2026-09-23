package li.gkd.app.util

import android.net.Uri
import li.gkd.app.text.UiStrings
import li.gkd.app.app
import java.io.ByteArrayOutputStream
import java.io.IOException

object UriUtils {
    fun uri2Bytes(uri: Uri, maxBytes: Int = DEFAULT_MAX_BYTES): ByteArray {
        require(maxBytes > 0)
        app.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream(minOf(maxBytes, BUFFER_SIZE))
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                if (output.size() + size > maxBytes) {
                    throw IOException(UiStrings.file_too_large)
                }
                output.write(buffer, 0, size)
            }
            return output.toByteArray()
        }
        return ByteArray(0)
    }

    private const val BUFFER_SIZE = 8_192
    private const val DEFAULT_MAX_BYTES = 32 * 1024 * 1024
}
