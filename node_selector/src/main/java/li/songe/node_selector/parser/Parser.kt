package li.songe.node_selector.parser

class Parser<T>(
    val prefix: String = "",
    private val invokeTemp: (source: String, offset: Int, prefix: String) -> ParserResult<T>
) : (String, Int) -> ParserResult<T> {


    companion object {
        fun assert(source: String, i: Int) {
            if (i >= source.length) {
                throw ParserException(source, i)
            }
        }

        fun assert(source: String, i: Int, expectValue: Char) {
            assert(source, i, expectValue.toString())
        }

        fun assert(source: String, i: Int, expectValue: String) {
            if (i >= source.length) {
                throw ParserException(source, i, expectValue)
            }
            if (!expectValue.contains(source[i])) {
                throw ParserException(source, i, expectValue)
            }
        }

        fun throwError(source: String, i: Int, expectValue: String? = ""): Nothing {
            throw ParserException(source, i, expectValue)
        }
    }

    override fun invoke(source: String, offset: Int): ParserResult<T> {
        return invokeTemp(source, offset, prefix)
    }

}