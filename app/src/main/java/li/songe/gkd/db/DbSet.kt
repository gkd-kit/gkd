package li.songe.gkd.db

import androidx.room.Room
import androidx.room.RoomDatabase
import li.songe.gkd.app
import li.songe.gkd.util.FolderExt

object DbSet {

    private fun <T : RoomDatabase> getDb(
        klass: Class<T>, name: String,
    ): T {
        return Room.databaseBuilder(
            app, klass, FolderExt.dbFolder.absolutePath.plus("/${name}.db")
        ).fallbackToDestructiveMigration().enableMultiInstanceInvalidation().build()
    }

    private val snapshotDb by lazy { getDb(SnapshotDb::class.java, "snapshot") }
    private val subsConfigDb by lazy { getDb(SubsConfigDb::class.java, "subsConfig") }
    private val subsItemDb by lazy { getDb(SubsItemDb::class.java, "subsItem") }
    val triggerLogDb by lazy { getDb(TriggerLogDb::class.java, "triggerLog-v2") }

    val subsItemDao by lazy { subsItemDb.subsItemDao() }
    val subsConfigDao by lazy { subsConfigDb.subsConfigDao() }
    val snapshotDao by lazy { snapshotDb.snapshotDao() }

}