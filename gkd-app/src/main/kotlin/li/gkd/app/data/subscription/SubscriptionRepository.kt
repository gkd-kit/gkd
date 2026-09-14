package li.gkd.app.data.subscription

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import li.gkd.app.data.RawSubscription
import li.gkd.app.core.state.Loadable
import li.gkd.db.SubsItem
import li.gkd.app.data.SubsVersion
import li.gkd.db.Db
import li.gkd.app.util.LogUtils
import li.gkd.app.util.MutexState
import li.gkd.app.util.NetworkUtils
import li.gkd.app.util.client
import li.gkd.app.util.distinctByIfAny
import li.gkd.app.util.filterIfNotAll
import li.gkd.app.util.json
import li.gkd.db.LOCAL_SUBS_ID
import li.songe.json5.decodeFromJson5String

object SubscriptionRepository {
    private val updateMutex = MutexState()

    val snapshotFlow: StateFlow<Loadable<SubscriptionSnapshot>>
        field = MutableStateFlow<Loadable<SubscriptionSnapshot>>(Loadable.Loading)
    val updating = updateMutex.state

    /** 内置默认订阅: 首次启动注入, 幂等(insertOrIgnore 不覆盖已有) */
    private val builtinSubscriptionSeeds = listOf(
        666L to "https://registry.npmmirror.com/@aisouler/gkd_subscription/latest/files/dist/AIsouler_gkd.json5",
        233L to "https://registry.npmmirror.com/@ganlinte/gkd-subscription/latest/files",
        1L to "https://registry.npmmirror.com/gkd-subscription/latest/files",
    )

    private suspend fun seedBuiltinSubscriptions(): Boolean = withContext(Dispatchers.IO) {
        val items = Db.subsItemDao.queryAll()
        val existingIds = items.map { it.id }.toSet()
        val maxOrder = items.maxOfOrNull { it.order } ?: 0
        val newSeeds = builtinSubscriptionSeeds.filter { (id, _) -> id !in existingIds }
        if (newSeeds.isEmpty()) return@withContext false
        Db.subsItemDao.insertOrIgnore(
            *newSeeds.mapIndexed { index, (id, url) ->
                SubsItem(
                    id = id,
                    order = maxOrder + 1 + index,
                    updateUrl = url,
                    enable = true,
                    enableUpdate = true,
                )
            }.toTypedArray()
        )
        LogUtils.d("内置订阅注入: ${newSeeds.map { it.first }}")
        true
    }

    /** 快照审查页生成的规则组: 落盘本地订阅(-2), key 自动分配避免冲突, 返回分配的 key */
    suspend fun saveRuleGroupToLocalStorage(
        appId: String,
        appName: String?,
        group: RawSubscription.RawAppGroup,
    ): Int = withContext(Dispatchers.IO) {
        val current = runCatching { SubscriptionFileStore.load(LOCAL_SUBS_ID) }.getOrDefault(
            RawSubscription(id = LOCAL_SUBS_ID, name = "本地订阅", version = 0),
        )
        val existing = current.apps.find { it.id == appId }
        val nextKey = (existing?.groups?.maxOfOrNull { it.key } ?: 0) + 1
        val finalGroup = group.copy(key = nextKey)
        val newApps = if (existing != null) {
            current.apps.map { if (it.id == appId) it.copy(groups = existing.groups + finalGroup) else it }
        } else {
            current.apps + RawSubscription.RawApp(id = appId, name = appName, groups = listOf(finalGroup))
        }
        saveWithItem(
            subscription = current.copy(apps = newApps),
            defaultItem = SubsItem(id = LOCAL_SUBS_ID, order = -2, enableUpdate = false),
        )
        nextKey
    }
    val isBusy: Boolean
        get() = updating.value

    suspend fun existingUpdateUrls(): Set<String> =
        Db.subsItemDao.queryAll().mapNotNullTo(mutableSetOf()) { it.updateUrl }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        updateMutex.withStateLock {
            snapshotFlow.value = Loadable.Loading
            try {
                refreshRawSubscriptions(
                    items = Db.subsItemDao.queryAll(),
                    previous = SubscriptionSnapshot(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snapshotFlow.value = Loadable.Failure(e)
                throw e
            }
        }
        ensureLocalSubscription()
        if (seedBuiltinSubscriptions()) {
            refresh()
        }
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
                if (SubscriptionFileStore.readBytes(LOCAL_SUBS_ID) != null) {
                    Db.subsItemDao.upsert(item)
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
            } catch (e: CancellationException) {
                throw e
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

    /**
     * Keeps backup file changes and compensation exclusive with subscription updates.
     * The block owns the database transaction and uses SubscriptionPersistence directly.
     */
    suspend fun <T> withBackupTransaction(
        subscriptions: List<RawSubscription>,
        block: suspend (List<RawSubscription>) -> T,
    ): T = withContext(Dispatchers.IO) {
        updateMutex.withStateLock {
            val previous = snapshotFlow.value.value ?: refreshRawSubscriptions(Db.subsItemDao.queryAll())
            val prepared = subscriptions.map { prepareSubscription(it, previous) }
            // Waiting for an in-flight refresh remains cancellable; an accepted restore completes.
            withContext(NonCancellable) {
                try {
                    val result = block(prepared)
                    refreshRawSubscriptions(
                        items = Db.subsItemDao.queryAll(),
                        previous = SubscriptionSnapshot(),
                    )
                    result
                } catch (error: Throwable) {
                    // Compensation can itself fail: publish what is actually readable from disk.
                    runCatching {
                        refreshRawSubscriptions(
                            items = Db.subsItemDao.queryAll(),
                            previous = SubscriptionSnapshot(),
                        )
                    }.exceptionOrNull()?.let(error::addSuppressed)
                    throw error
                }
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setUpdateError(id, e)
                throw e
            }
        }
        changed
    }

    suspend fun delete(vararg subscriptionIds: Long): SubscriptionResult =
        withContext(Dispatchers.IO) {
            if (subscriptionIds.isEmpty()) return@withContext SubscriptionResult.Success()
            var result: SubscriptionResult = SubscriptionResult.Busy
            updateMutex.withStateLock {
                val deletion = try {
                    SubscriptionPersistence.delete(subscriptionIds)
                } catch (e: SubscriptionPersistence.DeleteException) {
                    result = SubscriptionResult.Failure(
                        reason = when (e.stage) {
                            SubscriptionPersistence.DeleteStage.File ->
                                SubscriptionResult.FailureReason.DeleteFile

                            SubscriptionPersistence.DeleteStage.Database ->
                                SubscriptionResult.FailureReason.DeleteData
                        },
                        detail = e.message,
                        cause = e,
                    )
                    return@withStateLock
                }
                if (deletion.count == 0) {
                    result = SubscriptionResult.Success()
                    return@withStateLock
                }
                val snapshot = snapshotFlow.value.value
                if (snapshot != null) {
                    snapshotFlow.value = Loadable.Ready(snapshot.copy(
                        subscriptions = snapshot.subscriptions - deletion.ids,
                        loadErrors = snapshot.loadErrors - deletion.ids,
                        updateErrors = snapshot.updateErrors - deletion.ids,
                    ))
                }
                LogUtils.d("deleteSubscription", deletion.ids)
                result = SubscriptionResult.Success(
                    kind = SubscriptionResult.SuccessKind.Deleted,
                    count = deletion.count,
                )
            }
            result
        }

    suspend fun addOrModifyRemote(
        url: String,
        oldItem: SubsItem? = null,
    ): SubscriptionResult = withContext(Dispatchers.IO) {
        fun failure(
            reason: SubscriptionResult.FailureReason,
            detail: String? = null,
            cause: Exception = IllegalArgumentException(reason.name),
        ): SubscriptionResult.Failure {
            oldItem?.id?.let { setUpdateError(it, cause) }
            return SubscriptionResult.Failure(reason, detail, cause)
        }

        var result: SubscriptionResult = SubscriptionResult.Busy
        val acquired = updateMutex.tryWithStateLock {
            val items = Db.subsItemDao.queryAll()
            if (items.any { it.updateUrl == url && it.id != oldItem?.id }) {
                result = failure(SubscriptionResult.FailureReason.DuplicateUrl)
                return@tryWithStateLock
            }
            val text = try {
                client.get(url).bodyAsText()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                result = failure(
                    reason = SubscriptionResult.FailureReason.Download,
                    detail = e.message,
                    cause = e,
                )
                return@tryWithStateLock
            }
            val subscription = try {
                RawSubscription.parse(text)
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                result = failure(
                    reason = SubscriptionResult.FailureReason.Parse,
                    detail = e.message,
                    cause = e,
                )
                return@tryWithStateLock
            }
            if (oldItem == null && items.any { it.id == subscription.id }) {
                result = failure(SubscriptionResult.FailureReason.AlreadyExists)
                return@tryWithStateLock
            }
            if (oldItem != null && oldItem.id != subscription.id) {
                result = failure(SubscriptionResult.FailureReason.IdMismatch)
                return@tryWithStateLock
            }
            if (subscription.id < 0) {
                result = failure(
                    reason = SubscriptionResult.FailureReason.InvalidId,
                    detail = subscription.id.toString(),
                )
                return@tryWithStateLock
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setUpdateError(oldItem?.id ?: subscription.id, e)
                result = SubscriptionResult.Failure(
                    reason = SubscriptionResult.FailureReason.Save,
                    detail = e.message,
                    cause = e,
                )
                return@tryWithStateLock
            }
            result = SubscriptionResult.Success(
                if (oldItem == null) {
                    SubscriptionResult.SuccessKind.Added
                } else {
                    SubscriptionResult.SuccessKind.Modified
                },
            )
        }
        if (!acquired) return@withContext SubscriptionResult.Busy
        result
    }

    suspend fun refresh(): SubscriptionResult = withContext(Dispatchers.IO) {
        if (snapshotFlow.value is Loadable.Loading) {
            return@withContext SubscriptionResult.Busy
        }
        var result: SubscriptionResult = SubscriptionResult.Busy
        val acquired = updateMutex.tryWithStateLock {
            val items = try {
                Db.subsItemDao.queryAll()
            } catch (e: CancellationException) {
                throw e
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
            val entries = items.map { item ->
                SubsEntry(item, snapshot.subscriptions[item.id])
            }
            if (entries.any { !it.subsItem.isLocal } && !NetworkUtils.isAvailable()) {
                result = SubscriptionResult.Failure(
                    SubscriptionResult.FailureReason.NetworkUnavailable
                )
                return@tryWithStateLock
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    setUpdateError(entry.subsItem.id, e)
                    LogUtils.d("检测更新失败", e.message)
                }
            }
            result = SubscriptionResult.Success(
                kind = SubscriptionResult.SuccessKind.Refreshed,
                count = successCount,
            )
            LogUtils.d("结束检测更新")
        }
        if (!acquired) return@withContext SubscriptionResult.Busy
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
        val nextSubscription = prepareSubscription(subscription, snapshot)
        SubscriptionPersistence.save(nextSubscription, newItem, insertItem)
        snapshotFlow.value = Loadable.Ready(snapshot.copy(
            subscriptions = snapshot.subscriptions.toMutableMap().apply {
                set(id, nextSubscription)
            },
            loadErrors = snapshot.loadErrors.toMutableMap().apply { remove(id) },
            updateErrors = snapshot.updateErrors.toMutableMap().apply { remove(id) },
        ))
        LogUtils.d("更新订阅文件:id=$id,name=${nextSubscription.name}")
    }

    private fun prepareSubscription(
        subscription: RawSubscription,
        snapshot: SubscriptionSnapshot,
    ): RawSubscription = if (
        subscription.id < 0 && snapshot.subscriptions[subscription.id]?.version == subscription.version
    ) {
        subscription.copy(
            version = subscription.version + 1,
            apps = subscription.apps.filterIfNotAll { it.groups.isNotEmpty() }
                .distinctByIfAny { it.id },
        )
    } else {
        subscription
    }

    private fun load(id: Long): RawSubscription {
        return SubscriptionFileStore.load(id)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.d("快速检测更新失败", item, e.message)
            }
        }
        val updateUrl = current?.updateUrl ?: itemUpdateUrl
        val text = try {
            client.get(updateUrl).bodyAsText()
        } catch (e: CancellationException) {
            throw e
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
