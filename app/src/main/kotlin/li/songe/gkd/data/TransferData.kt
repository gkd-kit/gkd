package li.songe.gkd.data

import android.content.Context
import android.net.Uri
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.UriUtils
import com.blankj.utilcode.util.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.LOCAL_SUBS_IDS
import li.songe.gkd.util.checkSubsUpdate
import li.songe.gkd.util.exportZipDir
import li.songe.gkd.util.importZipDir
import li.songe.gkd.util.json
import li.songe.gkd.util.resetDirectory
import li.songe.gkd.util.shareFile
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription

@Serializable
private data class TransferData(
    val type: String = TYPE,
    val ctime: Long = System.currentTimeMillis(),
    val subsItems: List<SubsItem> = emptyList(),
    val subsConfigs: List<SubsConfig> = emptyList(),
    val categoryConfigs: List<CategoryConfig> = emptyList(),
) {
    companion object {
        const val TYPE = "transfer_data"
    }
}

private suspend fun importTransferData(transferData: TransferData): Boolean {
    // TODO transaction
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
    return hasNewSubsItem
}

suspend fun exportData(context: Context, subsIds: Collection<Long>) {
    if (subsIds.isEmpty()) return
    exportZipDir.resetDirectory()
    val dataFile = exportZipDir.resolve("${TransferData.TYPE}.json")
    dataFile.writeText(
        json.encodeToString(
            TransferData(
                subsItems = subsItemsFlow.value.filter { subsIds.contains(it.id) },
                subsConfigs = DbSet.subsConfigDao.querySubsItemConfig(subsIds.toList()),
                categoryConfigs = DbSet.categoryConfigDao.querySubsItemConfig(subsIds.toList()),
            )
        )
    )
    val files = exportZipDir.resolve("files").apply { mkdir() }
    subsIdToRawFlow.value.values.filter { it.id < 0 && subsIds.contains(it.id) }.forEach {
        val file = files.resolve("${it.id}.json")
        file.writeText(json.encodeToString(it))
    }
    val file = exportZipDir.resolve("backup-${System.currentTimeMillis()}.zip")
    ZipUtils.zipFiles(listOf(dataFile, files), file)
    dataFile.delete()
    files.deleteRecursively()
    context.shareFile(file, "分享数据文件")
}

suspend fun importData(uri: Uri) {
    importZipDir.resetDirectory()
    val zipFile = importZipDir.resolve("import.zip")
    zipFile.writeBytes(UriUtils.uri2Bytes(uri))
    val unZipImportFile = importZipDir.resolve("unzipImport")
    ZipUtils.unzipFile(zipFile, unZipImportFile)
    val transferFile = unZipImportFile.resolve("${TransferData.TYPE}.json")
    if (!transferFile.exists() || !transferFile.isFile) {
        toast("导入无数据")
        return
    }
    val data = withContext(Dispatchers.Default) {
        json.decodeFromString<TransferData>(transferFile.readText())
    }
    val hasNewSubsItem = importTransferData(data)
    val files = unZipImportFile.resolve("files")
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
    toast("导入成功")
    importZipDir.resetDirectory()
    if (hasNewSubsItem) {
        delay(1000)
        checkSubsUpdate(true)
    }
}
