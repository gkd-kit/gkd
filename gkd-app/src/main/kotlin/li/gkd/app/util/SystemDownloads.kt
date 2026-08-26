package li.gkd.app.util

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.app
import li.gkd.app.permission.PermissionStates
import java.io.File

object SystemDownloads {
    fun canSave(): Boolean = PermissionStates.writeExternalStorage.updateAndGet()

    suspend fun save(source: File): Boolean {
        if (!canSave()) return false
        withContext(Dispatchers.IO) {
            if (AndroidTarget.Q) {
                saveWithMediaStore(source)
            } else {
                saveToLegacyDownloads(source)
            }
        }
        return true
    }

    private fun saveToLegacyDownloads(source: File) {
        @Suppress("DEPRECATION")
        val downloadsDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDirectory.exists() && !downloadsDirectory.mkdirs()) {
            error("创建下载目录失败")
        }
        source.copyTo(downloadsDirectory.resolve(source.name), overwrite = true)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(source: File) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(source.extension)
                    ?: "application/octet-stream",
            )
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("创建下载文件失败")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("打开下载文件失败")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
