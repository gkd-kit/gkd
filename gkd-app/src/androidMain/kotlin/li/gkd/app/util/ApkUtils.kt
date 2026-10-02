package li.gkd.app.util

import li.gkd.app.META
import li.gkd.app.app
import java.io.File

object ApkUtils {
    fun createShareFile(): File =
        AndroidStorage.storage.sharedCache.resolve("gkd-v${META.versionName}.apk").apply {
            File(app.packageCodePath).copyTo(this, overwrite = true)
        }
}
