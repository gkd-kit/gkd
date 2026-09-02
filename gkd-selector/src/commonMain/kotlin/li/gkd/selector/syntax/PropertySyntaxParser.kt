package li.gkd.selector.syntax

import li.gkd.selector.SelectorPositionKind
import li.gkd.selector.LogicalOperator
import li.gkd.selector.property.ComparisonExpression
import li.gkd.selector.property.CompareOperator
import li.gkd.selector.property.LogicalExpression
import li.gkd.selector.property.NotExpression
import li.gkd.selector.property.PropertyExpression
import li.gkd.selector.property.PropertySelector
import li.gkd.selector.property.RegexCompileResult
import li.gkd.selector.property.ValueExpression
import li.gkd.selector.property.compileRegex

internal class PropertySyntaxParser(
    private val context: ParserContext,
) {
    private val cursor = context.cursor

    private class PositionedValue<T : Any>(
        val value: T,
        val start: Int,
        val end: Int,
    )

    private class ExpressionFrame(
        val start: Int,
        val negated: Boolean,
        val values: MutableList<PositionedValue<PropertyExpression>> = mutableListOf(),
        val operators: MutableList<LogicalOperator> = mutableListOf(),
    )

    private sealed interface ValueFrame

    private class CallFrame(
        val callee: ValueExpression.Variable,
        val start: Int,
        val arguments: MutableList<ValueExpression> = mutableListOf(),
    ) : ValueFrame

    private class GroupFrame : ValueFrame

    fun readPropertySelector(): PropertySelector =
        context.positioned(SelectorPositionKind.PropertySelector) {
            val target = cursor.current == '@'
            if (target) cursor.readChar('@')
            val name = if (cursor.current == '[') "" else readPropertyName()
            if (name.isEmpty()) cursor.expectChar('[')
            val filters = mutableListOf<PropertyExpression>()
            while (cursor.current == '[') filters.add(readFilter())
            PropertySelector(target, name, filters)
        }

    private fun readPropertyName(): String {
        val start = cursor.index
        if (cursor.current == '*') {
            cursor.index++
            return "*"
        }
        cursor.expectOneOf(IDENTIFIER_START_CHARS, "property name")
        cursor.index++
        while (true) {
            when (cursor.current) {
                '.' -> {
                    cursor.index++
                    cursor.expectOneOf(IDENTIFIER_START_CHARS, "property name segment")
                    cursor.index++
                }

                else -> if (cursor.current.isOneOf(IDENTIFIER_PART_CHARS)) {
                    cursor.index++
                } else {
                    return cursor.source.substring(start, cursor.index)
                }
            }
        }
    }

    private fun readFilter(): PropertyExpression {
        val start = cursor.index
        cursor.readChar('[')
        cursor.readWhitespace()
        val expression = readExpression()
        cursor.readWhitespace()
        cursor.readChar(']')
        context.recordPosition(SelectorPositionKind.Property, start)
        return expression
    }

    private fun readExpression(): PropertyExpression {
        val frames = mutableListOf(ExpressionFrame(cursor.index, negated = false))

        while (true) {
            val frame = frames.last()
            if (frame.values.size == frame.operators.size) {
                when (cursor.current) {
                    '(' -> {
                        val start = cursor.index
                        cursor.readChar('(')
                        cursor.readWhitespace()
                        frames.add(ExpressionFrame(start, negated = false))
                    }

                    '!' -> {
                        val start = cursor.index
                        cursor.readChar('!')
                        cursor.readChar('(')
                        cursor.readWhitespace()
                        frames.add(ExpressionFrame(start, negated = true))
                    }

                    else -> {
                        val start = cursor.index
                        frame.values.add(
                            PositionedValue(
                                value = readBinaryExpression(),
                                start = start,
                                end = cursor.index,
                            ),
                        )
                    }
                }
                continue
            }

            val whitespaceStart = cursor.index
            cursor.readWhitespace()
            val operator = peekLogicalOperator()
            if (operator != null) {
                cursor.index += operator.key.length
                reduceOperators(frame, operator)
                frame.operators.add(operator)
                cursor.readWhitespace()
                continue
            }

            if (frames.size > 1 && cursor.current == ')') {
                ensureComplete(frame)
                val term = finish(frame)
                cursor.readChar(')')
                frames.removeAt(frames.lastIndex)
                val groupedTerm: PositionedValue<PropertyExpression> = if (frame.negated) {
                    val value = NotExpression(term.value)
                    context.record(value, SelectorPositionKind.NegatedCondition, frame.start)
                    PositionedValue(value, frame.start, cursor.index)
                } else {
                    PositionedValue(term.value, frame.start, cursor.index)
                }
                frames.last().values.add(groupedTerm)
                continue
            }

            if (frames.size > 1) cursor.errorExpected("')'")
            cursor.index = whitespaceStart
            ensureComplete(frame)
            return finish(frame).value
        }
    }

    private fun ensureComplete(frame: ExpressionFrame) {
        if (frame.values.isEmpty() || frame.values.size != frame.operators.size + 1) {
            cursor.errorExpected("property expression")
        }
    }

    private fun finish(frame: ExpressionFrame): PositionedValue<PropertyExpression> {
        while (frame.operators.isNotEmpty()) reduceLastOperator(frame)
        return frame.values.single()
    }

    private fun reduceOperators(frame: ExpressionFrame, incoming: LogicalOperator) {
        while (
            frame.operators.isNotEmpty() &&
            frame.operators.last().precedence >= incoming.precedence
        ) {
            reduceLastOperator(frame)
        }
    }

    private fun reduceLastOperator(frame: ExpressionFrame) {
        val right = frame.values.removeAt(frame.values.lastIndex)
        val left = frame.values.removeAt(frame.values.lastIndex)
        val operator = frame.operators.removeAt(frame.operators.lastIndex)
        val value = LogicalExpression(left.value, operator, right.value)
        context.record(
            value,
            SelectorPositionKind.LogicalCondition,
            left.start,
            right.end,
        )
        frame.values.add(PositionedValue(value, left.start, right.end))
    }

    private fun peekLogicalOperator(): LogicalOperator? =
        LogicalOperator.parseOrder.firstOrNull {
            cursor.source.startsWith(it.key, cursor.index)
        }

    private fun readBinaryExpression(): ComparisonExpression =
        context.positioned(SelectorPositionKind.Comparison) {
            val left = readValueExpression()
            cursor.readWhitespace()
            val operator = readCompareOperator()
            cursor.readWhitespace()
            val regexStart = cursor.index
            val right = readValueExpression()
            when (operator) {
                is CompareOperator.RegexOperator -> {
                    if (right !is ValueExpression.StringLiteral) {
                        cursor.index = regexStart
                        cursor.errorExpected("regular expression string literal")
                    }
                    when (val result = right.value.compileRegex()) {
                        is RegexCompileResult.Success -> ComparisonExpression.RegexComparison(
                            left = left,
                            regexOperator = operator,
                            right = right,
                            matches = result.matches,
                        )

                        is RegexCompileResult.Failure -> {
                            val regexEnd = cursor.index
                            cursor.index = regexStart
                            cursor.errorExpected(
                                expected = "valid regular expression string",
                                range = li.gkd.selector.SourceRange(regexStart, regexEnd),
                                detail = result.detail,
                            )
                        }
                    }
                }

                is CompareOperator.ValueOperator -> ComparisonExpression.ValueComparison(left, operator, right)
            }
        }

    private fun readCompareOperator(): CompareOperator {
        val operator = CompareOperator.parseOrder.firstOrNull {
            cursor.source.startsWith(it.key, cursor.index)
        } ?: cursor.errorExpected("comparison operator")
        cursor.index += operator.key.length
        return operator
    }

    private fun readValueExpression(): ValueExpression {
        val frames = mutableListOf<ValueFrame>()
        var term: PositionedValue<ValueExpression>? = null

        while (true) {
            if (term == null) {
                if (cursor.current == '(') {
                    cursor.readChar('(')
                    cursor.readWhitespace()
                    if (!cursor.current.isOneOf(VALUE_START_CHARS)) {
                        cursor.errorExpected("value")
                    }
                    frames.add(GroupFrame())
                    continue
                }
                term = readValuePrimary()
            }
            val currentTerm = term
            val variable = currentTerm.value as? ValueExpression.Variable
            if (variable != null) {
                val whitespaceStart = cursor.index
                cursor.readWhitespace()
                when (cursor.current) {
                    '.' -> {
                        cursor.readChar('.')
                        cursor.readWhitespace()
                        val property = readIdentifierName()
                        val value = ValueExpression.MemberExpression(variable, property)
                        context.record(
                            value,
                            SelectorPositionKind.MemberAccess,
                            currentTerm.start,
                        )
                        term = PositionedValue(value, currentTerm.start, cursor.index)
                        continue
                    }

                    '(' -> {
                        if (variable is ValueExpression.CallExpression) {
                            cursor.errorExpected("non-call-expression callable")
                        }
                        cursor.readChar('(')
                        cursor.readWhitespace()
                        if (cursor.current == ')') {
                            cursor.readChar(')')
                            val value = ValueExpression.CallExpression(variable, emptyList())
                            context.record(
                                value,
                                SelectorPositionKind.Call,
                                currentTerm.start,
                            )
                            term = PositionedValue(value, currentTerm.start, cursor.index)
                            continue
                        }
                        if (!cursor.current.isOneOf(VALUE_START_CHARS)) {
                            cursor.errorExpected("call argument")
                        }
                        frames.add(CallFrame(variable, currentTerm.start))
                        term = null
                        continue
                    }

                    else -> cursor.index = whitespaceStart
                }
            }

            val completed = term
            while (true) {
                if (frames.isEmpty()) return completed.value
                when (val frame = frames.last()) {
                    is GroupFrame -> {
                        cursor.readWhitespace()
                        cursor.readChar(')')
                        frames.removeAt(frames.lastIndex)
                        if (frames.isEmpty()) return completed.value
                    }

                    is CallFrame -> {
                        frame.arguments.add(completed.value)
                        cursor.readWhitespace()
                        when (cursor.current) {
                            ',' -> {
                                cursor.readChar(',')
                                cursor.readWhitespace()
                                if (!cursor.current.isOneOf(VALUE_START_CHARS)) {
                                    cursor.errorExpected("value after comma")
                                }
                                term = null
                            }

                            ')' -> {
                                cursor.readChar(')')
                                frames.removeAt(frames.lastIndex)
                                val value = ValueExpression.CallExpression(
                                    frame.callee,
                                    frame.arguments,
                                )
                                context.record(value, SelectorPositionKind.Call, frame.start)
                                term = PositionedValue(value, frame.start, cursor.index)
                            }

                            else -> cursor.errorExpected("',' or ')' in call arguments")
                        }
                        break
                    }
                }
            }
        }
    }

    private fun readValuePrimary(): PositionedValue<ValueExpression> {
        val start = cursor.index
        cursor.expectOneOf(VALUE_PRIMARY_START_CHARS, "value")
        val value = when {
            cursor.readLiteral("null") -> ValueExpression.NullLiteral().also {
                context.record(it, SelectorPositionKind.NullLiteral, start)
            }

            cursor.readLiteral("false") -> ValueExpression.BooleanLiteral(false).also {
                context.record(it, SelectorPositionKind.BooleanLiteral, start)
            }

            cursor.readLiteral("true") -> ValueExpression.BooleanLiteral(true).also {
                context.record(it, SelectorPositionKind.BooleanLiteral, start)
            }

            cursor.current.isOneOf("-$DIGIT_CHARS") ->
                ValueExpression.IntLiteral(cursor.readInt()).also {
                    context.record(it, SelectorPositionKind.IntLiteral, start)
                }

            cursor.current.isOneOf(STRING_QUOTE_CHARS) ->
                ValueExpression.StringLiteral(cursor.readString()).also {
                    context.record(it, SelectorPositionKind.StringLiteral, start)
                }

            else -> ValueExpression.Identifier(readIdentifierName()).also {
                context.record(it, SelectorPositionKind.Identifier, start)
            }
        }
        return PositionedValue(value, start, cursor.index)
    }

    private fun readIdentifierName(): String {
        val start = cursor.index
        cursor.expectOneOf(IDENTIFIER_START_CHARS, "identifier")
        cursor.index++
        while (cursor.current.isOneOf(IDENTIFIER_PART_CHARS)) cursor.index++
        val value = cursor.source.substring(start, cursor.index)
        if (value == "null" || value == "false" || value == "true") {
            cursor.index = start
            cursor.errorExpected("non-keyword identifier")
        }
        return value
    }
}
