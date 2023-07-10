package li.songe.gkd.db

import androidx.room.Database
import androidx.room.RoomDatabase
import li.songe.gkd.data.TriggerLog

@Database(
    version = 1,
    entities = [TriggerLog::class],
)
abstract class TriggerLogDb : RoomDatabase() {
    abstract fun triggerLogDao(): TriggerLog.TriggerLogDao
}