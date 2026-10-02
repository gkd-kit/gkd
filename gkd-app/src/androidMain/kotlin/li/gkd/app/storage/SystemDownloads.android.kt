package li.gkd.app.storage

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.app
import li.gkd.app.platform.PlatformResult
import li.gkd.app.resources.Res
import li.gkd.app.resources.download_directory_create_failed
import li.gkd.app.resources.download_file_create_failed
import li.gkd.app.resources.download_file_open_failed
import li.gkd.app.ui.text.getSync
import java.io.File

actual suspend fun saveToDownloads(source: File): PlatformResult<String> {
    return withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 29) {
            PlatformResult.Success(saveWithMediaStore(source))
        } else {
            PlatformResult.Success(saveToLegacyDownloads(source))
        }
    }
}

private fun saveToLegacyDownloads(source: File): String {
    @Suppress("DEPRECATION")
    val downloadsDirectory =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!downloadsDirectory.exists() && !downloadsDirectory.mkdirs()) {
        error(Res.string.download_directory_create_failed.getSync())
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
        ?: error(Res.string.download_file_create_failed.getSync())
    try {
        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error(Res.string.download_file_open_failed.getSync())
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
