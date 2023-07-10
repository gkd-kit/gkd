package li.songe.selector

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
data class ExtSyntaxError internal constructor(
    val expectedValue: String,
    val position: Int,
    val source: String,
) : Exception(
    "expected $expectedValue in selector at position $position, but got ${
        source.getOrNull(
            position
        )
    }"
) {
    internal companion object {
        fun assert(source: String, offset: Int, value: String = "", expectedValue: String? = null) {
            if (offset >= source.length || (value.isNotEmpty() && !value.contains(source[offset]))) {
                throw ExtSyntaxError(expectedValue ?: value, offset, source)
            }
        }

        fun throwError(source: String, offset: Int, expectedValue: String = ""): Nothing {
            throw ExtSyntaxError(expectedValue, offset, source)
        }
    }
}