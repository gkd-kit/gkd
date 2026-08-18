package li.songe.gkd.ui

import li.songe.gkd.data.SubsItem
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.SubscriptionResult
import li.songe.gkd.util.SubscriptionStore

class SubsSheetVm : BaseViewModel() {
    val updating = SubscriptionStore.updating
    val isBusy: Boolean
        get() = SubscriptionStore.isBusy

    suspend fun addOrModifySubscription(
        url: String,
        oldItem: SubsItem,
    ): SubscriptionResult = SubscriptionStore.addOrModifyRemote(url, oldItem)

    suspend fun deleteSubscriptionItem(id: Long): SubscriptionResult =
        SubscriptionStore.delete(id)

    suspend fun refresh(): SubscriptionResult = SubscriptionStore.refresh()
}
