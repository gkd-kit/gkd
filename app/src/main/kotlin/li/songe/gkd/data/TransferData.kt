package li.songe.gkd.data

import kotlinx.serialization.Serializable
import li.songe.gkd.db.DbSet
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

suspend fun exportTransferData(subsItemIds: List<Long>): TransferData {
    return TransferData(
        subsItems = subsItemsFlow.value.filter { subsItemIds.contains(it.id) },
        subscriptions = subsIdToRawFlow.value.values.filter { it.id < 0 && subsItemIds.contains(it.id) },
        subsConfigs = DbSet.subsConfigDao.querySubsItemConfig(subsItemIds),
        categoryConfigs = DbSet.categoryConfigDao.querySubsItemConfig(subsItemIds),
    )
}

suspend fun importTransferData(transferData: TransferData): Boolean {
    // TODO transaction
    val localIds = arrayOf(-1L, -2L)
    val maxOrder = (subsItemsFlow.value.maxOfOrNull { it.order } ?: -1) + 1
    val subsItems = transferData.subsItems.filter { s -> s.id >= 0 || localIds.contains(s.id) }
        .mapIndexed { i, s ->
            s.copy(order = maxOrder + i)
        }
    val hasNewSubsItem =
        subsItems.any { newSubs -> newSubs.id >= 0 && subsItemsFlow.value.all { oldSubs -> oldSubs.id != newSubs.id } }
    DbSet.subsItemDao.insertOrIgnore(*subsItems.toTypedArray())
    DbSet.subsConfigDao.insertOrIgnore(*transferData.subsConfigs.toTypedArray())
    DbSet.categoryConfigDao.insertOrIgnore(*transferData.categoryConfigs.toTypedArray())
    transferData.subscriptions.forEach { subscription ->
        if (localIds.contains(subscription.id)) {
            updateSubscription(subscription)
        }
    }
    return hasNewSubsItem
}
