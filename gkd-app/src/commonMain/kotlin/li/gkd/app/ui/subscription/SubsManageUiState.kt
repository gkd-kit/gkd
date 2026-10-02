package li.gkd.app.ui.subscription

import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubsItem

data class SubsManageUiState(
    val subItems: List<SubsItem>,
    val subscriptions: Map<Long, RawSubscription>,
    val loadErrors: Map<Long, Exception>,
    val refreshErrors: Map<Long, Exception>,
)

