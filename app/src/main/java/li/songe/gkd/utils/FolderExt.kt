package li.songe.gkd.utils

import com.blankj.utilcode.util.PathUtils
import java.io.File

object FolderExt {
    private fun createFolder(name: String): File {
        return File(PathUtils.getExternalAppFilesPath().plus("/$name")).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    val dbFolder by lazy { createFolder("db") }
    val subsFolder by lazy { createFolder("subscription") }
    val snapshotFolder by lazy { createFolder("snapshot") }
}