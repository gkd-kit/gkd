package li.songe.selector_core.parser

data class SyntaxError(val expectedValue: String, val position: Int, val source: String) :
    Exception(
        "expected $expectedValue in selector at position $position, but got ${
            source.getOrNull(
                position
            )
        }"
    ) {
    companion object {
        fun assert(source: String, offset: Int, value: String = "", expectedValue: String? = null) {
            if (offset >= source.length || (value.isNotEmpty() && !value.contains(source[offset]))) {
                throw SyntaxError(expectedValue ?: value, offset, source)
            }
        }

        fun throwError(source: String, offset: Int, expectedValue: String = ""): Nothing {
            throw SyntaxError(expectedValue, offset, source)
        }
    }
}