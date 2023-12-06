package li.songe.gkd.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

private val json5IdentifierReg = Regex("[a-zA-Z_][a-zA-Z0-9_]*")

/**
 * https://spec.json5.org/#strings
 */
private fun escapeString(value: String): String {
    val wrapChar = '\''
    val sb = StringBuilder()
    sb.append(wrapChar)
    value.forEach { c ->
        val escapeChar = when (c) {
            wrapChar -> wrapChar
            '\n' -> 'n'
            '\r' -> 'r'
            '\t' -> 't'
            '\b' -> 'b'
            '\\' -> '\\'
            else -> null
        }
        if (escapeChar != null) {
            sb.append("\\" + escapeChar)
        } else {
            when (c.code) {
                in 0..0xf -> {
                    sb.append("\\x0" + c.code.toString(16))
                }

                in 0..0x1f -> {
                    sb.append("\\x" + c.code.toString(16))
                }

                else -> {
                    sb.append(c)
                }
            }
        }
    }
    sb.append(wrapChar)
    return sb.toString()
}

fun convertJsonElementToJson5(element: JsonElement): String {
    return when (element) {
        is JsonPrimitive -> {
            val content = element.content
            if (element.isString) {
                escapeString(content)
            } else {
                content
            }
        }

        is JsonObject -> {
            // Handle JSON objects
            val entries = element.entries.joinToString(",") { (key, value) ->
                // If key is a valid identifier, no quotes are needed
                if (key.matches(json5IdentifierReg)) {
                    "$key:${convertJsonElementToJson5(value)}"
                } else {
                    "${escapeString(key)}:${convertJsonElementToJson5(value)}"
                }
            }
            "{$entries}"
        }

        is JsonArray -> {
            val elements = element.joinToString(",") { convertJsonElementToJson5(it) }
            "[$elements]"
        }
    }
}

inline fun <reified T> Json.encodeToJson5String(value: T): String {
    return convertJsonElementToJson5(encodeToJsonElement(serializersModule.serializer(), value))
}
