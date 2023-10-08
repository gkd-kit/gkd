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
import li.songe.gkd.util.DEFAULT_SUBS_UPDATE_URL
import li.songe.gkd.util.FolderExt
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.isMainProcess
import li.songe.gkd.util.launchTry
import java.io.File

object DbSet {
    private val appDb by lazy {
        Room.databaseBuilder(
            app, AppDb::class.java, File(FolderExt.dbFolder, "gkd.db").absolutePath
        ).fallbackToDestructiveMigration().enableMultiInstanceInvalidation()
            .addCallback(createCallback()).build()
    }
    val subsItemDao by lazy { appDb.subsItemDao() }
    val subsConfigDao by lazy { appDb.subsConfigDao() }
    val snapshotDao by lazy { appDb.snapshotDao() }
    val clickLogDao by lazy { appDb.clickLogDao() }

    private fun createCallback(): RoomDatabase.Callback {
        return object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                if (!isMainProcess) return
                appScope.launchTry(Dispatchers.IO) {
                    val defaultSubsItem = SubsItem(
                        id = 0,
                        order = 0,
                        updateUrl = DEFAULT_SUBS_UPDATE_URL,
                        mtime = System.currentTimeMillis()
                    )
                    db.insert(
                        "subs_item",
                        SQLiteDatabase.CONFLICT_IGNORE,
                        defaultSubsItem.toContentValues()
                    )
                    try {
                        val s = System.currentTimeMillis()
                        val newSubsRaw = withTimeout(3000) {
                            SubscriptionRaw.parse(
                                Singleton.client.get(defaultSubsItem.updateUrl!!).bodyAsText()
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