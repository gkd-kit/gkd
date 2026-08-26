package li.gkd.selector.property

import li.gkd.selector.QueryContext
import li.gkd.selector.Stringify
import li.gkd.selector.Transform
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
