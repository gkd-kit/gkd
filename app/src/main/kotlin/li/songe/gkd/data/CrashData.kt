package li.songe.gkd.data

import kotlinx.serialization.Serializable
import li.songe.gkd.util.crashFolder
import li.songe.gkd.util.crashTempFolder
import li.songe.gkd.util.format
import li.songe.gkd.util.json

@Serializable
data class CrashData(
    val id: Long,
    val mtime: Long,
    val device: String,
    val androidVersionCode: Int,
    val androidVersionName: String,
    val versionCode: Int,
    val versionName: String,
    val name: String,
    val message: String?,
    val thread: String,
    val stackTrace: String,
) {
    val filename get() = "gkd_crash-" + mtime.format("yyyyMMdd_HHmmss") + ".json"
    fun save() {
        val text = json.encodeToString(this)
        crashFolder.resolve(filename).writeText(text)
        crashTempFolder.resolve(filename).writeText(text)
    }

}
