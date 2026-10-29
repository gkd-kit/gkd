package li.gkd.app.settings

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
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
import li.gkd.app.app.applicationScope
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.MatchInvalidationReason
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.model.isValidAppId
import li.gkd.app.platform.requestAutomatorRestart
import li.gkd.app.storage.appStorage
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult

private class PreparedValueRestore(
    val begin: suspend () -> Unit,
    val finish: suspend (committed: Boolean) -> Unit,
    val awaitPersistence: suspend () -> Unit,
)

private class PersistedValue<T>(
    val filename: String,
    private val storage: SettingsStorage,
    private val decode: (String?) -> T,
    private val encode: (T) -> String,
    private val scope: CoroutineScope,
    private val decodeBackup: (String) -> T = decode,
) : SynchronizedObject() {
    private data class WriteRequest<T>(
        val version: Long,
        val value: T,
    )

    private data class WriteResult(
        val version: Long,
        val error: Throwable?,
    )

    private val mutableState =
        MutableStateFlow(
            storage.readValue(filename, decode),
        )
    val state: StateFlow<T>
        field = mutableState

    private val writeRequests = Channel<WriteRequest<T>>(Channel.CONFLATED)
    private val writeResult = MutableStateFlow(WriteResult(version = 0, error = null))
    private var currentVersion = 0L
    private val saveMutex = Mutex()

    private class RollbackState<T>(var value: T)

    private var rollbackState: RollbackState<T>? = null

    init {
        scope.launch(Dispatchers.IO) {
            for (request in writeRequests) {
                val result =
                    try {
                        storage.writeAtomically(filename, encode(request.value))
                        WriteResult(request.version, null)
                    } catch (e: Exception) {
                        WriteResult(request.version, e)
                    }
                writeResult.value = result
                result.error?.let { error ->
                    LogUtils.e("Settings write failed: $filename", error)
                }
            }
        }
    }

    fun encodeCurrent(): String = encode(state.value)

    fun prepareRestore(text: String): PreparedValueRestore {
        val restored = decodeBackup(text)
        var started = false
        return PreparedValueRestore(
            begin = {
                saveMutex.withLock {
                    synchronized(this) {
                        check(rollbackState == null) { "设置恢复已在进行: $filename" }
                        rollbackState = RollbackState(mutableState.value)
                        started = true
                        mutableState.value = restored
                        enqueue(restored)
                    }
                }
            },
            finish = { committed ->
                saveMutex.withLock {
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
                }
            },
            awaitPersistence = ::awaitPersistence,
        )
    }

    // Enter the per-file queue in call order; the application owns the accepted save.
    suspend fun save(transform: (T) -> T) =
        scope
            .async(start = CoroutineStart.UNDISPATCHED) {
                saveMutex.withLock {
                    val value = transform(mutableState.value)
                    synchronized(this@PersistedValue) { enqueue(value) }
                    awaitPersistence()
                    synchronized(this@PersistedValue) {
                        rollbackState?.let { it.value = transform(it.value) }
                        mutableState.value = value
                    }
                }
            }
            .await()

    fun update(transform: (T) -> T) =
        synchronized(this) {
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

enum class ScreenshotConfigResult {
    Unchanged,
    Saved,
    InvalidApp,
    InvalidSelector,
}

object SettingsRepository {
    fun saveHttpServerPort(value: String): Boolean {
        val port = value.toIntOrNull()?.takeIf { it in 1000..65535 } ?: return false
        if (settings.value.httpServerPort != port) updateSettings { it.copy(httpServerPort = port) }
        return true
    }

    fun saveCaptureScreenshotConfig(appId: String, eventSelector: String): ScreenshotConfigResult {
        val current = settings.value
        if (
            appId == current.screenshotTargetAppId &&
                eventSelector == current.screenshotEventSelector
        ) {
            return ScreenshotConfigResult.Unchanged
        }
        if (appId.isNotEmpty() && !appId.isValidAppId()) {
            return ScreenshotConfigResult.InvalidApp
        }
        if (
            eventSelector.isNotEmpty() &&
                Selector.compile(eventSelector) is SelectorCompileResult.Failure
        ) {
            return ScreenshotConfigResult.InvalidSelector
        }
        updateSettings {
            it.copy(
                screenshotTargetAppId = appId,
                screenshotEventSelector = eventSelector,
            )
        }
        return ScreenshotConfigResult.Saved
    }

    suspend fun saveActionToast(enabled: Boolean, useSystemToast: Boolean, text: String): Boolean {
        require(text.isNotEmpty() && text.length <= 64)
        val store = settings.value
        val changed =
            store.toastWhenClick != enabled ||
                store.useSystemToast != useSystemToast ||
                store.actionToast != text
        updateSettings {
            it.copy(toastWhenClick = enabled, useSystemToast = useSystemToast, actionToast = text)
        }
        settingsValue.awaitPersistence()
        return changed
    }

    suspend fun saveNotificationText(enabled: Boolean, title: String, text: String): Boolean {
        val store = settings.value
        val changed =
            store.useCustomNotifText != enabled ||
                store.customNotifTitle != title ||
                store.customNotifText != text
        updateSettings {
            it.copy(
                useCustomNotifText = enabled,
                customNotifTitle = title,
                customNotifText = text,
            )
        }
        settingsValue.awaitPersistence()
        return changed
    }

    fun setBlockA11yAppListEnabled(enabled: Boolean) {
        updateSettings { it.copy(enableBlockA11yAppList = enabled) }
        if (!enabled) {
            requestAutomatorRestart()
        }
    }

    private val storage by lazy { FileSettingsStorage(appStorage().store) }
    private val scope
        get() = applicationScope()

    private val restoreMutex = Mutex()
    private val termsMutex = Mutex()
    val termsAccepted: StateFlow<Boolean>
        field = createTermsAcceptedFlow(storage)

    suspend fun acceptTerms() = termsMutex.withLock {
        if (!termsAccepted.value) {
            withContext(Dispatchers.IO) { storage.writeAtomically("terms_accepted.txt", "true") }
            termsAccepted.value = true
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private fun decodeSettings(text: String): SettingsStore =
        json.decodeFromJsonElement(
            SettingsStore.serializer(),
            JsonObject(
                json
                    .encodeToJsonElement(SettingsStore.serializer(), defaultSettings())
                    .jsonObject
                    .filterKeys { key ->
                        key in setOf("actionToast", "customNotifTitle", "updateChannel")
                    } + json.parseToJsonElement(text).jsonObject,
            ),
        )

    private val settingsValue by lazy {
        PersistedValue(
            filename = "store.json",
            storage = storage,
            decode = { text ->
                text?.let(::decodeSettings) ?: defaultSettings()
            },
            encode = { json.encodeToString(it) },
            scope = scope,
            decodeBackup = ::decodeSettings,
        )
    }
    private val actionCountValue by lazy {
        PersistedValue(
            filename = "action_count.txt",
            storage = storage,
            decode = { it?.toLongOrNull() ?: 0L },
            encode = { it.toString() },
            scope = scope,
            decodeBackup = String::toLong,
        )
    }
    private val blockMatchAppListValue by lazy {
        PersistedValue(
            filename = "block_match_app_list.txt",
            storage = storage,
            decode = { it?.let(SettingsAppIds::decode) ?: defaultBlockMatchAppList() },
            encode = SettingsAppIds::encode,
            scope = scope,
            decodeBackup = SettingsAppIds::decodeBackup,
        )
    }
    private val blockA11yAppListValue by lazy {
        PersistedValue(
            filename = "block_a11y_app_list.txt",
            storage = storage,
            decode = { it?.let(SettingsAppIds::decode) ?: emptySet() },
            encode = SettingsAppIds::encode,
            scope = scope,
            decodeBackup = SettingsAppIds::decodeBackup,
        )
    }
    private val a11yScopeAppListValue by lazy {
        PersistedValue(
            filename = "a11y_scope_app_list.txt",
            storage = storage,
            decode = { it?.let(SettingsAppIds::decode) ?: setOf("com.tencent.mm") },
            encode = SettingsAppIds::encode,
            scope = scope,
            decodeBackup = SettingsAppIds::decodeBackup,
        )
    }

    private val automationScopeAppListValue by lazy {
        PersistedValue(
            filename = "automation_scope_app_list.txt",
            storage = storage,
            decode = { it?.let(SettingsAppIds::decode) ?: emptySet() },
            encode = SettingsAppIds::encode,
            scope = scope,
            decodeBackup = SettingsAppIds::decodeBackup,
        )
    }

    private val overlayPositionsValue by lazy {
        PersistedValue(
            filename = "overlay_position.json",
            storage = storage,
            decode = { text ->
                text?.let { json.decodeFromString<Map<String, List<Int>>>(it) } ?: emptyMap()
            },
            encode = { json.encodeToString(it) },
            scope = scope,
        )
    }
    val overlayPositions: StateFlow<Map<String, List<Int>>>
        get() = overlayPositionsValue.state

    fun updateOverlayPosition(key: String, x: Int, y: Int) = overlayPositionsValue.update {
        it + (key to listOf(x, y))
    }

    val actualBlockA11yAppList: Set<String>
        get() =
            AppScopePolicy.blockedA11y(
                settings.value,
                blockMatchAppList.value,
                blockA11yAppList.value,
            )

    fun checkAppBlockMatch(appId: String): Boolean =
        AppScopePolicy.blocksMatch(
            settings.value,
            blockMatchAppList.value,
            blockA11yAppList.value,
            appId,
        )

    val settings: StateFlow<SettingsStore>
        get() = settingsValue.state

    val actionCount: StateFlow<Long>
        get() = actionCountValue.state

    val blockMatchAppList: StateFlow<Set<String>>
        get() = blockMatchAppListValue.state

    val blockA11yAppList: StateFlow<Set<String>>
        get() = blockA11yAppListValue.state

    val a11yScopeAppList: StateFlow<Set<String>>
        get() = a11yScopeAppListValue.state

    val automationScopeAppList: StateFlow<Set<String>>
        get() = automationScopeAppListValue.state

    fun appUsesA11y(appId: String): Boolean =
        AppScopePolicy.usesA11y(
            settings.value,
            a11yScopeAppList.value,
            automationScopeAppList.value,
            appId,
        )

    val hasServiceScope: Boolean
        get() =
            if (settings.value.useA11y) automationScopeAppList.value.isNotEmpty()
            else a11yScopeAppList.value.isNotEmpty()

    val backupFilenames: Set<String> by lazy {
        setOf(
            settingsValue.filename,
            actionCountValue.filename,
            blockMatchAppListValue.filename,
            blockA11yAppListValue.filename,
            a11yScopeAppListValue.filename,
            automationScopeAppListValue.filename,
        )
    }

    /**
     * Updates memory and enqueues persistence. The transform must be pure: a concurrent backup
     * restore may evaluate it twice.
     */
    fun updateSettings(transform: (SettingsStore) -> SettingsStore) {
        val previous = settings.value
        settingsValue.update(transform)
        val current = settings.value
        if (
            (previous.enableMatch && !current.enableMatch) ||
                (previous.enableAutomator && !current.enableAutomator)
        ) {
            MatchingRuntime.coordinator.invalidate(MatchInvalidationReason.AutomationDisabled)
        }
    }

    /** Re-selecting the active mode must not stop automation. */
    fun updateAutomatorMode(value: Int) {
        require(AutomatorMode.entries.any { it.value == value })
        updateSettings {
            if (it.automatorMode == value) it
            else
                it.copy(
                    automatorMode = value,
                    enableAutomator = false,
                )
        }
    }

    suspend fun awaitPersistence() {
        settingsValue.awaitPersistence()
        actionCountValue.awaitPersistence()
        blockMatchAppListValue.awaitPersistence()
        blockA11yAppListValue.awaitPersistence()
        a11yScopeAppListValue.awaitPersistence()
        automationScopeAppListValue.awaitPersistence()
        overlayPositionsValue.awaitPersistence()
    }

    fun incrementActionCount() = actionCountValue.update { it + 1 }

    suspend fun updateBlockMatchAppList(transform: (Set<String>) -> Set<String>) =
        blockMatchAppListValue.save(transform)

    suspend fun saveBlockMatchAppList(value: Set<String>) = blockMatchAppListValue.save { value }

    suspend fun updateBlockA11yAppList(transform: (Set<String>) -> Set<String>) =
        blockA11yAppListValue.save(transform)

    suspend fun saveBlockA11yAppList(value: Set<String>) = blockA11yAppListValue.save { value }

    suspend fun updateA11yScopeAppList(transform: (Set<String>) -> Set<String>) =
        a11yScopeAppListValue.save(transform)

    suspend fun saveA11yScopeAppList(value: Set<String>) = a11yScopeAppListValue.save { value }

    suspend fun updateAutomationScopeAppList(transform: (Set<String>) -> Set<String>) =
        automationScopeAppListValue.save(transform)

    suspend fun saveAutomationScopeAppList(value: Set<String>) = automationScopeAppListValue.save {
        value
    }

    fun exportBackupEntries(): Map<String, String> =
        mapOf(
            settingsValue.filename to settingsValue.encodeCurrent(),
            actionCountValue.filename to actionCountValue.encodeCurrent(),
            blockMatchAppListValue.filename to blockMatchAppListValue.encodeCurrent(),
            blockA11yAppListValue.filename to blockA11yAppListValue.encodeCurrent(),
            a11yScopeAppListValue.filename to a11yScopeAppListValue.encodeCurrent(),
            automationScopeAppListValue.filename to automationScopeAppListValue.encodeCurrent(),
        )

    /**
     * Persists imported values before [block]; on failure, rolls back while retaining later
     * commands.
     */
    suspend fun <T> withBackupRestore(entries: Map<String, String>, block: suspend () -> T): T =
        restoreMutex.withLock {
            // Decode everything before changing any value.
            val restores =
                listOf(
                        settingsValue,
                        actionCountValue,
                        blockMatchAppListValue,
                        blockA11yAppListValue,
                        a11yScopeAppListValue,
                        automationScopeAppListValue,
                    )
                    .mapNotNull { value ->
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
                            .exceptionOrNull()
                            ?.let(error::addSuppressed)
                    }
                    restores.forEach { restore ->
                        runCatching { restore.awaitPersistence() }
                            .exceptionOrNull()
                            ?.let(error::addSuppressed)
                    }
                }
                throw error
            }
        }
}
