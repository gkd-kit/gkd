package li.gkd.app.matching.input

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import com.android.internal.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.app as gkdApp
import li.gkd.app.app
import li.gkd.app.data.isSystem
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.Constants

var imeAppId = ""
val launcherAppIdFlow: StateFlow<String>
    field = MutableStateFlow("")
val launcherAppId: String
    get() = launcherAppIdFlow.value
var systemRecentCn = ComponentName("", "")

fun updateSystemDefaultAppId() {
    imeAppId =
        app.getSecureString(Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString)
            ?.packageName ?: ""
    val launcherCn =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .resolveActivity(app.packageManager)
    launcherAppIdFlow.value = launcherCn.packageName
    if (app.getPkgInfo(launcherAppId)?.applicationInfo?.isSystem == true) {
        systemRecentCn = launcherCn
    } else {
        if (AndroidTarget.P) {
            systemRecentCn =
                ComponentName.unflattenFromString(
                    gkdApp.getString(R.string.config_recentsComponentName),
                ) ?: systemRecentCn
        }
        if (systemRecentCn.packageName.isEmpty()) {
            // https://github.com/android-cs/8/blob/main/packages/SystemUI/src/com/android/systemui/recents/RecentsActivity.java
            systemRecentCn =
                ComponentName(
                    Constants.systemUiAppId,
                    "${Constants.systemUiAppId}.recents.RecentsActivity",
                )
        }
    }
}
