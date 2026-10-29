package li.gkd.app.util

import java.io.File
import li.gkd.app.META
import li.gkd.app.app

object ApkUtils {
    fun createShareFile(): File =
        AndroidStorage.storage.sharedCache.resolve("gkd-v${META.versionName}.apk").apply {
            File(app.packageCodePath).copyTo(this, overwrite = true)
        }
}
