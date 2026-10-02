package li.gkd.app.subscription

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import li.songe.json5.Json5
import li.songe.json5.Json5EncoderConfig
import li.songe.json5.encodeToJson5String

object SubscriptionJson {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    val config = Json5EncoderConfig(indent = "  ", trailingComma = true)
    inline fun <reified T> toJson5String(value: T): String =
        if (value is JsonElement) Json5.encodeToString(value, config) else json.encodeToJson5String(
            value,
            config
        )
}
