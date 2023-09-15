package li.songe.gkd.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.blankj.utilcode.util.LogUtils
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.util.FolderExt
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.isMainProcess
import li.songe.gkd.util.launchTry

object DbSet {

    private fun <T : RoomDatabase> getDb(
        klass: Class<T>, name: String,
    ): RoomDatabase.Builder<T> {
        return Room.databaseBuilder(
            app, klass, FolderExt.dbFolder.absolutePath.plus("/${name}.db")
        ).fallbackToDestructiveMigration().enableMultiInstanceInvalidation()
    }

    private val snapshotDb by lazy { getDb(SnapshotDb::class.java, "snapshot").build() }
    private val subsConfigDb by lazy { getDb(SubsConfigDb::class.java, "subsConfig").build() }
    private val subsItemDb by lazy {
        getDb(SubsItemDb::class.java, "subsItem").addCallback(createCallback()).build()
    }
    val clickLogDb by lazy { getDb(ClickLogDb::class.java, "clickLog").build() }
    val subsItemDao by lazy { subsItemDb.subsItemDao() }
    val subsConfigDao by lazy { subsConfigDb.subsConfigDao() }
    val snapshotDao by lazy { snapshotDb.snapshotDao() }

    private fun createCallback(): RoomDatabase.Callback {
        return object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                if (!isMainProcess) return
                appScope.launchTry(Dispatchers.IO) {
                    val defaultSubsItem = SubsItem(
                        id = 0,
                        order = 0,
                        updateUrl = "https://registry.npmmirror.com/@gkd-kit/subscription/latest/files",
                        mtime = 0
                    )
                    db.insert(
                        "subs_item",
                        SQLiteDatabase.CONFLICT_IGNORE,
                        defaultSubsItem.toContentValues()
                    )
                    if (defaultSubsItem.updateUrl == null) return@launchTry
                    try {
                        val s = System.currentTimeMillis()
                        val newSubsRaw = withTimeout(3000) {
                            SubscriptionRaw.parse(
                                Singleton.client.get(defaultSubsItem.updateUrl).bodyAsText()
                            )
                        }
                        delay(1500 - (System.currentTimeMillis() - s))
                        val newSubsItem = defaultSubsItem.copy(mtime = System.currentTimeMillis())
                        newSubsItem.subsFile.writeText(SubscriptionRaw.stringify(newSubsRaw))
                        subsItemDao.update(newSubsItem)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        LogUtils.d("创建数据库时获取订阅失败")
                    }
                }
            }
        }
    }
}