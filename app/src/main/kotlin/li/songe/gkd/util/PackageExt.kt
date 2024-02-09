package li.songe.gkd.util

import android.content.Intent
import android.content.pm.PackageManager

fun PackageManager.getDefaultLauncherAppId(): String? {
    val intent = Intent(Intent.ACTION_MAIN)
    intent.addCategory(Intent.CATEGORY_HOME)
    val defaultLauncher = this.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return defaultLauncher?.activityInfo?.packageName
}