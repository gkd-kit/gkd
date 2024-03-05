package li.songe.gkd.db

import androidx.room.Room
import li.songe.gkd.app
import li.songe.gkd.util.dbFolder

object DbSet {

    private fun buildDb(): AppDb {
        return Room.databaseBuilder(
            app, AppDb::class.java, dbFolder.resolve("gkd.db").absolutePath
        ).fallbackToDestructiveMigration().build()
    }

    private val db by lazy { buildDb() }
    val subsItemDao
        get() = db.subsItemDao()
    val subsConfigDao
        get() = db.subsConfigDao()
    val snapshotDao
        get() = db.snapshotDao()
    val clickLogDao
        get() = db.clickLogDao()
    val categoryConfigDao
        get() = db.categoryConfigDao()
}