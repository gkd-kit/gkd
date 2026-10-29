package li.gkd.app.storage

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.diagnostics.MatchDiagnosticFiles
import li.gkd.app.matching.diagnostics.MatchDiagnosticRecord

data class LogArchiveInputs(
    val metadata: Map<String, () -> String>,
    val additionalDirectories: List<File> = emptyList(),
)

expect fun logArchiveInputs(): LogArchiveInputs

suspend fun createLogArchive(): File = createLogArchive(emptyList())

suspend fun createLogArchive(diagnostics: List<MatchDiagnosticRecord>): File =
    withContext(Dispatchers.IO) {
        val inputs = logArchiveInputs()
        fun build() =
            LogArchive.build(
                inputs.metadata + ("log-writer.txt" to { LogUtils.status().toString() }),
                inputs.additionalDirectories,
                diagnostics.map { it.file },
            )
        if (diagnostics.isEmpty()) build() else MatchDiagnosticFiles.withSavedFiles { build() }
    }
