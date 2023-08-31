package li.songe.gkd.db

import androidx.room.Database
import androidx.room.RoomDatabase
import li.songe.gkd.data.ClickLog

@Database(
    version = 1,
    entities = [ClickLog::class],
)
abstract class ClickLogDb : RoomDatabase() {
    abstract fun clickLogDao(): ClickLog.TriggerLogDao
}