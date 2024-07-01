package li.songe.selector

import kotlin.js.JsExport

@JsExport
data class SyntaxError internal constructor(
    val expectedValue: String,
    val position: Int,
    val source: String,
    override val cause: Exception? = null
) : Exception(
    "expected $expectedValue in selector at position $position, but got ${
        source.getOrNull(
            position
        )
    }"
)

internal fun gkdAssert(
    source: CharSequence,
    offset: Int,
    value: String = "",
    expectedValue: String? = null
) {
    if (offset >= source.length || (value.isNotEmpty() && !value.contains(source[offset]))) {
        throw SyntaxError(expectedValue ?: value, offset, source.toString())
    }
}

internal fun gkdError(
    source: CharSequence,
    offset: Int,
    expectedValue: String = "",
    cause: Exception? = null
): Nothing {
    throw SyntaxError(expectedValue, offset, source.toString(), cause)
}