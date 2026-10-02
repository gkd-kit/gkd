package li.gkd.app.app

object ActivityNames {
    fun getShowActivityId(appId: String, activityId: String?): String? =
        activityId?.let { if (it.startsWith("$appId.")) it.substring(appId.length) else it }
}
