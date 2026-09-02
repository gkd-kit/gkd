package li.gkd.selector.syntax

import li.gkd.selector.SelectorPositionKind
import li.gkd.selector.relation.RelationExpression
import li.gkd.selector.relation.RelationOperator
import li.gkd.selector.relation.RelationSelector
import li.gkd.selector.relation.PolynomialExpression
import li.gkd.selector.relation.TupleExpression

internal class RelationSyntaxParser(
    private val context: ParserContext,
) {
    private val cursor = context.cursor

    private class Monomial(
        val coefficient: Int,
        val power: Int,
    )

    fun readRelationSelector(): RelationSelector = context.positioned(SelectorPositionKind.Relation) {
        val operator = readOperator()
        val expression = if (cursor.current.isOneOf("(n$DIGIT_CHARS")) {
            readExpression()
        } else {
            PolynomialExpression()
        }
        RelationSelector(operator, expression)
    }

    private fun readOperator(): RelationOperator {
        val operator = RelationOperator.parseOrder.firstOrNull {
            cursor.source.startsWith(it.key, cursor.index)
        } ?: cursor.errorExpected("relation operator")
        cursor.index += operator.key.length
        return operator
    }

    private fun readExpression(): RelationExpression =
        if (isTupleExpression()) readTupleExpression() else readPolynomialExpression()

    private fun isTupleExpression(): Boolean {
        val start = cursor.index
        try {
            if (cursor.current != '(') return false
            cursor.index++
            cursor.readWhitespace()
            if (!cursor.current.isOneOf(DIGIT_CHARS)) return false
            while (cursor.current.isOneOf(DIGIT_CHARS)) cursor.index++
            cursor.readWhitespace()
            return cursor.current == ','
        } finally {
            cursor.index = start
        }
    }

    private fun readTupleExpression(): TupleExpression =
        context.positioned(SelectorPositionKind.TupleRange) {
            cursor.readChar('(')
            cursor.readWhitespace()
            val numbers = mutableListOf<Int>()
            while (true) {
                val numberStart = cursor.index
                cursor.expectOneOf(POSITIVE_DIGIT_CHARS, "positive integer")
                val value = cursor.readUnsignedInt()
                if (numbers.lastOrNull()?.let { it >= value } == true) {
                    cursor.index = numberStart
                    cursor.errorExpected("increasing integer")
                }
                numbers.add(value)
                cursor.readWhitespace()
                if (cursor.current != ',') break
                cursor.readChar(',')
                cursor.readWhitespace()
            }
            cursor.readChar(')')
            TupleExpression(numbers)
        }

    private fun readMonomial(): Monomial {
        cursor.expectOneOf("+-n$DIGIT_CHARS", "monomial")
        val sign = when (cursor.current) {
            '+' -> {
                cursor.index++
                1
            }

            '-' -> {
                cursor.index++
                -1
            }

            else -> 1
        }
        cursor.readWhitespace()
        cursor.expectOneOf("n$DIGIT_CHARS", "monomial value")
        val coefficient = sign * if (cursor.current.isOneOf(DIGIT_CHARS)) {
            cursor.readUnsignedInt()
        } else {
            1
        }
        val power = if (cursor.current == 'n') {
            cursor.index++
            1
        } else {
            0
        }
        return Monomial(coefficient, power)
    }

    private fun readPolynomialExpression(): PolynomialExpression =
        context.positioned(SelectorPositionKind.PolynomialRange) {
            cursor.expectOneOf("(n$DIGIT_CHARS", "relation expression")
            val expressionStart = cursor.index
            val monomials = mutableListOf<Monomial>()
            if (cursor.current == '(') {
                cursor.readChar('(')
                cursor.readWhitespace()
                while (true) {
                    if (monomials.isNotEmpty()) cursor.expectOneOf("+-", "'+' or '-'")
                    if (monomials.size >= 2) cursor.errorExpected("at most two monomials")
                    val monomialStart = cursor.index
                    val monomial = readMonomial()
                    if (monomials.any { it.power == monomial.power }) {
                        cursor.index = monomialStart
                        cursor.errorExpected("distinct monomial powers")
                    }
                    monomials.add(monomial)
                    cursor.readWhitespace()
                    if (!cursor.current.isOneOf("+-")) break
                }
                cursor.readChar(')')
            } else {
                monomials.add(readMonomial())
            }

            val a = monomials.firstOrNull { it.power == 1 }?.coefficient ?: 0
            val b = monomials.firstOrNull { it.power == 0 }?.coefficient ?: 0
            if (!PolynomialExpression.isValid(a, b)) {
                cursor.index = expressionStart
                cursor.errorExpected("valid an+b polynomial")
            }
            PolynomialExpression(a, b)
        }
}
