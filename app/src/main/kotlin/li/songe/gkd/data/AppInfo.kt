package li.songe.gkd.data

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.serialization.Serializable
import li.songe.gkd.app

@Serializable
data class AppInfo(
    val id: String,
    val name: String,
    val versionCode: Int,
    val versionName: String?,
    val isSystem: Boolean,
    val mtime: Long,
    val hidden: Boolean,
    val userId: Int? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is AppInfo) return false
        return id == other.id && mtime == other.mtime
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + mtime.hashCode()
        return result
    }
}

val selfAppInfo by lazy {
    app.packageManager.getPackageInfo(app.packageName, 0).toAppInfo()
}

val PackageInfo.compatVersionCode: Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        versionCode
    }

fun PackageInfo.toAppInfo(
    userId: Int? = null,
    hidden: Boolean? = null,
): AppInfo {
    return AppInfo(
        id = packageName,
        versionCode = compatVersionCode,
        versionName = versionName,
        mtime = lastUpdateTime,
        isSystem = applicationInfo?.let { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 } ?: false,
        name = applicationInfo?.run { loadLabel(app.packageManager).toString() } ?: packageName,
        userId = userId,
        hidden = hidden ?: app.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).setPackage(packageName)
                .addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_DISABLED_COMPONENTS
        ).isEmpty(),
    )
}
