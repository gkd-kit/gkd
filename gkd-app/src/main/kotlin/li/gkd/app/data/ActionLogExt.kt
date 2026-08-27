package li.gkd.app.data

import li.gkd.app.util.format
import li.gkd.app.util.getShowActivityId
import li.gkd.db.ActionLog

val ActionLog.showActivityId: String?
    get() = getShowActivityId(appId, activityId)

val ActionLog.date: String
    get() = ctime.format("MM-dd HH:mm:ss SSS")
