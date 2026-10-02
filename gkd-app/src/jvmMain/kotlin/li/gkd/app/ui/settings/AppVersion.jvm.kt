package li.gkd.app.ui.settings

import li.gkd.app.DesktopProfile
import li.gkd.app.ui.navigation.AppWindow

actual fun AppWindow.appVersion() = AppVersion(
    DesktopProfile.appName,
    DesktopProfile.channel,
    DesktopProfile.versionCode,
    DesktopProfile.versionName,
    DesktopProfile.commitLabel,
    DesktopProfile.commitTime,
    DesktopProfile.commitUrl,
    true
)
