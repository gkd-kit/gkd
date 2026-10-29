package li.gkd.app.subscription

import li.gkd.db.SubscriptionConfigSnapshot

@kotlinx.serialization.Serializable
data class SubscriptionBackupSnapshot(
    val configuration: SubscriptionConfigSnapshot,
    val subscriptions: List<RawSubscription>,
)

data class SubscriptionSnapshot(
    val subscriptions: Map<Long, RawSubscription> = emptyMap(),
    val loadErrors: Map<Long, Exception> = emptyMap(),
    val updateErrors: Map<Long, Exception> = emptyMap(),
)
