package li.gkd.app.data

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import li.gkd.app.app
import li.gkd.app.model.AppInfo
import li.gkd.app.priv.currentUserId
import li.gkd.app.priv.toHidden
import li.gkd.app.util.AndroidTarget

private val PackageInfo.compatVersionCode: Int
    get() = if (AndroidTarget.P) {
        longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        versionCode
    }

private fun PackageInfo.checkHasActivity(): Boolean {
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
        hidden = hidden ?: (isSystem && (toHidden.overlayTarget != null || !checkHasActivity())),
    )
}
