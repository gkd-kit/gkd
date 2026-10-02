package li.gkd.app.network

import kotlinx.serialization.Serializable

@Serializable
data class NewVersion(
    val versionCode: Int, val versionName: String, val downloadUrl: String,
    val fileSize: Long, val versionLogs: List<VersionLog> = emptyList()
)

@Serializable
data class VersionLog(val name: String, val code: Int, val desc: String)
