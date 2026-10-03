package li.gkd.app.rule

import li.gkd.app.app.ActivityNames
import li.gkd.app.util.Constants

data class TopActivity(
    val appId: String = Constants.systemUiAppId,
    val activityId: String? = null,
    val number: Int = 0,
) {
    val shortActivityId: String? get() = ActivityNames.getShowActivityId(appId, activityId)
    fun format(): String = "$appId/$shortActivityId/$number"
    fun sameAs(appId: String, activityId: String?): Boolean =
        this.appId == appId && this.activityId == activityId
}
