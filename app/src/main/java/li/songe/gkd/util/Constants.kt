package li.songe.gkd.util

const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

const val FILE_UPLOAD_URL = "https://github-upload-assets.lisonge.workers.dev/"

const val DEFAULT_SUBS_UPDATE_URL =
    "https://registry.npmmirror.com/@gkd-kit/subscription/latest/files"

const val IMPORT_BASE_URL = "https://i.gkd.li/import/"

val safeRemoteBaseUrls = arrayOf(
    "https://registry.npmmirror.com/@gkd-kit/",
    "https://cdn.jsdelivr.net/npm/@gkd-kit/",
    "https://unpkg.com/@gkd-kit/",
    "https://github.com/gkd-kit/",
    "https://raw.githubusercontent.com/gkd-kit/"
)