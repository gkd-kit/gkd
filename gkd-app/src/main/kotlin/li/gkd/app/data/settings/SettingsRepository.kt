package li.gkd.app.data.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.gkd.app.text.UiStrings
import li.gkd.app.util.AppListString
import li.gkd.app.util.LogUtils
import li.gkd.app.util.json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private class PreparedValueRestore(
    val begin: () -> Unit,
    val finish: (committed: Boolean) -> Unit,
    val awaitPersistence: suspend () -> Unit,
)

private class PersistedValue<T>(
    val filename: String,
    file: File,
    private val decode: (String?) -> T,
    private val encode: (T) -> String,
    scope: CoroutineScope,
) {
    private data class WriteRequest<T>(
        val version: Long,
        val value: T,
    )

    private data class WriteResult(
        val version: Long,
        val error: Throwable?,
    )

    private val mutableState = MutableStateFlow(
        decode(file.takeIf { it.exists() }?.readText())
    )
    val state: StateFlow<T>
        field = mutableState

    private val writeRequests = Channel<WriteRequest<T>>(Channel.CONFLATED)
    private val writeResult = MutableStateFlow(WriteResult(version = 0, error = null))
    private var currentVersion = 0L
    private class RollbackState<T>(var value: T)
    private var rollbackState: RollbackState<T>? = null

    init {
        scope.launch(Dispatchers.IO) {
            for (request in writeRequests) {
                val tempFile = File("${file.absolutePath}.tmp")
                val result = try {
                    tempFile.outputStream().use {
                        it.write(encode(request.value).toByteArray(Charsets.UTF_8))
                        it.fd.sync()
                    }
                    Files.move(
                        tempFile.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                    WriteResult(request.version, null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    WriteResult(request.version, e)
                } finally {
                    tempFile.delete()
                }
                writeResult.value = result
                result.error?.let { error ->
                    runCatching { LogUtils.d("Settings write failed: $filename", error) }
                }
            }
        }
    }

    fun encodeCurrent(): String = encode(state.value)

    fun prepareRestore(text: String): PreparedValueRestore {
        val restored = decode(text)
        var started = false
        return PreparedValueRestore(
            begin = {
                synchronized(this) {
                    check(rollbackState == null) { "Settings restore is already in progress: $filename" }
                    rollbackState = RollbackState(mutableState.value)
                    started = true
                    mutableState.value = restored
                    enqueue(restored)
                }
            },
            finish = { committed ->
                synchronized(this) {
                    if (started) {
                        val rollback = checkNotNull(rollbackState).value
                        rollbackState = null
                        started = false
                        if (!committed) {
                            mutableState.value = rollback
                            enqueue(rollback)
                        }
                    }
                }
            },
            awaitPersistence = ::awaitPersistence,
        )
    }

    fun replace(value: T) {
        update { value }
    }

    @Synchronized
    fun update(transform: (T) -> T) {
        val value = transform(mutableState.value)
        // Keep later commands on both outcomes, without holding a lock across disk I/O.
        // Like MutableStateFlow.update, transforms must be pure and may run more than once.
        rollbackState?.let { it.value = transform(it.value) }
        mutableState.value = value
        enqueue(value)
    }

    suspend fun awaitPersistence() {
        val targetVersion = synchronized(this) { currentVersion }
        val result = writeResult.first { it.version >= targetVersion }
        result.error?.let { throw IOException(UiStrings.settings_write_failed(filename), it) }
    }

    private fun enqueue(value: T) {
        currentVersion += 1
        val request = WriteRequest(currentVersion, value)
        check(writeRequests.trySend(request).isSuccess) { "Settings write queue is closed: $filename" }
    }
}

class SettingsRepository(
    storeFolder: File,
    scope: CoroutineScope,
    defaultSettings: () -> SettingsStore,
    defaultBlockMatchAppList: () -> Set<String>,
) {
    private val restoreMutex = Mutex()

    private val settingsValue = PersistedValue(
        filename = "store.json",
        file = storeFolder.resolve("store.json"),
        decode = { text ->
            text?.let { runCatching { json.decodeFromString<SettingsStore>(it) }.getOrNull() }
                ?: defaultSettings()
        },
        encode = { json.encodeToString(it) },
        scope = scope,
    )
    private val actionCountValue = PersistedValue(
        filename = "action_count.txt",
        file = storeFolder.resolve("action_count.txt"),
        decode = { it?.toLongOrNull() ?: 0L },
        encode = { it.toString() },
        scope = scope,
    )
    private val blockMatchAppListValue = PersistedValue(
        filename = "block_match_app_list.txt",
        file = storeFolder.resolve("block_match_app_list.txt"),
        decode = { it?.let(AppListString::decode) ?: defaultBlockMatchAppList() },
        encode = AppListString::encode,
        scope = scope,
    )
    private val blockA11yAppListValue = PersistedValue(
        filename = "block_a11y_app_list.txt",
        file = storeFolder.resolve("block_a11y_app_list.txt"),
        decode = { it?.let(AppListString::decode) ?: emptySet() },
        encode = AppListString::encode,
        scope = scope,
    )
    private val a11yScopeAppListValue = PersistedValue(
        filename = "a11y_scope_app_list.txt",
        file = storeFolder.resolve("a11y_scope_app_list.txt"),
        decode = { it?.let(AppListString::decode) ?: setOf("com.tencent.mm") },
        encode = AppListString::encode,
        scope = scope,
    )

    val settings: StateFlow<SettingsStore> = settingsValue.state
    val actionCount: StateFlow<Long> = actionCountValue.state
    val blockMatchAppList: StateFlow<Set<String>> = blockMatchAppListValue.state
    val blockA11yAppList: StateFlow<Set<String>> = blockA11yAppListValue.state
    val a11yScopeAppList: StateFlow<Set<String>> = a11yScopeAppListValue.state

    val backupFilenames: Set<String> = setOf(
        settingsValue.filename,
        actionCountValue.filename,
        blockMatchAppListValue.filename,
        blockA11yAppListValue.filename,
        a11yScopeAppListValue.filename,
    )

    /**
     * Updates memory and enqueues persistence.
     * The transform must be pure: a concurrent backup restore may evaluate it twice.
     */
    fun updateSettings(transform: (SettingsStore) -> SettingsStore) =
        settingsValue.update(transform)

    fun incrementActionCount() = actionCountValue.update { it + 1 }

    fun updateBlockMatchAppList(transform: (Set<String>) -> Set<String>) =
        blockMatchAppListValue.update(transform)

    fun replaceBlockMatchAppList(value: Set<String>) = blockMatchAppListValue.replace(value)

    fun updateBlockA11yAppList(transform: (Set<String>) -> Set<String>) =
        blockA11yAppListValue.update(transform)

    fun replaceBlockA11yAppList(value: Set<String>) = blockA11yAppListValue.replace(value)

    fun updateA11yScopeAppList(transform: (Set<String>) -> Set<String>) =
        a11yScopeAppListValue.update(transform)

    fun replaceA11yScopeAppList(value: Set<String>) = a11yScopeAppListValue.replace(value)

    fun exportBackupEntries(): Map<String, String> = mapOf(
        settingsValue.filename to settingsValue.encodeCurrent(),
        actionCountValue.filename to actionCountValue.encodeCurrent(),
        blockMatchAppListValue.filename to blockMatchAppListValue.encodeCurrent(),
        blockA11yAppListValue.filename to blockA11yAppListValue.encodeCurrent(),
        a11yScopeAppListValue.filename to a11yScopeAppListValue.encodeCurrent(),
    )

    /** Persists imported values before [block]; on failure, rolls back while retaining later commands. */
    suspend fun <T> withBackupRestore(entries: Map<String, String>, block: suspend () -> T): T =
        restoreMutex.withLock {
            // Decode everything before changing any value.
            val restores = listOf(
                settingsValue, actionCountValue, blockMatchAppListValue,
                blockA11yAppListValue, a11yScopeAppListValue,
            ).mapNotNull { value ->
                entries[value.filename]?.let(value::prepareRestore)
            }
            try {
                restores.forEach { it.begin() }
                restores.forEach { it.awaitPersistence() }
                val result = block()
                restores.forEach { it.finish(true) }
                result
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    restores.forEach { restore ->
                        runCatching { restore.finish(false) }
                            .exceptionOrNull()?.let(error::addSuppressed)
                    }
                    restores.forEach { restore ->
                        runCatching { restore.awaitPersistence() }
                            .exceptionOrNull()?.let(error::addSuppressed)
                    }
                }
                throw error
            }
        }

}
