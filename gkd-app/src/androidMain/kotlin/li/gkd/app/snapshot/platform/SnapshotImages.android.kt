package li.gkd.app.snapshot.platform

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.app
import li.gkd.app.platform.PlatformResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private val webpFormat
    get() = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
    else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP

actual fun prepareSnapshotReplacement(original: File, bytes: ByteArray, output: File): Boolean {
    val previous = BitmapFactory.decodeFile(original.absolutePath) ?: return false
    try {
        val replacement = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        try {
            if (previous.width != replacement.width || previous.height != replacement.height) return false
            FileOutputStream(output).use {
                if (!replacement.compress(
                        webpFormat,
                        85,
                        it
                    )
                ) throw IOException("Cannot encode screenshot")
                it.fd.sync()
            }
            return true
        } finally {
            replacement.recycle()
        }
    } finally {
        previous.recycle()
    }
}

actual suspend fun saveImageToAlbum(image: File): PlatformResult<Unit> =
    withContext(Dispatchers.IO) {
        val context = app
        val bitmap = BitmapFactory.decodeFile(image.absolutePath)
            ?: throw IOException("Cannot decode screenshot")
        try {
            val name = "${System.currentTimeMillis()}_85.WEBP"
            if (Build.VERSION.SDK_INT < 29) {
                @Suppress("DEPRECATION")
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    context.packageName
                )
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create album directory")
                val destination = File(directory, name)
                try {
                    FileOutputStream(destination).use {
                        if (!bitmap.compress(
                                webpFormat,
                                85,
                                it
                            )
                        ) throw IOException("Cannot encode screenshot")
                    }
                } catch (e: Throwable) {
                    destination.delete(); throw e
                }
                @Suppress("DEPRECATION")
                context.sendBroadcast(
                    Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.fromFile(destination)
                    )
                )
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_DCIM + "/" + context.packageName
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val collection =
                    if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED)
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.INTERNAL_CONTENT_URI
                val uri = resolver.insert(collection, values)
                    ?: throw IOException("Cannot create album entry")
                try {
                    (resolver.openOutputStream(uri)
                        ?: throw IOException("Cannot open album entry")).use {
                        if (!bitmap.compress(
                                webpFormat,
                                85,
                                it
                            )
                        ) throw IOException("Cannot encode screenshot")
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    if (resolver.update(
                            uri,
                            values,
                            null,
                            null
                        ) <= 0
                    ) throw IOException("Cannot publish album entry")
                } catch (e: Throwable) {
                    resolver.delete(uri, null, null); throw e
                }
            }
            PlatformResult.Success(Unit)
        } finally {
            bitmap.recycle()
        }
    }

suspend fun Bitmap.encodeSnapshotScreenshot(): ByteArray = withContext(Dispatchers.Default) {
    ByteArrayOutputStream().use {
        if (!compress(webpFormat, 85, it)) throw IOException("Cannot encode screenshot")
        it.toByteArray()
    }
}
