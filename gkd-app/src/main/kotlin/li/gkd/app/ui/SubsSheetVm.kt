package li.gkd.app.ui

import li.gkd.db.SubsItem
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.SubscriptionResult
import li.gkd.app.util.SubscriptionStore

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
