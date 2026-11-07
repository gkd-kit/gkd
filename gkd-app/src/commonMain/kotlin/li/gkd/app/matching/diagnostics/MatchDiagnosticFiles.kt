package li.gkd.app.matching.diagnostics

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
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
            val prepared = prepareDiagnosticCapture(capture)
            val sections =
                linkedMapOf(
                    "accessibilitySettings" to "accessibility-settings.jsonl",
                    "events" to "events.jsonl",
                    "keyEvents" to "key-events.jsonl",
                    "boundaryEvents" to "boundary-events.jsonl",
                    "queryRecordings" to "query-recordings.jsonl",
                )
            captureDirectory
                .resolve(sections.getValue("accessibilitySettings"))
                .writeDiagnosticRecords(prepared.accessibilitySettings)
            captureDirectory
                .resolve(sections.getValue("events"))
                .writeDiagnosticRecords(prepared.events)
            captureDirectory
                .resolve(sections.getValue("keyEvents"))
                .writeDiagnosticRecords(prepared.keyEvents)
            captureDirectory
                .resolve(sections.getValue("boundaryEvents"))
                .writeDiagnosticRecords(prepared.boundaryEvents)
            captureDirectory
                .resolve(sections.getValue("queryRecordings"))
                .writeDiagnosticRecords(prepared.queryRecordings)
            captureDirectory
                .resolve("system-recording.json")
                .writeText(JsonUtils.keepingNulls.encodeToString(prepared.systemRecording))
            val encoded =
                JsonUtils.keepingNulls.encodeToJsonElement(prepared.metadataOnly()).jsonObject
            val metadata =
                JsonObject(
                    encoded.filterKeys { it !in sections && it != "systemRecording" } +
                        ("files" to
                            JsonObject(
                                sections.mapValues { JsonPrimitive(it.value) } +
                                    ("systemRecording" to JsonPrimitive("system-recording.json")),
                            )),
                )
            temporary.toFile().writeText(metadata.toString())
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
    private val pending = linkedMapOf<String, MatchDiagnosticCapture>()
    private val writing = Mutex()
    private val saveRequests = Channel<Unit>(Channel.CONFLATED)
    private val writer by lazy {
        applicationScope().launch(Dispatchers.IO) {
            for (request in saveRequests) {
                try {
                    flush()
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    LogUtils.e("Saving matching diagnostic failed", error)
                }
            }
        }
    }
    private val store by lazy { MatchDiagnosticFileStore(appStorage().matchingDiagnostics) }
    val state: StateFlow<Map<String, MatchDiagnosticSaveState>>
        field = MutableStateFlow(emptyMap())

    private fun updateState(value: MatchDiagnosticSaveState) {
        val ordered = (state.value - value.id) + (value.id to value)
        val retained = ordered.keys.toList().takeLast(5).toSet() + pending.keys
        state.value = ordered.filterKeys { it in retained }
    }

    fun enqueue(capture: MatchDiagnosticCapture) {
        synchronized(lock) {
            pending[capture.session.id] = capture
            updateState(MatchDiagnosticSaveState(capture.session.id))
        }
        writer.start()
        saveRequests.trySend(Unit)
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
                    val capture = synchronized(lock) { pending.values.firstOrNull() } ?: break
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
                        // A late completion may replace this session while its file is written.
                        if (pending[capture.session.id] === capture)
                            pending.remove(capture.session.id)
                        updateState(
                            MatchDiagnosticSaveState(
                                capture.session.id,
                                saved = capture.session.id !in pending,
                            ),
                        )
                    }
                }
                block()
            }
        }
}

private inline fun <reified T> File.writeDiagnosticRecords(records: List<T>) {
    bufferedWriter().use { writer ->
        for (record in records) {
            writer.write(JsonUtils.keepingNulls.encodeToString(record))
            writer.write("\n")
        }
    }
}
