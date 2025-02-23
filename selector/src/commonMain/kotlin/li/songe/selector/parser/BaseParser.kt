package li.songe.selector.parser

import li.songe.selector.SyntaxException

internal const val WHITESPACE_CHAR = "\u0020\t\r\n"
internal const val POSITIVE_DIGIT_CHAR = "123456789"
internal const val DIGIT_CHAR = "0$POSITIVE_DIGIT_CHAR"
internal const val HEX_DIGIT_CHAR = "abcdefABCDEF$DIGIT_CHAR"
internal const val INT_START_CHAR = "-$DIGIT_CHAR"
internal const val STRING_START_CHAR = "`'\""
internal const val VAR_START_CHAR = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_"
internal const val VAR_CONTINUE_CHAR = VAR_START_CHAR + DIGIT_CHAR
internal const val VALUE_START_CHAR = "($INT_START_CHAR$STRING_START_CHAR$VAR_START_CHAR"
internal const val EXP_START_CHAR = "!($VALUE_START_CHAR"
internal const val CONNECT_EXP_START_CHAR = "(n$DIGIT_CHAR"
internal const val MONOMIAL_START_CHAR = "+-n$DIGIT_CHAR"
internal const val CONNECT_START_CHAR = "+-<>"
internal const val PROPERTY_START_CHAR = "@[*$VAR_START_CHAR"

internal data class Monomial(
    val coefficient: Int,
    val power: Int,
)

private fun Char?.escapeString(): String {
    this ?: return "EOF"
    when (this) {
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        '\b' -> "\\b"
        else -> null
    }?.let { return it }
    if (code in 0x0000..0x001F || isWhitespace()) {
        return "\\u" + code.toString(16).padStart(4, '0')
    }
    return toString()
}

internal fun Char?.inStr(v: String): Boolean {
    return this != null && v.contains(this)
}

internal sealed interface BaseParser {
    val source: CharSequence
    var i: Int
    val char: Char?
        get() = source.getOrNull(i)

    fun readWhiteSpace() {
        while (char.inStr(WHITESPACE_CHAR)) {
            i++
        }
    }

    fun rollbackWhiteSpace() {
        while (source[i - 1] in WHITESPACE_CHAR) {
            i--
        }
    }

    fun readUInt(): Int {
        val start = i
        expectOneOfChar(DIGIT_CHAR, "digit")
        if (char == '0') {
            i++
            if (char?.isDigit() == true) {
                i = start
                errorExpect("no zero start int")
            }
            return 0
        } else {
            i++
        }
        while (char?.isDigit() == true) {
            i++
        }
        val value = source.substring(start, i).toIntOrNull()
        if (value == null) {
            i = start
            errorExpect("legal range int")
        }
        return value
    }

    fun readInt(): Int {
        val start = i
        if (char == '-') {
            i++
        }
        expectOneOfChar(DIGIT_CHAR, "digit")
        if (char == '0') {
            i++
            if (char?.isDigit() == true) {
                i = start
                errorExpect("no zero start int")
            }
            return 0
        } else {
            i++
        }
        while (char?.isDigit() == true) {
            i++
        }
        val value = source.substring(start, i).toIntOrNull()
        if (value == null) {
            i = start
            errorExpect("legal range int")
        }
        return value
    }

    fun readString(): String {
        val quote = expectOneOfChar(STRING_START_CHAR)
        i++
        val sb = StringBuilder()
        while (true) {
            val c = char ?: errorExpect(quote.toString())
            // https://www.rfc-editor.org/rfc/inline-errata/rfc7159.html
            if (c.code in 0x0000..0x001F) {
                errorExpect("no control character")
            } else if (c == quote) {
                i++
                break
            } else if (c == '\\') {
                i++
                val realChar = when (char) {
                    '\\' -> '\\'
                    '\'' -> '\''
                    '"' -> '"'
                    '`' -> '`'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'b' -> '\b'
                    'x' -> {
                        repeat(2) {
                            i++
                            expectOneOfChar(HEX_DIGIT_CHAR, "hex digit")
                        }
                        source.substring(i + 1 - 2, i + 1).toInt(16).toChar()
                    }

                    'u' -> {
                        repeat(4) {
                            i++
                            expectOneOfChar(HEX_DIGIT_CHAR, "hex digit")
                        }
                        source.substring(i + 1 - 4, i + 1).toInt(16).toChar()
                    }

                    else -> {
                        errorExpect("escape char")
                    }
                }
                sb.append(realChar)
                i++
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    fun readLiteral(v: String): Boolean {
        if (source.startsWith(v, i) && !source.getOrNull(i + v.length).inStr(VAR_CONTINUE_CHAR)) {
            i += v.length
            return true
        }
        return false
    }

    fun <T: Any> readBracketExpression(block: () -> T): T {
        expectChar('(')
        i++
        readWhiteSpace()
        return block().apply {
            readWhiteSpace()
            expectChar(')')
            i++
        }
    }
}

internal fun BaseParser.errorExpect(name: String): Nothing {
    throw SyntaxException("Expect $name, got ${char.escapeString()} at index $i")
}

internal fun BaseParser.expectOneOfChar(v: String, name: String? = null): Char {
    val c = char
    if (c == null || !v.contains(c)) {
        if (name != null) {
            errorExpect(name)
        } else {
            errorExpect("one of string $v")
        }
    }
    return c
}

internal fun BaseParser.expectChar(v: Char): Char {
    if (v != char) {
        errorExpect("char $v")
    }
    return v
}
