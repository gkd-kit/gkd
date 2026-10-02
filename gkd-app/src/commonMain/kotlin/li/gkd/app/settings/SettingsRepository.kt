package li.gkd.app.settings

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.app.applicationScope
import li.gkd.app.storage.appStorage
import li.gkd.app.util.LogUtils
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult

private class PreparedValueRestore(
    val begin: () -> Unit,
    val finish: (committed: Boolean) -> Unit,
    val awaitPersistence: suspend () -> Unit,
)

private class PersistedValue<T>(
    val filename: String,
    private val storage: SettingsStorage,
    private val decode: (String?) -> T,
    private val encode: (T) -> String,
    scope: CoroutineScope,
) : SynchronizedObject() {
    private data class WriteRequest<T>(
        val version: Long,
        val value: T,
    )

    private data class WriteResult(
        val version: Long,
        val error: Throwable?,
    )

    private val mutableState = MutableStateFlow(
        decode(storage.read(filename))
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
                val result = try {
                    storage.writeAtomically(filename, encode(request.value))
                    WriteResult(request.version, null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    WriteResult(request.version, e)
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
                    check(rollbackState == null) { "设置恢复已在进行: $filename" }
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

    fun update(transform: (T) -> T) = synchronized(this) {
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
        result.error?.let { throw SettingsWriteException(filename, it) }
    }

    private fun enqueue(value: T) {
        currentVersion += 1
        val request = WriteRequest(currentVersion, value)
        check(writeRequests.trySend(request).isSuccess) { "设置写入队列已关闭: $filename" }
    }
}

enum class ScreenshotConfigResult { Unchanged, Saved, InvalidApp, InvalidSelector }

object SettingsRepository {
    fun saveHttpServerPort(value: String): Boolean {
        val port = value.toIntOrNull()?.takeIf { it in 1000..65535 } ?: return false
        if (settings.value.httpServerPort != port) updateSettings { it.copy(httpServerPort = port) }
        return true
    }

    fun saveCaptureScreenshotConfig(appId: String, eventSelector: String): ScreenshotConfigResult {
        val current = settings.value
        if (appId == current.screenshotTargetAppId && eventSelector == current.screenshotEventSelector) {
            return ScreenshotConfigResult.Unchanged
        }
        if (appId.isNotEmpty() && AppInfoRepository.snapshot?.apps?.containsKey(appId) != true) {
            return ScreenshotConfigResult.InvalidApp
        }
        if (eventSelector.isNotEmpty() && Selector.compile(eventSelector) is SelectorCompileResult.Failure) {
            return ScreenshotConfigResult.InvalidSelector
        }
        updateSettings {
            it.copy(
                screenshotTargetAppId = appId,
                screenshotEventSelector = eventSelector
            )
        }
        return ScreenshotConfigResult.Saved
    }

    suspend fun saveActionToast(enabled: Boolean, useSystemToast: Boolean, text: String): Boolean {
        require(text.isNotEmpty() && text.length <= 64)
        val store = settings.value
        val changed =
            store.toastWhenClick != enabled || store.useSystemToast != useSystemToast || store.actionToast != text
        updateSettings {
            it.copy(toastWhenClick = enabled, useSystemToast = useSystemToast, actionToast = text)
        }
        awaitPersistence()
        return changed
    }

    suspend fun saveNotificationText(enabled: Boolean, title: String, text: String): Boolean {
        val store = settings.value
        val changed =
            store.useCustomNotifText != enabled || store.customNotifTitle != title || store.customNotifText != text
        updateSettings {
            it.copy(
                useCustomNotifText = enabled,
                customNotifTitle = title,
                customNotifText = text,
            )
        }
        awaitPersistence()
        return changed
    }

    fun setBlockA11yAppListEnabled(enabled: Boolean) {
        updateSettings { it.copy(enableBlockA11yAppList = enabled) }
        if (!enabled) {
            li.gkd.app.platform.requestAutomatorRestart()
        }
    }

    private val storage by lazy { FileSettingsStorage(appStorage().store) }
    private val scope get() = applicationScope()

    private val restoreMutex = Mutex()
    private val termsMutex = Mutex()
    val termsAccepted: StateFlow<Boolean>
        field = MutableStateFlow(storage.read("terms_accepted.txt") == "true")

    suspend fun acceptTerms() = termsMutex.withLock {
        if (!termsAccepted.value) {
            withContext(Dispatchers.IO) { storage.writeAtomically("terms_accepted.txt", "true") }
            termsAccepted.value = true
        }
    }

    private val json =
        Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private val settingsValue by lazy {
        PersistedValue(
            filename = "store.json",
            storage = storage,
            decode = { text ->
                text?.let {
                    runCatching {
                        json.decodeFromJsonElement(
                            SettingsStore.serializer(), JsonObject(
                                json.encodeToJsonElement(
                                    SettingsStore.serializer(),
                                    defaultSettings()
                                ).jsonObject.filterKeys { key ->
                                    key in setOf("actionToast", "customNotifTitle", "updateChannel")
                                } + json.parseToJsonElement(it).jsonObject
                            ))
                    }.getOrNull()
                }
                    ?: defaultSettings()
            },
            encode = { json.encodeToString(it) },
            scope = scope,
        )
    }
    private val actionCountValue by lazy {
        PersistedValue(
            filename = "action_count.txt",
            storage = storage,
            decode = { it?.toLongOrNull() ?: 0L },
            encode = { it.toString() },
            scope = scope,
        )
    }
    private val blockMatchAppListValue by lazy {
        PersistedValue(
            filename = "block_match_app_list.txt",
            storage = storage,
            decode = { it?.let(SettingsAppIds::decode) ?: defaultBlockMatchAppList() },
            encode = SettingsAppIds::encode,
            scope = scope,
        )
    }
    private val blockA11yAppListValue by lazy {
        PersistedValue(
            filename = "block_a11y_app_list.txt",
            storage = storage,
            decode = { it?.let(SettingsAppIds::decode) ?: emptySet() },
            encode = SettingsAppIds::encode,
            scope = scope,
        )
    }
    private val a11yScopeAppListValue by lazy {
        PersistedValue(
            filename = "a11y_scope_app_list.txt",
            storage = storage,
            decode = { it?.let(SettingsAppIds::decode) ?: setOf("com.tencent.mm") },
            encode = SettingsAppIds::encode,
            scope = scope,
        )
    }

    private val overlayPositionsValue by lazy {
        PersistedValue(
            filename = "overlay_position.json",
            storage = storage,
            decode = { text ->
                text?.let {
                    runCatching {
                        json.decodeFromString<Map<String, List<Int>>>(
                            it
                        )
                    }.getOrNull()
                } ?: emptyMap()
            },
            encode = { json.encodeToString(it) },
            scope = scope,
        )
    }
    val overlayPositions: StateFlow<Map<String, List<Int>>> get() = overlayPositionsValue.state

    fun updateOverlayPosition(key: String, x: Int, y: Int) =
        overlayPositionsValue.update { it + (key to listOf(x, y)) }

    val actualBlockA11yAppList: Set<String>
        get() = AppScopePolicy.blockedA11y(
            settings.value,
            blockMatchAppList.value,
            blockA11yAppList.value
        )

    val actualA11yScopeAppList: Set<String>
        get() = AppScopePolicy.a11yScope(settings.value, a11yScopeAppList.value)

    fun checkAppBlockMatch(appId: String): Boolean =
        AppScopePolicy.blocksMatch(
            settings.value,
            blockMatchAppList.value,
            blockA11yAppList.value,
            appId
        )

    val settings: StateFlow<SettingsStore> get() = settingsValue.state
    val actionCount: StateFlow<Long> get() = actionCountValue.state
    val blockMatchAppList: StateFlow<Set<String>> get() = blockMatchAppListValue.state
    val blockA11yAppList: StateFlow<Set<String>> get() = blockA11yAppListValue.state
    val a11yScopeAppList: StateFlow<Set<String>> get() = a11yScopeAppListValue.state

    val backupFilenames: Set<String> by lazy {
        setOf(
            settingsValue.filename,
            actionCountValue.filename,
            blockMatchAppListValue.filename,
            blockA11yAppListValue.filename,
            a11yScopeAppListValue.filename,
        )
    }

    /**
     * Updates memory and enqueues persistence.
     * The transform must be pure: a concurrent backup restore may evaluate it twice.
     */
    fun updateSettings(transform: (SettingsStore) -> SettingsStore) =
        settingsValue.update(transform)

    /** Re-selecting the active mode must not stop automation. */
    fun updateAutomatorMode(value: Int) {
        require(AutomatorMode.entries.any { it.value == value })
        updateSettings {
            if (it.automatorMode == value) it else it.copy(
                automatorMode = value,
                enableAutomator = false
            )
        }
    }

    suspend fun awaitPersistence() {
        settingsValue.awaitPersistence()
        actionCountValue.awaitPersistence()
        blockMatchAppListValue.awaitPersistence()
        blockA11yAppListValue.awaitPersistence()
        a11yScopeAppListValue.awaitPersistence()
        overlayPositionsValue.awaitPersistence()
    }

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
