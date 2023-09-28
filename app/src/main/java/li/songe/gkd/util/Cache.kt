package li.songe.gkd.util

import com.blankj.utilcode.util.PathUtils
import java.io.File

private val cacheParentDir by lazy {
    PathUtils.getExternalAppCachePath()
}

val snapshotZipDir by lazy {
    File(cacheParentDir.plus("/snapshotZip")).apply {
        if (!exists()) {
            mkdirs()
        }
    }
}
