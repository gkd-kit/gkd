package li.songe.gkd.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.blankj.utilcode.util.PathUtils
import li.songe.gkd.App
import li.songe.gkd.db.table.TriggerLog
import java.io.File

@Database(
    version = 1,
    entities = [TriggerLog::class],
)
abstract class LogDatabase : RoomDatabase() {
    abstract fun triggerLogRoomDao(): TriggerLog.RoomDao

    companion object {
        val logDb by lazy {
            File(PathUtils.getExternalAppFilesPath().plus("/db/")).apply {
                if (!exists()) {
                    mkdir()
                }
            }
            val name = PathUtils.getExternalAppFilesPath().plus("/db/log.db")
            Room.databaseBuilder(
                App.context,
                LogDatabase::class.java,
                name
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}