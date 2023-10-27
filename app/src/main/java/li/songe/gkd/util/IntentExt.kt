package li.songe.gkd.util

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

fun Context.shareFile(file: File, tile: String) {
    val context = this
    val uri = FileProvider.getUriForFile(
        context, "${context.packageName}.provider", file
    )
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(
        Intent.createChooser(
            intent, tile
        )
    )
}