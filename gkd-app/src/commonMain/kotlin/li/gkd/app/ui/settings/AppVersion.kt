package li.gkd.app.ui.settings

import li.gkd.app.ui.navigation.AppWindow

data class AppVersion(
    val appName: String,
    val channel: String,
    val versionCode: String,
    val versionName: String,
    val commitLabel: String,
    val commitTime: String,
    val commitUrl: String,
    val isGkdChannel: Boolean,
)

expect fun AppWindow.appVersion(): AppVersion
