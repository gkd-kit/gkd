package li.songe.selector.parser

import li.songe.selector.BinaryExpression
import li.songe.selector.CompareOperator
import li.songe.selector.ConnectExpression
import li.songe.selector.ConnectOperator
import li.songe.selector.ConnectSegment
import li.songe.selector.ConnectWrapper
import li.songe.selector.Expression
import li.songe.selector.LogicalExpression
import li.songe.selector.LogicalOperator
import li.songe.selector.NotExpression
import li.songe.selector.PolynomialExpression
import li.songe.selector.Position
import li.songe.selector.PositionImpl
import li.songe.selector.PropertySegment
import li.songe.selector.PropertyWrapper
import li.songe.selector.Selector
import li.songe.selector.TupleExpression
import li.songe.selector.ValueExpression
import li.songe.selector.gkdAssert
import li.songe.selector.gkdError
import li.songe.selector.parser.ParserSet.connectSelectorParser
import li.songe.selector.parser.ParserSet.endParser
import li.songe.selector.parser.ParserSet.whiteCharParser
import li.songe.selector.toMatches

internal object ParserSet {
    val whiteCharParser = Parser("\u0020\t\r\n") { source, offset, prefix ->
        var i = offset
        var data = ""
        while (i < source.length && prefix.contains(source[i])) {
            data += source[i]
            i++
        }
        ParserResult(data, i - offset)
    }
    val whiteCharStrictParser = Parser("\u0020\t\r\n") { source, offset, prefix ->
        gkdAssert(source, offset, prefix, "whitespace")
        whiteCharParser(source, offset)
    }
    val nameParser =
        Parser("*1234567890qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM_") { source, offset, prefix ->
            var i = offset
            val s0 = source.getOrNull(i)
            if ((s0 != null) && !prefix.contains(s0)) {
                return@Parser ParserResult("")
            }
            gkdAssert(source, i, prefix, "*0-9a-zA-Z_")
            var data = source[i].toString()
            i++
            if (data == "*") { // 范匹配
                return@Parser ParserResult(data, i - offset)
            }
            val center = "1234567890qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM_."
            while (i < source.length) {
//                . 不能在开头和结尾
                if (data[i - offset - 1] == '.') {
                    gkdAssert(source, i, prefix, "[0-9a-zA-Z_]")
                }
                if (center.contains(source[i])) {
                    data += source[i]
                } else {
                    break
                }
                i++
            }
            ParserResult(data, i - offset)
        }

    val combinatorOperatorParser =
        Parser(ConnectOperator.allSubClasses.joinToString("") { it.key }) { source, offset, _ ->
            val operator = ConnectOperator.allSubClasses.find { subOperator ->
                source.startsWith(
                    subOperator.key, offset
                )
            } ?: gkdError(source, offset, "ConnectOperator")
            ParserResult(operator, operator.key.length)
        }

    val integerParser = Parser("1234567890") { source, offset, prefix ->
        var i = offset
        gkdAssert(source, i, prefix, "number")
        var s = ""
        while (i < source.length && prefix.contains(source[i])) {
            s += source[i]
            i++
        }
        ParserResult(
            try {
                s.toInt()
            } catch (e: NumberFormatException) {
                gkdError(source, offset, "valid format number")
            }, i - offset
        )
    }


    //    [+-][a][n]
    val monomialParser = Parser("+-1234567890n") { source, offset, prefix ->
        var i = offset
        gkdAssert(source, i, prefix)
        /**
         * one of 1, -1
         */
        val signal = when (source[i]) {
            '+' -> {
                i++
                1
            }

            '-' -> {
                i++
                -1
            }

            else -> 1
        }
        i += whiteCharParser(source, i).length
        // [a][n]
        gkdAssert(source, i, integerParser.prefix + "n")
        val coefficient = if (integerParser.prefix.contains(source[i])) {
            val coefficientResult = integerParser(source, i)
            i += coefficientResult.length
            coefficientResult.data
        } else {
            1
        } * signal
        // [n]
        if (i < source.length && source[i] == 'n') {
            i++
            // +-an
            return@Parser ParserResult(Pair(1, coefficient), i - offset)
        } else {
            // +-a
            return@Parser ParserResult(Pair(0, coefficient), i - offset)
        }
    }


    // (+-an+-b)
    val polynomialExpressionParser = Parser("(0123456789n") { source, offset, prefix ->
        var i = offset
        gkdAssert(source, i, prefix)
        val monomialResultList = mutableListOf<ParserResult<Pair<Int, Int>>>()
        when (source[i]) {
            '(' -> {
                i++
                i += whiteCharParser(source, i).length
                gkdAssert(source, i, monomialParser.prefix)
                while (source[i] != ')') {
                    if (monomialResultList.size > 0) {
                        gkdAssert(source, i, "+-")
                    }
                    val monomialResult = monomialParser(source, i)
                    monomialResultList.add(monomialResult)
                    i += monomialResult.length
                    i += whiteCharParser(source, i).length
                    if (i >= source.length) {
                        gkdAssert(source, i, ")")
                    }
                }
                i++
            }

            else -> {
                val monomialResult = monomialParser(source, i)
                monomialResultList.add(monomialResult)
                i += monomialResult.length
            }
        }
        val map = mutableMapOf<Int, Int>()
        monomialResultList.forEach { monomialResult ->
            val (power, coefficient) = monomialResult.data
            map[power] = (map[power] ?: 0) + coefficient
        }
        map.mapKeys { power ->
            if (power.key > 1) {
                gkdError(source, offset, "power must be 0 or 1")
            }
        }
        val polynomialExpression = try {
            PolynomialExpression(map[1] ?: 0, map[0] ?: 0)
        } catch (e: Exception) {
            gkdError(source, offset, "valid polynomialExpression")
        }
        ParserResult(polynomialExpression, i - offset)
    }

    val tupleExpressionParser = Parser { source, offset, _ ->
        var i = offset
        gkdAssert(source, i, "(")
        i++
        val numbers = mutableListOf<Int>()
        while (i < source.length && source[i] != ')') {
            i += whiteCharParser(source, i).length
            val intResult = integerParser(source, i)
            if (numbers.isEmpty()) {
                if (intResult.data <= 0) {
                    gkdError(source, i, "positive integer")
                }
            } else {
                if (intResult.data <= numbers.last()) {
                    gkdError(source, i, ">" + numbers.last())
                }
            }
            i += intResult.length
            numbers.add(intResult.data)
            i += whiteCharParser(source, i).length
            if (source.getOrNull(i) == ',') {
                i++
                i += whiteCharParser(source, i).length
                // (1,2,3,) or (1, 2, 6)
                gkdAssert(source, i, integerParser.prefix + ")")
            }
        }
        gkdAssert(source, i, ")")
        i++
        ParserResult(TupleExpression(numbers), i - offset)
    }

    private fun isTupleExpression(source: CharSequence): Boolean {
        // ^\(\s*\d+\s*,
        var i = 0
        if (source.getOrNull(i) != '(') {
            return false
        }
        i++
        i += whiteCharParser(source, i).length
        if (source.getOrNull(i) !in '0'..'9') {
            return false
        }
        i += integerParser(source, i).length
        i += whiteCharParser(source, i).length
        return source.getOrNull(i) == ','
    }

    val connectExpressionParser = Parser(polynomialExpressionParser.prefix) { source, offset, _ ->
        var i = offset
        if (isTupleExpression(source.subSequence(offset, source.length))) {
            val tupleExpressionResult = tupleExpressionParser(source, i)
            i += tupleExpressionResult.length
            ParserResult(tupleExpressionResult.data, i - offset)
        } else {
            val polynomialExpressionResult = polynomialExpressionParser(source, offset)
            i += polynomialExpressionResult.length
            ParserResult(polynomialExpressionResult.data, i - offset)
        }
    }

    //    [+-><](a*n+b)
    //    [+-><](1,2,3,4)
    val combinatorParser = Parser(combinatorOperatorParser.prefix) { source, offset, _ ->
        var i = offset
        val operatorResult = combinatorOperatorParser(source, i)
        i += operatorResult.length
        var expressionResult: ParserResult<ConnectExpression>? = null
        if (i < source.length && connectExpressionParser.prefix.contains(source[i])) {
            expressionResult = connectExpressionParser(source, i)
            i += expressionResult.length
        }
        ParserResult(
            ConnectSegment(
                operatorResult.data, expressionResult?.data ?: PolynomialExpression()
            ), i - offset
        )
    }

    private fun attrOperatorParser(
        source: CharSequence,
        offset: Int
    ): PositionImpl<CompareOperator> {
        val operator = CompareOperator.allSubClasses.find { compareOperator ->
            source.startsWith(compareOperator.key, offset)
        } ?: gkdError(source, offset, "CompareOperator")
        return PositionImpl(
            start = offset,
            end = offset + operator.key.length,
            value = operator
        )
    }

    val stringParser = Parser("`'\"") { source, offset, prefix ->
        var i = offset
        gkdAssert(source, i, prefix)
        val startChar = source[i]
        i++
        if (i >= source.length) {
            gkdError(source, i, "any char")
        }
        var data = ""
        while (source[i] != startChar) {
            if (i >= source.length - 1) {
                gkdAssert(source, i, startChar.toString())
                break
            }
            // https://www.rfc-editor.org/rfc/inline-errata/rfc7159.html
            if (source[i].code in 0x0000..0x001F) {
                gkdError(source, i, "0-1f escape char")
            }
            if (source[i] == '\\') {
                i++
                gkdAssert(source, i)
                data += when (source[i]) {
                    '\\' -> '\\'
                    '\'' -> '\''
                    '"' -> '"'
                    '`' -> '`'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'b' -> '\b'
                    'x' -> {
                        repeat(2) {
                            i++
                            gkdAssert(source, i, "0123456789abcdefABCDEF")
                        }
                        source.substring(i - 2 + 1, i + 1).toInt(16).toChar()
                    }

                    'u' -> {
                        repeat(4) {
                            i++
                            gkdAssert(source, i, "0123456789abcdefABCDEF")
                        }
                        source.substring(i - 4 + 1, i + 1).toInt(16).toChar()
                    }

                    else -> {
                        gkdError(source, i, "escape char")
                    }
                }
            } else {
                data += source[i]
            }
            i++
        }
        i++
        ParserResult(data, i - offset)
    }

    private val varPrefix = "_" + ('a'..'z').joinToString("") + ('A'..'Z').joinToString("")
    private val varStr = varPrefix + '.' + ('0'..'9').joinToString("")
    private val propertyParser = Parser(varPrefix) { source, offset, prefix ->
        var i = offset
        gkdAssert(source, i, prefix)
        var data = source[i].toString()
        i++
        while (i < source.length && varStr.contains(source[i])) {
            if (source[i] == '.') {
                gkdAssert(source, i + 1, prefix)
            }
            data += source[i]
            i++
        }
        ParserResult(data, i - offset)
    }

    private fun isVarChar(c: Char?, start: Boolean = false): Boolean {
        c ?: return false
        return (c == '_' || c in 'a'..'z' || c in 'A'..'Z' || (!start && c in '0'..'9'))
    }

    private fun matchLiteral(source: CharSequence, offset: Int, raw: String): Boolean {
        if (source.startsWith(raw, offset)) {
            val c = source.getOrNull(offset + raw.length) ?: return true
            return !(c == '_' || c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9')
        }
        return false
    }

    fun parseVariable(source: CharSequence, offset: Int): ValueExpression {
        var i = offset
        i += whiteCharParser(source, i).length
        if (i >= source.length) {
            gkdError(source, i, "Variable")
        }
        if (matchLiteral(source, i, "true")) {
            return ValueExpression.BooleanLiteral(start = i, value = true)
        } else if (matchLiteral(source, i, "false")) {
            return ValueExpression.BooleanLiteral(start = i, value = false)
        } else if (matchLiteral(source, i, "null")) {
            return ValueExpression.NullLiteral(start = i)
        }
        if (source[i] in stringParser.prefix) {
            val result = stringParser(source, i)
            i += result.length
            return ValueExpression.StringLiteral(
                start = i - result.length,
                end = i,
                value = result.data
            )
        }
        if (source[i] == '-') {
            i++
            val result = integerParser(source, i)
            i += result.length
            return ValueExpression.IntLiteral(
                start = i - result.length - 1,
                end = i,
                value = -result.data
            )
        }
        if (source[i] in integerParser.prefix) {
            val result = integerParser(source, i)
            i += result.length
            return ValueExpression.IntLiteral(
                start = i - result.length,
                end = i, value = result.data
            )
        }

        var lastToken: ValueExpression.Variable? = null
        while (i < source.length) {
            i += whiteCharParser(source, i).length
            val char = source.getOrNull(i)
            when {
                char == '(' -> {
                    val start = i
                    i++
                    i += whiteCharParser(source, i).length
                    if (lastToken != null) {
                        // 暂不支持 object()()
                        if (lastToken is ValueExpression.CallExpression) {
                            gkdError(source, i, "Variable")
                        }
                        val arguments = mutableListOf<ValueExpression>()
                        while (i < source.length && source[i] != ')') {
                            val result = parseVariable(source, i)
                            arguments.add(result)
                            i += result.length
                            if (source.getOrNull(i) == ',') {
                                i++
                                i += whiteCharParser(source, i).length
                            }
                        }
                        i += whiteCharParser(source, i).length
                        gkdAssert(source, i, ")")
                        i++
                        lastToken = ValueExpression.CallExpression(
                            start = lastToken.start,
                            end = i,
                            lastToken,
                            arguments
                        )
                    } else {
                        val result = parseVariable(source, i)
                        i += result.length
                        i += whiteCharParser(source, i).length
                        gkdAssert(source, i, ")")
                        i++
                        val end = i
                        return when (result) {
                            is ValueExpression.BooleanLiteral -> result.copy(
                                start = start
                            )

                            is ValueExpression.IntLiteral -> result.copy(start = start, end = end)
                            is ValueExpression.NullLiteral -> result.copy(start = start)
                            is ValueExpression.StringLiteral -> result.copy(
                                start = start,
                                end = end
                            )

                            is ValueExpression.CallExpression -> result.copy(
                                start = start,
                                end = end
                            )

                            is ValueExpression.Identifier -> result.copy(start = start)
                            is ValueExpression.MemberExpression -> result.copy(
                                start = start,
                                end = end
                            )
                        }
                    }
                }

                char == '.' -> {
                    i++
                    if (lastToken !is ValueExpression.Variable) {
                        gkdError(source, i, "Variable")
                    }
                    if (!isVarChar(source.getOrNull(i), true)) {
                        gkdError(source, i, "Variable")
                    }
                    val property = source.drop(i).takeWhile { c -> isVarChar(c, false) }.toString()
                    lastToken = ValueExpression.MemberExpression(
                        start = lastToken.start,
                        end = i + property.length,
                        lastToken,
                        property
                    )
                    i += property.length
                }

                isVarChar(char) -> {
                    val variable = source.drop(i).takeWhile { c -> isVarChar(c) }.toString()
                    lastToken = ValueExpression.Identifier(start = i, variable)
                    i += variable.length
                }

                else -> {
                    break
                }
            }
        }
        if (lastToken == null) {
            gkdError(source, i, "Variable")
        }
        return lastToken
    }

    private fun valueParser(source: CharSequence, offset: Int): ValueExpression {
        val prefix = "tfn-" + stringParser.prefix + integerParser.prefix + varPrefix
        gkdAssert(source, offset, prefix)
        val result = parseVariable(source, offset)
        return result
    }

    private fun binaryExpressionParser(source: CharSequence, offset: Int): BinaryExpression {
        var i = offset
        val leftValueResult = valueParser(source, i)
        i += leftValueResult.length
        i += whiteCharParser(source, i).length
        val operatorResult = attrOperatorParser(source, i)
        i += operatorResult.length
        i += whiteCharParser(source, i).length
        val rightValueResult = valueParser(source, i).let { result ->
            // check regex
            if ((operatorResult.value == CompareOperator.Matches || operatorResult.value == CompareOperator.NotMatches) && result is ValueExpression.StringLiteral) {
                val matches = try {
                    result.value.toMatches()
                } catch (e: Exception) {
                    gkdError(source, i, "valid primitive string regex", e)
                }
                result.copy(
                    matches = matches
                )
            } else {
                result
            }
        }
        i += rightValueResult.length
        return BinaryExpression(
            start = offset,
            end = i,
            leftValueResult,
            operatorResult,
            rightValueResult
        )
    }

    private fun logicalOperatorParser(
        source: CharSequence,
        offset: Int
    ): PositionImpl<LogicalOperator> {
        var i = offset
        i += whiteCharParser(source, i).length
        val operator = LogicalOperator.allSubClasses.find { logicalOperator ->
            source.startsWith(logicalOperator.key, offset)
        } ?: gkdError(source, offset, "LogicalOperator")
        return PositionImpl(
            start = i,
            end = i + operator.key.length,
            value = operator
        )
    }

    private fun unaryExpressionParser(
        source: CharSequence,
        offset: Int
    ): NotExpression {
        var i = offset
        i += whiteCharParser(source, i).length
        gkdAssert(source, i, "!")
        val start = i
        i += 1
        gkdAssert(source, i, "(")
        val expression = expressionParser(source, i, true)
        i += expression.length
        return NotExpression(
            start = start,
            expression
        )
    }

    //    a>1 && a>1 || a>1
//    (a>1 || a>1) && a>1
    fun expressionParser(
        source: CharSequence,
        offset: Int,
        one: Boolean = false, // 是否只解析一个表达式
    ): Expression {
        var i = offset
        i += whiteCharParser(source, i).length
//        [exp, ||, exp, &&, &&]
        val parserResults = mutableListOf<Position>()
        while (i < source.length && source[i] != ']' && source[i] != ')') {
            when (source[i]) {
                '(' -> {
                    val start = i
                    if (parserResults.isNotEmpty()) {
                        val lastToken = parserResults.last()
                        if (!(lastToken is PositionImpl<*> && lastToken.value is LogicalOperator)) {
                            var count = 0
                            while (i - 1 >= count && source[i - 1 - count] in whiteCharParser.prefix) {
                                count++
                            }
                            gkdError(
                                source, i - count - lastToken.length, "LogicalOperator"
                            )
                        }
                    }
                    // [(a)=1]
                    // [(a=1)]
                    i++
                    val exp = expressionParser(source, i).apply { i += length }
                    gkdAssert(source, i, ")")
                    i++
                    val end = i
                    parserResults.add(
                        when (exp) {
                            is BinaryExpression -> exp.copy(
                                start = start,
                                end = end
                            )

                            is LogicalExpression -> exp.copy(
                                start = start,
                                end = end
                            )

                            is NotExpression -> exp.copy(
                                start = start
                            )
                        }
                    )
                    if (one) {
                        break
                    }
                }

                in "|&" -> {
                    parserResults.add(logicalOperatorParser(source, i).apply { i += length })
                    i += whiteCharParser(source, i).length
                    gkdAssert(source, i, "(!" + propertyParser.prefix)
                }

                '!' -> {
                    parserResults.add(unaryExpressionParser(source, i).apply { i += length })
                    i += whiteCharParser(source, i).length
                }

                else -> {
                    parserResults.add(binaryExpressionParser(source, i).apply { i += length })
                }
            }
            i += whiteCharParser(source, i).length
        }
        if (parserResults.isEmpty()) {
            gkdError(
                source, i - offset, "Expression"
            )
        }
        if (parserResults.size == 1) {
            return parserResults.first() as Expression
        }

//        运算符优先级 && > ||
//        a && b || c -> ab || c
//        0 1  2 3  4 -> 0  1  2
        val tokens = parserResults.toMutableList()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token is PositionImpl<*> && token.value == LogicalOperator.AndOperator) {
                val left = tokens[index - 1] as Expression
                val right = tokens[index + 1] as Expression

                @Suppress("UNCHECKED_CAST")
                val operator = token as PositionImpl<LogicalOperator>
                tokens[index] = LogicalExpression(
                    start = left.start,
                    end = right.end,
                    left = left,

                    operator = operator,
                    right = right
                )
                tokens.removeAt(index - 1)
                tokens.removeAt(index + 1 - 1)
            } else {
                index++
            }
        }
        while (tokens.size > 1) {
            val left = tokens[0] as Expression

            @Suppress("UNCHECKED_CAST")
            val operator = tokens[1] as PositionImpl<LogicalOperator>
            val right = tokens[2] as Expression
            tokens[1] = LogicalExpression(
                start = left.start,
                end = right.end,
                left = left,
                operator = operator,
                right = right
            )
            tokens.removeAt(0)
            tokens.removeAt(2 - 1)
        }
        return tokens.first() as Expression
    }


    val attrParser = Parser("[") { source, offset, prefix ->
        var i = offset
        gkdAssert(source, i, prefix)
        i++
        i += whiteCharParser(source, i).length
        val exp = expressionParser(source, i)
        i += exp.length
        gkdAssert(source, i, "]")
        i++
        ParserResult(
            exp, i - offset
        )
    }


    val selectorUnitParser = Parser { source, offset, _ ->
        var i = offset
        var tracked = false
        if (source.getOrNull(i) == '@') {
            tracked = true
            i++
        }
        val nameResult = nameParser(source, i)
        i += nameResult.length
        val expressions = mutableListOf<Expression>()
        while (i < source.length && source[i] == '[') {
            val attrResult = attrParser(source, i)
            i += attrResult.length
            expressions.add(attrResult.data)
        }
        if (nameResult.length == 0 && expressions.size == 0) {
            gkdError(source, i, "[")
        }
        ParserResult(PropertySegment(tracked, nameResult.data, expressions), i - offset)
    }

    val connectSelectorParser = Parser { source, offset, _ ->
        var i = offset
        i += whiteCharParser(source, i).length
        val topSelector = selectorUnitParser(source, i)
        i += topSelector.length
        val selectorList = mutableListOf<Pair<ConnectSegment, PropertySegment>>()
        while (i < source.length && whiteCharParser.prefix.contains(source[i])) {
            i += whiteCharStrictParser(source, i).length
            if (i >= source.length) {
                break
            }
            val combinator = if (combinatorParser.prefix.contains(source[i])) {
                val combinatorResult = combinatorParser(source, i)
                i += combinatorResult.length
                i += whiteCharStrictParser(source, i).length
                combinatorResult.data
            } else {
                // A B
                ConnectSegment(connectExpression = PolynomialExpression(1, 0))
            }
            val selectorResult = selectorUnitParser(source, i)
            i += selectorResult.length
            selectorList.add(combinator to selectorResult.data)
        }
        ParserResult(topSelector.data to selectorList, i - offset)
    }

    val endParser = Parser { source, offset, _ ->
        if (offset != source.length) {
            gkdError(source, offset, "EOF")
        }
        ParserResult(Unit, 0)
    }
}

internal fun selectorParser(source: String): Selector {
    var i = 0
    i += whiteCharParser(source, i).length
    val combinatorSelectorResult = connectSelectorParser(source, i)
    i += combinatorSelectorResult.length

    i += whiteCharParser(source, i).length
    i += endParser(source, i).length
    val data = combinatorSelectorResult.data
    val propertySelectorList = mutableListOf<PropertySegment>()
    val combinatorSelectorList = mutableListOf<ConnectSegment>()
    propertySelectorList.add(data.first)
    data.second.forEach {
        propertySelectorList.add(it.second)
        combinatorSelectorList.add(it.first)
    }
    val wrapperList = mutableListOf(PropertyWrapper(propertySelectorList.first()))
    combinatorSelectorList.forEachIndexed { index, combinatorSelector ->
        val combinatorSelectorWrapper = ConnectWrapper(combinatorSelector, wrapperList.last())
        val propertySelectorWrapper =
            PropertyWrapper(propertySelectorList[index + 1], combinatorSelectorWrapper)
        wrapperList.add(propertySelectorWrapper)
    }
    return Selector(source, wrapperList.last())
}