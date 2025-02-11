package li.songe.selector.parser

import li.songe.selector.SyntaxException
import li.songe.selector.connect.ConnectExpression
import li.songe.selector.connect.ConnectOperator
import li.songe.selector.connect.ConnectSegment
import li.songe.selector.connect.PolynomialExpression
import li.songe.selector.connect.TupleExpression

internal sealed interface ConnectParser : BaseParser {

    private fun isTupleExpression(): Boolean {
        // ^\(\s*\d+\s*,
        var start = i
        try {
            if (char == '(') {
                i++
                while (char.inStr(WHITESPACE_CHAR)) {
                    i++
                }
                if (char.inStr(DIGIT_CHAR)) {
                    i++
                    while (char.inStr(DIGIT_CHAR)) {
                        i++
                    }
                    while (char.inStr(WHITESPACE_CHAR)) {
                        i++
                    }
                    if (char == ',') {
                        return true
                    }
                }
            }
            return false
        } finally {
            i = start
        }
    }

    fun readConnectOperator(): ConnectOperator {
        val operator = ConnectOperator.allSubClasses.find { v ->
            source.startsWith(v.key, i)
        }
        if (operator == null) {
            errorExpect("connect operator")
        }
        i += operator.key.length
        return operator
    }

    // [+-][a][n]
    fun readMonomial(): Monomial {
        expectOneOfChar(MONOMIAL_START_CHAR, "MONOMIAL_START_CHAR")
        val signal = when (char) {
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
        readWhiteSpace()
        expectOneOfChar("1234567890n", "Monomial")
        val coefficient = signal * if (char.inStr(DIGIT_CHAR)) {
            readUInt()
        } else {
            1
        }
        val power = if (char == 'n') {
            i++
            1
        } else {
            0
        }
        return Monomial(coefficient = coefficient, power = power)
    }

    // (1,2,3)
    fun readTupleExpression(): TupleExpression {
        expectChar('(')
        i++
        readWhiteSpace()
        val numbers = mutableListOf<Int>()
        expectOneOfChar(POSITIVE_DIGIT_CHAR, "POSITIVE_DIGIT_CHAR")
        while (char.inStr(POSITIVE_DIGIT_CHAR)) {
            val t = i
            val v = readUInt()
            if (numbers.isNotEmpty()) {
                if (numbers.last() >= v) {
                    i = t
                    errorExpect("increasing int")
                }
            }
            numbers.add(v)
            readWhiteSpace()
            if (char == ',') {
                i++
                readWhiteSpace()
            }
        }
        expectChar(')')
        i++
        return TupleExpression(numbers)
    }

    // (+-an+-b)
    fun readPolynomialExpression(): PolynomialExpression {
        expectOneOfChar(CONNECT_EXP_START_CHAR, "CONNECT_EXP_START_CHAR")
        val start = i
        val monomials = mutableListOf<Monomial>()
        if (char == '(') {
            i++
            readWhiteSpace()
            while (true) {
                if (monomials.isNotEmpty()) {
                    expectOneOfChar("+-", "+-")
                }
                if (monomials.size >= 2) {
                    errorExpect("only support tow monomial")
                }
                val t = i
                val v = readMonomial()
                if (monomials.any { it.power == v.power }) {
                    i = t
                    errorExpect("duplicated monomial power")
                }
                monomials.add(v)
                readWhiteSpace()
                if (!char.inStr("+-")) {
                    break
                }
            }
            expectChar(')')
            i++
        } else {
            monomials.add(readMonomial())
        }
        // an+b
        try {
            return PolynomialExpression(
                a = monomials.find { it.power == 1 }?.coefficient ?: 0,
                b = monomials.find { it.power == 0 }?.coefficient ?: 0
            )
        } catch (_: SyntaxException) {
            i = start
            errorExpect("valid an+b polynomial")
        }
    }

    fun readConnectExpression(): ConnectExpression {
        return if (isTupleExpression()) {
            readTupleExpression()
        } else {
            readPolynomialExpression()
        }
    }

    fun readConnectSegment(): ConnectSegment {
        val operator = readConnectOperator()
        val connectExpression = if (char.inStr(CONNECT_EXP_START_CHAR)) {
            readConnectExpression()
        } else {
            PolynomialExpression()
        }
        return ConnectSegment(
            operator,
            connectExpression
        )
    }
}
