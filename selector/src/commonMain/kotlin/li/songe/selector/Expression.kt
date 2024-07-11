package li.songe.selector

import kotlin.js.JsExport

@JsExport
sealed class Expression : Position {
    abstract fun <T> match(
        context: Context<T>,
        transform: Transform<T>,
    ): Boolean

    abstract val binaryExpressions: Array<BinaryExpression>
}
