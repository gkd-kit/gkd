package li.gkd.app.util

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.text.UiStrings
import li.gkd.app.app
import li.gkd.app.permission.PermissionStates
import java.io.File

object SystemDownloads {
    fun canSave(): Boolean = PermissionStates.writeExternalStorage.updateAndGet()

    suspend fun save(source: File): String? {
        if (!canSave()) return null
        return withContext(Dispatchers.IO) {
            if (AndroidTarget.Q) {
                saveWithMediaStore(source)
            } else {
                saveToLegacyDownloads(source)
            }
        }
    }

    private fun saveToLegacyDownloads(source: File): String {
        @Suppress("DEPRECATION")
        val downloadsDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDirectory.exists() && !downloadsDirectory.mkdirs()) {
            error(UiStrings.download_directory_create_failed)
        }
        val destination = ExportFileNames.reserve(
            downloadsDirectory,
            source.nameWithoutExtension,
            source.extension,
        )
        try {
            source.copyTo(destination, overwrite = true)
            return destination.name
        } catch (e: Throwable) {
            destination.delete()
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(source: File): String {
        val resolver = app.contentResolver
        val displayName = ExportFileNames.availableName(
            source.nameWithoutExtension,
            source.extension,
        ) { name ->
            runCatching {
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                    arrayOf(name, "${Environment.DIRECTORY_DOWNLOADS}/"),
                    null,
                )?.use { it.moveToFirst() } == true
            }.getOrDefault(false)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(source.extension)
                    ?: "application/octet-stream",
            )
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error(UiStrings.download_file_create_failed)
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error(UiStrings.download_file_open_failed)
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return runCatching {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull() ?: displayName
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
