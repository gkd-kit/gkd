package li.songe.gkd.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInfoHidden
import dev.rikka.tools.refine.Refine
import kotlinx.serialization.Serializable
import li.songe.gkd.app
import li.songe.gkd.shizuku.currentUserId
import li.songe.gkd.util.AndroidTarget

@Serializable
data class AppInfo(
    val id: String,
    val name: String,
    val versionCode: Int,
    val versionName: String?,
    val isSystem: Boolean,
    val mtime: Long,
    val hidden: Boolean,
    val enabled: Boolean,
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

    val visible get() = !(hidden && isSystem)
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

private val PackageInfo.isOverlay: Boolean
    get() = try {
        Refine.unsafeCast<PackageInfoHidden>(this).overlayTarget != null
    } catch (_: Throwable) {
        false
    }

val ApplicationInfo.isSystem: Boolean
    get() = flags and ApplicationInfo.FLAG_SYSTEM != 0

fun PackageInfo.toAppInfo(
    userId: Int = currentUserId,
) = AppInfo(
    userId = userId,
    id = packageName,
    versionCode = compatVersionCode,
    versionName = versionName,
    mtime = lastUpdateTime,
    isSystem = applicationInfo?.isSystem ?: false,
    name = applicationInfo?.run { loadLabel(app.packageManager).toString() } ?: packageName,
    hidden = activities?.isEmpty() != false || isOverlay,
    enabled = applicationInfo?.enabled ?: true,
)
