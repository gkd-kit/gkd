package li.gkd.app.settings

object SubscriptionUpdateInterval {
    const val Paused = -1L
    const val Daily = 86_400_000L
    const val EveryThreeDays = Daily * 3
    const val Weekly = Daily * 7
}
