package li.songe.gkd.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.songe.gkd.data.AppConfig
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubsItem
import li.songe.gkd.db.DbSet
import li.songe.gkd.store.a11yScopeAppListFlow
import li.songe.gkd.store.actionCountFlow
import li.songe.gkd.store.blockA11yAppListFlow
import li.songe.gkd.store.blockMatchAppListFlow
import li.songe.gkd.store.storeFlow
import java.io.File

@Serializable
private data class DbData(
    val subsItems: List<SubsItem>?,
    val subsConfigs: List<SubsConfig>?,
    val categoryConfigs: List<CategoryConfig>?,
    val appConfigs: List<AppConfig>?,
)

object BackupUtils {
    private val backupStoreFlowList
        get() = listOf(
            storeFlow,
            actionCountFlow,
            blockMatchAppListFlow,
            blockA11yAppListFlow,
            a11yScopeAppListFlow,
        )

    suspend fun exportBackUpData(): File {
        val tempDir = createGkdTempDir()
        tempDir.resolve("store").run {
            mkdir()
            backupStoreFlowList.forEach { storeFlow ->
                resolve(storeFlow.filename).writeText(storeFlow.encodeSelf())
            }
        }
        tempDir.resolve("db.json").writeText(
            json.encodeToString(
                DbData(
                    subsItems = DbSet.subsItemDao.queryAll(),
                    subsConfigs = DbSet.subsConfigDao.queryAll(),
                    categoryConfigs = DbSet.categoryConfigDao.queryAll(),
                    appConfigs = DbSet.appConfigDao.queryAll(),
                )
            )
        )
        tempDir.resolve("subscription").run {
            mkdir()
            subsMapFlow.value.values.forEach { subs ->
                resolve("${subs.id}.json").writeText(json.encodeToString(subs))
            }
        }
        val file = sharedDir.resolve("gkd-backup-${System.currentTimeMillis()}.zip")
        ZipUtils.zipFiles(tempDir.listFiles()!!.filterNotNull(), file)
        tempDir.deleteRecursively()
        return file
    }

    suspend fun importBackUpData(uri: Uri) {
        toast("导入备份中...")
        val tempDir = createGkdTempDir()
        val zipFile = tempDir.resolve("file.zip").apply {
            writeBytes(UriUtils.uri2Bytes(uri))
        }
        val unzipDir = tempDir.resolve("unzip")
        try {
            ZipUtils.unzipFile(zipFile, unzipDir)
            zipFile.delete()
        } catch (e: Exception) {
            LogUtils.d("importBackUpData.unzipFile", e)
            toast("解压失败，非法备份文件")
            tempDir.deleteRecursively()
            return
        }
        backupStoreFlowList.forEach { storeFlow ->
            val file = unzipDir.resolve("store/${storeFlow.filename}")
            if (file.exists() && file.isFile) {
                try {
                    storeFlow.updateByDecode(file.readText())
                } catch (e: Exception) {
                    LogUtils.d("importBackUpData.updateByDecode", storeFlow.filename, e)
                }
            }
        }
        val dbFile = unzipDir.resolve("db.json")
        if (dbFile.exists() && dbFile.isFile) {
            val dbData = withContext(Dispatchers.Default) {
                json.decodeFromString<DbData>(dbFile.readText())
            }
            if (!dbData.subsItems.isNullOrEmpty()) {
                DbSet.subsItemDao.insertOrIgnore(*dbData.subsItems.toTypedArray())
            }
            if (!dbData.subsConfigs.isNullOrEmpty()) {
                DbSet.subsConfigDao.insertOrIgnore(*dbData.subsConfigs.toTypedArray())
            }
            if (!dbData.categoryConfigs.isNullOrEmpty()) {
                DbSet.categoryConfigDao.insertOrIgnore(*dbData.categoryConfigs.toTypedArray())
            }
            if (!dbData.appConfigs.isNullOrEmpty()) {
                DbSet.appConfigDao.insertOrIgnore(*dbData.appConfigs.toTypedArray())
            }
        }
        val subsDir = unzipDir.resolve("subscription")
        if (subsDir.exists() && subsDir.isDirectory) {
            (subsDir.listFiles {
                it.isFile && it.name.endsWith(".json")
            } ?: emptyArray()).filterNotNull().forEach { file ->
                try {
                    val subs = withContext(Dispatchers.Default) {
                        json.decodeFromString<RawSubscription>(file.readText())
                    }
                    updateSubscription(subs)
                } catch (e: Exception) {
                    LogUtils.d("importBackUpData.saveSubs", file.name, e)
                }
            }
        }
        toast("导入成功")
        tempDir.deleteRecursively()
        delay(1000)
        checkSubsUpdate(false)
    }
}