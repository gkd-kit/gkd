package li.gkd.app.snapshot

import li.gkd.app.text.UiStrings
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

// 文件重命名和数据库发布必须作为不可取消的提交阶段完成，耗时写入仍应响应取消。
suspend fun commitSnapshotDirectory(
    layout: SnapshotFileLayout,
    id: Long,
    write: (SnapshotFileLayout.Files) -> Unit,
    publish: suspend () -> Unit,
) {
    currentCoroutineContext().ensureActive()
    val target = layout.committed(id)
    val staging = layout.staging(id)
    if (target.directory.exists()) {
        throw IOException(UiStrings.directory_target_exists(target.directory.name))
    }
    staging.directory.deleteIfExists()
    if (!staging.directory.mkdirs()) {
        throw IOException(UiStrings.directory_temp_create_failed(staging.directory.name))
    }
    try {
        write(staging)
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            if (!staging.directory.renameTo(target.directory)) {
                throw IOException(UiStrings.directory_commit_failed(target.directory.name))
            }
            try {
                publish()
            } catch (e: Throwable) {
                try {
                    target.directory.deleteIfExists()
                } catch (cleanupError: IOException) {
                    e.addSuppressed(cleanupError)
                }
                throw e
            }
        }
    } catch (e: Throwable) {
        try {
            withContext(NonCancellable) {
                staging.directory.deleteIfExists()
            }
        } catch (cleanupError: IOException) {
            e.addSuppressed(cleanupError)
        }
        throw e
    }
}

private fun File.deleteIfExists() {
    if (exists() && !deleteRecursively()) {
        throw IOException(UiStrings.directory_delete_failed(name))
    }
}
