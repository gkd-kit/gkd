package li.songe.selector.parser

import li.songe.selector.property.BinaryExpression
import li.songe.selector.property.CompareOperator
import li.songe.selector.property.Expression
import li.songe.selector.property.ExpressionToken
import li.songe.selector.property.LogicalExpression
import li.songe.selector.property.LogicalOperator
import li.songe.selector.property.NotExpression
import li.songe.selector.property.PropertySegment
import li.songe.selector.property.PropertyUnit
import li.songe.selector.property.ValueExpression
import li.songe.selector.toMatches

internal sealed interface PropertyParser : BaseParser {

    fun readLogicalOperator(): LogicalOperator {
        val operator = LogicalOperator.allSubClasses.find { v ->
            source.startsWith(v.key, i)
        }
        if (operator == null) {
            errorExpect("logical operator")
        }
        i += operator.key.length
        return operator
    }

    fun readCompareOperator(): CompareOperator {
        val operator = CompareOperator.allSubClasses.find { v ->
            source.startsWith(v.key, i)
        }
        if (operator == null) {
            errorExpect("compare operator")
        }
        i += operator.key.length
        return operator
    }

    fun readVariableName(): String {
        val start = i
        expectOneOfChar(VAR_START_CHAR, "VAR_START_CHAR")
        i++
        while (char.inStr(VAR_CONTINUE_CHAR)) {
            i++
        }
        val v = source.substring(start, i)
        if (v == "null" || v == "false" || v == "true") {
            i = start
            errorExpect("no keyword variable")
        }
        return v
    }

    fun mergeIdentifier(expression: ValueExpression.Identifier) {}

    fun mergeMemberExpression(expression: ValueExpression.MemberExpression) {}

    fun mergeCallExpression(expression: ValueExpression.CallExpression) {}

    fun readValueExpression(): ValueExpression {
        expectOneOfChar(VALUE_START_CHAR, "VAL_START_CHAR")
        if (readLiteral("null")) {
            return ValueExpression.NullLiteral
        }
        if (readLiteral("false")) {
            return ValueExpression.BooleanLiteral(false)
        }
        if (readLiteral("true")) {
            return ValueExpression.BooleanLiteral(true)
        }
        if (char.inStr(INT_START_CHAR)) {
            return ValueExpression.IntLiteral(readInt())
        }
        if (char.inStr(STRING_START_CHAR)) {
            return ValueExpression.StringLiteral(readString())
        }
        var lastToken: ValueExpression.Variable? = null
        while (true) {
            readWhiteSpace()
            val c = char ?: break
            if (c.inStr(VAR_START_CHAR)) {
                if (lastToken != null) {
                    errorExpect("Variable End")
                }
                lastToken =
                    ValueExpression.Identifier(readVariableName()).apply { mergeIdentifier(this) }
            } else if (c == '.') {
                if (lastToken !is ValueExpression.Variable) {
                    errorExpect("Variable End")
                }
                i++
                readWhiteSpace()
                lastToken = ValueExpression.MemberExpression(
                    lastToken,
                    readVariableName()
                ).apply { mergeMemberExpression(this) }
            } else if (c == '(') {
                i++
                readWhiteSpace()
                if (lastToken != null) {
                    // 暂不支持 object()()
                    if (lastToken is ValueExpression.CallExpression) {
                        errorExpect("Variable")
                    }
                    val arguments = mutableListOf<ValueExpression>()
                    while (char.inStr(VALUE_START_CHAR)) {
                        arguments.add(readValueExpression())
                        if (char == ',') {
                            i++
                            readWhiteSpace()
                        }
                    }
                    readWhiteSpace()
                    expectChar(')')
                    i++
                    lastToken = ValueExpression.CallExpression(
                        lastToken,
                        arguments
                    ).apply { mergeCallExpression(this) }
                } else {
                    return readValueExpression().apply {
                        readWhiteSpace()
                        expectChar(')')
                        i++
                    }
                }
            } else {
                break
            }
        }
        if (lastToken == null) {
            errorExpect("Variable")
        }
        rollbackWhiteSpace()
        return lastToken
    }

    fun readBinaryExpression(): BinaryExpression {
        val leftValue = readValueExpression()
        readWhiteSpace()
        val operator = readCompareOperator()
        readWhiteSpace()
        val regexIndex = i
        val rightValue = readValueExpression().let {
            if (it is ValueExpression.StringLiteral && (operator == CompareOperator.Matches || operator == CompareOperator.NotMatches)) {
                val matches = try {
                    it.value.toMatches()
                } catch (_: Exception) {
                    i = regexIndex
                    errorExpect("valid regex string")
                }
                it.copy(matches = matches)
            } else {
                it
            }
        }
        return BinaryExpression(
            leftValue,
            operator,
            rightValue
        )
    }

    fun readNotExpression(): NotExpression {
        expectChar('!')
        i++
        expectChar('(')
        i++
        readWhiteSpace()
        return NotExpression(readExpression()).apply {
            readWhiteSpace()
            expectChar(')')
            i++
        }
    }

    fun mergeLogicalExpression(expression: LogicalExpression) {}

    // a>1 && a>1 || a>1
    // (a>1 || a>1) && a>1
    fun readExpression(): Expression {
        // LogicalOperator/Expression
        expectOneOfChar(EXP_START_CHAR, "EXP_START_CHAR")
        val tokens = mutableListOf<ExpressionToken>()
        while (true) {
            if (tokens.lastOrNull() is LogicalOperator) {
                expectOneOfChar(EXP_START_CHAR, "EXP_START_CHAR")
            }
            val c = char
            if (c == null) {
                if (tokens.isEmpty()) {
                    errorExpect("EXP_START_CHAR")
                } else {
                    break
                }
            }
            val token: ExpressionToken = if (tokens.lastOrNull() is Expression) {
                if (c in "&|") {
                    readLogicalOperator()
                } else {
                    break
                }
            } else if (c == '(') {
                readBracketExpression {
                    readExpression()
                }
            } else if (c == '!') {
                readNotExpression()
            } else {
                readBinaryExpression()
            }
            tokens.add(token)
            readWhiteSpace()
        }
        rollbackWhiteSpace()
        if (tokens.size == 1) {
            return tokens.first() as Expression
        }
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token === LogicalOperator.AndOperator) {
                // && > ||
                // a && b || c -> (a && b) || c
                // 0 1  2 3  4 -> 0  1  2
                val left = tokens[index - 1] as Expression
                val right = tokens[index + 1] as Expression
                tokens[index] = LogicalExpression(
                    left = left,
                    operator = token,
                    right = right
                ).apply { mergeLogicalExpression(this) }
                tokens.removeAt(index - 1)
                tokens.removeAt(index + 1 - 1)
            } else {
                index++
            }
        }
        while (tokens.size > 1) {
            // a || b || c -> (a || b) || c -> (ab || c)
            val left = tokens[0] as Expression
            val operator = tokens[1] as LogicalOperator
            val right = tokens[2] as Expression
            tokens[1] = LogicalExpression(
                left = left,
                operator = operator,
                right = right
            ).apply { mergeLogicalExpression(this) }
            tokens.removeAt(0)
            tokens.removeAt(2 - 1)
        }
        return tokens.first() as Expression
    }

    fun readPropertyName(): String {
        val start = i
        if (char == '*') {
            i++
            return "*"
        }
        expectOneOfChar(VAR_START_CHAR, "VAR_START_CHAR")
        i++
        while (true) {
            val c = char ?: break
            when (c) {
                '.' -> {
                    i++
                    expectOneOfChar(VAR_START_CHAR, "VAR_START_CHAR")
                    i++
                }

                in VAR_CONTINUE_CHAR -> {
                    i++
                }

                else -> break
            }
        }
        return source.substring(start, i)

    }

    // [a=b||c=d]
    fun readPropertyUnit(): PropertyUnit {
        expectChar('[')
        i++
        readWhiteSpace()
        return PropertyUnit(readExpression()).apply {
            readWhiteSpace()
            expectChar(']')
            i++
        }
    }

    // @a[a=b||c=d]
    fun readPropertySegment(): PropertySegment {
        val at = char == '@'
        if (at) {
            i++
        }
        val name = if (char == '[') {
            ""
        } else {
            readPropertyName()
        }
        if (name.isEmpty()) {
            expectChar('[')
        }
        val expressions = mutableListOf<PropertyUnit>()
        while (char == '[') {
            expressions.add(readPropertyUnit())
        }
        return PropertySegment(
            at,
            name,
            expressions
        )
    }
}
