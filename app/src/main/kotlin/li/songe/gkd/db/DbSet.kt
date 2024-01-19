package li.songe.gkd.db

import androidx.room.Room
import li.songe.gkd.app
import li.songe.gkd.util.dbFolder
import java.io.File

object DbSet {
    private val appDb by lazy {
        Room.databaseBuilder(
            app, AppDb::class.java, File(dbFolder, "gkd.db").absolutePath
        ).fallbackToDestructiveMigration().build()
    }
    val subsItemDao by lazy { appDb.subsItemDao() }
    val subsConfigDao by lazy { appDb.subsConfigDao() }
    val snapshotDao by lazy { appDb.snapshotDao() }
    val clickLogDao by lazy { appDb.clickLogDao() }
    val categoryConfigDao by lazy { appDb.categoryConfigDao() }
}