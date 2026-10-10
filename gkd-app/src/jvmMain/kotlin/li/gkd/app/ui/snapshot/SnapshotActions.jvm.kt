package li.gkd.app.ui.snapshot

import java.io.File
import li.gkd.app.platform.PlatformResult
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.appStorage
import li.gkd.app.ui.platform.UiHost

actual fun UiHost.snapshotPlatformActions(): SnapshotPlatformActions =
    object : SnapshotPlatformActions {
        override suspend fun ensureSavePermission() = true
        override suspend fun share(files: List<File>) = PlatformResult.Unsupported
        override suspend fun save(file: File) =
            PlatformResult.Success(fileActions.saveAs(file))

        override suspend fun pickImage() =
            fileActions.choose(appStorage().sharedCache)?.let(FileSource::Local)
    }
