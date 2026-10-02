package li.gkd.app.ui.snapshot

import li.gkd.app.platform.PlatformResult
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.appStorage
import li.gkd.app.ui.navigation.AppWindow
import java.io.File

actual fun AppWindow.snapshotPlatformActions(): SnapshotPlatformActions =
    object : SnapshotPlatformActions {
        override suspend fun ensureSavePermission() = true
        override suspend fun share(file: File) = PlatformResult.Unsupported
        override suspend fun save(file: File) =
            PlatformResult.Success(fileActions.saveAs(file))

        override suspend fun pickImage() =
            fileActions.choose(appStorage().sharedCache)?.let(FileSource::Local)
    }
