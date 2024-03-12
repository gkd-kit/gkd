package li.songe.selector.data

sealed class PrimitiveValue(open val value: Any?, open val type: String) {
    data object NullValue : PrimitiveValue(null, "null") {
        override fun toString() = "null"
    }

    data class BooleanValue(override val value: Boolean) : PrimitiveValue(value, TYPE_NAME) {
        override fun toString() = value.toString()

        companion object {
            const val TYPE_NAME = "boolean"
        }
    }

    data class IntValue(override val value: Int) : PrimitiveValue(value, TYPE_NAME) {
        override fun toString() = value.toString()

        companion object {
            const val TYPE_NAME = "int"
        }
    }

    data class StringValue(
        override val value: String,
        val matches: ((CharSequence) -> Boolean)? = null
    ) : PrimitiveValue(value, TYPE_NAME) {

        val outMatches: (value: CharSequence) -> Boolean = run {
            matches ?: return@run { false }
            getMatchValue(value, "(?is)", ".*")?.let { startsWithValue ->
                return@run { value -> value.startsWith(startsWithValue, ignoreCase = true) }
            }
            getMatchValue(value, "(?is).*", ".*")?.let { containsValue ->
                return@run { value -> value.contains(containsValue, ignoreCase = true) }
            }
            getMatchValue(value, "(?is).*", "")?.let { endsWithValue ->
                return@run { value -> value.endsWith(endsWithValue, ignoreCase = true) }
            }
            return@run matches
        }

        companion object {
            const val TYPE_NAME = "string"
            private const val REG_SPECIAL_STRING = "\\^$.?*|+()[]{}"
            private fun getMatchValue(value: String, prefix: String, suffix: String): String? {
                if (value.startsWith(prefix) && value.endsWith(suffix) && value.length >= (prefix.length + suffix.length)) {
                    for (i in prefix.length until value.length - suffix.length) {
                        if (value[i] in REG_SPECIAL_STRING) {
                            return null
                        }
                    }
                    return value.subSequence(prefix.length, value.length - suffix.length).toString()
                }
                return null
            }
        }

        override fun toString(): String {
            val wrapChar = '"'
            val sb = StringBuilder(value.length + 2)
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

                        in 0x10..0x1f -> {
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
    }
}
