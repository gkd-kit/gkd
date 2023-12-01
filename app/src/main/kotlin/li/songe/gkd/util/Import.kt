package li.songe.gkd.util

import android.content.Intent
import android.webkit.URLUtil

fun getImportUrl(intent: Intent?): String? {
    val data = intent?.data
    if (data?.toString()?.startsWith("gkd://import?url=") == true) {
        val url = data.getQueryParameter("url")
        if (URLUtil.isNetworkUrl(url)) {
            return url
        }
    }
    return null
}