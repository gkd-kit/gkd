package li.gkd.app.data

import li.gkd.app.util.format
import li.gkd.app.util.getShowActivityId
import li.gkd.db.ActivityLog

val ActivityLog.showActivityId: String?
    get() = getShowActivityId(appId, activityId)

val ActivityLog.date: String
    get() = ctime.format("HH:mm:ss SSS")
