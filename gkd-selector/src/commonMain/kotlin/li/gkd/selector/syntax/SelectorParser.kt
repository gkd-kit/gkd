package li.gkd.selector.syntax

import li.gkd.selector.LogicalOperator
import li.gkd.selector.SelectorPositionKind
import li.gkd.selector.relation.RelationSelector
import li.gkd.selector.relation.PolynomialExpression
import li.gkd.selector.engine.LogicalSelectorExpression
import li.gkd.selector.engine.NotSelectorExpression
import li.gkd.selector.engine.SelectorExpression
import li.gkd.selector.engine.UnitSelectorExpression

internal class SelectorParser(
    source: String,
    positionRecorder: PositionRecorder? = null,
) {
    private val context = ParserContext(source, positionRecorder)
    private val cursor = context.cursor
    private val propertyParser = PropertySyntaxParser(context)
    private val relationParser = RelationSyntaxParser(context)

    private class SelectorTerm(
        val value: SelectorExpression,
        val grouped: Boolean,
        val start: Int,
        val end: Int,
    )

    private class ExpressionFrame(
        val start: Int,
        val negated: Boolean,
        val values: MutableList<SelectorTerm> = mutableListOf(),
        val operators: MutableList<LogicalOperator> = mutableListOf(),
    )

    fun readSelector(): SelectorExpression {
        val start = cursor.index
        cursor.readWhitespace()
        val expression = readExpression()
        cursor.readWhitespace()
        if (cursor.current != null) cursor.errorExpected("end of selector")
        context.recordPosition(SelectorPositionKind.Selector, start)
        return expression
    }

    private fun readExpression(): SelectorExpression {
        val frames = mutableListOf(ExpressionFrame(cursor.index, negated = false))

        while (true) {
            val frame = frames.last()
            if (frame.values.size == frame.operators.size) {
                when {
                    cursor.current == '(' -> {
                        val start = cursor.index
                        cursor.readChar('(')
                        cursor.readWhitespace()
                        frames.add(ExpressionFrame(start, negated = false))
                    }

                    cursor.current == '!' -> {
                        val start = cursor.index
                        cursor.readChar('!')
                        cursor.readChar('(')
                        cursor.readWhitespace()
                        frames.add(ExpressionFrame(start, negated = true))
                    }

                    cursor.current.isOneOf(PROPERTY_START_CHARS) -> {
                        val start = cursor.index
                        frame.values.add(
                            SelectorTerm(
                                value = readUnitExpression(),
                                grouped = false,
                                start = start,
                                end = cursor.index,
                            ),
                        )
                    }

                    else -> cursor.errorExpected("selector expression")
                }
                continue
            }

            val whitespaceStart = cursor.index
            cursor.readWhitespace()
            val operator = peekLogicalOperator()
            if (operator != null) {
                if (!frame.values.last().grouped) {
                    cursor.errorExpected("parenthesized selector before logical operator")
                }
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
                val groupedTerm = if (frame.negated) {
                    val value = NotSelectorExpression(term.value)
                    context.record(
                        value,
                        SelectorPositionKind.NegatedSelector,
                        frame.start,
                    )
                    SelectorTerm(value, grouped = true, frame.start, cursor.index)
                } else {
                    SelectorTerm(term.value, grouped = true, frame.start, cursor.index)
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
            cursor.errorExpected("selector expression")
        }
        if (frame.operators.isNotEmpty() && !frame.values.last().grouped) {
            cursor.index = frame.values.last().start
            cursor.errorExpected("parenthesized selector after logical operator")
        }
    }

    private fun finish(frame: ExpressionFrame): SelectorTerm {
        while (frame.operators.isNotEmpty()) reduceLastOperator(frame)
        return frame.values.single()
    }

    private fun reduceOperators(
        frame: ExpressionFrame,
        incoming: LogicalOperator,
    ) {
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
        if (!right.grouped) {
            cursor.index = right.start
            cursor.errorExpected("parenthesized selector after logical operator")
        }
        val operator = frame.operators.removeAt(frame.operators.lastIndex)
        val value = LogicalSelectorExpression(left.value, operator, right.value)
        context.record(
            value,
            SelectorPositionKind.LogicalSelector,
            left.start,
            right.end,
        )
        frame.values.add(
            SelectorTerm(
                value = value,
                grouped = true,
                start = left.start,
                end = right.end,
            ),
        )
    }

    private fun peekLogicalOperator(): LogicalOperator? =
        LogicalOperator.parseOrder.firstOrNull {
            cursor.source.startsWith(it.key, cursor.index)
        }

    private fun readUnitExpression(): UnitSelectorExpression =
        context.positioned(SelectorPositionKind.Unit) {
            val propertySelectors = mutableListOf(propertyParser.readPropertySelector())
            val relations = mutableListOf<RelationSelector>()
            while (cursor.current.isOneOf(WHITESPACE_CHARS)) {
                val whitespaceStart = cursor.index
                cursor.readWhitespace()
                when {
                    cursor.current.isOneOf(CONNECT_START_CHARS) -> {
                        relations.add(relationParser.readRelationSelector())
                        cursor.expectOneOf(
                            WHITESPACE_CHARS,
                            "whitespace after relation expression",
                        )
                        cursor.readWhitespace()
                        propertySelectors.add(propertyParser.readPropertySelector())
                    }

                    cursor.current.isOneOf(PROPERTY_START_CHARS) -> {
                        relations.add(
                            RelationSelector(
                                relationExpression = PolynomialExpression(a = 1, b = 0),
                            ),
                        )
                        propertySelectors.add(propertyParser.readPropertySelector())
                    }

                    else -> {
                        cursor.index = whitespaceStart
                        break
                    }
                }
            }
            UnitSelectorExpression(propertySelectors, relations)
        }
}
