package li.gkd.app.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.logging.LogUtils
import java.io.File

data class LogArchiveInputs(
    val metadata: Map<String, () -> String>,
    val additionalDirectories: List<File> = emptyList(),
)

expect fun logArchiveInputs(): LogArchiveInputs

suspend fun createLogArchive(): File = createLogArchive(emptyList())

suspend fun createLogArchive(diagnostics: List<li.gkd.app.matching.diagnostics.MatchDiagnosticRecord>): File = withContext(Dispatchers.IO) {
    val inputs = logArchiveInputs()
    fun build() = LogArchive.build(
        inputs.metadata + ("log-writer.txt" to { LogUtils.status().toString() }),
        inputs.additionalDirectories, diagnostics.map { it.file },
    )
    if (diagnostics.isEmpty()) build()
    else li.gkd.app.matching.diagnostics.MatchDiagnosticFiles.withSavedFiles { build() }
}
