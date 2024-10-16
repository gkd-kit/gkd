package li.songe.gkd.util

import android.webkit.URLUtil

const val FILE_SHORT_URL = "https://f.gkd.li/"
const val IMPORT_SHORT_URL = "https://i.gkd.li/i/"

const val SERVER_SCRIPT_URL =
    "https://registry.npmmirror.com/@gkd-kit/config/latest/files/dist/server.js"

const val REPOSITORY_URL = "https://github.com/gkd-kit/gkd"

const val HOME_PAGE_URL = "https://gkd.li"

private val safeRemoteBaseUrls = arrayOf(
    "https://registry.npmmirror.com/@gkd-kit/",
    "https://raw.githubusercontent.com/gkd-kit/",

    "https://cdn.jsdelivr.net/gh/gkd-kit/",
    "https://cdn.jsdelivr.net/npm/@gkd-kit/",
    "https://fastly.jsdelivr.net/gh/gkd-kit/",
    "https://fastly.jsdelivr.net/npm/@gkd-kit/",
)

fun isSafeUrl(url: String): Boolean {
    if (!URLUtil.isHttpsUrl(url)) return false
    return safeRemoteBaseUrls.any { u -> url.startsWith(u) }
}

const val LOCAL_SUBS_ID = -2L
const val LOCAL_HTTP_SUBS_ID = -1L
val LOCAL_SUBS_IDS = arrayOf(LOCAL_SUBS_ID, LOCAL_HTTP_SUBS_ID)
