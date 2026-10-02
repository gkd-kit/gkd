package li.gkd.app.settings

import li.gkd.app.META
import li.gkd.app.util.DefaultBlockedApps

actual fun defaultSettings() = SettingsStore(
    actionToast = META.appName,
    customNotifTitle = META.appName,
    updateChannel = if (META.isBeta) 1 else 0,
)

actual fun defaultBlockMatchAppList(): Set<String> = DefaultBlockedApps.query()
