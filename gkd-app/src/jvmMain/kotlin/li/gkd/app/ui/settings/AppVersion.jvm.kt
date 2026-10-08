package li.gkd.app.ui.settings

import li.gkd.app.DesktopProfile

actual fun appVersion() = AppVersion(
    DesktopProfile.appName,
    DesktopProfile.channel,
    DesktopProfile.versionCode,
    DesktopProfile.versionName,
    DesktopProfile.commitLabel,
    DesktopProfile.commitTime,
    DesktopProfile.commitUrl,
    isGkdChannel = true,
    updateEnabled = true,
)
