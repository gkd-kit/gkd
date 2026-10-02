package li.gkd.app.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object FileExports {
    /** User has already selected the target; cancellation cannot leave a partial destination. */
    suspend fun copyTo(source: File, target: File): Unit = withContext(Dispatchers.IO) {
        if (source.canonicalFile == target.canonicalFile) return@withContext
        val parent =
            requireNotNull(target.absoluteFile.parentFile) { "Export target must have a parent directory: $target" }
        val staged = Files.createTempFile(parent.toPath(), ".gkd-export-", ".tmp").toFile()
        try {
            source.inputStream().use { input ->
                FileOutputStream(staged).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val size = input.read(buffer)
                        if (size < 0) break
                        output.write(buffer, 0, size)
                    }
                    output.fd.sync()
                }
            }
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                Files.move(
                    staged.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            }
        } finally {
            staged.delete()
        }
    }

    suspend fun <T> withTemporaryFile(
        create: suspend () -> File, delete: suspend (File) -> Unit,
        consume: suspend (File) -> T
    ): T {
        val file = create()
        var failure: Throwable? = null
        try {
            return consume(file)
        } catch (e: Throwable) {
            failure = e; throw e
        } finally {
            try {
                withContext(NonCancellable) { delete(file) }
            } catch (cleanup: Throwable) {
                val original = failure
                if (original == null) throw cleanup
                if (cleanup !== original) original.addSuppressed(cleanup)
            }
        }
    }
}
