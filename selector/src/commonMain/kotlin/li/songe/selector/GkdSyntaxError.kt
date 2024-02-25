package li.songe.selector

import kotlin.js.JsExport

@JsExport
data class GkdSyntaxError internal constructor(
    val expectedValue: String,
    val position: Int,
    val source: String,
) : Exception(
    "expected $expectedValue in selector at position $position, but got ${
        source.getOrNull(
            position
        )
    }"
)

internal fun gkdAssert(
    source: String,
    offset: Int,
    value: String = "",
    expectedValue: String? = null
) {
    if (offset >= source.length || (value.isNotEmpty() && !value.contains(source[offset]))) {
        throw GkdSyntaxError(expectedValue ?: value, offset, source)
    }
}

internal fun gkdError(source: String, offset: Int, expectedValue: String = ""): Nothing {
    throw GkdSyntaxError(expectedValue, offset, source)
}