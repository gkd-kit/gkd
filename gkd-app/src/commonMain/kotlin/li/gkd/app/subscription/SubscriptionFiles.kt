package li.gkd.app.subscription

interface SubscriptionFiles {
    fun load(id: Long): RawSubscription
    fun readBytes(id: Long): ByteArray?
    fun write(subscription: RawSubscription)
    fun restore(id: Long, bytes: ByteArray?)
    fun delete(id: Long)
}
