package li.songe.gkd.shizuku

import android.content.Context
import android.util.Log
import java.io.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.CRC32


object IOUtils {
    private const val TAG = "IOUtils"
    @Throws(IOException::class)
    fun copyStream(from: InputStream, to: OutputStream) {
        val buf = ByteArray(1024 * 1024)
        var len: Int
        while (from.read(buf).also { len = it } > 0) {
            to.write(buf, 0, len)
        }
    }

    @Throws(IOException::class)
    fun copyFile(original: File?, destination: File?) {
        FileInputStream(original).use { inputStream ->
            FileOutputStream(destination).use { outputStream ->
                copyStream(
                    inputStream,
                    outputStream
                )
            }
        }
    }

    @Throws(IOException::class)
    fun copyFileFromAssets(context: Context, assetFileName: String?, destination: File?) {
        context.assets.open(assetFileName!!).use { inputStream ->
            FileOutputStream(destination).use { outputStream ->
                copyStream(
                    inputStream,
                    outputStream
                )
            }
        }
    }

    fun deleteRecursively(f: File) {
        if (f.isDirectory) {
            val files = f.listFiles()
            if (files != null) {
                for (child in files) deleteRecursively(child)
            }
        }
        f.delete()
    }

    @Throws(IOException::class)
    fun calculateFileCrc32(file: File?): Long {
        return calculateCrc32(FileInputStream(file))
    }

    @Throws(IOException::class)
    fun calculateBytesCrc32(bytes: ByteArray?): Long {
        return calculateCrc32(ByteArrayInputStream(bytes))
    }

    @Throws(IOException::class)
    fun calculateCrc32(inputStream: InputStream): Long {
        inputStream.use { `in` ->
            val crc32 = CRC32()
            val buffer = ByteArray(1024 * 1024)
            var read: Int
            while (`in`.read(buffer).also { read = it } > 0) crc32.update(buffer, 0, read)
            return crc32.value
        }
    }

    fun writeStreamToStringBuilder(builder: StringBuilder, inputStream: InputStream?): Thread {
        val t = Thread {
            try {
                val buf = CharArray(1024)
                var len: Int
                val reader =
                    BufferedReader(InputStreamReader(inputStream))
                while (reader.read(buf).also { len = it } > 0) builder.append(buf, 0, len)
                reader.close()
            } catch (e: Exception) {
                Log.wtf(TAG, e)
            }
        }
        t.start()
        return t
    }

    /**
     * Read contents of input stream to a byte array and close it
     *
     * @param inputStream
     * @return contents of input stream
     * @throws IOException
     */
    @Throws(IOException::class)
    fun readStream(inputStream: InputStream): ByteArray {
        inputStream.use { `in` -> return readStreamNoClose(`in`) }
    }

    @Throws(IOException::class)
    fun readStream(inputStream: InputStream, charset: Charset?): String {
        return String(readStream(inputStream), charset!!)
    }

    /**
     * Read contents of input stream to a byte array, but don't close the stream
     *
     * @param inputStream
     * @return contents of input stream
     * @throws IOException
     */
    @Throws(IOException::class)
    fun readStreamNoClose(inputStream: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        copyStream(inputStream, buffer)
        return buffer.toByteArray()
    }

    fun closeSilently(closeable: Closeable?) {
        if (closeable == null) return
        try {
            closeable.close()
        } catch (e: Exception) {
            Log.w(TAG, String.format("Unable to close %s", closeable.javaClass.canonicalName), e)
        }
    }

    /**
     * Hashes stream content using passed [MessageDigest], closes the stream and returns digest bytes
     *
     * @param inputStream
     * @param messageDigest
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    fun hashStream(inputStream: InputStream?, messageDigest: MessageDigest): ByteArray {
        DigestInputStream(inputStream, messageDigest).use { digestInputStream ->
            val buffer = ByteArray(1024 * 64)
            var read: Int
            while (digestInputStream.read(buffer).also { read = it } > 0) {
                //Do nothing
            }
            return messageDigest.digest()
        }
    }

    @Throws(IOException::class)
    fun hashString(s: String, messageDigest: MessageDigest): ByteArray {
        return hashStream(
            ByteArrayInputStream(s.toByteArray(StandardCharsets.UTF_8)),
            messageDigest
        )
    }

    @Throws(IOException::class)
    fun readFile(file: File?): ByteArray {
        FileInputStream(file).use { `in` -> return readStream(`in`) }
    }
}