package li.gkd.app.matching.diagnostics

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import li.gkd.app.app.applicationScope
import li.gkd.app.logging.LogUtils
import li.gkd.app.storage.ExportFileNames
import li.gkd.app.storage.appStorage
import li.gkd.app.util.JsonUtils

class MatchDiagnosticFileStore(private val directory: File) {
    private val deleted = mutableSetOf<String>()

    fun records(): List<MatchDiagnosticRecord> =
        directory
            .listFiles()
            .orEmpty()
            .mapNotNull { file ->
                val name = file.name
                if (file.isDirectory && file.resolve("diagnostic.json").isFile) {
                    val split = name.indexOf("Z-")
                    if (split < 0) return@mapNotNull null
                    val startedAt =
                        java.time.LocalDateTime.parse(
                                name.substring(0, split),
                                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
                            )
                            .toInstant(ZoneOffset.UTC)
                            .toEpochMilli()
                    MatchDiagnosticRecord(
                        file,
                        name.substring(split + 2),
                        startedAt,
                        file
                            .walkTopDown()
                            .onFail { _, error -> throw error }
                            .filter { it.isFile }
                            .sumOf { it.length() },
                    )
                } else null
            }
            .sortedByDescending { it.startedAt }

    fun save(capture: MatchDiagnosticCapture) {
        check(!capture.session.active)
        val session = capture.session
        val captureDirectory =
            directory.resolve(
                "${ExportFileNames.timestamp(session.startedAt, ZoneOffset.UTC)}Z-${session.id.take(8)}",
            )
        if (captureDirectory.name in deleted) return
        check(captureDirectory.isDirectory || captureDirectory.mkdirs())
        val target = captureDirectory.resolve("diagnostic.json")
        val temporary = Files.createTempFile(captureDirectory.toPath(), "capture-", ".tmp")
        try {
            temporary.toFile().writeText(JsonUtils.keepingNulls.encodeToString(capture))
            Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun delete(records: List<MatchDiagnosticRecord>) {
        records.forEach { record ->
            require(record.file.parentFile == directory)
            check(record.file.deleteRecursively()) {
                "Cannot remove diagnostic: ${record.file.name}"
            }
            // Physical API completion can still enqueue an updated stopped capture.
            deleted.add(record.file.name)
        }
    }
}

data class MatchDiagnosticRecord(
    val file: File,
    val id: String,
    val startedAt: Long,
    val sizeBytes: Long,
)

data class MatchDiagnosticSaveState(
    val id: String,
    val saved: Boolean = false,
    val error: String? = null,
)

/** Stopped immutable snapshots survive page disposal; failed writes are retried by log sharing. */
object MatchDiagnosticFiles {
    private val lock = Any()
    private val pending = ArrayDeque<MatchDiagnosticCapture>()
    private val writing = Mutex()
    private val store by lazy { MatchDiagnosticFileStore(appStorage().matchingDiagnostics) }
    val changes: StateFlow<Long>
        field = MutableStateFlow(0L)
    val state: StateFlow<Map<String, MatchDiagnosticSaveState>>
        field = MutableStateFlow(emptyMap())

    private fun updateState(value: MatchDiagnosticSaveState) {
        val ordered = (state.value - value.id) + (value.id to value)
        val retained = ordered.keys.toList().takeLast(5).toSet() + pending.map { it.session.id }
        state.value = ordered.filterKeys { it in retained }
    }

    fun enqueue(capture: MatchDiagnosticCapture) {
        synchronized(lock) {
            pending.addLast(capture)
            updateState(MatchDiagnosticSaveState(capture.session.id))
        }
        applicationScope().launch(Dispatchers.IO) {
            try {
                flush()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                LogUtils.e("Saving matching diagnostic failed", error)
            }
        }
    }

    suspend fun records(): List<MatchDiagnosticRecord> =
        withContext(Dispatchers.IO) {
            writing.withLock { store.records() }
        }

    suspend fun delete(records: List<MatchDiagnosticRecord>) = withSavedFiles {
        try {
            store.delete(records)
        } finally {
            changes.value++
        }
    }

    suspend fun flush() = withSavedFiles {}

    suspend fun <T> withSavedFiles(block: () -> T): T =
        withContext(Dispatchers.IO) {
            writing.withLock {
                while (true) {
                    val capture = synchronized(lock) { pending.firstOrNull() } ?: break
                    try {
                        store.save(capture)
                        changes.value++
                    } catch (error: Exception) {
                        synchronized(lock) {
                            updateState(
                                MatchDiagnosticSaveState(
                                    capture.session.id,
                                    error = error.toString(),
                                ),
                            )
                        }
                        throw error
                    }
                    synchronized(lock) {
                        pending.removeFirst()
                        updateState(
                            MatchDiagnosticSaveState(
                                capture.session.id,
                                saved = pending.none { it.session.id == capture.session.id },
                            ),
                        )
                    }
                }
                block()
            }
        }
}
