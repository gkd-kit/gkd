package li.gkd.selector.property

internal sealed interface RegexCompileResult {
    data class Success(
        val matches: (input: CharSequence) -> Boolean,
    ) : RegexCompileResult

    data class Failure(
        val detail: String,
    ) : RegexCompileResult
}

private const val SIMPLE_REGEX_PREFIX = "(?is)"
private const val SIMPLE_REGEX_WILDCARD_PREFIX = "(?is).*"
private const val SIMPLE_REGEX_WILDCARD_SUFFIX = ".*"
private const val REGEX_SPECIAL_CHARS = "\\^$.?*|+()[]{}"

internal fun String.compileRegex(): RegexCompileResult {
    val platformRegex = compilePlatformRegex()
    val simpleRegex = parseSimpleRegex()
    if (simpleRegex == null || platformRegex !is RegexCompileResult.Success) {
        return platformRegex
    }

    return RegexCompileResult.Success { input ->
        when (simpleRegex.match(input)) {
            SimpleRegexMatch.Matched -> true
            SimpleRegexMatch.NotMatched -> false
            SimpleRegexMatch.Unknown -> platformRegex.matches(input)
        }
    }
}

private fun String.parseSimpleRegex(): SimpleRegex? {
    extractPlainRegexText(SIMPLE_REGEX_PREFIX, SIMPLE_REGEX_WILDCARD_SUFFIX)?.let { value ->
        return SimpleRegex.create(SimpleRegexKind.Start, value)
    }
    extractPlainRegexText(
        SIMPLE_REGEX_WILDCARD_PREFIX,
        SIMPLE_REGEX_WILDCARD_SUFFIX,
    )?.let { value ->
        return SimpleRegex.create(SimpleRegexKind.Include, value)
    }
    extractPlainRegexText(SIMPLE_REGEX_WILDCARD_PREFIX, "")?.let { value ->
        return SimpleRegex.create(SimpleRegexKind.End, value)
    }
    return null
}

private fun String.extractPlainRegexText(prefix: String, suffix: String): String? {
    if (length < prefix.length + suffix.length) return null
    if (!startsWith(prefix) || !endsWith(suffix)) return null
    val end = length - suffix.length
    for (index in prefix.length until end) {
        if (this[index] in REGEX_SPECIAL_CHARS) return null
    }
    return substring(prefix.length, end)
}

private enum class SimpleRegexKind {
    Start,
    Include,
    End,
}

private enum class SimpleRegexMatch {
    Matched,
    NotMatched,
    Unknown,
}

private class SimpleRegex private constructor(
    private val kind: SimpleRegexKind,
    private val value: String,
) {
    private val includeFailure: IntArray? = if (kind == SimpleRegexKind.Include) {
        value.buildAsciiIgnoreCaseFailureTable()
    } else {
        null
    }

    fun match(input: CharSequence): SimpleRegexMatch =
        when (kind) {
            SimpleRegexKind.Start -> matchAt(input, 0)
            SimpleRegexKind.Include -> matchIncluded(input)
            SimpleRegexKind.End -> matchAt(input, input.length - value.length)
        }

    private fun matchIncluded(input: CharSequence): SimpleRegexMatch {
        if (value.isEmpty()) return SimpleRegexMatch.Matched
        if (input.hasNonAsciiCaseCharacter()) return SimpleRegexMatch.Unknown
        val failure = checkNotNull(includeFailure)
        var matchedLength = 0
        for (character in input) {
            while (
                matchedLength > 0 &&
                !value[matchedLength].equalsAsciiIgnoreCase(character)
            ) {
                matchedLength = failure[matchedLength - 1]
            }
            if (value[matchedLength].equalsAsciiIgnoreCase(character)) {
                matchedLength++
                if (matchedLength == value.length) return SimpleRegexMatch.Matched
            }
        }
        return SimpleRegexMatch.NotMatched
    }

    private fun matchAt(
        input: CharSequence,
        start: Int,
    ): SimpleRegexMatch {
        if (start < 0 || start + value.length > input.length) {
            return if (input.hasNonAsciiCaseCharacter()) {
                SimpleRegexMatch.Unknown
            } else {
                SimpleRegexMatch.NotMatched
            }
        }
        var hasMismatch = false
        var hasUnknown = false
        for (index in value.indices) {
            val expected = value[index]
            val actual = input[start + index]
            if (expected == actual) continue
            if (expected.isAsciiLetter) {
                if (actual.isAsciiLetter) {
                    if (expected.lowercaseChar() == actual.lowercaseChar()) continue
                } else if (actual.hasCase || actual.isSurrogateCodeUnit) {
                    hasUnknown = true
                    continue
                }
            }
            hasMismatch = true
        }
        return when {
            hasUnknown -> SimpleRegexMatch.Unknown
            hasMismatch -> SimpleRegexMatch.NotMatched
            else -> SimpleRegexMatch.Matched
        }
    }

    companion object {
        fun create(
            kind: SimpleRegexKind,
            value: String,
        ): SimpleRegex? {
            if (
                value.any { character ->
                    character.isSurrogateCodeUnit ||
                        character.hasCase && !character.isAsciiLetter
                }
            ) {
                return null
            }
            return SimpleRegex(kind, value)
        }
    }
}

private val Char.isAsciiLetter: Boolean
    get() = this in 'A'..'Z' || this in 'a'..'z'

private val Char.hasCase: Boolean
    get() = isLowerCase() || isUpperCase() || isTitleCase()

private val Char.isSurrogateCodeUnit: Boolean
    get() = this in '\uD800'..'\uDFFF'

private fun Char.equalsAsciiIgnoreCase(other: Char): Boolean =
    this == other || isAsciiLetter && other.isAsciiLetter && lowercaseChar() == other.lowercaseChar()

private fun String.buildAsciiIgnoreCaseFailureTable(): IntArray {
    val failure = IntArray(length)
    var prefixLength = 0
    for (index in 1 until length) {
        while (prefixLength > 0 && !this[index].equalsAsciiIgnoreCase(this[prefixLength])) {
            prefixLength = failure[prefixLength - 1]
        }
        if (this[index].equalsAsciiIgnoreCase(this[prefixLength])) prefixLength++
        failure[index] = prefixLength
    }
    return failure
}

private fun CharSequence.hasNonAsciiCaseCharacter(): Boolean {
    for (character in this) {
        if (
            character.isSurrogateCodeUnit ||
            character.hasCase && !character.isAsciiLetter
        ) {
            return true
        }
    }
    return false
}

internal expect fun String.compilePlatformRegex(): RegexCompileResult
