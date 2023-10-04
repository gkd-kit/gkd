package li.songe.gkd.util

import com.blankj.utilcode.util.PathUtils
import java.io.File

private val cacheParentDir by lazy {
    File(PathUtils.getExternalAppCachePath())
}

val snapshotZipDir by lazy {
    File(cacheParentDir, "snapshotZip").apply {
        if (!exists()) {
            mkdirs()
        }
    }
}
val newVersionApkDir by lazy {
    File(cacheParentDir, "newVersionApk").apply {
        if (!exists()) {
            mkdirs()
        }
    }
}
