package li.songe.gkd.data

import kotlinx.serialization.Serializable
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.LOCAL_SUBS_IDS
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.updateSubscription

@Serializable
data class TransferData(
    val type: String = TYPE,
    val ctime: Long = System.currentTimeMillis(),

    val subsItems: List<SubsItem> = emptyList(),
    val subscriptions: List<RawSubscription> = emptyList(),
    val subsConfigs: List<SubsConfig> = emptyList(),
    val categoryConfigs: List<CategoryConfig> = emptyList(),
) {
    companion object {
        const val TYPE = "transfer_data"
    }
}

suspend fun exportTransferData(subsItemIds: Collection<Long>): TransferData {
    return TransferData(
        subsItems = subsItemsFlow.value.filter { subsItemIds.contains(it.id) },
        subscriptions = subsIdToRawFlow.value.values.filter { it.id < 0 && subsItemIds.contains(it.id) },
        subsConfigs = DbSet.subsConfigDao.querySubsItemConfig(subsItemIds.toList()),
        categoryConfigs = DbSet.categoryConfigDao.querySubsItemConfig(subsItemIds.toList()),
    )
}

suspend fun importTransferData(transferData: TransferData): Boolean {
    // TODO transaction
    val maxOrder = (subsItemsFlow.value.maxOfOrNull { it.order } ?: -1) + 1
    val subsItems =
        transferData.subsItems.filter { s -> s.id >= 0 || LOCAL_SUBS_IDS.contains(s.id) }
            .mapIndexed { i, s ->
                s.copy(order = maxOrder + i)
            }
    val hasNewSubsItem =
        subsItems.any { newSubs -> newSubs.id >= 0 && subsItemsFlow.value.all { oldSubs -> oldSubs.id != newSubs.id } }
    DbSet.subsItemDao.insertOrIgnore(*subsItems.toTypedArray())
    DbSet.subsConfigDao.insertOrIgnore(*transferData.subsConfigs.toTypedArray())
    DbSet.categoryConfigDao.insertOrIgnore(*transferData.categoryConfigs.toTypedArray())
    transferData.subscriptions.forEach { subscription ->
        if (LOCAL_SUBS_IDS.contains(subscription.id)) {
            updateSubscription(subscription)
        }
    }
    return hasNewSubsItem
}
