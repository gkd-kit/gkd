package li.gkd.app.storage

import java.io.File
import kotlinx.coroutines.flow.first
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.diagnostics.MatchDiagnosticFiles
import li.gkd.db.Db
import li.gkd.db.MatchDiagnosticRecord

data class LogArchiveInputs(
    val metadata: Map<String, () -> String>,
    val additionalDirectories: List<File> = emptyList(),
)

expect fun logArchiveInputs(): LogArchiveInputs

/** An empty selection includes all saved diagnostic records. */
suspend fun createLogArchive(diagnostics: List<MatchDiagnosticRecord> = emptyList()): File =
    MatchDiagnosticFiles.withSavedFiles {
        val inputs = logArchiveInputs()
        val records = diagnostics.ifEmpty { Db.matchDiagnosticRecordDao.observeAll().first() }
        LogArchive.build(
            inputs.metadata + ("log-writer.txt" to { LogUtils.status().toString() }),
            inputs.additionalDirectories,
            records.map { MatchDiagnosticFiles.directory(it) },
        )
    }
