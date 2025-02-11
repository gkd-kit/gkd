package li.songe.selector.property

import li.songe.selector.QueryContext
import li.songe.selector.Stringify
import li.songe.selector.Transform
import kotlin.js.JsExport

// for parser string token merge
internal sealed interface ExpressionToken

@JsExport
sealed class Expression : Stringify, ExpressionToken {
    abstract fun <T> match(
        context: QueryContext<T>,
        transform: Transform<T>,
    ): Boolean

    abstract fun getBinaryExpressionList(): Array<BinaryExpression>
}
