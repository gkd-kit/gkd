package li.songe.gkd.debug.server.api

import android.os.Build
import kotlinx.serialization.Serializable

@Serializable
data class Snapshot(
    val id: Long = System.currentTimeMillis(),
    val device: String = Build.DEVICE,
    val versionRelease: String = Build.VERSION.RELEASE,
    val model: String = Build.MODEL,
    val manufacturer: String = Build.MANUFACTURER,
    val androidVersion: Int = Build.VERSION.SDK_INT,
    val appId: String = "",
    val appName: String = "",
    val activityId: String = "",
    val comment: String = ""
)
