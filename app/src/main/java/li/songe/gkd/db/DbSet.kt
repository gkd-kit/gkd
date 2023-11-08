package li.songe.gkd.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.blankj.utilcode.util.LogUtils
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeout
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.util.DEFAULT_SUBS_UPDATE_URL
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.dbFolder
import li.songe.gkd.util.launchTry
import java.io.File

object DbSet {
    private val appDb by lazy {
        Room.databaseBuilder(
            app, AppDb::class.java, File(dbFolder, "gkd.db").absolutePath
        ).addCallback(createCallback()).fallbackToDestructiveMigration().build()
    }
    val subsItemDao by lazy { appDb.subsItemDao() }
    val subsConfigDao by lazy { appDb.subsConfigDao() }
    val snapshotDao by lazy { appDb.snapshotDao() }
    val clickLogDao by lazy { appDb.clickLogDao() }

    private fun createCallback(): RoomDatabase.Callback {
        return object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                appScope.launchTry(Dispatchers.IO) {
                    delay(3000)
                    if (subsItemDao.queryById(0).firstOrNull() != null) {
                        return@launchTry
                    }
                    val defaultSubsItem = SubsItem(
                        id = 0,
                        order = 0,
                        updateUrl = DEFAULT_SUBS_UPDATE_URL,
                        mtime = System.currentTimeMillis()
                    )
                    subsItemDao.insert(defaultSubsItem)
                    val newSubsRaw = try {
                        withTimeout(3000) {
                            SubscriptionRaw.parse(
                                Singleton.client.get(defaultSubsItem.updateUrl!!).bodyAsText()
                            )
                        }
                    } catch (e: Exception) {
                        LogUtils.d("获取默认订阅失败")
                        LogUtils.d(e)
                        return@launchTry
                    }
                    defaultSubsItem.subsFile.writeText(SubscriptionRaw.stringify(newSubsRaw))
                    subsItemDao.update(defaultSubsItem.copy(mtime = System.currentTimeMillis()))
                }
            }
        }
    }
}