package li.gkd.app.model

import li.gkd.app.app.ActivityNames.getShowActivityId
import li.gkd.app.time.format
import li.gkd.db.ActionLog

val ActionLog.showActivityId: String?
    get() = getShowActivityId(appId, activityId)

val ActionLog.date: String
    get() = ctime.format("MM-dd HH:mm:ss SSS")
