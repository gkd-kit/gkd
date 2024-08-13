package li.songe.json5

import kotlin.text.category

private val unicodeLetterCategories = hashSetOf(
    CharCategory.UPPERCASE_LETTER,
    CharCategory.LOWERCASE_LETTER,
    CharCategory.TITLECASE_LETTER,
    CharCategory.MODIFIER_LETTER,
    CharCategory.OTHER_LETTER,
    CharCategory.LETTER_NUMBER,
)

private val unicodeIdCategories = hashSetOf(
    CharCategory.NON_SPACING_MARK,
    CharCategory.COMBINING_SPACING_MARK,
    CharCategory.DECIMAL_DIGIT_NUMBER,
    CharCategory.CONNECTOR_PUNCTUATION,
)

internal fun isIdStartChar(c: Char?): Boolean {
    c ?: return false
    return c.category in unicodeLetterCategories || c == '_' || c == '$'
}

internal fun isIdContinueChar(c: Char?): Boolean {
    c ?: return false
    return isIdStartChar(c) || c.category in unicodeIdCategories || c == '\u200C' || c == '\u200D'
}

internal fun isDigit(c: Char?): Boolean {
    c ?: return false
    return c in '0'..'9'
}

internal fun isHexDigit(c: Char?): Boolean {
    c ?: return false
    return (c in '0'..'9') || (c in 'A'..'F') || (c in 'a'..'f')
}

internal fun isPowerStartChar(c: Char?): Boolean {
    c ?: return false
    return c == 'e' || c == 'E'
}

internal fun isHexStartChar(c: Char?): Boolean {
    c ?: return false
    return c == 'x' || c == 'X'
}

internal fun isWhiteSpace(c: Char?): Boolean {
    c ?: return false
    return when (c) {
        '\u0009' -> true
        in '\u000A'..'\u000D' -> true
        '\u0020' -> true
        '\u00A0' -> true
        '\u2028' -> true
        '\u2029' -> true
        '\uFEFF' -> true

        '\u1680' -> true
        in '\u2000'..'\u200A' -> true
        '\u202F' -> true
        '\u205F' -> true
        '\u3000' -> true
        else -> false
    }
}

internal fun isNewLine(c: Char?): Boolean {
    c ?: return false
    return when (c) {
        '\u000A' -> true
        '\u000D' -> true
        '\u2028' -> true
        '\u2029' -> true
        else -> false
    }
}

private val escapeReplacements = hashMapOf(
    '\\' to "\\\\",
    '\b' to "\\b",
    '\u000C' to "\\f",
    '\n' to "\\n",
    '\r' to "\\r",
    '\t' to "\\t",
    '\u000B' to "\\v",
    '\u0000' to "\\0",
    '\u2028' to "\\u2028",
    '\u2029' to "\\u2029",
)

internal fun stringifyString(value: String, singleQuote: Boolean = true): String {
    // https://github.com/json5/json5/blob/main/lib/stringify.js
    val wrapChar = if (singleQuote) '\'' else '"'
    val sb = StringBuilder()
    sb.append(wrapChar)
    value.forEachIndexed { i, c ->
        when {
            c == wrapChar -> {
                sb.append("\\$wrapChar")
            }

            c == '\u0000' -> {
                if (isDigit(value.getOrNull(i + 1))) {
                    // "\u00002" -> \x002
                    sb.append("\\x00")
                } else {
                    sb.append("\\0")
                }
            }

            c in escapeReplacements.keys -> {
                sb.append(escapeReplacements[c])
            }

            c.code in 0..0xf -> {
                sb.append("\\x0" + c.code.toString(16))
            }

            c.code in 0..0x1f -> {
                sb.append("\\x" + c.code.toString(16))
            }

            else -> {
                sb.append(c)
            }
        }
    }
    sb.append(wrapChar)
    return sb.toString()
}

internal fun stringifyKey(key: String, singleQuote: Boolean = true): String {
    if (key.isEmpty()) {
        return stringifyString(key, singleQuote)
    }
    if (!isIdStartChar(key[0])) {
        return stringifyString(key, singleQuote)
    }
    for (c in key) {
        if (!isIdContinueChar(c)) {
            return stringifyString(key, singleQuote)
        }
    }
    return key
}
