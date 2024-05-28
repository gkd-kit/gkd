package li.songe.gkd.util

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

fun readFileZipByteArray(zipByteArray: ByteArray, fileName: String): String? {
    val byteArrayInputStream = ByteArrayInputStream(zipByteArray)
    val zipInputStream = ZipInputStream(byteArrayInputStream)
    zipInputStream.use {
        var zipEntry: ZipEntry? = zipInputStream.nextEntry
        while (zipEntry != null) {
            if (zipEntry.name == fileName) {
                val reader = BufferedReader(InputStreamReader(zipInputStream))
                val content = reader.use { it.readText() }
                return content
            }
            zipEntry = zipInputStream.nextEntry
        }
    }
    return null
}