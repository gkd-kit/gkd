package li.gkd.app.storage

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object BoundedStreams {
    /** Caller owns the streams. Never writes the bytes exceeding the limit. */
    fun copy(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long,
        tooLarge: () -> IOException
    ): Long {
        require(maxBytes > 0)
        val buffer = ByteArray(8192)
        var copied = 0L
        while (true) {
            val size = input.read(buffer)
            if (size < 0) return copied
            if (size == 0) {
                val value = input.read()
                if (value < 0) return copied
                if (copied == maxBytes) throw tooLarge()
                output.write(value)
                copied++
            } else {
                if (size.toLong() > maxBytes - copied) throw tooLarge()
                output.write(buffer, 0, size)
                copied += size
            }
        }
    }
}
