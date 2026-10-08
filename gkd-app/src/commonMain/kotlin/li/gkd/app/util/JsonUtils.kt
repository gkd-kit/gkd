package li.gkd.app.util

import kotlinx.serialization.json.Json

object JsonUtils {
    val default by lazy {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
    }

    val keepingNulls by lazy {
        Json(from = default) {
            explicitNulls = true
        }
    }
}
