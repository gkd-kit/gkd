package li.gkd.app.model

import li.gkd.app.app.ActivityNames.getShowActivityId
import li.gkd.app.time.format
import li.gkd.db.ActivityLog

val ActivityLog.showActivityId: String?
    get() = getShowActivityId(appId, activityId)

val ActivityLog.date: String
    get() = ctime.format("HH:mm:ss SSS")
