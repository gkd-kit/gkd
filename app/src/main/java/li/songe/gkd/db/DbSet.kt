package li.songe.gkd.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.blankj.utilcode.util.PathUtils
import li.songe.gkd.App
import li.songe.gkd.utils.FolderExt
import java.io.File

object DbSet {


    private fun <T : RoomDatabase> getDb(
        klass: Class<T>, name: String
    ): T {
        return Room.databaseBuilder(
            App.context, klass, FolderExt.dbFolder.absolutePath.plus("/${name}.db")
        ).fallbackToDestructiveMigration()
            .enableMultiInstanceInvalidation()
            .build()
    }

    private val snapshotDb by lazy { getDb(SnapshotDb::class.java, "snapshot") }
    private val subsConfigDb by lazy { getDb(SubsConfigDb::class.java, "subsConfig") }
    private val subsItemDb by lazy { getDb(SubsItemDb::class.java, "subsItem") }
    private val triggerLogDb by lazy { getDb(TriggerLogDb::class.java, "triggerLog") }

    val subsItemDao by lazy { subsItemDb.subsItemDao() }
    val subsConfigDao by lazy { subsConfigDb.subsConfigDao() }
    val snapshotDao by lazy { snapshotDb.snapshotDao() }
    val triggerLogDao by lazy { triggerLogDb.triggerLogDao() }
}