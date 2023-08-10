package li.songe.gkd.util

import com.blankj.utilcode.util.LogUtils
import li.songe.gkd.app
import java.io.File

object FolderExt {
    private fun createFolder(name: String): File {
        return File(
            app.getExternalFilesDir(name)?.absolutePath
                ?: app.filesDir.absolutePath.plus(name)
        ).apply {
            if (!exists()) {
                mkdirs()
                LogUtils.d("mkdirs", absolutePath)
            }
        }
    }

    val dbFolder by lazy { createFolder("db") }
    val subsFolder by lazy { createFolder("subscription") }
    val snapshotFolder by lazy { createFolder("snapshot") }
}