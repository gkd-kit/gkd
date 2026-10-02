package li.gkd.app.ui.settings

import li.gkd.app.META
import li.gkd.app.time.format
import li.gkd.app.ui.navigation.AppWindow

actual fun AppWindow.appVersion() = AppVersion(
    META.appName,
    META.channel,
    META.versionCode.toString(),
    META.versionName,
    META.tagName ?: META.commitId.take(16),
    META.commitTime.format("yyyy-MM-dd HH:mm:ss ZZ"),
    META.commitUrl,
    META.isGkdChannel
)
