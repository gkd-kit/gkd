package li.gkd.app.data

import li.gkd.app.META
import li.gkd.app.a11y.launcherAppId
import li.gkd.app.util.systemUiAppId
import li.gkd.db.AppVisitLog
import li.gkd.db.Db

private var appLogCount = 0

suspend fun AppVisitLog.AppLogDao.insert(oldAppId: String, newAppId: String, mtime: Long) {
    Db.withTransaction {
        insert(
            AppVisitLog(oldAppId, fixAppVisitTime(oldAppId, mtime - 1)),
            AppVisitLog(newAppId, fixAppVisitTime(newAppId, mtime)),
        )
        if (appLogCount++ % 100 == 0) {
            deleteKeepLatest()
        }
    }
}

private fun fixAppVisitTime(appId: String, time: Long): Long = when (appId) {
    META.appId -> time - 120_000
    launcherAppId, systemUiAppId -> time - 60_000
    else -> time
}
