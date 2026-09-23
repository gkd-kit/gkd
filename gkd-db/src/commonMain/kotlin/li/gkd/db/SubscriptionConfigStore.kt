package li.gkd.db

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// A single checkpoint for the subscription metadata and all of its user overrides.
data class SubscriptionConfigSnapshot(
    val subsItems: List<SubsItem> = emptyList(),
    val appConfigs: List<SubsAppConfig> = emptyList(),
    val categoryConfigs: List<SubsCategoryConfig> = emptyList(),
    val appGroupConfigs: List<SubsAppGroupConfig> = emptyList(),
    val globalGroupConfigs: List<SubsGlobalGroupConfig> = emptyList(),
)

class SubscriptionConfigStore(private val database: AppDb) {
    suspend fun setAppEnabled(subsId: Long, appId: String, enabled: Boolean?) = database.withWriteTransaction {
        val dao = database.subsAppConfigDao()
        val current = dao.querySubsItemConfig(listOf(subsId)).find { it.appId == appId }
        if (enabled == null) current?.let { dao.delete(it) }
        else if (current?.enable != enabled) dao.upsert(SubsAppConfig(enabled, subsId, appId))
    }

    suspend fun updateAppGroupConfig(
        subsId: Long,
        appId: String,
        groupKey: Int,
        transform: (SubsAppGroupConfig) -> SubsAppGroupConfig,
    ): SubsAppGroupConfig = database.withWriteTransaction {
        val dao = database.subsAppGroupConfigDao()
        val current = dao.getConfig(subsId, appId, groupKey)
        val next = transform(current ?: SubsAppGroupConfig(subsId, appId, groupKey))
        require(next.subsId == subsId && next.appId == appId && next.groupKey == groupKey)
        if (next.enable == null && next.exclude.isEmpty()) {
            if (current != null) dao.delete(current)
        } else if (next != current) dao.upsert(next)
        next
    }

    suspend fun updateGlobalGroupConfig(
        subsId: Long,
        groupKey: Int,
        transform: (SubsGlobalGroupConfig) -> SubsGlobalGroupConfig,
    ): SubsGlobalGroupConfig = database.withWriteTransaction {
        val dao = database.subsGlobalGroupConfigDao()
        val current = dao.getConfig(subsId, groupKey)
        val next = transform(current ?: SubsGlobalGroupConfig(subsId, groupKey))
        require(next.subsId == subsId && next.groupKey == groupKey)
        if (next.enable == null && next.exclude.isEmpty()) {
            if (current != null) dao.delete(current)
        } else if (next != current) dao.upsert(next)
        next
    }

    fun observe(): Flow<SubscriptionConfigSnapshot> = database.invalidationTracker.createFlow(
        "subs_item", "subs_app_config", "subs_category_config", "subs_app_group_config", "subs_global_group_config",
    ).map { capture() }.distinctUntilChanged()

    suspend fun capture(): SubscriptionConfigSnapshot = database.withReadTransaction {
        SubscriptionConfigSnapshot(
            subsItems = database.subsItemDao().queryAll(),
            appConfigs = database.subsAppConfigDao().queryAll(),
            categoryConfigs = database.subsCategoryConfigDao().queryAll(),
            appGroupConfigs = database.subsAppGroupConfigDao().queryAll(),
            globalGroupConfigs = database.subsGlobalGroupConfigDao().queryAll(),
        )
    }

    // Returns the number of orphaned overrides skipped from older backups.
    suspend fun merge(snapshot: SubscriptionConfigSnapshot): Int = database.withWriteTransaction {
        database.subsItemDao().insertOrIgnore(*snapshot.subsItems.toTypedArray())
        val subsIds = database.subsItemDao().queryAll().mapTo(mutableSetOf()) { it.id }
        var skipped = 0
        val appConfigs = snapshot.appConfigs.filter { it.subsId in subsIds }
        skipped += snapshot.appConfigs.size - appConfigs.size
        database.subsAppConfigDao().insertOrIgnore(*appConfigs.toTypedArray())
        val categoryConfigs = snapshot.categoryConfigs.filter { it.subsId in subsIds }
        skipped += snapshot.categoryConfigs.size - categoryConfigs.size
        database.subsCategoryConfigDao().insertOrIgnore(*categoryConfigs.toTypedArray())
        val appGroupConfigs = snapshot.appGroupConfigs.filter { it.subsId in subsIds }
        skipped += snapshot.appGroupConfigs.size - appGroupConfigs.size
        database.subsAppGroupConfigDao().insertOrIgnore(*appGroupConfigs.toTypedArray())
        val globalGroupConfigs = snapshot.globalGroupConfigs.filter { it.subsId in subsIds }
        skipped += snapshot.globalGroupConfigs.size - globalGroupConfigs.size
        database.subsGlobalGroupConfigDao().insertOrIgnore(*globalGroupConfigs.toTypedArray())
        skipped
    }

    suspend fun restore(snapshot: SubscriptionConfigSnapshot) = database.withWriteTransaction {
        val appConfigKeys = snapshot.appConfigs.mapTo(mutableSetOf()) { it.subsId to it.appId }
        val removedAppConfigs = database.subsAppConfigDao().queryAll().filter {
            (it.subsId to it.appId) !in appConfigKeys
        }
        database.subsAppConfigDao().delete(*removedAppConfigs.toTypedArray())
        val categoryConfigKeys = snapshot.categoryConfigs.mapTo(mutableSetOf()) {
            it.subsId to it.categoryKey
        }
        val removedCategoryConfigs = database.subsCategoryConfigDao().queryAll().filter {
            (it.subsId to it.categoryKey) !in categoryConfigKeys
        }
        database.subsCategoryConfigDao().delete(*removedCategoryConfigs.toTypedArray())
        val appGroupConfigKeys = snapshot.appGroupConfigs.mapTo(mutableSetOf()) {
            Triple(it.subsId, it.appId, it.groupKey)
        }
        val removedAppGroupConfigs = database.subsAppGroupConfigDao().queryAll().filter {
            Triple(it.subsId, it.appId, it.groupKey) !in appGroupConfigKeys
        }
        database.subsAppGroupConfigDao().delete(*removedAppGroupConfigs.toTypedArray())
        val globalGroupConfigKeys = snapshot.globalGroupConfigs.mapTo(mutableSetOf()) {
            it.subsId to it.groupKey
        }
        val removedGlobalGroupConfigs = database.subsGlobalGroupConfigDao().queryAll().filter {
            (it.subsId to it.groupKey) !in globalGroupConfigKeys
        }
        database.subsGlobalGroupConfigDao().delete(*removedGlobalGroupConfigs.toTypedArray())
        val subsItemIds = snapshot.subsItems.mapTo(mutableSetOf()) { it.id }
        val removedSubsItems = database.subsItemDao().queryAll().filter {
            it.id !in subsItemIds
        }
        database.subsItemDao().delete(*removedSubsItems.toTypedArray())
        database.subsItemDao().upsert(*snapshot.subsItems.toTypedArray())
        database.subsAppConfigDao().upsert(*snapshot.appConfigs.toTypedArray())
        database.subsCategoryConfigDao().upsert(*snapshot.categoryConfigs.toTypedArray())
        database.subsAppGroupConfigDao().upsert(*snapshot.appGroupConfigs.toTypedArray())
        database.subsGlobalGroupConfigDao().upsert(*snapshot.globalGroupConfigs.toTypedArray())
    }
}
