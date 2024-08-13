package li.songe.json5

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer

inline fun <reified T> Json.encodeToJson5String(value: T): String {
    return Json5.encodeToString(
        encodeToJsonElement(serializersModule.serializer(), value),
    )
}

inline fun <reified T> Json.decodeFromJson5String(value: String): T {
    return decodeFromJsonElement<T>(Json5.parseToJson5Element(value))
}
