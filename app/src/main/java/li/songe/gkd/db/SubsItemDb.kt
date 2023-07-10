package li.songe.gkd.db

import androidx.room.Database
import androidx.room.RoomDatabase
import li.songe.gkd.data.SubsItem

@Database(
    version = 1,
    entities = [SubsItem::class],
)
abstract class SubsItemDb: RoomDatabase() {
    abstract fun subsItemDao(): SubsItem.SubsItemDao
}