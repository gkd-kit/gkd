package li.gkd.app.util

import li.gkd.app.app
import li.gkd.app.storage.AppStorageLayout
import java.io.File

object AndroidStorage {
    private val filesDir: File by lazy {
        val markFile = app.filesDir.resolve(".gkd")
        if (markFile.isFile) {
            app.filesDir
        } else {
            // fix #1333
            app.getExternalFilesDir(null) ?: app.filesDir.also {
                markFile.createNewFile()
            }
        }
    }

    val storage by lazy { AppStorageLayout(filesDir, cacheDir, app.filesDir) }

    private val cacheDir by lazy { app.externalCacheDir ?: app.cacheDir }

}
