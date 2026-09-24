package li.gkd.app.data

import kotlinx.serialization.Serializable
import li.gkd.app.util.LogUtils
import li.gkd.app.util.FolderUtils
import li.gkd.app.util.format
import li.gkd.app.util.json

private const val MAX_CRASH_RECORD_COUNT = 20

@Serializable
data class CrashData(
    val id: Long,
    val mtime: Long,
    val device: String,
    val androidVersionCode: Int,
    val androidVersionName: String,
    val versionCode: Int,
    val versionName: String,
    val name: String,
    val message: String?,
    val thread: String,
    val stackTrace: String,
) {
    val filename get() = "gkd_crash-" + mtime.format("yyyyMMdd_HHmmss") + ".json"
    fun save() {
        val text = json.encodeToString(this)
        FolderUtils.crashFolder.resolve(filename).writeText(text)
        FolderUtils.crashTempFolder.resolve(filename).writeText(text)
        trimCrashDataFiles()
    }

    fun delete(): Boolean = listOf(
        FolderUtils.crashFolder.resolve(filename),
        FolderUtils.crashTempFolder.resolve(filename),
    ).map { file ->
        !file.exists() || file.delete()
    }.all { it }
}

fun deleteCrashDataList(): Boolean = listOf(
    FolderUtils.crashFolder,
    FolderUtils.crashTempFolder,
).flatMap { folder ->
    (folder.listFiles() ?: emptyArray()).filter { it.isFile }
}.map { file ->
    !file.exists() || file.delete()
}.all { it }

fun trimCrashDataFiles() {
    listOf(FolderUtils.crashFolder, FolderUtils.crashTempFolder).forEach { folder ->
        (folder.listFiles() ?: emptyArray())
            .filter { it.isFile }
            .sortedByDescending { it.name }
            .drop(MAX_CRASH_RECORD_COUNT)
            .forEach { file ->
                if (!file.delete()) {
                    LogUtils.d("Failed to delete expired crash log: ${file.name}")
                }
            }
    }
}

fun loadCrashDataList(): List<CrashData> =
    (FolderUtils.crashFolder.listFiles() ?: emptyArray())
        .filter { it.isFile }
        .mapNotNull { file ->
            try {
                json.decodeFromString<CrashData>(file.readText())
            } catch (e: Exception) {
                LogUtils.d("Failed to parse crash log: ${file.name}", e)
                null
            }
        }
        .sortedByDescending { it.mtime }
