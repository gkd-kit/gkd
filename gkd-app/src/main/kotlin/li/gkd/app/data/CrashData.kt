package li.gkd.app.data

import kotlinx.serialization.Serializable
import li.gkd.app.util.crashFolder
import li.gkd.app.util.crashTempFolder
import li.gkd.app.util.format
import li.gkd.app.util.json

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
