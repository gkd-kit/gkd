package li.gkd.app.snapshot

import li.gkd.app.permission.PermissionStates

actual suspend fun canExportDownloads() =
    PermissionStates.writeExternalStorage.updateAndGet()
