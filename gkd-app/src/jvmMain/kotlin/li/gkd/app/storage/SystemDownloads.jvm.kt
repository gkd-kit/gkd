package li.gkd.app.storage

import java.io.File
import li.gkd.app.platform.PlatformResult

/** Desktop uses an explicit save-as target; there is no portable Downloads collection contract. */
actual suspend fun saveToDownloads(source: File): PlatformResult<String> =
    PlatformResult.Unsupported
