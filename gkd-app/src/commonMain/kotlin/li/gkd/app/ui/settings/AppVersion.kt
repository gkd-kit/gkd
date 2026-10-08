package li.gkd.app.ui.settings

data class AppVersion(
    val appName: String,
    val channel: String,
    val versionCode: String,
    val versionName: String,
    val commitLabel: String,
    val commitTime: String,
    val commitUrl: String,
    val isGkdChannel: Boolean,
    val updateEnabled: Boolean,
)

expect fun appVersion(): AppVersion
