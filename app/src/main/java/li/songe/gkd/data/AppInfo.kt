package li.songe.gkd.data

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import li.songe.gkd.app
import li.songe.gkd.util.Ext.getApplicationInfoExt

data class AppInfo(
    val id: String,
    val name: String? = null,
    val icon: Drawable? = null,
    val installed: Boolean = true,
) {
    val realName = if (name.isNullOrBlank()) id else name
}

private val appInfoCache = mutableMapOf<String, AppInfo>()

fun getAppInfo(id: String): AppInfo {
    appInfoCache[id]?.let { return it }
    val packageManager = app.packageManager
    val info = try { //        需要权限
        val rawInfo = app.packageManager.getApplicationInfoExt(
            id, PackageManager.GET_META_DATA
        )
        AppInfo(
            id = id,
            name = packageManager.getApplicationLabel(rawInfo).toString(),
            icon = packageManager.getApplicationIcon(rawInfo),
        )
    } catch (e: Exception) {
        return AppInfo(id = id, installed = false)
    }
    appInfoCache[id] = info
    return info
}

fun getAppName(id: String?): String? {
    id ?: return null
    return getAppInfo(id).name
}