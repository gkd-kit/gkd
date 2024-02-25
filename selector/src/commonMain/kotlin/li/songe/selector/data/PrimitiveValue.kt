package li.songe.selector.data

sealed class PrimitiveValue(open val value: Any?) {
    data object NullValue : PrimitiveValue(null) {
        override fun toString() = "null"
    }

    data class BooleanValue(override val value: Boolean) : PrimitiveValue(value) {
        override fun toString() = value.toString()
    }

    data class IntValue(override val value: Int) : PrimitiveValue(value) {
        override fun toString() = value.toString()
    }

    data class StringValue(override val value: String) : PrimitiveValue(value) {
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
