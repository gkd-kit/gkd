package li.gkd.app.util

import android.util.AtomicFile
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import li.gkd.app.data.RawSubscription
import li.gkd.db.SubsConfig
import li.gkd.db.SubsItem
import li.gkd.app.data.SubsVersion
import li.gkd.db.Db
import li.gkd.app.ui.share.Loadable
import li.gkd.db.LOCAL_HTTP_SUBS_ID
import li.gkd.db.LOCAL_SUBS_ID
import li.songe.json5.decodeFromJson5String
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class LoadedSubscription(
    val value: RawSubscription,
    val updateError: Exception?,
)

data class SubscriptionSnapshot(
    val subscriptions: Map<Long, RawSubscription> = emptyMap(),
    val loadErrors: Map<Long, Exception> = emptyMap(),
    val updateErrors: Map<Long, Exception> = emptyMap(),
)

sealed interface SubscriptionResult {
    val message: String?

    data object Busy : SubscriptionResult {
        override val message = "正在处理订阅，请稍后重试"
    }

    data class Success(override val message: String?) : SubscriptionResult

    data class Failure(
        override val message: String,
        val cause: Throwable? = null,
    ) : SubscriptionResult
}

object SubscriptionStore {
    private val updateMutex = MutexState()

    val snapshotFlow: StateFlow<Loadable<SubscriptionSnapshot>>
        field = MutableStateFlow<Loadable<SubscriptionSnapshot>>(Loadable.Loading)
    val updating = updateMutex.state
    val isBusy: Boolean
        get() = updateMutex.mutex.isLocked

    suspend fun initialize() = withContext(Dispatchers.IO) {
        updateMutex.withStateLock {
            snapshotFlow.value = Loadable.Loading
            try {
                refreshRawSubscriptions(
                    items = Db.subsItemDao.queryAll(),
                    previous = SubscriptionSnapshot(),
                )
            } catch (e: Exception) {
                snapshotFlow.value = Loadable.Failure(e)
                throw e
            }
        }
        ensureLocalSubscription()
    }

    private suspend fun ensureLocalSubscription() = withContext(Dispatchers.IO) {
        updateMutex.withStateLock {
            try {
                val items = Db.subsItemDao.queryAll()
                if (snapshotFlow.value !is Loadable.Ready) {
                    refreshRawSubscriptions(
                        items = items,
                        previous = SubscriptionSnapshot(),
                    )
                }
                if (items.any { it.id == LOCAL_SUBS_ID }) return@withStateLock
                val item = SubsItem(
                    id = LOCAL_SUBS_ID,
                    order = items.minByOrNull { it.order }?.order ?: 0,
                )
                val file = subsFolder.resolve("$LOCAL_SUBS_ID.json")
                if (file.exists()) {
                    Db.subsItemDao.insert(item)
                    refreshRawSubscriptions(listOf(item))
                } else {
                    saveLocked(
                        subscription = RawSubscription(
                            id = LOCAL_SUBS_ID,
                            name = "本地订阅",
                            version = 0,
                        ),
                        newItem = item,
                        insertItem = true,
                    )
                }
            } catch (e: Exception) {
                if (snapshotFlow.value !is Loadable.Ready) {
                    snapshotFlow.value = Loadable.Failure(e)
                }
                throw e
            }
        }
    }

    suspend fun awaitSnapshot(): SubscriptionSnapshot {
        return when (val state = snapshotFlow.first { it !is Loadable.Loading }) {
            Loadable.Loading -> error("订阅尚未加载")
            is Loadable.Failure -> throw state.cause
            is Loadable.Ready -> state.value
        }
    }

    suspend fun awaitSubscription(id: Long): RawSubscription {
        val snapshot = awaitSnapshot()
        return snapshot.subscriptions[id]
            ?: throw (snapshot.loadErrors[id] ?: IllegalStateException("订阅不存在: $id"))
    }

    suspend fun save(subscription: RawSubscription) = withContext(Dispatchers.IO) {
        updateMutex.withStateLock {
            try {
                saveLocked(subscription)
            } catch (e: Exception) {
                setUpdateError(subscription.id, e)
                throw e
            }
        }
    }

    suspend fun saveWithItem(
        subscription: RawSubscription,
        defaultItem: SubsItem,
    ) = withContext(Dispatchers.IO) {
        require(subscription.id == defaultItem.id) {
            "订阅与订阅项id不一致: ${subscription.id} != ${defaultItem.id}"
        }
        updateMutex.withStateLock {
            val currentItem = Db.subsItemDao.queryAll().find { it.id == subscription.id }
            try {
                saveLocked(
                    subscription = subscription,
                    newItem = currentItem ?: defaultItem,
                    insertItem = currentItem == null,
                )
            } catch (e: Exception) {
                setUpdateError(subscription.id, e)
                throw e
            }
        }
    }

    suspend fun update(
        id: Long,
        transform: (RawSubscription) -> RawSubscription,
    ): Boolean = withContext(Dispatchers.IO) {
        var changed = false
        updateMutex.withStateLock {
            val snapshot = requireSnapshot(id)
            val current = snapshot.subscriptions[id]
                ?: throw (snapshot.loadErrors[id] ?: IllegalStateException("订阅不存在: $id"))
            val next = transform(current)
            require(next.id == id) { "订阅id不可修改: $id -> ${next.id}" }
            if (next == current) return@withStateLock
            try {
                saveLocked(next)
                changed = true
            } catch (e: Exception) {
                setUpdateError(id, e)
                throw e
            }
        }
        changed
    }

    suspend fun delete(vararg subscriptionIds: Long): SubscriptionResult =
        withContext(Dispatchers.IO) {
            if (subscriptionIds.isEmpty()) return@withContext SubscriptionResult.Success(null)
            var result: SubscriptionResult = SubscriptionResult.Busy
            updateMutex.withStateLock {
                val deleteSize = try {
                    Db.withTransaction {
                        val size = Db.subsItemDao.deleteById(*subscriptionIds)
                        if (size > 0) {
                            Db.subsConfigDao.deleteBySubsId(*subscriptionIds)
                            Db.actionLogDao.deleteBySubsId(*subscriptionIds)
                            Db.categoryConfigDao.deleteBySubsId(*subscriptionIds)
                        }
                        size
                    }
                } catch (e: Exception) {
                    result = SubscriptionResult.Failure(
                        "删除订阅数据失败\n${e.message}".trimEnd(),
                        e,
                    )
                    return@withStateLock
                }
                if (deleteSize == 0) {
                    result = SubscriptionResult.Success(null)
                    return@withStateLock
                }
                val snapshot = snapshotFlow.value.value
                if (snapshot != null) {
                    snapshotFlow.value = Loadable.Ready(snapshot.copy(
                        subscriptions = snapshot.subscriptions - subscriptionIds.toSet(),
                        loadErrors = snapshot.loadErrors - subscriptionIds.toSet(),
                        updateErrors = snapshot.updateErrors - subscriptionIds.toSet(),
                    ))
                }
                var fileError: Throwable? = null
                subscriptionIds.forEach { id ->
                    if (fileError != null) return@forEach
                    fileError = runCatching {
                        val file = subsFolder.resolve("$id.json")
                        AtomicFile(file).delete()
                        if (file.exists()) throw IOException("无法删除 ${file.name}")
                    }.exceptionOrNull()
                }
                LogUtils.d("deleteSubscription", subscriptionIds)
                val error = fileError
                result = if (error == null) {
                    SubscriptionResult.Success("删除成功")
                } else {
                    SubscriptionResult.Failure(
                        "订阅数据已删除，但文件清理失败\n${error.message}".trimEnd(),
                        error,
                    )
                }
            }
            result
        }

    suspend fun addOrModifyRemote(
        url: String,
        oldItem: SubsItem? = null,
    ): SubscriptionResult = withContext(Dispatchers.IO) {
        fun failure(
            message: String,
            cause: Exception = IllegalArgumentException(message),
        ): SubscriptionResult.Failure {
            oldItem?.id?.let { setUpdateError(it, cause) }
            return SubscriptionResult.Failure(message, cause)
        }

        if (updateMutex.mutex.isLocked) return@withContext SubscriptionResult.Busy
        var result: SubscriptionResult = SubscriptionResult.Busy
        updateMutex.withStateLock {
            val items = Db.subsItemDao.queryAll()
            if (items.any { it.updateUrl == url && it.id != oldItem?.id }) {
                result = failure("已有相同链接订阅")
                return@withStateLock
            }
            val text = try {
                client.get(url).bodyAsText()
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                result = failure(
                    "下载订阅文件失败\n${e.message}".trimEnd(),
                    e,
                )
                return@withStateLock
            }
            val subscription = try {
                RawSubscription.parse(text)
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                result = failure(
                    "解析订阅文件失败\n${e.message}".trimEnd(),
                    e,
                )
                return@withStateLock
            }
            if (oldItem == null && items.any { it.id == subscription.id }) {
                result = failure("订阅已存在")
                return@withStateLock
            }
            if (oldItem != null && oldItem.id != subscription.id) {
                result = failure("订阅id不对应")
                return@withStateLock
            }
            if (subscription.id < 0) {
                result = failure(
                    "订阅id不可为${subscription.id}\n负数id为内部使用",
                )
                return@withStateLock
            }
            val newItem = oldItem?.copy(updateUrl = url) ?: SubsItem(
                id = subscription.id,
                updateUrl = url,
                order = if (items.isEmpty()) 1 else items.maxOf { it.order } + 1,
            )
            try {
                saveLocked(
                    subscription = subscription,
                    newItem = newItem,
                    insertItem = oldItem == null,
                )
            } catch (e: Exception) {
                setUpdateError(oldItem?.id ?: subscription.id, e)
                result = SubscriptionResult.Failure(
                    "保存订阅文件失败\n${e.message}".trimEnd(),
                    e,
                )
                return@withStateLock
            }
            result = SubscriptionResult.Success(
                if (oldItem == null) "成功添加订阅" else "成功修改订阅",
            )
        }
        result
    }

    suspend fun refresh(): SubscriptionResult = withContext(Dispatchers.IO) {
        if (snapshotFlow.value is Loadable.Loading) {
            return@withContext SubscriptionResult.Busy
        }
        if (updateMutex.mutex.isLocked) return@withContext SubscriptionResult.Busy
        var result: SubscriptionResult = SubscriptionResult.Busy
        updateMutex.withStateLock {
            val items = try {
                Db.subsItemDao.queryAll()
            } catch (e: Exception) {
                if (snapshotFlow.value !is Loadable.Ready) {
                    snapshotFlow.value = Loadable.Failure(e)
                }
                throw e
            }
            val currentSnapshot = snapshotFlow.value.value
            val missingItems = if (currentSnapshot == null) {
                items
            } else {
                items.filter { item -> item.id !in currentSnapshot.subscriptions }
            }
            val snapshot = refreshRawSubscriptions(
                items = missingItems,
                previous = currentSnapshot ?: SubscriptionSnapshot(),
            )
            val entries = buildSubsEntries(items, snapshot.subscriptions)
            if (entries.any { !it.subsItem.isLocal } && !NetworkUtils.isAvailable()) {
                result = SubscriptionResult.Failure("网络不可用")
                return@withStateLock
            }
            LogUtils.d("开始检测更新")
            var successCount = 0
            entries.filter { !it.subsItem.isLocal }.forEach { entry ->
                try {
                    val subscription = fetchUpdate(entry)
                    if (subscription != null) {
                        saveLocked(subscription)
                        successCount++
                    } else {
                        clearUpdateError(entry.subsItem.id)
                    }
                } catch (e: Exception) {
                    setUpdateError(entry.subsItem.id, e)
                    LogUtils.d("检测更新失败", e.message)
                }
            }
            result = SubscriptionResult.Success(
                if (successCount > 0) "更新 $successCount 条订阅" else "暂无更新",
            )
            LogUtils.d("结束检测更新")
        }
        result
    }

    private suspend fun saveLocked(
        subscription: RawSubscription,
        newItem: SubsItem? = null,
        insertItem: Boolean = false,
    ) {
        val id = subscription.id
        val snapshot = snapshotFlow.value.value
            ?: refreshRawSubscriptions(
                items = Db.subsItemDao.queryAll(),
                previous = SubscriptionSnapshot(),
            )
        val nextSubscription = if (
            id < 0 && snapshot.subscriptions[id]?.version == subscription.version
        ) {
            subscription.copy(
                version = subscription.version + 1,
                apps = subscription.apps.filterIfNotAll { it.groups.isNotEmpty() }
                    .distinctByIfAny { it.id },
            )
        } else {
            subscription
        }
        val file = subsFolder.resolve("$id.json")
        val previousBytes = file.takeIf { it.exists() }?.readBytes()
        writeAtomic(file, json.encodeToString(nextSubscription).encodeToByteArray())
        try {
            Db.withTransaction {
                if (newItem != null) {
                    if (insertItem) {
                        Db.subsItemDao.insert(newItem)
                    } else {
                        Db.subsItemDao.update(newItem)
                    }
                }
                Db.subsItemDao.updateMtime(id, System.currentTimeMillis())
                cleanupConfigs(id, nextSubscription)
            }
        } catch (e: Exception) {
            runCatching {
                if (previousBytes == null) {
                    AtomicFile(file).delete()
                } else {
                    writeAtomic(file, previousBytes)
                }
            }.exceptionOrNull()?.let(e::addSuppressed)
            throw e
        }
        snapshotFlow.value = Loadable.Ready(snapshot.copy(
            subscriptions = snapshot.subscriptions.toMutableMap().apply {
                set(id, nextSubscription)
            },
            loadErrors = snapshot.loadErrors.toMutableMap().apply { remove(id) },
            updateErrors = snapshot.updateErrors.toMutableMap().apply { remove(id) },
        ))
        LogUtils.d("更新订阅文件:id=$id,name=${nextSubscription.name}")
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        val atomicFile = AtomicFile(file)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (e: Exception) {
            atomicFile.failWrite(output)
            throw e
        }
    }

    private fun load(id: Long): RawSubscription {
        val file = subsFolder.resolve("$id.json")
        if (!file.exists()) {
            return when (id) {
                LOCAL_SUBS_ID -> RawSubscription(id = id, name = "本地订阅", version = 0)
                LOCAL_HTTP_SUBS_ID -> RawSubscription(id = id, name = "内存订阅", version = 0)
                else -> error("订阅文件不存在")
            }
        }
        val subscription = try {
            RawSubscription.parse(file.readText(), json5 = false)
        } catch (e: Exception) {
            throw Exception("订阅文件解析失败", e)
        }
        if (subscription.id != id) error("订阅文件id不一致")
        return subscription
    }

    private fun refreshRawSubscriptions(
        items: List<SubsItem>,
        previous: SubscriptionSnapshot = snapshotFlow.value.value ?: SubscriptionSnapshot(),
    ): SubscriptionSnapshot {
        val subscriptions = previous.subscriptions.toMutableMap()
        val errors = previous.loadErrors.toMutableMap()
        items.forEach { item ->
            try {
                subscriptions[item.id] = load(item.id)
                errors.remove(item.id)
            } catch (e: Exception) {
                errors[item.id] = e
            }
        }
        val nextSnapshot = previous.copy(
            subscriptions = subscriptions,
            loadErrors = errors,
        )
        snapshotFlow.value = Loadable.Ready(nextSnapshot)
        return nextSnapshot
    }

    private fun clearUpdateError(id: Long) {
        val snapshot = snapshotFlow.value.value ?: return
        if (id !in snapshot.updateErrors) return
        snapshotFlow.value = Loadable.Ready(snapshot.copy(
            updateErrors = snapshot.updateErrors.toMutableMap().apply { remove(id) },
        ))
    }

    private fun setUpdateError(id: Long, error: Exception) {
        val snapshot = snapshotFlow.value.value ?: return
        snapshotFlow.value = Loadable.Ready(snapshot.copy(
            updateErrors = snapshot.updateErrors.toMutableMap().apply { set(id, error) },
        ))
    }

    private fun requireSnapshot(id: Long): SubscriptionSnapshot {
        return when (val state = snapshotFlow.value) {
            Loadable.Loading -> error("订阅尚未加载: $id")
            is Loadable.Failure -> throw state.cause
            is Loadable.Ready -> state.value
        }
    }

    private suspend fun cleanupConfigs(id: Long, subscription: RawSubscription): Int {
        val globalKeys = subscription.globalGroups.map { it.key }.toHashSet()
        val appKeys = subscription.apps.associate { app ->
            app.id to app.groups.map { it.key }.toHashSet()
        }
        val configs = Db.subsConfigDao.querySubsItemConfig(listOf(id))
        val obsolete = configs.filter { config ->
            when (config.type) {
                SubsConfig.AppGroupType -> appKeys[config.appId]?.contains(config.groupKey) != true
                SubsConfig.GlobalGroupType -> config.groupKey !in globalKeys
                else -> false
            }
        }
        if (obsolete.isEmpty()) return 0
        Db.subsConfigDao.delete(*obsolete.toTypedArray())
        LogUtils.d("清理已移除规则配置", "subsId=$id, delete=${obsolete.size}")
        return obsolete.size
    }

    private suspend fun fetchUpdate(entry: SubsEntry): RawSubscription? {
        val item = entry.subsItem
        val current = entry.subscription
        val itemUpdateUrl = item.updateUrl ?: return null
        if (item.id < 0) return null
        val checkUrl = entry.checkUpdateUrl
        if (checkUrl != null && current != null) {
            try {
                val version = json.decodeFromJson5String<SubsVersion>(
                    client.get(checkUrl).bodyAsText(),
                )
                if (version.id == current.id && version.version <= current.version) return null
            } catch (e: Exception) {
                LogUtils.d("快速检测更新失败", item, e.message)
            }
        }
        val updateUrl = current?.updateUrl ?: itemUpdateUrl
        val text = try {
            client.get(updateUrl).bodyAsText()
        } catch (e: Exception) {
            throw Exception("请求更新链接失败", e)
        }
        val subscription = try {
            RawSubscription.parse(text)
        } catch (e: Exception) {
            throw Exception("解析文本失败", e)
        }
        if (subscription.id != item.id) {
            error("新id=${subscription.id}不匹配旧id=${item.id}")
        }
        if (current != null && subscription.version <= current.version) {
            LogUtils.d(
                "版本号不满足条件:id=${item.id}",
                "${current.version} -> ${subscription.version}",
            )
            return null
        }
        return subscription
    }
}
