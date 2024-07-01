package li.songe.selector

/**
 * an+b
 */
data class PolynomialExpression(val a: Int = 0, val b: Int = 1) : ConnectExpression() {

    override fun toString(): String {
        if (a > 0 && b > 0) {
            if (a == 1) {
                return "(n+$b)"
            }
            return "(${a}n+$b)"
        }
        if (a < 0 && b > 0) {
            if (a == -1) {
                return "($b-n)"
            }
            return "($b${a}n)"
        }
        if (b == 0) {
            if (a == 1) return "n"
            return if (a > 0) {
                "${a}n"
            } else {
                "(${a}n)"
            }
        }
        if (a == 0) {
            if (b == 1) return ""
            return if (b > 0) {
                b.toString()
            } else {
                "(${b})"
            }
        }
        val bOp = if (b >= 0) "+" else ""
        return "(${a}n${bOp}${b})"
    }

    private fun invalidValue(): Nothing {
        error("invalid PolynomialExpression: a=$a, b=$b")
    }

    override val minOffset = if (a > 0) {
        if (b > 0) {
            a + b
        } else if (b == 0) {
            a
        } else {
            // 2n-10 -> n>=6
            // 3n-10 -> n>=4
            // 3n-3 -> n>=2
            // 3n-1 -> n>=1
            // an+b>0 -> n>-b/a
            val minN = -b / a + 1
            a * minN + b
        }
    } else if (a == 0) {
        if (b > 0) {
            b
        } else {
            invalidValue()
        }
    } else {
        if (b > 0) {
            if (b <= -a) {
                invalidValue()
            } else {
                // -2n+9 -> (1_7,2_5,3_3,4_1) -> (1,3,5,7) -> 1
                // -3n+9 -> (1_6,2_3) -> (3,6)
                // -5n+7 -> (1_2) -> (2)
                val maxN = -b / a - if (b % a == 0) 1 else 0
                a * maxN + b
            }
        } else {
            invalidValue()
        }
    } - 1

    override val maxOffset = if (a > 0) {
        null
    } else if (a == 0) {
        if (b > 0) {
            b
        } else {
            invalidValue()
        }
    } else {
        if (b > 0) {
            if (b <= -a) {
                invalidValue()
            } else {
                a + b
            }
        } else {
            invalidValue()
        }
    } - 1

    private val isConstant = minOffset == maxOffset

    // (2n-1) -> (1,3,5) -> [0,2,4]
    override fun checkOffset(offset: Int): Boolean {
        if (isConstant) {
            return offset == minOffset
        }
        val y = (offset + 1) - b
        return y % a == 0 && y / a >= 1
    }

    private val innerGetOffset: (Int) -> Int = if (a > 0) {
        if (b > 0) {
            { i -> a * i + b }
        } else if (b == 0) {
            { i -> a * i + b }
        } else {
            val minN = -b / a + 1
            { i -> a * (minN + i) + b }
        }
    } else if (a == 0) {
        if (b > 0) {
            { i ->
                if (i != 0) {
                    invalidValue()
                }
                b
            }
        } else {
            invalidValue()
        }
    } else {
        if (b > 0) {
            if (b <= -a) {
                invalidValue()
            } else {
                // -2n+9 -> (1_7,2_5,3_3,4_1) -> (1,3,5,7) -> 1
                // -3n+9 -> (1_6,2_3) -> (3,6)
                // -5n+7 -> (1_2) -> (2)
                val maxN = -b / a - if (b % a == 0) 1 else 0
                { i -> a * (maxN - i) + b }
            }
        } else {
            invalidValue()
        }
    }

    override fun getOffset(i: Int) = innerGetOffset(i) - 1
}
