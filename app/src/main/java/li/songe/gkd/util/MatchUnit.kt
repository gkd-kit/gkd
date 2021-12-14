package li.songe.gkd.util

data class MatchUnit(
    val className: String,
    val attributeSelectorList: List<AttributeSelector>,
    var relationUnit: RelationUnit?,
) {
    companion object {
        fun parse(text: String): MatchUnit {
            val sb = StringBuilder()
            var markIndex = -1
            run loop@{
                text.forEachIndexed { index, c ->
                    if (c == '[') {
                        markIndex = index
                        return@loop
                    } else {
                        sb.append(c)
                    }
                }
            }
            val className = sb.toString()

            return MatchUnit(
                className,
                AttributeSelector.parseMulti(if (markIndex > 0) text.substring(markIndex) else ""),
                null
            )
        }

        fun stringify(matchUnit: MatchUnit): String {
            TODO()
        }
    }
}
