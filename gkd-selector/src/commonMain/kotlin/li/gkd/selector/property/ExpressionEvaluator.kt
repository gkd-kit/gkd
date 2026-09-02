package li.gkd.selector.property

import li.gkd.selector.MatchContext
import li.gkd.selector.NodeAdapter
import li.gkd.selector.LogicalOperator

private class EvaluationFrame(
    val expression: PropertyExpression,
    var state: Int = 0,
)

internal fun <T : Any> PropertyExpression.evaluate(
    context: MatchContext<T>,
    adapter: NodeAdapter<T>,
): Boolean {
    if (this is ComparisonExpression) {
        return match(context, adapter)
    }
    val stack = mutableListOf<EvaluationFrame>()
    var current: PropertyExpression = this
    var result: Boolean

    while (true) {
        when (current) {
            is ComparisonExpression -> result = current.match(context, adapter)
            is NotExpression -> {
                stack.add(EvaluationFrame(current))
                current = current.expression
                continue
            }

            is LogicalExpression -> {
                stack.add(EvaluationFrame(current))
                current = current.left
                continue
            }
        }

        while (true) {
            if (stack.isEmpty()) return result
            val frame = stack.last()
            when (val expression = frame.expression) {
                is ComparisonExpression -> error("Binary expressions are evaluated directly")
                is NotExpression -> {
                    stack.removeAt(stack.lastIndex)
                    result = !result
                }

                is LogicalExpression -> {
                    if (frame.state == 0) {
                        val shortCircuited = when (expression.operator) {
                            LogicalOperator.And -> !result
                            LogicalOperator.Or -> result
                        }
                        if (shortCircuited) {
                            stack.removeAt(stack.lastIndex)
                        } else {
                            frame.state = 1
                            current = expression.right
                            break
                        }
                    } else {
                        stack.removeAt(stack.lastIndex)
                    }
                }
            }
        }
    }
}

internal fun PropertyExpression.collectBinaryExpressions(): List<ComparisonExpression> {
    val result = mutableListOf<ComparisonExpression>()
    val stack = mutableListOf<PropertyExpression>(this)
    while (stack.isNotEmpty()) {
        when (val expression = stack.removeAt(stack.lastIndex)) {
            is ComparisonExpression -> result.add(expression)
            is NotExpression -> stack.add(expression.expression)
            is LogicalExpression -> {
                stack.add(expression.right)
                stack.add(expression.left)
            }
        }
    }
    return result
}

internal fun PropertyExpression.collectOrFastQueryExpressions(): List<ComparisonExpression>? {
    val result = mutableListOf<ComparisonExpression>()
    val stack = mutableListOf<PropertyExpression>(this)
    while (stack.isNotEmpty()) {
        when (val expression = stack.removeAt(stack.lastIndex)) {
            is ComparisonExpression -> result.add(expression)
            is NotExpression -> return null
            is LogicalExpression -> {
                if (expression.operator != LogicalOperator.Or) return null
                stack.add(expression.right)
                stack.add(expression.left)
            }
        }
    }
    return result
}

internal fun PropertyExpression.usesPreviousContext(): Boolean {
    val stack = mutableListOf(this)
    while (stack.isNotEmpty()) {
        when (val expression = stack.removeAt(stack.lastIndex)) {
            is ComparisonExpression -> if (
                expression.left.usesPreviousContext() ||
                expression.right.usesPreviousContext()
            ) {
                return true
            }

            is NotExpression -> stack.add(expression.expression)
            is LogicalExpression -> {
                stack.add(expression.right)
                stack.add(expression.left)
            }
        }
    }
    return false
}

private fun ValueExpression.usesPreviousContext(): Boolean {
    val stack = mutableListOf<ValueExpression>(this)
    while (stack.isNotEmpty()) {
        when (val expression = stack.removeAt(stack.lastIndex)) {
            is ValueExpression.LiteralExpression -> Unit
            is ValueExpression.Identifier -> if (
                expression.role == ValueExpression.IdentifierRole.Previous
            ) {
                return true
            }
            is ValueExpression.MemberExpression -> stack.add(expression.object0)
            is ValueExpression.CallExpression -> {
                val callee = expression.callee
                if (
                    (callee is ValueExpression.Identifier && callee.name == "getPrev") ||
                    (callee is ValueExpression.MemberExpression && callee.property == "getPrev")
                ) {
                    return true
                }
                stack.add(callee)
                for (argument in expression.arguments) stack.add(argument)
            }
        }
    }
    return false
}
