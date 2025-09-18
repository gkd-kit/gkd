package li.songe.gkd.util

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import li.songe.gkd.app
import li.songe.gkd.permission.canWriteExternalStorage
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream


object ImageUtils {
    fun save2Album(
        src: Bitmap,
        quality: Int = 100,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        recycle: Boolean = true,
    ): Boolean {
        val safeDirName = app.packageName
        val suffix: String? = if (Bitmap.CompressFormat.JPEG == format) "JPG" else format.name
        val fileName = System.currentTimeMillis().toString() + "_" + quality + "." + suffix
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!canWriteExternalStorage.updateAndGet()) {
                return false
            }
            val picDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val destFile = File(picDir, "$safeDirName/$fileName")
            BufferedOutputStream(FileOutputStream(destFile)).use {
                val ret = src.compress(format, quality, it)
                if (!ret) return false
            }
            if (recycle && !src.isRecycled) {
                src.recycle()
            }
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.setData(("file://" + destFile.absolutePath).toUri())
            app.sendBroadcast(intent)
            return true
        } else {
            val contentValues = ContentValues()
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/*")
            val contentUri: Uri
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            } else {
                contentUri = MediaStore.Images.Media.INTERNAL_CONTENT_URI
            }
            contentValues.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_DCIM + "/" + safeDirName
            )
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1)
            val uri: Uri? = app.contentResolver.insert(contentUri, contentValues)
            if (uri == null) {
                return false
            }
            var os: OutputStream? = null
            try {
                os = app.contentResolver.openOutputStream(uri)
                src.compress(format, quality, os!!)
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                app.contentResolver.update(uri, contentValues, null, null)
                return true
            } catch (e: Exception) {
                app.contentResolver.delete(uri, null, null)
                e.printStackTrace()
                return false
            } finally {
                try {
                    os?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

    }
}