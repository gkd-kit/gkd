package li.songe.selector_android.selector

/**
 * 关系连接选择器
 */
data class CombinatorSelector(
    val operator: Operator = Operator.Ancestor,
    val polynomialExpression: PolynomialExpression = PolynomialExpression(mapOf(1 to 1))
) {
    override fun toString()="${operator}(${polynomialExpression})"

    sealed class Operator(private val key: String) {
        object ElderBrother : Operator("+")
        object YoungerBrother : Operator("-")
        object Ancestor : Operator(">")
        object Child : Operator("<")

        override fun toString() = key
    }

    data class PolynomialExpression(val power2coefficientMap: Map<Int, Int> = mapOf(0 to 1)) {
        fun calculate(n: Int = 1): Int {
            if (power2coefficientMap.isEmpty()) {
                return 1
            }
            var sum = 0
            power2coefficientMap.forEach { (power, coefficient) ->
                sum += coefficient * pow(n, power)
            }
            return sum
        }

        private fun pow(x: Int, y: Int): Int {
            assert(x >= 1 && y >= 0)
            if (x == 1 || y == 0) {
                return 1
            }
            var product = x
            repeat(y - 1) {
                product *= x
            }
            return product
        }

        override fun toString() = power2coefficientMap.map { (power, coefficient) ->
            if (coefficient == 0) {
                return@map "0"
            } else if (coefficient < 0) {
                ""
            } else {
                "+"
            } + coefficient.toString() + when (power) {
                0 -> {
                    ""
                }
                1 -> {
                    "n"
                }
                else -> {
                    "n^$power"
                }
            }
        }.joinToString("")

        val isConstant by lazy {
            power2coefficientMap.run {
                isEmpty() || (size == 1 && containsKey(0))
            }
        }


    }
}
