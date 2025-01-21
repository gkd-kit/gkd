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
    val userId: Int? = null, // null=0
//    val activities: List<String> = emptyList(),
) {
    // 重写 equals 和 hashCode 便于 compose 重组比较
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        return (other is AppInfo && id == other.id && mtime == other.mtime)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + mtime.hashCode()
        return result
    }
}

val selfAppInfo by lazy {
    app.packageManager.getPackageInfo(app.packageName, 0).toAppInfo()
}

/**
 * 平均单次调用时间 11ms
 */
fun PackageInfo.toAppInfo(
    userId: Int? = null,
    hidden: Boolean? = null,
): AppInfo {
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
        isSystem = applicationInfo?.let { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 } ?: false,
        name = applicationInfo?.run { loadLabel(app.packageManager).toString() } ?: packageName,
        icon = applicationInfo?.loadIcon(app.packageManager),
        userId = userId,
        hidden = hidden ?: (app.packageManager.getLaunchIntentForPackage(packageName) == null),
//        activities = (activities ?: emptyArray()).map {
//            if (
//                it.name.startsWith(packageName) && it.name.getOrNull(packageName.length) == '.'
//            ) {
//                it.name.substring(packageName.length)
//            } else {
//                it.name
//            }
//        },
    )
}
