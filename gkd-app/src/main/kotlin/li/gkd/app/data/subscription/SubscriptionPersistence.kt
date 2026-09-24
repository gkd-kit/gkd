package li.gkd.app.data.subscription

import kotlinx.coroutines.CancellationException
import li.gkd.app.data.RawSubscription
import li.gkd.app.util.LogUtils
import li.gkd.db.Db
import li.gkd.db.SubsItem

object SubscriptionPersistence {
    enum class DeleteStage {
        File,
        Database,
    }

    class DeleteException(
        val stage: DeleteStage,
        cause: Throwable,
    ) : Exception(cause.message, cause)

    data class DeleteResult(
        val ids: Set<Long>,
        val count: Int,
    )

    suspend fun save(
        subscription: RawSubscription,
        newItem: SubsItem? = null,
        insertItem: Boolean = false,
    ) {
        val previousBytes = SubscriptionFileStore.readBytes(subscription.id)
        SubscriptionFileStore.write(subscription)
        try {
            Db.withTransaction {
                if (newItem != null) {
                    if (insertItem) {
                        Db.subsItemDao.upsert(newItem)
                    } else {
                        Db.subsItemDao.update(newItem)
                    }
                }
                Db.subsItemDao.updateMtime(subscription.id, System.currentTimeMillis())
                cleanupConfigs(subscription)
            }
        } catch (e: Throwable) {
            restoreFile(subscription.id, previousBytes, e)
            throw e
        }
    }

    suspend fun delete(requestedIds: LongArray): DeleteResult {
        val existingIds = try {
            Db.subsItemDao.queryAll().mapTo(mutableSetOf()) { it.id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw DeleteException(DeleteStage.Database, e)
        }
        val targetIds = requestedIds.filterTo(mutableSetOf()) { it in existingIds }
        if (targetIds.isEmpty()) return DeleteResult(emptySet(), 0)

        val previousFiles = try {
            targetIds.associateWith(SubscriptionFileStore::readBytes)
        } catch (e: Exception) {
            throw DeleteException(DeleteStage.File, e)
        }
        try {
            targetIds.forEach(SubscriptionFileStore::delete)
        } catch (e: Exception) {
            restoreFiles(previousFiles, e)
            throw DeleteException(DeleteStage.File, e)
        }

        val deleteSize = try {
            Db.withTransaction {
                val ids = targetIds.toLongArray()
                val size = Db.subsItemDao.deleteById(*ids)
                if (size > 0) {
                    Db.actionLogDao.deleteBySubsId(*ids)
                }
                size
            }
        } catch (e: CancellationException) {
            restoreFiles(previousFiles, e)
            throw e
        } catch (e: Exception) {
            restoreFiles(previousFiles, e)
            throw DeleteException(DeleteStage.Database, e)
        }
        if (deleteSize == 0) {
            restoreFiles(previousFiles)?.let { error ->
                throw DeleteException(DeleteStage.File, error)
            }
        }
        return DeleteResult(targetIds, deleteSize)
    }

    private suspend fun cleanupConfigs(subscription: RawSubscription): Int {
        val globalKeys = subscription.globalGroups.mapTo(mutableSetOf()) { it.key }
        val appKeys = subscription.apps.associate { app ->
            app.id to app.groups.mapTo(mutableSetOf()) { it.key }
        }
        val appConfigs = Db.subsAppGroupConfigDao.queryBySubsIds(listOf(subscription.id))
        val globalConfigs = Db.subsGlobalGroupConfigDao.queryBySubsIds(listOf(subscription.id))
        val obsoleteApps = appConfigs.filter { appKeys[it.appId]?.contains(it.groupKey) != true }
        val obsoleteGlobals = globalConfigs.filter { it.groupKey !in globalKeys }
        val categoryKeys = subscription.categories.mapTo(mutableSetOf()) { it.key }
        val obsoleteCategories = Db.subsCategoryConfigDao.querySubsItemConfig(listOf(subscription.id))
            .filter { it.categoryKey !in categoryKeys }
        val size = obsoleteApps.size + obsoleteGlobals.size + obsoleteCategories.size
        if (size == 0) return 0
        Db.subsAppGroupConfigDao.delete(*obsoleteApps.toTypedArray())
        Db.subsGlobalGroupConfigDao.delete(*obsoleteGlobals.toTypedArray())
        Db.subsCategoryConfigDao.delete(*obsoleteCategories.toTypedArray())
        LogUtils.d(
            "Clean up removed rule configurations",
            "subsId=${subscription.id}, delete=$size",
        )
        return size
    }

    private fun restoreFile(id: Long, bytes: ByteArray?, cause: Throwable) {
        runCatching { SubscriptionFileStore.restore(id, bytes) }
            .exceptionOrNull()
            ?.let(cause::addSuppressed)
    }

    private fun restoreFiles(
        files: Map<Long, ByteArray?>,
        cause: Throwable? = null,
    ): Throwable? {
        var failure = cause
        files.forEach { (id, bytes) ->
            runCatching { SubscriptionFileStore.restore(id, bytes) }
                .exceptionOrNull()
                ?.let { restoreError ->
                    val currentFailure = failure
                    if (currentFailure == null) {
                        failure = restoreError
                    } else {
                        currentFailure.addSuppressed(restoreError)
                    }
                }
        }
        return failure
    }
}
