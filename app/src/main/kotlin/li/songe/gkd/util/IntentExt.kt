package li.songe.gkd.util

import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.songe.gkd.MainActivity
import li.songe.gkd.app
import li.songe.gkd.permission.canWriteExternalStorage
import li.songe.gkd.permission.foregroundServiceSpecialUseState
import li.songe.gkd.permission.notificationState
import li.songe.gkd.permission.requiredPermission
import java.io.File
import kotlin.reflect.KClass

fun MainActivity.shareFile(file: File, title: String) {
    val uri = FileProvider.getUriForFile(
        app, "${app.packageName}.provider", file
    )
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    tryStartActivity(
        Intent.createChooser(
            intent, title
        )
    )
}

suspend fun MainActivity.saveFileToDownloads(file: File) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        requiredPermission(this, canWriteExternalStorage)
        val targetFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            file.name
        )
        targetFile.writeBytes(file.readBytes())
    } else {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        withContext(Dispatchers.IO) {
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("创建URI失败")
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(file.readBytes())
                outputStream.flush()
            }
        }
    }
    toast("已保存 ${file.name} 到下载")
}

fun Context.tryStartActivity(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        LogUtils.d("tryStartActivity", e)
        toast("跳转失败\n" + (e.message ?: e.stackTraceToString()))
    }
}

fun openWeChatScaner() {
    val intent = app.packageManager.getLaunchIntentForPackage("com.tencent.mm")?.apply {
        putExtra("LauncherUI.From.Scaner.Shortcut", true)
    }
    if (intent == null) {
        toast("请检查微信是否安装")
        return
    }
    app.tryStartActivity(intent)
}

fun openA11ySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    app.tryStartActivity(intent)
}

fun openUri(uri: String) {
    val u = try {
        uri.toUri()
    } catch (e: Exception) {
        e.printStackTrace()
        toast("非法链接")
        return
    }
    openUri(u)
}

fun openUri(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    app.tryStartActivity(intent)
}

fun openApp(appId: String) {
    val intent = app.packageManager.getLaunchIntentForPackage(appId)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.tryStartActivity(intent)
    } else {
        toast("请检查此应用是否安装")
    }
}

fun <T : Service> stopServiceByClass(clazz: KClass<T>) {
    val intent = Intent(app, clazz.java)
    app.stopService(intent)
}

fun <T : Service> startForegroundServiceByClass(clazz: KClass<T>) {
    if (!notificationState.checkOrToast()) return
    if (!foregroundServiceSpecialUseState.checkOrToast()) return
    val intent = Intent(app, clazz.java)
    try {
        app.startForegroundService(intent)
    } catch (e: Throwable) {
        LogUtils.d(e)
        toast("启动服务失败: ${e.message}")
    }
}
