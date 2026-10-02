package li.gkd.app.snapshot

import li.gkd.app.permission.PermissionStates

internal actual suspend fun canExportDownloads() =
    PermissionStates.writeExternalStorage.updateAndGet()
