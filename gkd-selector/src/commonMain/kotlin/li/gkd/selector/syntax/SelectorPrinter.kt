package li.gkd.selector.syntax

import li.gkd.selector.relation.RelationExpression
import li.gkd.selector.relation.RelationSelector
import li.gkd.selector.relation.PolynomialExpression
import li.gkd.selector.relation.TupleExpression
import li.gkd.selector.engine.LogicalSelectorExpression
import li.gkd.selector.engine.NotSelectorExpression
import li.gkd.selector.engine.SelectorExpression
import li.gkd.selector.engine.UnitSelectorExpression
import li.gkd.selector.property.ComparisonExpression
import li.gkd.selector.property.LogicalExpression
import li.gkd.selector.property.NotExpression
import li.gkd.selector.property.PropertyExpression
import li.gkd.selector.property.PropertySelector
import li.gkd.selector.property.ValueExpression

internal object SelectorPrinter {
    private sealed interface Action

    private class Text(val value: String) : Action

    private class SelectorAction(
        val expression: SelectorExpression,
        val wrapped: Boolean = false,
    ) : Action

    private class UnitAction(val expression: UnitSelectorExpression) : Action

    private class PropertySelectorAction(val selector: PropertySelector) : Action

    private class PropertyExpressionAction(
        val expression: PropertyExpression,
        val wrapped: Boolean = false,
    ) : Action

    private class ValueAction(val expression: ValueExpression) : Action

    private class RelationSelectorAction(val selector: RelationSelector) : Action

    private class RelationExpressionAction(val expression: RelationExpression) : Action

    fun render(expression: SelectorExpression): String = render(SelectorAction(expression))

    fun render(expression: PropertyExpression): String = render(PropertyExpressionAction(expression))

    fun render(expression: ValueExpression): String = render(ValueAction(expression))

    private fun render(initial: Action): String = buildString {
        val stack = mutableListOf<Action>(initial)
        while (stack.isNotEmpty()) {
            when (val action = stack.removeAt(stack.lastIndex)) {
                is Text -> append(action.value)
                is SelectorAction -> appendSelector(action, stack)
                is UnitAction -> appendUnit(action.expression, stack)
                is PropertySelectorAction -> appendPropertySelector(action.selector, stack)
                is PropertyExpressionAction -> appendPropertyExpression(action, stack)
                is ValueAction -> appendValue(action.expression, stack)
                is RelationSelectorAction -> appendRelationSelector(action.selector, stack)
                is RelationExpressionAction -> appendRelationExpression(action.expression)
            }
        }
    }

    private fun StringBuilder.appendSelector(
        action: SelectorAction,
        stack: MutableList<Action>,
    ) {
        if (action.wrapped) {
            append('(')
            stack.add(Text(")"))
        }
        when (val expression = action.expression) {
            is UnitSelectorExpression -> stack.add(UnitAction(expression))
            is NotSelectorExpression -> {
                append("!(")
                stack.add(Text(")"))
                stack.add(SelectorAction(expression.expression))
            }

            is LogicalSelectorExpression -> {
                stack.add(
                    SelectorAction(
                        expression.right,
                        expression.right is UnitSelectorExpression ||
                                expression.right is LogicalSelectorExpression &&
                                expression.right.operator != expression.operator,
                    ),
                )
                stack.add(Text(" ${expression.operator.key} "))
                stack.add(
                    SelectorAction(
                        expression.left,
                        expression.left is UnitSelectorExpression ||
                                expression.left is LogicalSelectorExpression &&
                                expression.left.operator != expression.operator,
                    ),
                )
            }
        }
    }

    private fun StringBuilder.appendUnit(
        expression: UnitSelectorExpression,
        stack: MutableList<Action>,
    ) {
        for (index in expression.propertySelectors.lastIndex downTo 1) {
            stack.add(PropertySelectorAction(expression.propertySelectors[index]))
            val relation = expression.relations[index - 1]
            if (!relation.isMatchAnyAncestor) {
                stack.add(Text(" "))
                stack.add(RelationSelectorAction(relation))
            }
            stack.add(Text(" "))
        }
        stack.add(PropertySelectorAction(expression.propertySelectors.first()))
    }

    private fun StringBuilder.appendPropertySelector(
        selector: PropertySelector,
        stack: MutableList<Action>,
    ) {
        if (selector.at) append('@')
        append(selector.name)
        for (index in selector.filters.lastIndex downTo 0) {
            stack.add(Text("]"))
            stack.add(PropertyExpressionAction(selector.filters[index]))
            stack.add(Text("["))
        }
    }

    private fun StringBuilder.appendPropertyExpression(
        action: PropertyExpressionAction,
        stack: MutableList<Action>,
    ) {
        if (action.wrapped) {
            append('(')
            stack.add(Text(")"))
        }
        when (val expression = action.expression) {
            is ComparisonExpression -> {
                stack.add(ValueAction(expression.right))
                stack.add(Text(expression.operator.key))
                stack.add(ValueAction(expression.left))
            }

            is NotExpression -> {
                append("!(")
                stack.add(Text(")"))
                stack.add(PropertyExpressionAction(expression.expression))
            }

            is LogicalExpression -> {
                stack.add(
                    PropertyExpressionAction(
                        expression.right,
                        expression.right is LogicalExpression &&
                                expression.right.operator != expression.operator,
                    ),
                )
                stack.add(Text(" ${expression.operator.key} "))
                stack.add(
                    PropertyExpressionAction(
                        expression.left,
                        expression.left is LogicalExpression &&
                                expression.left.operator != expression.operator,
                    ),
                )
            }
        }
    }

    private fun StringBuilder.appendValue(
        expression: ValueExpression,
        stack: MutableList<Action>,
    ) {
        when (expression) {
            is ValueExpression.NullLiteral -> append("null")
            is ValueExpression.BooleanLiteral -> append(expression.value)
            is ValueExpression.IntLiteral -> append(expression.value)
            is ValueExpression.StringLiteral -> append(escapeString(expression.value))
            is ValueExpression.Identifier -> append(expression.name)
            is ValueExpression.MemberExpression -> {
                stack.add(Text(".${expression.property}"))
                stack.add(ValueAction(expression.object0))
            }

            is ValueExpression.CallExpression -> {
                stack.add(Text(")"))
                for (index in expression.arguments.lastIndex downTo 0) {
                    stack.add(ValueAction(expression.arguments[index]))
                    if (index > 0) stack.add(Text(","))
                }
                stack.add(Text("("))
                stack.add(ValueAction(expression.callee))
            }
        }
    }

    private fun StringBuilder.appendRelationSelector(
        selector: RelationSelector,
        stack: MutableList<Action>,
    ) {
        if (selector.isMatchAnyAncestor) return
        append(selector.operator.key)
        stack.add(RelationExpressionAction(selector.relationExpression))
    }

    private fun StringBuilder.appendRelationExpression(expression: RelationExpression) {
        when (expression) {
            is TupleExpression -> when {
                expression.numbers.size == 1 && expression.numbers.first() == 1 -> Unit
                expression.numbers.size == 1 -> append(expression.numbers.first())
                else -> {
                    append('(')
                    expression.numbers.forEachIndexed { index, number ->
                        if (index > 0) append(',')
                        append(number)
                    }
                    append(')')
                }
            }

            is PolynomialExpression -> appendPolynomial(expression)
        }
    }

    private fun StringBuilder.appendPolynomial(expression: PolynomialExpression) {
        val a = expression.a
        val b = expression.b
        when {
            a > 0 && b > 0 -> append(if (a == 1) "(n+$b)" else "(${a}n+$b)")
            a < 0 && b > 0 -> append(if (a == -1) "($b-n)" else "($b${a}n)")
            b == 0 -> when {
                a == 1 -> append('n')
                a > 0 -> append("${a}n")
                else -> append("(${a}n)")
            }

            a == 0 -> when {
                b == 1 -> Unit
                b > 0 -> append(b)
                else -> append("($b)")
            }

            else -> append("(${a}n${if (b >= 0) "+" else ""}$b)")
        }
    }
}

private fun escapeString(value: String, wrapChar: Char = '"'): String {
    val result = StringBuilder(value.length + 2)
    result.append(wrapChar)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (
            char in '\uD800'..'\uDBFF' &&
            value.getOrNull(index + 1) in '\uDC00'..'\uDFFF'
        ) {
            result.append(char)
            result.append(value[index + 1])
            index += 2
            continue
        }
        val escapeChar = when (char) {
            wrapChar -> wrapChar
            '\n' -> 'n'
            '\r' -> 'r'
            '\t' -> 't'
            '\b' -> 'b'
            '\\' -> '\\'
            else -> null
        }
        if (escapeChar != null) {
            result.append("\\" + escapeChar)
        } else {
            when (char.code) {
                in 0..0xf -> result.append("\\x0" + char.code.toString(16))
                in 0x10..0x1f -> result.append("\\x" + char.code.toString(16))
                in 0xd800..0xdfff ->
                    result.append("\\u" + char.code.toString(16).padStart(4, '0'))
                else -> result.append(char)
            }
        }
        index++
    }
    result.append(wrapChar)
    return result.toString()
}
