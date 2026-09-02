package li.gkd.selector.syntax

import li.gkd.selector.LogicalOperator
import li.gkd.selector.SelectorToken
import li.gkd.selector.SelectorTokenKind
import li.gkd.selector.SelectorTokenScope
import li.gkd.selector.relation.RelationOperator
import li.gkd.selector.property.CompareOperator

internal object SelectorTokenizer {
    /** Tolerant lexical scanning for editors and syntax highlighters. */
    fun tokenize(source: String): Array<out SelectorToken> {
        val result = mutableListOf<SelectorToken>()
        var index = 0
        var bracketDepth = 0
        var relationExpressionActive = false
        var relationParenthesisDepth = 0

        fun currentScope(): SelectorTokenScope = when {
            bracketDepth > 0 -> SelectorTokenScope.Property
            relationExpressionActive -> SelectorTokenScope.Relation
            else -> SelectorTokenScope.Selector
        }

        fun add(
            kind: SelectorTokenKind,
            start: Int,
            end: Int,
            scope: SelectorTokenScope = currentScope(),
        ) {
            result.add(SelectorToken(kind, scope, start, end))
        }

        while (index < source.length) {
            val start = index
            val char = source[index]
            val logicalOperator = LogicalOperator.parseOrder.firstOrNull {
                source.startsWith(it.key, index)
            }
            when {
                char in WHITESPACE_CHARS -> {
                    val scope = when {
                        bracketDepth > 0 -> SelectorTokenScope.Property
                        relationParenthesisDepth > 0 -> SelectorTokenScope.Relation
                        else -> SelectorTokenScope.Selector
                    }
                    index++
                    while (source.getOrNull(index).isOneOf(WHITESPACE_CHARS)) index++
                    add(SelectorTokenKind.Whitespace, start, index, scope)
                    if (relationParenthesisDepth == 0) relationExpressionActive = false
                }

                char in STRING_QUOTE_CHARS -> {
                    val scan = scanString(source, index, decode = false)
                    index = scan.end
                    add(
                        if (scan.error == null) SelectorTokenKind.String else SelectorTokenKind.Invalid,
                        start,
                        index,
                    )
                }

                char.isOneOf(IDENTIFIER_START_CHARS) -> {
                    index++
                    while (source.getOrNull(index).isOneOf(IDENTIFIER_PART_CHARS)) index++
                    val text = source.substring(start, index)
                    val kind = if (bracketDepth > 0) {
                        if (text == "null" || text == "true" || text == "false") {
                            SelectorTokenKind.Keyword
                        } else {
                            SelectorTokenKind.Identifier
                        }
                    } else if (relationExpressionActive && text == "n") {
                        SelectorTokenKind.PolynomialVariable
                    } else {
                        SelectorTokenKind.Selector
                    }
                    add(kind, start, index)
                }

                char.isOneOf(DIGIT_CHARS) ||
                        (char == '-' && source.getOrNull(index + 1).isOneOf(DIGIT_CHARS) && bracketDepth > 0) -> {
                    index++
                    while (source.getOrNull(index).isOneOf(DIGIT_CHARS)) index++
                    add(SelectorTokenKind.Integer, start, index)
                }

                char == '[' -> {
                    bracketDepth++
                    index++
                    add(
                        SelectorTokenKind.Punctuation,
                        start,
                        index,
                        SelectorTokenScope.Property,
                    )
                }

                char == ']' -> {
                    index++
                    add(
                        SelectorTokenKind.Punctuation,
                        start,
                        index,
                        SelectorTokenScope.Property,
                    )
                    bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                }

                char == '@' -> {
                    index++
                    add(SelectorTokenKind.Target, start, index)
                }

                char == '*' && source.getOrNull(index + 1) != '=' -> {
                    index++
                    add(SelectorTokenKind.Wildcard, start, index)
                }

                char == '(' || char == ')' || char == ',' || char == '.' -> {
                    if (bracketDepth == 0 && relationExpressionActive) {
                        if (char == '(') relationParenthesisDepth++
                        if (char == ')' && relationParenthesisDepth > 0) relationParenthesisDepth--
                    }
                    index++
                    add(SelectorTokenKind.Punctuation, start, index)
                }

                logicalOperator != null -> {
                    index += logicalOperator.key.length
                    add(SelectorTokenKind.LogicalOperator, start, index)
                }

                else -> {
                    if (
                        bracketDepth == 0 &&
                        relationExpressionActive &&
                        relationParenthesisDepth > 0 &&
                        (char == '+' || char == '-')
                    ) {
                        index++
                        add(SelectorTokenKind.ArithmeticOperator, start, index)
                        continue
                    }
                    val operator = if (bracketDepth > 0) {
                        CompareOperator.parseOrder.firstOrNull { source.startsWith(it.key, index) }?.key
                            ?: "!".takeIf { source.startsWith(it, index) }
                    } else {
                        RelationOperator.parseOrder.firstOrNull { source.startsWith(it.key, index) }?.key
                            ?: "!".takeIf { source.startsWith(it, index) }
                    }
                    if (operator == null) {
                        index++
                        add(SelectorTokenKind.Invalid, start, index)
                    } else {
                        index += operator.length
                        if (bracketDepth == 0 && operator != "!") {
                            relationExpressionActive = true
                            relationParenthesisDepth = 0
                        }
                        add(
                            when {
                                operator == "!" -> SelectorTokenKind.LogicalOperator
                                bracketDepth > 0 -> SelectorTokenKind.CompareOperator
                                else -> SelectorTokenKind.RelationOperator
                            },
                            start,
                            index,
                            when {
                                bracketDepth > 0 -> SelectorTokenScope.Property
                                operator == "!" -> SelectorTokenScope.Selector
                                else -> SelectorTokenScope.Relation
                            },
                        )
                    }
                }
            }
        }
        return result.toTypedArray()
    }
}
