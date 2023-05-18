package li.songe.selector_core.parser

internal open class Parser<T>(
    val prefix: String = "",
    private val temp: (source: String, offset: Int, prefix: String) -> ParserResult<T>
) : (String, Int) -> ParserResult<T> {
    override fun invoke(source: String, offset: Int) = temp(source, offset, prefix)
}