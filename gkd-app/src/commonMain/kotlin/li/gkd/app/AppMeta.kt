package li.gkd.app

import kotlinx.serialization.Serializable
import li.gkd.app.network.AppLinks

@Serializable
data class AppMeta(
    val channel: String,
    val buildKey: String,
    val commitId: String,
    val commitTime: Long,
    val tagName: String?,
    val debuggable: Boolean,
    val versionCode: Int,
    val versionName: String,
    val appId: String,
    val appName: String,
) {
    val commitUrl = "${AppLinks.Repository}/".run {
        plus(if (tagName != null) "tree/$tagName" else "commit/$commitId")
    }
    val isGkdChannel get() = channel == "gkd"
    val updateEnabled get() = isGkdChannel
    val isBeta get() = versionName.contains("beta")
}

expect val META: AppMeta
