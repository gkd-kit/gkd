package li.songe.node_selector.parser

class ParserException(private val source: String, val i: Int, val expectValue: String? = "") :
    Exception() {
    override val message: String
        get() {
            return if (expectValue != null) {
                when {
                    expectValue.isEmpty() -> {
                        return "source: $source, index:${i}, expect:any, actual:${source.getOrNull(i) ?: "array bound"}"
                    }
                    expectValue.length == 1 -> {
                        return "source: $source, index:${i}, expect:${expectValue}, actual:${source.getOrNull(i) ?: "array bound"}"
                    }
                    else -> {
                        val list = expectValue.toList().map {
                            when (it) {
                                '\n' -> "\\n"
                                '\t' -> "\\t"
                                '\r' -> "\\r"
                                '\u0020' -> "\\u0020"
                                else -> it.toString()
                            }
                        }
                        return "source: $source, index:${i}, expect one of:${list.joinToString(",")}, actual:${
                            source.getOrNull(
                                i
                            ) ?: "array bound"
                        }"
                    }
                }
            } else {
                "source: $source, index:${i}, expect eof, actual:${
                    source.getOrNull(
                        i
                    ) ?: "array bound"
                }"
            }
        }
}