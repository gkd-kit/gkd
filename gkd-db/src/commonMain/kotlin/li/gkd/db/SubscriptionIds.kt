package li.gkd.db

object SubscriptionIds {
    const val LOCAL = -2L
    const val LOCAL_HTTP = -1L

    fun isLocal(id: Long): Boolean = id == LOCAL || id == LOCAL_HTTP

    fun isRemote(id: Long): Boolean = id >= 0
}
