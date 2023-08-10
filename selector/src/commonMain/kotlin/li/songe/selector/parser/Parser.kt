package li.songe.selector.parser

internal data class Parser<T>(
    val prefix: String = "",
    private val temp: (source: String, offset: Int, prefix: String) -> ParserResult<T>
)  {
    operator fun invoke(source: String, offset: Int) = temp(source, offset, prefix)
}