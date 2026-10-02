package li.gkd.app.subscription

data class SubscriptionSnapshot(
    val subscriptions: Map<Long, RawSubscription> = emptyMap(),
    val loadErrors: Map<Long, Exception> = emptyMap(),
    val updateErrors: Map<Long, Exception> = emptyMap(),
)
