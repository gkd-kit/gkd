package li.songe.gkd.data

import android.net.Uri
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.LOCAL_SUBS_IDS
import li.songe.gkd.util.UriUtils
import li.songe.gkd.util.ZipUtils
import li.songe.gkd.util.checkSubsUpdate
import li.songe.gkd.util.createTempDir
import li.songe.gkd.util.json
import li.songe.gkd.util.sharedDir
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.subsMapFlow
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription
import java.io.File

@Serializable
private data class TransferData(
    val type: String = TYPE,
    val ctime: Long = System.currentTimeMillis(),
    val subsItems: List<SubsItem> = emptyList(),
    val subsConfigs: List<SubsConfig> = emptyList(),
    val categoryConfigs: List<CategoryConfig> = emptyList(),
    val appConfigs: List<AppConfig> = emptyList()
) {
    companion object {
        const val TYPE = "transfer_data"
    }
}

private const val subsDirName = "files"

private suspend fun importTransferData(transferData: TransferData): Boolean {
    val maxOrder = (subsItemsFlow.value.maxOfOrNull { it.order } ?: -1) + 1
    val subsItems =
        transferData.subsItems.filter { s -> s.id >= 0 || LOCAL_SUBS_IDS.contains(s.id) }
            .mapIndexed { i, s ->
                s.copy(order = maxOrder + i)
            }
    val hasNewSubsItem =
        subsItems.any { newSubs -> newSubs.id >= 0 && subsItemsFlow.value.all { oldSubs -> oldSubs.id != newSubs.id } }
    DbSet.subsItemDao.insertOrIgnore(*subsItems.toTypedArray())
    DbSet.subsConfigDao.insertOrIgnore(*transferData.subsConfigs.toTypedArray())
    DbSet.categoryConfigDao.insertOrIgnore(*transferData.categoryConfigs.toTypedArray())
    DbSet.appConfigDao.insertOrIgnore(*transferData.appConfigs.toTypedArray())
    return hasNewSubsItem
}

suspend fun exportData(subsIds: Collection<Long>): File {
    val tempDir = createTempDir()
    val dataFile = tempDir.resolve("${TransferData.TYPE}.json")
    dataFile.writeText(
        json.encodeToString(
            TransferData(
                subsItems = subsItemsFlow.value.filter { subsIds.contains(it.id) },
                subsConfigs = DbSet.subsConfigDao.querySubsItemConfig(subsIds.toList()),
                categoryConfigs = DbSet.categoryConfigDao.querySubsItemConfig(subsIds.toList()),
                appConfigs = DbSet.appConfigDao.querySubsItemConfig(subsIds.toList()),
            )
        )
    )
    val localSubsList = subsMapFlow.value.values.filter {
        it.id < 0 && subsIds.contains(it.id) && !it.isEmpty
    }
    val files = if (localSubsList.isNotEmpty()) {
        val f = tempDir.resolve(subsDirName).apply { mkdir() }
        localSubsList.forEach {
            val file = f.resolve("${it.id}.json")
            file.writeText(json.encodeToString(it))
        }
        f
    } else {
        null
    }
    val file = sharedDir.resolve("backup-${System.currentTimeMillis()}.zip")
    ZipUtils.zipFiles(listOfNotNull(dataFile, files), file)
    tempDir.deleteRecursively()
    return file
}

suspend fun importData(uri: Uri) {
    val tempDir = createTempDir()
    val zipFile = tempDir.resolve("file.zip").apply {
        writeBytes(UriUtils.uri2Bytes(uri))
    }
    val unzipDir = tempDir.resolve("unzip").apply {
        ZipUtils.unzipFile(zipFile, this)
    }
    val transferFile = unzipDir.resolve("${TransferData.TYPE}.json")
    if (!transferFile.exists() || !transferFile.isFile) {
        toast("导入无数据")
        tempDir.deleteRecursively()
        return
    }
    val data = withContext(Dispatchers.Default) {
        json.decodeFromString<TransferData>(transferFile.readText())
    }
    val hasNewSubsItem = importTransferData(data)
    val files = unzipDir.resolve(subsDirName)
    if (files.exists()) {
        val subscriptions = (files.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?: emptyArray()).mapNotNull { f ->
            try {
                RawSubscription.parse(f.readText())
            } catch (e: Exception) {
                LogUtils.d(e)
                null
            }
        }
        subscriptions.forEach { subscription ->
            if (LOCAL_SUBS_IDS.contains(subscription.id)) {
                updateSubscription(subscription)
            }
        }
    }
    toast("导入成功")
    tempDir.deleteRecursively()
    if (hasNewSubsItem) {
        delay(1000)
        checkSubsUpdate(true)
    }
}
