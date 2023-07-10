package li.songe.gkd.db

import androidx.room.Database
import androidx.room.RoomDatabase
import li.songe.gkd.data.SubsConfig

@Database(
    version = 1,
    entities = [SubsConfig::class],
)
abstract class SubsConfigDb: RoomDatabase() {
    abstract fun subsConfigDao(): SubsConfig.SubsConfigDao
}