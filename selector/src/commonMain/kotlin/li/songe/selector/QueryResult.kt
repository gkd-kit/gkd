package li.songe.selector

import li.songe.selector.unit.LogicalSelectorExpression
import li.songe.selector.unit.NotSelectorExpression
import li.songe.selector.unit.SelectorExpression
import li.songe.selector.unit.UnitSelectorExpression
import kotlin.js.JsExport

@JsExport
sealed class QueryResult<T> {
    abstract val context: QueryContext<T>
    abstract val expression: SelectorExpression
    abstract val targetIndex: Int
    abstract val matched: Boolean

    @Suppress("unused")
    val target: T
        get() = context.get(targetIndex).current

    data class UnitResult<T>(
        override val context: QueryContext<T>,
        override val expression: UnitSelectorExpression,
        override val targetIndex: Int,
    ) : QueryResult<T>() {
        override val matched: Boolean
            get() = context.matched
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
    }
}
