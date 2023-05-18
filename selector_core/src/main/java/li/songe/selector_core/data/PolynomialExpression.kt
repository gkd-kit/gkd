package li.songe.selector_core.data

import li.songe.selector_core.Node

/**
 * an+b
 */
data class PolynomialExpression(val a: Int = 0, val b: Int = 1) {

    override fun toString(): String {
        if (a == 0 && b == 0) return "0"
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

    /**
     *  [nth-child](https://developer.mozilla.org/zh-CN/docs/Web/CSS/:nth-child)
     */
    val b1 = b - 1

    val traversal: (Sequence<Node?>) -> Sequence<Node?> =
        if (a <= 0 && b <= 0) ({ emptySequence() })
        else ({ sequence ->
            sequence.filterIndexed { x, _ -> (x - b1) % a == 0 && (x - b1) / a > 0 }
        })

    val isConstant = a == 0
}

// 3n+1, 1,4,7
// -n+9, 9,8,7,...,1
// an+b=x, n=(x-b)/a
