package li.gkd.selector.syntax

import li.gkd.selector.SelectorSyntaxException
import li.gkd.selector.SourceRange

internal const val WHITESPACE_CHARS = "\u0020\t\r\n"
internal const val DIGIT_CHARS = "0123456789"
internal const val POSITIVE_DIGIT_CHARS = "123456789"
internal const val HEX_DIGIT_CHARS = "abcdefABCDEF$DIGIT_CHARS"
internal const val STRING_QUOTE_CHARS = "`'\""
internal const val IDENTIFIER_START_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_"
internal const val IDENTIFIER_PART_CHARS = IDENTIFIER_START_CHARS + DIGIT_CHARS
internal const val VALUE_PRIMARY_START_CHARS =
    "-$DIGIT_CHARS$STRING_QUOTE_CHARS$IDENTIFIER_START_CHARS"
internal const val VALUE_START_CHARS = "($VALUE_PRIMARY_START_CHARS"
internal const val PROPERTY_START_CHARS = "@[*$IDENTIFIER_START_CHARS"
internal const val CONNECT_START_CHARS = "+-<>"

internal fun Char?.isOneOf(chars: String): Boolean = this != null && chars.contains(this)

internal class ParserCursor(
    val source: String,
) {
    var index = 0

    val current: Char?
        get() = source.getOrNull(index)

    fun readWhitespace() {
        while (current.isOneOf(WHITESPACE_CHARS)) index++
    }

    fun readChar(expected: Char) {
        expectChar(expected)
        index++
    }

    fun readLiteral(value: String): Boolean {
        if (
            source.startsWith(value, index) &&
            !source.getOrNull(index + value.length).isOneOf(IDENTIFIER_PART_CHARS)
        ) {
            index += value.length
            return true
        }
        return false
    }

    fun readUnsignedInt(): Int {
        val start = index
        expectOneOf(DIGIT_CHARS, "digit")
        if (current == '0') {
            index++
            if (current.isOneOf(DIGIT_CHARS)) {
                index = start
                errorExpected("integer without leading zero")
            }
            return 0
        }
        while (current.isOneOf(DIGIT_CHARS)) index++
        return source.substring(start, index).toIntOrNull() ?: run {
            index = start
            errorExpected("32-bit integer")
        }
    }

    fun readInt(): Int {
        val start = index
        if (current == '-') index++
        expectOneOf(DIGIT_CHARS, "digit")
        if (current == '0') {
            index++
            if (current.isOneOf(DIGIT_CHARS)) {
                index = start
                errorExpected("integer without leading zero")
            }
            return 0
        }
        while (current.isOneOf(DIGIT_CHARS)) index++
        return source.substring(start, index).toIntOrNull() ?: run {
            index = start
            errorExpected("32-bit integer")
        }
    }

    fun readString(): String {
        expectOneOf(STRING_QUOTE_CHARS, "string quote")
        val result = scanString(source, index)
        result.error?.let { error ->
            index = error.index
            errorExpected(error.expected)
        }
        index = result.end
        return checkNotNull(result.value)
    }

    fun expectChar(expected: Char): Char {
        if (current != expected) errorExpected("'$expected'")
        return expected
    }

    fun expectOneOf(chars: String, description: String): Char {
        val char = current ?: errorExpected(description)
        if (!chars.contains(char)) errorExpected(description)
        return char
    }

    fun errorExpected(
        expected: String,
        range: SourceRange = SourceRange(
            start = index,
            end = if (index < source.length) index + 1 else index,
        ),
        detail: String? = null,
    ): Nothing {
        throw SelectorSyntaxException(
            expected = expected,
            actual = current.toDisplayText(),
            range = range,
            detail = detail,
        )
    }
}

private fun Char?.toDisplayText(): String {
    this ?: return "EOF"
    return when (this) {
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        '\b' -> "\\b"
        else -> if (code in 0x0000..0x001F || isWhitespace()) {
            "\\u" + code.toString(16).padStart(4, '0')
        } else {
            toString()
        }
    }
}
