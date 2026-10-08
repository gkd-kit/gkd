package li.gkd.app.storage

import li.gkd.app.data.LogMetadataSources
import li.gkd.app.util.AndroidStorage

actual fun logArchiveInputs() = LogArchiveInputs(
    LogMetadataSources.sources(),
    listOfNotNull(AndroidStorage.privilegeCrashDirectory),
)
