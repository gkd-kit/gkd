package li.songe.selector_android.parser

import li.songe.selector_android.GkdSelector
import li.songe.selector_android.expression.BinaryExpression
import li.songe.selector_android.operator.End
import li.songe.selector_android.operator.Equal
import li.songe.selector_android.operator.Include
import li.songe.selector_android.operator.Less
import li.songe.selector_android.operator.LessEqual
import li.songe.selector_android.operator.More
import li.songe.selector_android.operator.MoreEqual
import li.songe.selector_android.operator.NotEqual
import li.songe.selector_android.operator.Start
import li.songe.selector_android.selector.CombinatorSelector
import li.songe.selector_android.selector.PropertySelector
import li.songe.selector_android.wrapper.CombinatorSelectorWrapper
import li.songe.selector_android.wrapper.PropertySelectorWrapper

internal object Transform {
    val whiteCharParser = GkdParser("\u0020\t\r\n") { source, offset, prefix ->
        var i = offset
        var data = ""
        while (i < source.length && prefix.contains(source[i])) {
            data += source[i]
            i++
        }
        GkdParserResult(data, i - offset)
    }

    val whiteCharStrictParser = GkdParser("\u0020\t\r\n") { source, offset, prefix ->
        GkdSyntaxError.assert(source, offset, prefix, "whitespace")
        whiteCharParser(source, offset)
    }

    val nameParser =
        GkdParser("*1234567890qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM_") { source, offset, prefix ->
            var i = offset
            val s0 = source.getOrNull(i)
            if (s0 != null && !prefix.contains(s0)) {
                return@GkdParser GkdParserResult("", i - offset)
            }
            GkdSyntaxError.assert(source, i, prefix, "*0-9a-zA-Z_")
            var data = source[i].toString()
            i++
            if (data == "*") { // 范匹配
                return@GkdParser GkdParserResult(data, i - offset)
            }
            val center = "1234567890qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM_."
            while (i < source.length) {
//                . 不能在开头和结尾
                if (data[i - offset - 1] == '.') {
                    GkdSyntaxError.assert(source, i, prefix, "[0-9a-zA-Z_]")
                }
                if (center.contains(source[i])) {
                    data += source[i]
                } else {
                    break
                }
                i++
            }
            GkdParserResult(data, i - offset)
        }

    val combinatorOperatorParser = GkdParser("+-><") { source, offset, prefix ->
        var i = offset
        GkdSyntaxError.assert(source, i, prefix)
        return@GkdParser when (source[i]) {
            '+' -> GkdParserResult(CombinatorSelector.Operator.ElderBrother, ++i - offset)
            '-' -> GkdParserResult(CombinatorSelector.Operator.YoungerBrother, ++i - offset)
            '>' -> GkdParserResult(CombinatorSelector.Operator.Ancestor, ++i - offset)
            '<' -> GkdParserResult(CombinatorSelector.Operator.Child, ++i - offset)
            else -> GkdSyntaxError.throwError(source, i, prefix)
        }
    }

    val integerParser = GkdParser("1234567890") { source, offset, prefix ->
        var i = offset
        GkdSyntaxError.assert(source, i, prefix, "number")
        var s = ""
        while (prefix.contains(source[i]) && i < source.length) {
            s += source[i]
            i++
        }
        GkdParserResult(s.toInt(), i - offset)
    }

    //    [+-][a][n[^b]]
    val monomialParser = GkdParser("+-1234567890n") { source, offset, prefix ->
        var i = offset
        GkdSyntaxError.assert(source, i, prefix)
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
        // [a][n[^b]]
        GkdSyntaxError.assert(source, i, integerParser.prefix + "n")
        val coefficient =
            if (integerParser.prefix.contains(source[i])) {
                val coefficientResult = integerParser(source, i)
                i += coefficientResult.length
                coefficientResult.data
            } else {
                1
            } * signal
        // [n[^b]]
        if (i < source.length && source[i] == 'n') {
            i++
            if (i < source.length && source[i] == '^') {
                i++
                val powerResult = integerParser(source, i)
                i += powerResult.length
                return@GkdParser GkdParserResult(Pair(powerResult.data, coefficient), i - offset)
            } else {
                return@GkdParser GkdParserResult(Pair(1, coefficient), i - offset)
            }
        } else {
            return@GkdParser GkdParserResult(Pair(0, coefficient), i - offset)
        }
    }

    //    ([+-][a][n[^b]] [+-][a][n[^b]])
    val expressionParser = GkdParser("(0123456789n") { source, offset, prefix ->
        var i = offset
        GkdSyntaxError.assert(source, i, prefix)
        val monomialResultList = mutableListOf<GkdParserResult<Pair<Int, Int>>>()
        when (source[i]) {
            '(' -> {
                i++
                i += whiteCharParser(source, i).length
                GkdSyntaxError.assert(source, i, monomialParser.prefix)
                while (source[i] != ')') {
                    if (monomialResultList.size > 0) {
                        GkdSyntaxError.assert(source, i, "+-")
                    }
                    val monomialResult = monomialParser(source, i)
                    monomialResultList.add(monomialResult)
                    i += monomialResult.length
                    i += whiteCharParser(source, i).length
                    if (i >= source.length) {
                        GkdSyntaxError.assert(source, i, ")")
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
        GkdParserResult(CombinatorSelector.PolynomialExpression(map.filter { (_, coefficient) ->
            coefficient != 0
        }), i - offset)
    }

    //    [+-><](a*n^b)
    val combinatorParser = GkdParser(combinatorOperatorParser.prefix) { source, offset, _ ->
        var i = offset
        val operatorResult = combinatorOperatorParser(source, i)
        i += operatorResult.length
        var expressionResult: GkdParserResult<CombinatorSelector.PolynomialExpression>? = null
        if (i < source.length && expressionParser.prefix.contains(source[i])) {
            expressionResult = expressionParser(source, i)
            i += expressionResult.length
        }
        GkdParserResult(
            CombinatorSelector(
                operatorResult.data,
                expressionResult?.data ?: CombinatorSelector.PolynomialExpression()
            ), i - offset
        )
    }

    val attrOperatorParser = GkdParser("><!*$^=") { source, offset, prefix ->
        var i = offset
        GkdSyntaxError.assert(source, i, prefix)
        val attrOperator =
            when (source[i]) {
                '=' -> {
                    i++
                    when (source.getOrNull(i)) {
                        '=' -> {
                            i++
                            Equal
                        }
                        else -> {
                            Equal
                        }
                    }
                }
                '>' -> {
                    i++
                    when (source.getOrNull(i)) {
                        '=' -> {
                            i++
                            MoreEqual
                        }
                        else -> {
                            More
                        }
                    }
                }
                '<' -> {
                    i++
                    when (source.getOrNull(i)) {
                        '=' -> {
                            i++
                            LessEqual
                        }
                        else -> {
                            Less
                        }
                    }
                }
                '!' -> {
                    i++
                    GkdSyntaxError.assert(source, i, "=")
                    i++
                    NotEqual
                }
                '*' -> {
                    i++
                    GkdSyntaxError.assert(source, i, "=")
                    i++
                    Include
                }
                '^' -> {
                    i++
                    GkdSyntaxError.assert(source, i, "=")
                    i++
                    Start
                }
                '$' -> {
                    i++
                    GkdSyntaxError.assert(source, i, "=")
                    i++
                    End
                }
                else -> {
                    GkdSyntaxError.throwError(source, i, prefix)
                }
            }
        GkdParserResult(attrOperator, i - offset)
    }

    val stringParser = GkdParser("`") { source, offset, prefix ->
        var i = offset
        GkdSyntaxError.assert(source, i, prefix)
        i++
        var data = ""
        while (source[i] != '`') {
            if (i == source.length - 1) {
                GkdSyntaxError.assert(source, i, "`")
                break
            }
            if (source[i] == '\\') {
                i++
                GkdSyntaxError.assert(source, i)
                if (source[i] == '`') {
                    data += source[i]
                    GkdSyntaxError.assert(source, i + 1)
                } else {
                    data += '\\' + source[i].toString()
                }
            } else {
                data += source[i]
            }
            i++
        }
        i++
        GkdParserResult(data, i - offset)
    }

    val numberParser = GkdParser("1234567890.") { source, offset, prefix ->
        var i = offset
        GkdSyntaxError.assert(source, i, prefix)
        var value = ""
        value = if (source[i] == '.') {
            value += source[i]
            i++
            GkdSyntaxError.assert(source, i, "1234567890")
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
        GkdParserResult<Number>(data, i - offset)
    }

    val propertyParser =
        GkdParser("1234567890qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM_") { source, offset, prefix ->
            var i = offset
            GkdSyntaxError.assert(source, i, prefix)
            var data = source[i].toString()
            i++
            while (i < source.length) {
                if (!prefix.contains(source[i])) {
                    break
                }
                data += source[i]
                i++
            }

            GkdParserResult(data, i - offset)
        }

    val valueParser = GkdParser("tfn`1234567890.") { source, offset, prefix ->
        var i = offset
        GkdSyntaxError.assert(source, i, prefix)
        val value: Any? = when (source[i]) {
            't' -> {
                i++
                "rue".forEach { c ->
                    GkdSyntaxError.assert(source, i, c.toString())
                    i++
                }
                true
            }
            'f' -> {
                i++
                "alse".forEach { c ->
                    GkdSyntaxError.assert(source, i, c.toString())
                    i++
                }
                false
            }
            'n' -> {
                i++
                "ull".forEach { c ->
                    GkdSyntaxError.assert(source, i, c.toString())
                    i++
                }
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
                GkdSyntaxError.throwError(source, i, prefix)
            }
        }
        GkdParserResult(value, i - offset)
    }

    val attrParser = GkdParser("[") { source, offset, prefix ->
        var i = offset
        GkdSyntaxError.assert(source, i, prefix)
        i++
        val parserResult = propertyParser(source, i)
        i += parserResult.length
        val operatorResult = attrOperatorParser(source, i)
        i += operatorResult.length
        val valueResult = valueParser(source, i)
        i += valueResult.length
        GkdSyntaxError.assert(source, i, "]")
        i++
        GkdParserResult(
            BinaryExpression(
                parserResult.data,
                operatorResult.data,
                valueResult.data
            ), i - offset
        )
    }

    val selectorParser = GkdParser { source, offset, _ ->
        var i = offset
        var match = false
        if (source.getOrNull(i) == '@') {
            match = true
            i++
        }
        val nameResult = nameParser(source, i)
        i += nameResult.length
        val attrList = mutableListOf<BinaryExpression>()
        while (i < source.length && source[i] == '[') {
            val attrResult = attrParser(source, i)
            i += attrResult.length
            attrList.add(attrResult.data)
        }
        if (nameResult.length == 0 && attrList.size == 0) {
            GkdSyntaxError.throwError(source, i, "[")
        }
        GkdParserResult(PropertySelector(match, nameResult.data, attrList), i - offset)
    }

    val combinatorSelectorParser = GkdParser { source, offset, _ ->
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
        GkdParserResult(topSelector.data to selectorList, i - offset)
    }

    val endParser = GkdParser { source, offset, _ ->
        if (offset != source.length) {
            GkdSyntaxError.throwError(source, offset, "end")
        }
        GkdParserResult(Unit, 0)
    }

    val gkdSelectorParser: (String) -> GkdSelector = { source ->
        var i = 0
        i += whiteCharParser(source, i).length
        val combinatorSelectorResult = combinatorSelectorParser(source, i)
        i += combinatorSelectorResult.length

        i += whiteCharParser(source, i).length
        i += endParser(source, i).length
        val data = combinatorSelectorResult.data
        val propertySelectorList = mutableListOf<PropertySelector>()
        val combinatorSelectorList = mutableListOf<CombinatorSelector>()
        propertySelectorList.add(data.first)
        data.second.forEach {
            propertySelectorList.add(it.second)
            combinatorSelectorList.add(it.first)
        }
        val wrapperList = mutableListOf(PropertySelectorWrapper(propertySelectorList.first()))
        combinatorSelectorList.forEachIndexed { index, combinatorSelector ->
            val combinatorSelectorWrapper =
                CombinatorSelectorWrapper(combinatorSelector, wrapperList.last())
            val propertySelectorWrapper =
                PropertySelectorWrapper(propertySelectorList[index + 1], combinatorSelectorWrapper)
            wrapperList.add(propertySelectorWrapper)
        }
        GkdSelector(wrapperList.last())
    }
}