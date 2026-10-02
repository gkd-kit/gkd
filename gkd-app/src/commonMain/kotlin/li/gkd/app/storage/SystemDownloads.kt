package li.gkd.app.storage

import li.gkd.app.platform.PlatformResult

import java.io.File

/** Saves to the system Downloads collection, not a save-as dialog. Permission is obtained by the caller. */
expect suspend fun saveToDownloads(source: File): PlatformResult<String>
