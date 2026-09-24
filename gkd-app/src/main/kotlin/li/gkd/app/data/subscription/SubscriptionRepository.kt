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
import li.gkd.app.text.UiStrings
import li.gkd.app.core.state.Loadable
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.SubsVersion
import li.gkd.app.data.edit
import li.gkd.app.domain.rule.CategoryPolicy
import li.gkd.app.util.LogUtils
import li.gkd.app.util.MutexState
import li.gkd.app.util.NetworkUtils
import li.gkd.app.util.client
import li.gkd.app.util.distinctByIfAny
import li.gkd.app.util.filterIfNotAll
import li.gkd.app.util.json
import li.gkd.db.Db
import li.gkd.db.LOCAL_SUBS_ID
import li.gkd.db.SubsItem
import li.songe.json5.decodeFromJson5String

object SubscriptionRepository {
    private val updateMutex = MutexState()

    val snapshotFlow: StateFlow<Loadable<SubscriptionSnapshot>>
        field = MutableStateFlow<Loadable<SubscriptionSnapshot>>(Loadable.Loading)
    val updating = updateMutex.state
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
                            name = UiStrings.subscription_local,
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
            Loadable.Loading -> error(UiStrings.subscription_not_loaded)
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
            UiStrings.subscription_item_id_mismatch(subscription.id, defaultItem.id)
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
                ?: error(UiStrings.subscription_missing)
            check(current == subscription) { UiStrings.subscription_content_conflict }
        }
        action()
    }

    suspend fun saveCategory(
        expected: RawSubscription,
        categoryKey: Int?,
        name: String,
        description: String,
    ): Boolean = update(expected.id) { current ->
        check(current == expected) { UiStrings.subscription_preview_conflict }
        CategoryPolicy.previewEdit(current, categoryKey, name, description)
    }

    suspend fun deleteCategory(expected: RawSubscription, categoryKey: Int): Boolean =
        deleteCategories(expected, setOf(categoryKey))

    suspend fun deleteCategories(expected: RawSubscription, categoryKeys: Set<Int>): Boolean =
        update(expected.id) { current ->
            require(current.isLocal) { UiStrings.remote_category_delete_unsupported }
            check(current == expected) { UiStrings.subscription_content_conflict }
            current.edit {
                categoryKeys.forEach { categoryKey ->
                    check(removeCategory(categoryKey) != null) { UiStrings.category_missing }
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
                ?: throw (snapshot.loadErrors[id] ?: IllegalStateException(UiStrings.subscription_missing_id(id)))
            val next = transform(current)
            require(next.id == id) { UiStrings.subscription_id_immutable(id, next.id) }
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
            LogUtils.d("Start checking for updates")
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
                    LogUtils.d("Failed to check for updates", e.message)
                }
            }
            result = SubscriptionResult.Success(
                kind = SubscriptionResult.SuccessKind.Refreshed,
                count = successCount,
            )
            LogUtils.d("End checking for updates")
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
        LogUtils.d("Update subscription file:id=$id,name=${nextSubscription.name}")
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
            Loadable.Loading -> error(UiStrings.subscription_not_loaded_id(id))
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
                LogUtils.d("Quick update check failed", item, e.message)
            }
        }
        val updateUrl = current?.updateUrl ?: itemUpdateUrl
        val text = try {
            client.get(updateUrl).bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw Exception(UiStrings.subscription_update_url_request_failed, e)
        }
        val subscription = try {
            RawSubscription.parse(text)
        } catch (e: Exception) {
            throw Exception(UiStrings.text_parse_failed, e)
        }
        if (subscription.id != item.id) {
            error(UiStrings.subscription_updated_id_mismatch(subscription.id, item.id))
        }
        if (current != null && subscription.version <= current.version) {
            LogUtils.d(
                UiStrings.subscription_version_mismatch(item.id),
                "${current.version} -> ${subscription.version}",
            )
            return null
        }
        return subscription
    }
}
