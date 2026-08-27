package li.gkd.app.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.gkd.db.AppConfig
import li.gkd.db.CategoryConfig
import li.gkd.app.data.RawSubscription
import li.gkd.db.SubsConfig
import li.gkd.db.SubsItem
import li.gkd.db.Db
import li.gkd.app.store.a11yScopeAppListFlow
import li.gkd.app.store.actionCountFlow
import li.gkd.app.store.blockA11yAppListFlow
import li.gkd.app.store.blockMatchAppListFlow
import li.gkd.app.store.storeFlow
import java.io.File

@Serializable
private data class DbData(
    val subsItems: List<SubsItem>?,
    val subsConfigs: List<SubsConfig>?,
    val categoryConfigs: List<CategoryConfig>?,
    val appConfigs: List<AppConfig>?,
)

private data class PreparedBackup(
    val dbData: DbData?,
    val storeUpdates: List<() -> Unit>,
    val subscriptions: List<RawSubscription>,
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
                    subsItems = Db.subsItemDao.queryAll(),
                    subsConfigs = Db.subsConfigDao.queryAll(),
                    categoryConfigs = Db.categoryConfigDao.queryAll(),
                    appConfigs = Db.appConfigDao.queryAll(),
                )
            )
        )
        tempDir.resolve("subscription").run {
            mkdir()
            SubscriptionStore.awaitSnapshot().subscriptions.values.forEach { subs ->
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
        try {
            val zipFile = tempDir.resolve("file.zip").apply {
                writeBytes(UriUtils.uri2Bytes(uri))
            }
            val unzipDir = tempDir.resolve("unzip")
            try {
                ZipUtils.unzipFile(zipFile, unzipDir)
            } catch (e: Exception) {
                LogUtils.d("importBackUpData.unzipFile", e)
                throw IllegalArgumentException("解压失败，非法备份文件", e)
            }
            zipFile.delete()

            val prepared = prepareBackup(unzipDir)
            prepared.dbData?.let { dbData ->
                Db.withTransaction {
                    if (!dbData.subsItems.isNullOrEmpty()) {
                        Db.subsItemDao.insertOrIgnore(*dbData.subsItems.toTypedArray())
                    }
                    if (!dbData.subsConfigs.isNullOrEmpty()) {
                        Db.subsConfigDao.insertOrIgnore(*dbData.subsConfigs.toTypedArray())
                    }
                    if (!dbData.categoryConfigs.isNullOrEmpty()) {
                        Db.categoryConfigDao.insertOrIgnore(*dbData.categoryConfigs.toTypedArray())
                    }
                    if (!dbData.appConfigs.isNullOrEmpty()) {
                        Db.appConfigDao.insertOrIgnore(*dbData.appConfigs.toTypedArray())
                    }
                }
            }
            prepared.subscriptions.forEach { SubscriptionStore.save(it) }
            prepared.storeUpdates.forEach { it() }
            toast("导入成功")
            delay(1000)
            SubscriptionStore.refresh()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private suspend fun prepareBackup(unzipDir: File): PreparedBackup =
        withContext(Dispatchers.Default) {
            val dbFile = unzipDir.resolve("db.json")
            val dbData = if (dbFile.exists() && dbFile.isFile) {
                json.decodeFromString<DbData>(dbFile.readText())
            } else {
                null
            }
            val storeUpdates = backupStoreFlowList.mapNotNull { storeFlow ->
                val file = unzipDir.resolve("store/${storeFlow.filename}")
                if (!file.exists() || !file.isFile) return@mapNotNull null
                val text = file.readText()
                storeFlow.decode(text)
                val applyUpdate: () -> Unit = { storeFlow.updateByDecode(text) }
                applyUpdate
            }
            val subsDir = unzipDir.resolve("subscription")
            val subscriptions = if (subsDir.exists() && subsDir.isDirectory) {
                (subsDir.listFiles { file ->
                    file.isFile && file.name.endsWith(".json")
                } ?: emptyArray()).filterNotNull().sortedBy { it.name }.map { file ->
                    val fileId = file.nameWithoutExtension.toLongOrNull()
                        ?: error("非法订阅文件名: ${file.name}")
                    json.decodeFromString<RawSubscription>(file.readText()).also { subscription ->
                        require(subscription.id == fileId) {
                            "订阅文件id不一致: $fileId != ${subscription.id}"
                        }
                    }
                }.also { list ->
                    require(list.map { it.id }.distinct().size == list.size) {
                        "备份中存在重复订阅id"
                    }
                }
            } else {
                emptyList()
            }
            PreparedBackup(
                dbData = dbData,
                storeUpdates = storeUpdates,
                subscriptions = subscriptions,
            )
        }
}
