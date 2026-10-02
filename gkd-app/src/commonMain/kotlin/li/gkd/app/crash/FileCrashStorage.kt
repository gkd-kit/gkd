package li.gkd.app.crash

import kotlinx.serialization.json.Json
import li.gkd.app.storage.appStorage
import li.gkd.app.util.LogUtils

object FileCrashStorage {
    private val directory get() = appStorage().crash

    private val json =
        Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val directories get() = listOf(directory, appStorage().crashTemp)
    fun save(record: CrashData) {
        val text = json.encodeToString(record)
        directories.forEach { it.mkdirs(); it.resolve(record.filename).writeText(text) }
        trim()
    }

    fun load(): List<CrashData> =
        directory.listFiles().orEmpty().filter { it.isFile }.mapNotNull { file ->
            try {
                json.decodeFromString<CrashData>(file.readText())
            } catch (e: Exception) {
                LogUtils.d("解析崩溃日志失败: ${file.name}", e); null
            }
        }.sortedByDescending { it.mtime }

    fun delete(record: CrashData): Boolean = directories.map { folder ->
        val file = folder.resolve(record.filename)
        !file.exists() || file.delete()
    }.all { it }

    fun deleteAll(): Boolean =
        directories.flatMap { it.listFiles().orEmpty().filter { file -> file.isFile } }
            .map { !it.exists() || it.delete() }.all { it }

    fun trim() {
        directories.forEach { folder ->
            folder.listFiles().orEmpty().filter { it.isFile }.sortedByDescending { it.name }
                .drop(20).forEach {
                    if (!it.delete()) LogUtils.d("删除过期崩溃日志失败: ${it.name}", null)
                }
        }
    }
}
