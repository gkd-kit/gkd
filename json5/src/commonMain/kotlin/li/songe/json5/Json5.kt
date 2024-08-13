package li.songe.json5

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object Json5 {
    fun parseToJson5Element(string: String): JsonElement {
        return Json5Decoder(string).read()
    }

    fun encodeToString(element: JsonElement, indent: Int = 2) = encodeToString(element, indent, 0)

    private fun encodeToString(element: JsonElement, indent: Int = 2, depth: Int = 0): String {
        val lineSeparator = if (indent == 0) "" else "\n"
        val keySeparator = if (indent == 0) ":" else ": "
        val prefixSpaces = if (indent == 0) "" else " ".repeat(indent * (depth + 1))
        val closingSpaces = if (indent == 0) "" else " ".repeat(indent * depth)

        return when (element) {
            is JsonPrimitive -> {
                if (element.isString) {
                    stringifyString(element.content)
                } else {
                    element.content
                }
            }

            is JsonObject -> {
                if (element.isEmpty()) {
                    "{}"
                } else {
                    element.entries.joinToString(",$lineSeparator") { (key, value) ->
                        "${prefixSpaces}${stringifyKey(key)}${keySeparator}${
                            encodeToString(
                                value,
                                indent,
                                depth + 1
                            )
                        }"
                    }.let {
                        "{$lineSeparator$it$lineSeparator$closingSpaces}"
                    }
                }
            }

            is JsonArray -> {
                if (element.isEmpty()) {
                    "[]"
                } else {
                    element.joinToString(",$lineSeparator") {
                        "${prefixSpaces}${encodeToString(it, indent, depth + 1)}"
                    }.let {
                        "[$lineSeparator$it$lineSeparator$closingSpaces]"
                    }
                }
            }
        }
    }

}


