package li.songe.gkd.util

import android.net.Uri
import li.songe.gkd.app

object UriUtils {
    fun uri2Bytes(uri: Uri): ByteArray {
        app.contentResolver.openInputStream(uri)?.use {
            return it.readBytes()
        }
        return ByteArray(0)
    }
}