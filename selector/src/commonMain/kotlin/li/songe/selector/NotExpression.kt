package li.songe.selector

import kotlin.js.JsExport

@JsExport
data class NotExpression(
    override val start: Int,
    val expression: Expression
) : Expression() {
    override val end: Int
        get() = expression.end

    override fun <T> match(
        context: Context<T>,
        transform: Transform<T>,
    ): Boolean {
        return !expression.match(context, transform)
    }

    override val binaryExpressions: Array<BinaryExpression>
        get() = expression.binaryExpressions

    override fun stringify(): String {
        return "!(${expression.stringify()})"
    }
}
