package li.gkd.db

import androidx.room3.withWriteTransaction
import li.gkd.db.Db.database

/** Each write includes its retention step in the same database transaction. */
object RuntimeRecordStore {
    suspend fun writeVisits(records: List<AppLastVisit>, trim: Boolean) {
        database.withWriteTransaction {
            database.appLastVisitDao().insert(*records.toTypedArray())
            if (trim) database.appLastVisitDao().deleteKeepLatest()
        }
    }

    suspend fun writeActivities(records: List<ActivityLog>, trim: Boolean) {
        database.withWriteTransaction {
            database.activityLogDao().insert(*records.toTypedArray())
            if (trim) database.activityLogDao().deleteKeepLatest()
        }
    }

    suspend fun writeAction(record: ActionLog, trim: Boolean) {
        database.withWriteTransaction {
            database.actionLogDao().insert(record)
            if (trim) database.actionLogDao().deleteKeepLatest()
        }
    }
}
