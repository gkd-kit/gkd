package li.songe.node_selector.selector

/**
 * 属性选择器
 */
data class PropertySelector(
    val name: String,
    val expressionList: List<BinaryExpression>
) {
    override fun toString() = "${name}${expressionList.joinToString("")}"
    sealed class Operator(private val key: String) {
        abstract fun compare(a: Any?, b: Any?): Boolean
        abstract fun acceptValue(a: Any?): Boolean

        object More : Operator(">") {
            override fun compare(a: Any?, b: Any?) =
                (a is Int && b is Int && a > b) || (a is Float && b is Int && a > b) || (a is Float && b is Float && a > b) || (a is Int && b is Float && a > b)
            override fun acceptValue(a: Any?) = a is Int || a is Float
        }

        object Less : Operator("<") {
            override fun compare(a: Any?, b: Any?) =
                (a is Int && b is Int && a < b) || (a is Float && b is Int && a < b) || (a is Float && b is Float && a < b) || (a is Int && b is Float && a < b)

            override fun acceptValue(a: Any?) = a is Int || a is Float
        }

        object Equal : Operator("=") {
            override fun compare(a: Any?, b: Any?) = a == b
            override fun acceptValue(a: Any?) =
                a is Int? || a is String? || a is Boolean? || a is Float?
        }

        object NotEqual : Operator("!=") {
            override fun compare(a: Any?, b: Any?) = a != b
            override fun acceptValue(a: Any?) =
                a is Int? || a is String? || a is Boolean? || a is Float?
        }

        object MoreEqual : Operator(">=") {
            override fun compare(a: Any?, b: Any?) =
                (a is Int && b is Int && a >= b) || (a is Float && b is Int && a >= b) || (a is Float && b is Float && a >= b) || (a is Int && b is Float && a >= b)

            override fun acceptValue(a: Any?) = a is Int || a is Float
        }

        object LessEqual : Operator("<=") {
            override fun compare(a: Any?, b: Any?) =
                (a is Int && b is Int && a <= b) || (a is Float && b is Int && a <= b) || (a is Float && b is Float && a <= b) || (a is Int && b is Float && a <= b)

            override fun acceptValue(a: Any?) = a is Int || a is Float
        }

        object Include : Operator("*=") {
            override fun compare(a: Any?, b: Any?) = (a is String && b is String && a.contains(b))
            override fun acceptValue(a: Any?) = a is String
        }

        object Start : Operator("^=") {
            override fun compare(a: Any?, b: Any?) = (a is String && b is String && a.startsWith(b))
            override fun acceptValue(a: Any?) = a is String
        }

        object End : Operator("$=") {
            override fun compare(a: Any?, b: Any?) = (a is String && b is String && a.endsWith(b))
            override fun acceptValue(a: Any?) = a is String
        }

        override fun toString() = key
    }

    data class BinaryExpression(val name: String, val operator: Operator, val value: Any?) {
        fun compare(otherValue: Any?) = operator.compare(otherValue, value)
        override fun toString() = "[${name}${operator}${
            if (value is String) {
                "`${value.replace("`", "\\`")}`"
            } else {
                value
            }
        }]"
    }

}
