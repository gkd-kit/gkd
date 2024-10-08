package li.songe.gkd.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import li.songe.gkd.app

@Serializable
data class AppInfo(
    val id: String,
    val name: String,
    @Transient
    val icon: Drawable? = null,
    val versionCode: Long,
    val versionName: String?,
    val isSystem: Boolean,
    val mtime: Long,
    val hidden: Boolean,
)

val selfAppInfo by lazy {
    app.packageManager.getPackageInfo(app.packageName, 0).toAppInfo()
}

/**
 * 平均单次调用时间 11ms
 */
fun PackageInfo.toAppInfo(): AppInfo {
    val info = applicationInfo
    return AppInfo(
        id = packageName,
        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        },
        versionName = versionName,
        mtime = lastUpdateTime,
        hidden = app.packageManager.getLaunchIntentForPackage(packageName) == null,
        name = info?.run { loadLabel(app.packageManager).toString() } ?: packageName,
        icon = info?.loadIcon(app.packageManager),
        isSystem = info?.run { (ApplicationInfo.FLAG_SYSTEM and flags) != 0 } ?: false,
    )
}