package li.songe.gkd.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.blankj.utilcode.util.PathUtils
import li.songe.gkd.App
import li.songe.gkd.db.table.SubsConfig
import li.songe.gkd.db.table.SubsItem
import java.io.File

@Database(
    version = 3,
    entities = [SubsItem::class, SubsConfig::class],
    autoMigrations = [
//        AutoMigration(from = 1, to = 2),
//        AutoMigration(from = 2, to = 3),
    ],
// 自动迁移 https://developer.android.com/training/data-storage/room/migrating-db-versions#automated
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subsItemRoomDao(): SubsItem.RoomDao
    abstract fun subsConfigRoomDao(): SubsConfig.RoomDao

    companion object {
        val db by lazy {
            File(PathUtils.getExternalAppFilesPath().plus("/db/")).apply {
                if (!exists()) {
                    mkdir()
                }
            }
            val name = PathUtils.getExternalAppFilesPath().plus("/db/database.db")
            Room.databaseBuilder(
                App.context,
                AppDatabase::class.java,
                name
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
