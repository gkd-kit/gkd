package li.gkd.app.util

import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import li.gkd.app.META
import li.gkd.app.app

object DefaultBlockedApps {
    fun query(): Set<String> {
        val set = hashSetOf(META.appId, Constants.systemUiAppId)
        listOf(
            Intent.ACTION_MAIN to Intent.CATEGORY_HOME,
            Intent.ACTION_MAIN to Intent.CATEGORY_APP_GALLERY,
            Intent.ACTION_MAIN to Intent.CATEGORY_APP_CONTACTS,
            Intent.ACTION_MAIN to Intent.CATEGORY_APP_CALENDAR,
            Intent.ACTION_MAIN to Intent.CATEGORY_APP_MESSAGING,
            Intent.ACTION_MAIN to Intent.CATEGORY_APP_CALCULATOR,
            Intent.ACTION_OPEN_DOCUMENT to Intent.CATEGORY_OPENABLE,
            AlarmClock.ACTION_SHOW_ALARMS to null,
            MediaStore.ACTION_IMAGE_CAPTURE to null,
            Settings.ACTION_SETTINGS to null,
        ).forEach {
            app.resolveAppId(it.first, it.second)?.let(set::add)
        }
        return set
    }
}

