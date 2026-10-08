package li.gkd.app.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.util.LogUtils
import java.io.File

data class LogArchiveInputs(
    val metadata: Map<String, () -> String>,
    val additionalDirectories: List<File> = emptyList(),
)

expect fun logArchiveInputs(): LogArchiveInputs

suspend fun createLogArchive(): File = withContext(Dispatchers.IO) {
    LogUtils.flush()
    val inputs = logArchiveInputs()
    LogArchive.build(inputs.metadata, inputs.additionalDirectories)
}
