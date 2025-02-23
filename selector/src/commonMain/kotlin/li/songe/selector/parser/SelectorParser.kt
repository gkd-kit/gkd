package li.songe.selector.parser

import li.songe.selector.Selector
import li.songe.selector.connect.ConnectSegment
import li.songe.selector.connect.ConnectWrapper
import li.songe.selector.connect.PolynomialExpression
import li.songe.selector.property.PropertySegment
import li.songe.selector.property.PropertyWrapper
import li.songe.selector.unit.LogicalSelectorExpression
import li.songe.selector.unit.NotSelectorExpression
import li.songe.selector.unit.SelectorExpression
import li.songe.selector.unit.SelectorExpressionToken
import li.songe.selector.unit.SelectorLogicalOperator
import li.songe.selector.unit.UnitSelectorExpression

internal open class SelectorParser(
    override val source: CharSequence,
) : PropertyParser, ConnectParser {
    override var i = 0

    open fun readUnitSelectorExpression(): UnitSelectorExpression {
        val top = readPropertySegment()
        val pairs = mutableListOf<Pair<ConnectSegment, PropertySegment>>()
        while (char.inStr(WHITESPACE_CHAR)) {
            readWhiteSpace()
            if (char.inStr(CONNECT_START_CHAR)) {
                // A > B
                val connectSegment = readConnectSegment()
                expectOneOfChar(WHITESPACE_CHAR, "WHITESPACE")
                readWhiteSpace()
                val propertySegment = readPropertySegment()
                pairs.add(connectSegment to propertySegment)
            } else if (char.inStr(PROPERTY_START_CHAR)) {
                // A B
                val connectSegment = ConnectSegment(connectExpression = PolynomialExpression(1, 0))
                val propertySegment = readPropertySegment()
                pairs.add(connectSegment to propertySegment)
            } else {
                break
            }
        }
        var topWrapper = PropertyWrapper(top)
        pairs.forEach { (connectSegment, propertySegment) ->
            topWrapper = PropertyWrapper(
                propertySegment,
                ConnectWrapper(connectSegment, topWrapper)
            )
        }
        return UnitSelectorExpression(topWrapper)
    }

    // !(A > B)
    open fun readNotSelectorExpression(): NotSelectorExpression {
        expectChar('!')
        i++
        expectChar('(')
        i++
        readWhiteSpace()
        return NotSelectorExpression(readSelectorExpression()).apply {
            readWhiteSpace()
            expectChar(')')
            i++
        }
    }

    // (A + B) || (A - B)
    // (A + B) && (A - B)
    open fun readSelectorLogicalOperator(): SelectorLogicalOperator {
        val operator = SelectorLogicalOperator.allSubClasses.find { v ->
            source.startsWith(v.key, i)
        }
        if (operator == null) {
            errorExpect("selector logical operator")
        }
        i += operator.key.length
        return operator
    }

    open fun mergeLogicalSelectorExpression(expression: LogicalSelectorExpression) {}

    // A + B
    open fun readSelectorExpression(): SelectorExpression {
        val tokens = mutableListOf<SelectorExpressionToken>()
        while (true) {
            if (tokens.lastOrNull() is SelectorLogicalOperator) {
                expectOneOfChar("!(", "selector")
            }
            val c = char
            if (c == null) {
                if (tokens.isEmpty()) {
                    errorExpect("selector")
                } else {
                    break
                }
            }
            val token: SelectorExpressionToken = if (tokens.lastOrNull() is SelectorExpression) {
                if (c in "&|") {
                    readSelectorLogicalOperator()
                } else {
                    break
                }
            } else if (c == '(') {
                i++
                readWhiteSpace()
                readSelectorExpression().apply {
                    readWhiteSpace()
                    expectChar(')')
                    i++
                }
            } else if (c == '!') {
                readNotSelectorExpression()
            } else if (c.inStr(PROPERTY_START_CHAR)) {
                readUnitSelectorExpression()
            } else {
                break
            }
            tokens.add(token)
            readWhiteSpace()
        }
        if (tokens.size == 1) {
            return tokens.first() as SelectorExpression
        }
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token === SelectorLogicalOperator.AndOperator) {
                val left = tokens[index - 1] as SelectorExpression
                val right = tokens[index + 1] as SelectorExpression
                tokens[index] = LogicalSelectorExpression(
                    left = left,
                    operator = token,
                    right = right
                ).apply { mergeLogicalSelectorExpression(this) }
                tokens.removeAt(index - 1)
                tokens.removeAt(index + 1 - 1)
            } else {
                index++
            }
        }
        while (tokens.size > 1) {
            val left = tokens[0] as SelectorExpression
            val operator = tokens[1] as SelectorLogicalOperator
            val right = tokens[2] as SelectorExpression
            tokens[1] = LogicalSelectorExpression(
                left = left,
                operator = operator,
                right = right
            ).apply { mergeLogicalSelectorExpression(this) }
            tokens.removeAt(0)
            tokens.removeAt(2 - 1)
        }
        return tokens.first() as SelectorExpression
    }

    open fun readSelector(): Selector {
        readWhiteSpace()
        return Selector(
            expression = readSelectorExpression()
        ).apply {
            readWhiteSpace()
            if (char != null) {
                errorExpect("EOF")
            }
        }
    }
}
