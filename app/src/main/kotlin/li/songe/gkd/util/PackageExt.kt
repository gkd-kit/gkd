package li.songe.gkd.util

import android.content.Intent
import android.content.pm.PackageManager
import li.songe.gkd.service.TopActivity

fun PackageManager.getDefaultLauncherActivity(): TopActivity {
    val intent = Intent(Intent.ACTION_MAIN)
    intent.addCategory(Intent.CATEGORY_HOME)
    val info =
        this.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?: return TopActivity("")
    val appId = info.packageName ?: ""
    val name = info.name ?: ""
    val activityId = if (name.startsWith('.')) appId + name else name
    return TopActivity(
        appId = appId,
        activityId = activityId
    )
}