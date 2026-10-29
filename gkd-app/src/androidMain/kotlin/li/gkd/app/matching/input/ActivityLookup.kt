package li.gkd.app.matching.input

import android.content.ComponentName
import android.content.pm.PackageManager
import android.util.LruCache
import li.gkd.app.app
import li.gkd.app.data.appinfo.PackageAppCatalog

private object ActivityCache : LruCache<Pair<String, String>, Boolean>(256) {
    override fun create(key: Pair<String, String>): Boolean =
        try {
            app.packageManager.getActivityInfo(
                ComponentName(key.first, key.second),
                PackageAppCatalog.packageFlags,
            )
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}

fun isActivity(
    appId: String,
    activityId: String,
): Boolean {
    return currentTopActivity.sameAs(appId, activityId) || ActivityCache.get(appId to activityId)
}
