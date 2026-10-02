package li.gkd.app.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import li.gkd.app.util.LogUtils
import java.io.File
import java.nio.file.Files

object StorageMaintenance {
    suspend fun deleteSharedFile(layout: AppStorageLayout, file: File) =
        withContext(NonCancellable + Dispatchers.IO) {
            if (file.parentFile == layout.sharedCache && file.exists() && !file.delete()) {
                LogUtils.d("无法清理共享缓存文件", file.absolutePath)
            }
        }

    fun temporaryDirectory(layout: AppStorageLayout): File =
        Files.createTempDirectory(layout.tempCache.toPath(), "gkd-").toFile()

    fun clearExpired(layout: AppStorageLayout, now: Long = System.currentTimeMillis()) {
        listOf(layout.sharedCache, layout.tempCache).forEach { directory ->
            directory.listFiles().orEmpty().filter { now - it.lastModified() > 3_600_000L }
                .forEach { file ->
                    check(if (file.isDirectory) file.deleteRecursively() else file.delete()) { "Cannot remove expired file: $file" }
                }
        }
    }
}
