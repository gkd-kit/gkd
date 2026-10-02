package li.gkd.app.subscription

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.gkd.app.network.NetworkClients
import li.gkd.app.network.isNetworkAvailable
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.state.MutexState
import li.gkd.app.storage.appStorage
import li.gkd.app.util.LogUtils
import li.gkd.db.Db
import li.gkd.db.LOCAL_HTTP_SUBS_ID
import li.gkd.db.LOCAL_SUBS_ID
import li.gkd.db.SubsItem
import li.songe.json5.decodeFromJson5String

object SubscriptionRepository {
    val files by lazy { FileSubscriptionFiles(appStorage().subscription) }
    private val categoryPolicy by lazy { CategoryPolicy() }

    private val updateMutex = MutexState()

    val snapshotFlow: StateFlow<Loadable<SubscriptionSnapshot>>
        field = MutableStateFlow<Loadable<SubscriptionSnapshot>>(Loadable.Loading)
    val updating get() = updateMutex.state
    val isBusy: Boolean
        get() = updating.value

    suspend fun existingUpdateUrls(): Set<String> =
        Db.subsItemDao.queryAll().mapNotNullTo(mutableSetOf()) { it.updateUrl }

    suspend fun initialize(defaults: SubscriptionDefaults) = withContext(Dispatchers.IO) {
        updateMutex.withStateLock {
            snapshotFlow.value = Loadable.Loading
            try {
                refreshRawSubscriptions(
                    items = Db.subsItemDao.queryAll(),
                    previous = SubscriptionSnapshot(),
                    creationDefaults = defaults,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snapshotFlow.value = Loadable.Failure(e)
                throw e
            }
        }
        ensureLocalSubscription(defaults.localName)
    }

    private suspend fun ensureLocalSubscription(initialName: String) = withContext(Dispatchers.IO) {
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
                if (files.readBytes(LOCAL_SUBS_ID) != null) {
                    Db.subsItemDao.upsert(item)
                    refreshRawSubscriptions(listOf(item))
                } else {
                    saveLocked(
                        subscription = RawSubscription(
                            id = LOCAL_SUBS_ID,
                            name = initialName,
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
            Loadable.Loading -> throw SubscriptionException(SubscriptionFailureReason.SubscriptionNotLoaded)
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
            val previous =
                snapshotFlow.value.value ?: refreshRawSubscriptions(Db.subsItemDao.queryAll())
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

    /** Returns false when enabling requires the user's power warning confirmation. */
    suspend fun requestEnabled(id: Long, enabled: Boolean, confirmed: Boolean = false): Boolean =
        updateMutex.withStateLock {
            val items = Db.subsItemDao.queryAll()
            val item = items.firstOrNull { it.id == id } ?: return@withStateLock true
            if (enabled && !confirmed && SettingsRepository.settings.value.subsPowerWarn && !item.isLocal) {
                val subscriptions = snapshotFlow.value.value?.subscriptions.orEmpty()
                if (items.any { it.id != id && it.enable && !it.isLocal && subscriptions[it.id]?.hasRule != false }) {
                    return@withStateLock false
                }
            }
            Db.subsItemDao.updateEnable(id, enabled)
            true
        }

    suspend fun saveWithItem(
        subscription: RawSubscription,
        defaultItem: SubsItem,
    ) = withContext(Dispatchers.IO) {
        require(subscription.id == defaultItem.id) {
            throw SubscriptionException(SubscriptionFailureReason.SubscriptionItemIdMismatch, listOf(subscription.id.toString(), defaultItem.id.toString()))
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

    // Configuration commands share the subscription lock so an update cannot change
    // category membership between validating the displayed snapshot and writing.
    suspend fun <T> withSubscriptionSnapshot(
        expected: RawSubscription,
        action: suspend (RawSubscription) -> T,
    ): T = withSubscriptionSnapshots(listOf(expected)) { action(expected) }

    suspend fun <T> withSubscriptionSnapshots(
        expected: Collection<RawSubscription>,
        action: suspend () -> T,
    ): T = updateMutex.withStateLock {
        expected.forEach { subscription ->
            val current = requireSnapshot(subscription.id).subscriptions[subscription.id]
                ?: throw SubscriptionException(SubscriptionFailureReason.SubscriptionMissing)
            check(current == subscription) {
                throw SubscriptionException(SubscriptionFailureReason.SubscriptionContentConflict)
            }
        }
        action()
    }

    suspend fun saveCategory(
        expected: RawSubscription,
        categoryKey: Int?,
        name: String,
        description: String,
    ): Boolean = update(expected.id) { current ->
        check(current == expected) {
            throw SubscriptionException(SubscriptionFailureReason.SubscriptionPreviewConflict)
        }
        categoryPolicy.previewEdit(current, categoryKey, name, description)
    }

    suspend fun deleteCategory(expected: RawSubscription, categoryKey: Int): Boolean =
        deleteCategories(expected, setOf(categoryKey))

    suspend fun deleteCategories(expected: RawSubscription, categoryKeys: Set<Int>): Boolean =
        update(expected.id) { current ->
            require(current.isLocal) {
                throw SubscriptionException(SubscriptionFailureReason.RemoteCategoryDeleteUnsupported)
            }
            check(current == expected) {
                throw SubscriptionException(SubscriptionFailureReason.SubscriptionContentConflict)
            }
            current.edit {
                categoryKeys.forEach { categoryKey ->
                    check(removeCategory(categoryKey) != null) {
                        throw SubscriptionException(SubscriptionFailureReason.CategoryMissing)
                    }
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
                ?: throw (snapshot.loadErrors[id] ?: SubscriptionException(SubscriptionFailureReason.SubscriptionMissingId, listOf(id.toString())))
            val next = transform(current)
            require(next.id == id) {
                throw SubscriptionException(SubscriptionFailureReason.SubscriptionIdImmutable, listOf(id.toString(), next.id.toString()))
            }
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
                    snapshotFlow.value = Loadable.Ready(
                        snapshot.copy(
                            subscriptions = snapshot.subscriptions - deletion.ids,
                            loadErrors = snapshot.loadErrors - deletion.ids,
                            updateErrors = snapshot.updateErrors - deletion.ids,
                        )
                    )
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
                NetworkClients.client.get(url).bodyAsText()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                result = failure(
                    reason = SubscriptionResult.FailureReason.Download,
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
                UpdateEntry(item, snapshot.subscriptions[item.id])
            }
            if (entries.any { !it.subsItem.isLocal } && !isNetworkAvailable()) {
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
        snapshotFlow.value = Loadable.Ready(
            snapshot.copy(
                subscriptions = snapshot.subscriptions.toMutableMap().apply {
                    set(id, nextSubscription)
                },
                loadErrors = snapshot.loadErrors.toMutableMap().apply { remove(id) },
                updateErrors = snapshot.updateErrors.toMutableMap().apply { remove(id) },
            )
        )
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
        return files.load(id)
    }

    private fun refreshRawSubscriptions(
        items: List<SubsItem>,
        previous: SubscriptionSnapshot = snapshotFlow.value.value ?: SubscriptionSnapshot(),
        creationDefaults: SubscriptionDefaults? = null,
    ): SubscriptionSnapshot {
        val subscriptions = previous.subscriptions.toMutableMap()
        val errors = previous.loadErrors.toMutableMap()
        items.forEach { item ->
            try {
                // Only initialization may create absent built-in files. Existing bytes, including
                // Android recovery files and user names, always take precedence over defaults.
                val initialName = when (item.id) {
                    LOCAL_SUBS_ID -> creationDefaults?.localName
                    LOCAL_HTTP_SUBS_ID -> creationDefaults?.memoryName
                    else -> null
                }
                if (initialName != null && files.readBytes(item.id) == null) {
                    files.write(RawSubscription(item.id, initialName, 0))
                }
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
        snapshotFlow.value = Loadable.Ready(
            snapshot.copy(
                updateErrors = snapshot.updateErrors.toMutableMap().apply { remove(id) },
            )
        )
    }

    private fun setUpdateError(id: Long, error: Exception) {
        val snapshot = snapshotFlow.value.value ?: return
        snapshotFlow.value = Loadable.Ready(
            snapshot.copy(
                updateErrors = snapshot.updateErrors.toMutableMap().apply { set(id, error) },
            )
        )
    }

    private fun requireSnapshot(id: Long): SubscriptionSnapshot {
        return when (val state = snapshotFlow.value) {
            Loadable.Loading -> throw SubscriptionException(SubscriptionFailureReason.SubscriptionNotLoadedId, listOf(id.toString()))

            is Loadable.Failure -> throw state.cause
            is Loadable.Ready -> state.value
        }
    }

    private suspend fun fetchUpdate(entry: UpdateEntry): RawSubscription? {
        val item = entry.subsItem
        val current = entry.subscription
        val itemUpdateUrl = item.updateUrl ?: return null
        if (item.id < 0) return null
        val checkUrl = current?.checkUpdateUrl?.let { check ->
            val base = current.updateUrl ?: itemUpdateUrl
            runCatching { URI(base).resolve(check).toString() }.getOrNull()
        }
        if (checkUrl != null) {
            try {
                val version = SubscriptionJson.json.decodeFromJson5String<SubsVersion>(
                    NetworkClients.client.get(checkUrl).bodyAsText(),
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
            NetworkClients.client.get(updateUrl).bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SubscriptionException(SubscriptionFailureReason.SubscriptionUpdateUrlRequestFailed, cause = e)
        }
        val subscription = try {
            RawSubscription.parse(text)
        } catch (e: Exception) {
            throw SubscriptionException(SubscriptionFailureReason.TextParseFailed, cause = e)
        }
        if (subscription.id != item.id) {
            throw SubscriptionException(SubscriptionFailureReason.SubscriptionUpdatedIdMismatch, listOf(subscription.id.toString(), item.id.toString()))
        }
        if (current != null && subscription.version <= current.version) {
            LogUtils.d(
                "Subscription version did not advance: ${item.id}",
                "${current.version} -> ${subscription.version}",
            )
            return null
        }
        return subscription
    }
}

private data class UpdateEntry(val subsItem: SubsItem, val subscription: RawSubscription?)

@Serializable
private data class SubsVersion(val id: Long, val version: Int)

private fun <T> List<T>.filterIfNotAll(predicate: (T) -> Boolean): List<T> =
    if (all(predicate)) this else filter(predicate)

private fun <T, K> List<T>.distinctByIfAny(selector: (T) -> K): List<T> =
    distinctBy(selector).let { if (it.size == size) this else it }
