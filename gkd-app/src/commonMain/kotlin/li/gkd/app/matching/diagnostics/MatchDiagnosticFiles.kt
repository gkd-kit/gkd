package li.gkd.app.matching.diagnostics

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
import li.gkd.app.storage.appStorage
import li.gkd.app.util.JsonUtils
import li.gkd.db.Db
import li.gkd.db.MatchDiagnosticRecord

class MatchDiagnosticFileStore(private val directory: File) {
    private val deleted = mutableSetOf<Long>()

    fun directory(id: Long): File = directory.resolve(id.toString())

    fun save(capture: MatchDiagnosticCapture): MatchDiagnosticRecord? {
        check(!capture.session.active)
        val session = capture.session
        val captureDirectory = directory(session.startedAt)
        if (session.startedAt in deleted) return null
        check(captureDirectory.isDirectory || captureDirectory.mkdirs())
        val target = captureDirectory.resolve("diagnostic.json")
        val temporary = Files.createTempFile(captureDirectory.toPath(), "capture-", ".tmp")
        try {
            temporary.toFile().writeText(JsonUtils.keepingNulls.encodeToString(capture))
            Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
        return MatchDiagnosticRecord(
            id = session.startedAt,
            startedAt = session.startedAt,
            stoppedAt = checkNotNull(session.stoppedAt),
            durationMillis = checkNotNull(session.durationMillis),
            sizeBytes =
                captureDirectory
                    .walkTopDown()
                    .onFail { _, error -> throw error }
                    .filter { it.isFile }
                    .sumOf { it.length() },
            appIds = session.appIds,
        )
    }

    fun delete(id: Long) {
        val file = directory(id)
        check(file.deleteRecursively()) { "Cannot remove diagnostic: $id" }
        // Physical API completion can still enqueue an updated stopped capture.
        deleted.add(id)
    }
}

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

    fun directory(record: MatchDiagnosticRecord): File = store.directory(record.id)

    suspend fun delete(records: List<MatchDiagnosticRecord>) = withSavedFiles {
        records.forEach { record ->
            store.delete(record.id)
            Db.matchDiagnosticRecordDao.delete(record.id)
        }
    }

    suspend fun flush() = withSavedFiles {}

    suspend fun <T> withSavedFiles(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            writing.withLock {
                while (true) {
                    val capture = synchronized(lock) { pending.firstOrNull() } ?: break
                    try {
                        store.save(capture)?.let { Db.matchDiagnosticRecordDao.upsert(it) }
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
