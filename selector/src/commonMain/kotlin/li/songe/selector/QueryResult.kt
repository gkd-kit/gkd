package li.songe.selector

import li.songe.selector.connect.PolynomialExpression
import li.songe.selector.unit.LogicalSelectorExpression
import li.songe.selector.unit.NotSelectorExpression
import li.songe.selector.unit.SelectorExpression
import li.songe.selector.unit.UnitSelectorExpression
import kotlin.js.JsExport

@Suppress("unused")
@JsExport
sealed class QueryResult<T> {
    abstract val context: QueryContext<T>
    abstract val expression: SelectorExpression
    abstract val targetIndex: Int
    abstract val matched: Boolean

    abstract val unitResults: List<UnitResult<T>>

    val target: T
        get() = context.get(targetIndex).current

    data class UnitResult<T>(
        override val context: QueryContext<T>,
        override val expression: UnitSelectorExpression,
        override val targetIndex: Int,
    ) : QueryResult<T>() {
        override val matched: Boolean
            get() = context.matched
        override val unitResults: List<UnitResult<T>>
            get() = if (matched) listOf(this) else emptyList()

        fun getNodeConnectPath(transform: Transform<T>): List<QueryPath<T>> {
            val contextList = context.toContextList().reversed()
            if (contextList.size <= 1) {
                return emptyList()
            }
            val connectSize = expression.connectWrappers.count()
            if (connectSize != contextList.size - 1) {
                error("Connect size not match")
            }
            val list = mutableListOf<QueryPath<T>>()
            var current = expression.propertyWrapper
            var i = 0
            val polynomialExpression = PolynomialExpression(a = 1, b = 0)
            while (true) {
                current.to ?: break
                val offset = current.to.segment.run {
                    operator
                        .traversal(contextList[i], transform, polynomialExpression)
                        .indexOf(contextList[i + 1].current)
                }
                require(offset >= 0)
                list.add(
                    QueryPath(
                        propertyWrapper = current,
                        connectWrapper = current.to,
                        offset = offset,
                        source = contextList[i].current,
                        target = contextList[i + 1].current,
                    )
                )
                current = current.to.to
                i++
            }
            return list
        }
    }

    data class AndResult<T>(
        override val expression: LogicalSelectorExpression,
        val left: QueryResult<T>,
        val right: QueryResult<T>? = null,
    ) : QueryResult<T>() {
        override val matched: Boolean
            get() = left.matched && right?.matched == true
        override val context: QueryContext<T>
            get() = right?.context ?: error("No matched result")
        override val targetIndex: Int
            get() = right?.targetIndex ?: error("No matched result")
        override val unitResults: List<UnitResult<T>>
            get() = if (right != null) {
                left.unitResults + right.unitResults
            } else {
                left.unitResults
            }
    }

    data class OrResult<T>(
        override val expression: LogicalSelectorExpression,
        val left: QueryResult<T>,
        val right: QueryResult<T>? = null,
    ) : QueryResult<T>() {
        override val matched: Boolean
            get() = left.matched || right?.matched == true
        override val context: QueryContext<T>
            get() = (if (left.matched) left else right)?.context ?: error("No matched result")
        override val targetIndex: Int
            get() = (if (left.matched) left else right)?.targetIndex ?: error("No matched result")
        override val unitResults: List<UnitResult<T>>
            get() = if (right != null) {
                left.unitResults + right.unitResults
            } else {
                left.unitResults
            }
    }

    data class NotResult<T>(
        override val expression: NotSelectorExpression,
        val originalContext: QueryContext<T>,
        val result: QueryResult<T>,
    ) : QueryResult<T>() {
        override val matched: Boolean
            get() = !result.matched
        override val context: QueryContext<T>
            get() = originalContext
        override val targetIndex: Int
            get() = 0
        override val unitResults: List<UnitResult<T>>
            get() = result.unitResults
    }
}
