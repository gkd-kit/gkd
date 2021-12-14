package li.songe.gkd.util

data class RelationUnit(val to: MatchUnit, val operator: Operator) {
    sealed class Operator {
        object Parent : Operator()
        object Ancestor : Operator()
        data class Brother(val offset: Int) : Operator()

        override fun toString(): String {
            return when (this) {
                Ancestor -> ">>"
                is Brother -> {
                    assert(offset != 0)
                    return offset.toString()
                }
                Parent -> ">"
                else -> throw NotImplementedError()
            }
        }
    }

    companion object {
        fun parse(text: String): Operator {
            if (text == ">>") {
                return Operator.Ancestor
            } else if (text == ">") {
                return Operator.Parent
            }
            val i = when (text) {
                "+" -> {
                    1
                }
                "-" -> {
                    -1
                }
                else -> {
                    text.toIntOrNull()
                }
            }
            if (i != null) {
                return Operator.Brother(i)
            }
            throw Error("invalid operator: $text")
        }

        fun stringify(operator: Operator): String {
            return when (operator) {
                Operator.Parent -> ">"
                Operator.Ancestor -> "\u0020"
                is Operator.Brother -> when {
                    operator.offset > 0 -> {
                        "+" + operator.offset.toString()
                    }
                    operator.offset < 0 -> {
                        operator.offset.toString()
                    }
                    else -> {
                        throw Error("operator.offset: expect no-zero, got 0")
                    }
                }
                else -> throw NotImplementedError()
            }
        }
    }
}
