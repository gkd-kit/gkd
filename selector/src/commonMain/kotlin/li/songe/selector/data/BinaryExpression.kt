package li.songe.selector.data

import li.songe.selector.Transform

data class BinaryExpression(val name: String, val operator: CompareOperator, val value: Any?) :
    Expression() {
    override fun <T> match(node: T, transform: Transform<T>) =
        operator.compare(transform.getAttr(node, name), value)

    override fun toString() = "${name}${operator}${
        if (value is String) {
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
                    else -> null
                }
                if (escapeChar != null) {
                    sb.append("\\" + escapeChar)
                } else {
                    when (c.code) {
                        in 0..9 -> {
                            sb.append("\\x0" + c.code.toString(16))
                        }

                        in 10..0x1f -> {
                            sb.append("\\x" + c.code.toString(16))
                        }

                        else -> {
                            sb.append(c)
                        }
                    }
                }
            }
            sb.append(wrapChar)
            sb.toString()
        } else {
            value
        }
    }"
}
