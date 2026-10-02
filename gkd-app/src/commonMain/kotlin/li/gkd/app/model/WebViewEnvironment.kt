package li.gkd.app.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON contract returned by the synchronous gkd.getEnvironment() web API. */
@Serializable
data class WebViewEnvironment(
    val platform: String,
    val appId: String,
    val appName: String,
    val versionCode: Int,
    val versionName: String,
    val channel: String,
    val debuggable: Boolean,
) {
    fun toJson(): String = Json.encodeToString(this)
}
