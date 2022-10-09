package li.songe.node_selector.parser

import li.songe.node_selector.GkdSelector
import li.songe.node_selector.selector.CombinatorSelector
import li.songe.node_selector.selector.Position
import li.songe.node_selector.selector.PropertySelector
import li.songe.node_selector.wrapper.CombinatorSelectorWrapper
import li.songe.node_selector.wrapper.PropertySelectorWrapper

object Tools {
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
        var i = offset
        Parser.assert(source, i, prefix)
        var data = source[i].toString()
        i++
        while (i < source.length && prefix.contains(source[i])) {
            data += source[i]
            i++
        }
        ParserResult(data, i - offset)
    }

    val nameParser =
        Parser("1234567890qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM_") { source, offset, prefix ->
            var i = offset
            Parser.assert(source, i, prefix)
            var data = source[i].toString()
            i++
            val center = "1234567890qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM_."
            while (i < source.length) {
                if (data[i - offset - 1] == '.') {
                    Parser.assert(source, i, prefix)
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

    val combinatorOperatorParser = Parser("+-><") { source, offset, prefix ->
        var i = offset
        Parser.assert(source, i, prefix)
        return@Parser when (source[i]) {
            '+' -> ParserResult(CombinatorSelector.Operator.ElderBrother, ++i - offset)
            '-' -> ParserResult(CombinatorSelector.Operator.YoungerBrother, ++i - offset)
            '>' -> ParserResult(CombinatorSelector.Operator.Ancestor, ++i - offset)
            '<' -> ParserResult(CombinatorSelector.Operator.Child, ++i - offset)
            else -> throw ParserException(source, i, prefix)
        }
    }

    val integerParser = Parser("1234567890") { source, offset, prefix ->
        var i = offset
        Parser.assert(source, i, prefix)
        var s = ""
        while (prefix.contains(source[i]) && i < source.length) {
            s += source[i]
            i++
        }
        ParserResult(s.toInt(), i - offset)
    }

    val monomialParser = Parser("+-") { source, offset, _ ->
        var i = offset
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
        Parser.assert(source, i)
        i += whiteCharParser(source, i).length
        Parser.assert(source, i)
        val coefficient =
            if (integerParser.prefix.contains(source[i])) {
                val coefficientResult = integerParser(source, i)
                i += coefficientResult.length
                coefficientResult.data
            } else {
                1
            } * signal
        if (i < source.length && source[i] == 'n') {
            i++
            if (i < source.length && source[i] == '^') {
                i++
                val powerResult = integerParser(source, i)
                i += powerResult.length
                return@Parser ParserResult(Pair(powerResult.data, coefficient), i - offset)
            } else {
                return@Parser ParserResult(Pair(1, coefficient), i - offset)
            }
        } else {
            return@Parser ParserResult(Pair(0, coefficient), i - offset)
        }
    }

    val expressionParser = Parser("(0123456789n") { source, offset, prefix ->
        var i = offset
        Parser.assert(source, i, prefix)
        val monomialResultList = mutableListOf<ParserResult<Pair<Int, Int>>>()
        when (source[i]) {
            '(' -> {
                i += 1
                i += whiteCharParser(source, i).length
                Parser.assert(source, i, "+-n1234567890")
                while (source[i] != ')') {
                    if (monomialResultList.size > 0) {
                        Parser.assert(source, i, "+-")
                    }
                    val monomialResult = monomialParser(source, i)
                    monomialResultList.add(monomialResult)
                    i += monomialResult.length
                    i += whiteCharParser(source, i).length
                    if (i >= source.length) {
                        Parser.assert(source, i, ")")
                    }
                }
                i += 1
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
        ParserResult(CombinatorSelector.PolynomialExpression(map.filter { (_, coefficient) ->
            coefficient != 0
        }), i - offset)
    }

    val combinatorParser = Parser(combinatorOperatorParser.prefix) { source, offset, _ ->
        var i = offset
        val operatorResult = combinatorOperatorParser(source, i)
        i += operatorResult.length
        var expressionResult: ParserResult<CombinatorSelector.PolynomialExpression>? = null
        if (i < source.length && expressionParser.prefix.contains(source[i])) {
            expressionResult = expressionParser(source, i)
            i += expressionResult.length
        }
        ParserResult(
            CombinatorSelector(
                operatorResult.data,
                expressionResult?.data ?: CombinatorSelector.PolynomialExpression()
            ), i - offset
        )
    }

    val attrOperatorParser = Parser("><!*$^=") { source, offset, prefix ->
        var i = offset
        Parser.assert(source, i, prefix)
        val attrOperator =
            when (source[i]) {
                '=' -> {
                    i++
                    when (source.getOrNull(i)) {
                        '=' -> {
                            i++
                            PropertySelector.Operator.Equal
                        }
                        else -> {
                            PropertySelector.Operator.Equal
                        }
                    }
                }
                '>' -> {
                    i++
                    when (source.getOrNull(i)) {
                        '=' -> {
                            i++
                            PropertySelector.Operator.MoreEqual
                        }
                        else -> {
                            PropertySelector.Operator.More
                        }
                    }
                }
                '<' -> {
                    i++
                    when (source.getOrNull(i)) {
                        '=' -> {
                            i++
                            PropertySelector.Operator.LessEqual
                        }
                        else -> {
                            PropertySelector.Operator.Less
                        }
                    }
                }
                '!' -> {
                    i++
                    Parser.assert(source, i, '=')
                    i++
                    PropertySelector.Operator.NotEqual
                }
                '*' -> {
                    i++
                    Parser.assert(source, i, '=')
                    i++
                    PropertySelector.Operator.Include
                }
                '^' -> {
                    i++
                    Parser.assert(source, i, '=')
                    i++
                    PropertySelector.Operator.Start
                }
                '$' -> {
                    i++
                    Parser.assert(source, i, '=')
                    i++
                    PropertySelector.Operator.End
                }
                else -> {
                    Parser.throwError(source, i, prefix)
                }
            }
        ParserResult(attrOperator, i - offset)
    }

    val stringParser = Parser("`") { source, offset, prefix ->
        var i = offset
        Parser.assert(source, i, prefix)
        i++
        var data = ""
        while (source[i] != '`') {
            if (i == source.length - 1) {
                Parser.assert(source, i, '`')
                break
            }
            if (source[i] == '\\') {
                i++
                Parser.assert(source, i)
                if (source[i] == '`') {
                    data += source[i]
                    Parser.assert(source, i + 1)
                } else {
                    data += '\\' + source[i].toString()
                }
            } else {
                data += source[i]
            }
            i++
        }
        i++
        ParserResult(data, i - offset)
    }

    val numberParser = Parser("1234567890.") { source, offset, prefix ->
        var i = offset
        Parser.assert(source, i, prefix)
        var value = ""
        value = if (source[i] == '.') {
            value += source[i]
            i++
            Parser.assert(source, i, "1234567890")
            while ("1234567890".contains(source[i])) {
                value += source[i]
                i++
            }
            value
        } else {
            while ("1234567890.".contains(source[i])) {
                if (source[i] == '.') {
                    value += source[i]
                    i++
                    // expectAssert(source, i, '1234567890')
                    while ("1234567890".contains(source[i])) {
                        value += source[i]
                        i++
                    }
                    break
                } else {
                    value += source[i]
                    i++
                }
            }
            value
        }

        val data = when {
//            value.endsWith('.') -> {
//                value = value.substring(0, value.length - 1)
//                value.toInt()
//            }
            value.contains('.') -> {
                value.toFloat()
            }
            else -> {
                value.toInt()
            }
        }
        ParserResult<Number>(data, i - offset)
    }

    val valueParser = Parser("tfn`1234567890.") { source, offset, prefix ->
        var i = offset
        Parser.assert(source, i, prefix)
        val value: Any? = when (source[i]) {
            't' -> {
                i++
                Parser.assert(source, i, 'r')
                i++
                Parser.assert(source, i, 'u')
                i++
                Parser.assert(source, i, 'e')
                i++
                true
            }
            'f' -> {
                i++
                Parser.assert(source, i, 'a')
                i++
                Parser.assert(source, i, 'l')
                i++
                Parser.assert(source, i, 's')
                i++
                Parser.assert(source, i, 'e')
                i++
                false
            }
            'n' -> {
                i++
                Parser.assert(source, i, 'u')
                i++
                Parser.assert(source, i, 'l')
                i++
                Parser.assert(source, i, 'l')
                i++
                null
            }
            '`' -> {
                val s = stringParser(source, i)
                i += s.length
                s.data
            }
            in "1234567890." -> {
                val n = numberParser(source, i)
                i += n.length
                n.data
            }
            else -> {
                Parser.throwError(source, i, prefix)
            }
        }
        ParserResult(value, i - offset)
    }

    val attrParser = Parser("[") { source, offset, prefix ->
        var i = offset
        Parser.assert(source, i, prefix)
        i++
        val nameResult = nameParser(source, i)
        i += nameResult.length
        val operatorResult = attrOperatorParser(source, i)
        i += operatorResult.length
        val valueResult = valueParser(source, i)
        i += valueResult.length
        Parser.assert(source, i, ']')
        i++
        ParserResult(
            PropertySelector.BinaryExpression(
                nameResult.data,
                operatorResult.data,
                valueResult.data
            ), i - offset
        )
    }

    val selectorParser = Parser(nameParser.prefix) { source, offset, _ ->
        var i = offset
        val nameResult = nameParser(source, i)
        i += nameResult.length
        val attrList = mutableListOf<PropertySelector.BinaryExpression>()
        while (i < source.length && source[i] == '[') {
            val attrResult = attrParser(source, i)
            i += attrResult.length
            attrList.add(attrResult.data)
        }
        ParserResult(PropertySelector(nameResult.data, attrList), i - offset)
    }

    val combinatorSelectorParser = Parser { source, offset, _ ->
        var i = offset
        i += whiteCharParser(source, i).length
        val topSelector = selectorParser(source, i)
        i += topSelector.length
        val selectorList = mutableListOf<Pair<CombinatorSelector, PropertySelector>>()
        while (i < source.length && whiteCharParser.prefix.contains(source[i])) {
            i += whiteCharStrictParser(source, i).length
            val combinator = if (combinatorParser.prefix.contains((source[i]))) {
                val combinatorResult = combinatorParser(source, i)
                i += combinatorResult.length
                i += whiteCharStrictParser(source, i).length
                combinatorResult.data
            } else {
                CombinatorSelector()
            }
            val selectorResult = selectorParser(source, i)
            i += selectorResult.length
            selectorList.add(combinator to selectorResult.data)
        }
        ParserResult(topSelector.data to selectorList, i - offset)
    }

    val unitParser = Parser<Position.Unit> { source, offset, _ ->
        var i = offset
        Parser.assert(source, i, "%")
        i++
        ParserResult(Position.Unit.Percentage, i - offset)
    }

    val numberUnitParser = Parser { source, offset, _ ->
        var i = offset
        val valueResult = numberParser(source, i)
        i += valueResult.length
        val unitResult = unitParser(source, i)
        i += unitResult.length
        ParserResult(Position.NumberUnit(valueResult.data, unitResult.data), i - offset)
    }

    val positionParser = Parser("(") { source, offset, _ ->
        var i = offset
        Parser.assert(source, i, '(')
        i++
        i += whiteCharParser(source, i).length
        val xNumberUnitResult = numberUnitParser(source, i)
        i += xNumberUnitResult.length
        i += whiteCharParser(source, i).length
        Parser.assert(source, i, ',')
        i++
        i += whiteCharParser(source, i).length
        val yNumberUnitResult = numberUnitParser(source, i)
        i += yNumberUnitResult.length
        i += whiteCharParser(source, i).length
        Parser.assert(source, i, ')')
        i++
        ParserResult(Position(xNumberUnitResult.data, yNumberUnitResult.data), i - offset)
    }

    val endParser = Parser { source, offset, _ ->
        if (offset != source.length) {
            Parser.throwError(source, offset, null)
        }
        ParserResult(Unit, 0)
    }

    val combinatorPositionSelectorParser = Parser { source, offset, _ ->
        var i = offset
        i += whiteCharParser(source, i).length
        val combinatorSelectorResult = combinatorSelectorParser(source, i)
        i += combinatorSelectorResult.length
        val position =
            if (i < source.length && positionParser.prefix.contains(source[i])) {
                val positionResult = positionParser(source, i)
                i += positionResult.length
                positionResult.data
            } else {
                null
            }
        i += whiteCharParser(source, i).length
        i += endParser(source, i).length
        ParserResult(Pair(combinatorSelectorResult.data, position), i - offset)
    }

     val gkdSelectorParser: (String) -> GkdSelector = { source ->
        val (data) = combinatorPositionSelectorParser(source, 0)
        val propertySelectorList = mutableListOf<PropertySelector>()
        val combinatorSelectorList = mutableListOf<CombinatorSelector>()
        propertySelectorList.add(data.first.first)
        data.first.second.forEach {
            propertySelectorList.add(it.second)
            combinatorSelectorList.add(it.first)
        }
        val wrapperList = mutableListOf(PropertySelectorWrapper(propertySelectorList.first()))
        combinatorSelectorList.forEachIndexed { index, combinatorSelector ->
            val combinatorSelectorWrapper =
                CombinatorSelectorWrapper(combinatorSelector, wrapperList.last())
            val propertySelectorWrapper = PropertySelectorWrapper(propertySelectorList[index + 1], combinatorSelectorWrapper)
            wrapperList.add(propertySelectorWrapper)
        }
        GkdSelector(wrapperList.last(), data.second)
    }
}