package li.songe.gkd.data

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
    val versionCode: Int,
    val versionName: String?,
    val isSystem: Boolean,
    val mtime: Long,
    val hidden: Boolean,
    val userId: Int? = null, // null -> current user
)

val selfAppInfo by lazy {
    app.packageManager.getPackageInfo(
        app.packageName,
        PackageManager.MATCH_UNINSTALLED_PACKAGES
    ).toAppInfo()
}

fun Drawable.safeGet(): Drawable? {
    return if (intrinsicHeight <= 0 || intrinsicWidth <= 0) {
        // https://github.com/gkd-kit/gkd/issues/924
        null
    } else {
        this
    }
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
            longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            versionCode
        },
        versionName = versionName,
        mtime = lastUpdateTime,
        isSystem = applicationInfo?.let { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 } ?: false,
        name = applicationInfo?.run { loadLabel(app.packageManager).toString() } ?: packageName,
        icon = applicationInfo?.loadIcon(app.packageManager)?.safeGet(),
        userId = userId,
        hidden = hidden ?: app.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).setPackage(packageName).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_DISABLED_COMPONENTS
        ).isEmpty(),
    )
}
