package li.gkd.selector.syntax

internal data class StringScanError(
    val index: Int,
    val expected: String,
)

internal data class StringScanResult(
    val end: Int,
    val value: String?,
    val error: StringScanError?,
)

internal fun scanString(
    source: String,
    start: Int,
    decode: Boolean = true,
): StringScanResult {
    val quote = source.getOrNull(start)
    if (!quote.isOneOf(STRING_QUOTE_CHARS)) {
        return StringScanResult(
            end = (start + 1).coerceAtMost(source.length),
            value = null,
            error = StringScanError(start, "string quote"),
        )
    }
    var index = start + 1
    val value = if (decode) StringBuilder() else null

    fun failure(expected: String, consumeCurrent: Boolean = false): StringScanResult {
        val errorIndex = index
        if (consumeCurrent && index < source.length) index++
        return StringScanResult(
            end = index,
            value = null,
            error = StringScanError(errorIndex, expected),
        )
    }

    while (true) {
        val char = source.getOrNull(index) ?: return failure(quote.toString())
        when {
            char.code in 0x0000..0x001F ->
                return failure("escaped control character", consumeCurrent = true)

            char == quote -> return StringScanResult(index + 1, value?.toString(), null)

            char != '\\' -> {
                value?.append(char)
                index++
            }

            else -> {
                index++
                when (val escaped = source.getOrNull(index)) {
                    null -> return failure("escape character")
                    '\\', '\'', '"', '`' -> {
                        value?.append(escaped)
                        index++
                    }

                    'n', 'r', 't', 'b' -> {
                        value?.append(
                            when (escaped) {
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                else -> '\b'
                            },
                        )
                        index++
                    }

                    'x', 'u' -> {
                        val length = if (escaped == 'x') 2 else 4
                        val digitsStart = index + 1
                        index = digitsStart
                        repeat(length) {
                            if (!source.getOrNull(index).isOneOf(HEX_DIGIT_CHARS)) {
                                return failure("hex digit", consumeCurrent = index < source.length)
                            }
                            index++
                        }
                        value?.append(
                            source.substring(digitsStart, digitsStart + length).toInt(16).toChar(),
                        )
                    }

                    else -> return failure("escape character", consumeCurrent = true)
                }
            }
        }
    }
}
