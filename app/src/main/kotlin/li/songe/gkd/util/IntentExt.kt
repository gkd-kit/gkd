package li.songe.gkd.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.blankj.utilcode.util.LogUtils
import java.io.File

fun Context.shareFile(file: File, tile: String) {
    val uri = FileProvider.getUriForFile(
        this, "${packageName}.provider", file
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
            intent, tile
        )
    )
}

fun Context.tryStartActivity(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        LogUtils.d("tryStartActivity", e)
        // 在某些模拟器上/特定设备 ActivityNotFoundException
        toast(e.message ?: e.stackTraceToString())
    }
}

fun Context.openUri(uri: String) {
    val u = try {
        Uri.parse(uri)
    } catch (e: Exception) {
        e.printStackTrace()
        toast("非法链接")
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, u)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    tryStartActivity(intent)
}