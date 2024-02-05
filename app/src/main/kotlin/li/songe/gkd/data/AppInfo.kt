package li.songe.gkd.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.os.Build
import li.songe.gkd.app

data class AppInfo(
    val id: String,
    val name: String,
    val icon: Drawable?,
    val versionCode: Long,
    val versionName: String?,
    val isSystem: Boolean,
    val mtime: Long,
)

fun PackageInfo.toAppInfo(): AppInfo? {
    applicationInfo ?: return null
    return AppInfo(
        id = packageName,
        name = applicationInfo.loadLabel(app.packageManager).toString(),
        icon = applicationInfo.loadIcon(app.packageManager),
        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        },
        versionName = versionName,
        isSystem = (ApplicationInfo.FLAG_SYSTEM and applicationInfo.flags) != 0,
        mtime = lastUpdateTime
    )
}