package li.songe.gkd.data

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.serialization.Serializable
import li.songe.gkd.app
import li.songe.gkd.priv.currentUserId
import li.songe.gkd.priv.toHidden
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.pkgIcon

@Serializable
data class AppInfo(
    val id: String,
    val name: String,
    val versionCode: Int,
    val versionName: String?,
    val isSystem: Boolean,
    val mtime: Long,
    val hidden: Boolean,
    val userId: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is AppInfo) return false
        return id == other.id && mtime == other.mtime && userId == other.userId
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + mtime.hashCode()
        result = 31 * result + userId
        return result
    }
}

val selfAppInfo by lazy {
    app.packageManager.getPackageInfo(app.packageName, 0).toAppInfo()
}

private val PackageInfo.compatVersionCode: Int
    get() = if (AndroidTarget.P) {
        longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        versionCode
    }

val ApplicationInfo.isSystem: Boolean
    get() = flags and ApplicationInfo.FLAG_SYSTEM != 0 || flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0

private fun checkHasActivity(packageName: String): Boolean {
    return app.packageManager.getLaunchIntentForPackage(packageName) != null || app.packageManager.queryIntentActivities(
        Intent().setPackage(packageName),
        PackageManager.MATCH_DISABLED_COMPONENTS
    ).isNotEmpty() || try {
        app.packageManager.getPackageInfo(
            packageName,
            PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_ACTIVITIES
        ).activities?.isNotEmpty() == true
    } catch (_: Throwable) {
        // #1195 packageManager.getPackageInfo android.os.DeadSystemRuntimeException
        true
    }
}

// all->433 isOverlay->354 checkAppHasActivity->271
fun PackageInfo.toAppInfo(
    userId: Int = currentUserId,
    hidden: Boolean? = null,
): AppInfo {
    val isSystem = applicationInfo?.isSystem ?: false
    return AppInfo(
        userId = userId,
        id = packageName,
        versionCode = compatVersionCode,
        versionName = versionName,
        mtime = lastUpdateTime,
        isSystem = isSystem,
        name = applicationInfo?.run { loadLabel(app.packageManager).toString() } ?: packageName,
        hidden = hidden ?: (isSystem && (toHidden.overlayTarget != null || !checkHasActivity(
            packageName
        ))),
    )
}

fun PackageInfo.toAppInfoAndIcon(
    userId: Int = currentUserId,
    hidden: Boolean? = null,
): Pair<AppInfo, Drawable?> {
    val appInfo = toAppInfo(userId, hidden)
    return if (appInfo.hidden) {
        appInfo to null
    } else {
        appInfo to pkgIcon
    }
}
