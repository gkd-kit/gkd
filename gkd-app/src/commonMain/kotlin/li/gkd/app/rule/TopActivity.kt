package li.gkd.app.rule

import li.gkd.app.app.ActivityNames

data class TopActivity(
    val appId: String = "",
    val activityId: String? = null,
    val number: Int = 0,
) {
    val shortActivityId: String? get() = ActivityNames.getShowActivityId(appId, activityId)
    fun format(): String = "$appId/$shortActivityId/$number"
    fun sameAs(appId: String, activityId: String?): Boolean =
        this.appId == appId && this.activityId == activityId
}
