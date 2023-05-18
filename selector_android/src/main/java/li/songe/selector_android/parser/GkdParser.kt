package li.songe.selector_android.parser

open class GkdParser<T>(
    val prefix: String = "",
    private val invokeTemp: (source: String, offset: Int, prefix: String) -> GkdParserResult<T>
) : (String, Int) -> GkdParserResult<T> {
    override fun invoke(source: String, offset: Int) = invokeTemp(source, offset, prefix)
}