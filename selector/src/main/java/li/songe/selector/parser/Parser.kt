package li.songe.selector.parser

open class Parser<T>(
    val prefix: String = "",
    private val invokeTemp: (source: String, offset: Int, prefix: String) -> ParserResult<T>
) : (String, Int) -> ParserResult<T> {
    override fun invoke(source: String, offset: Int) = invokeTemp(source, offset, prefix)
}