package li.songe.gkd.util

import java.lang.Error
import java.util.*

data class AttributeSelector(val attr: Attribute, val operator: Operator, val value: String) {
    sealed class Operator {
        object Equal : Operator()
        object Include : Operator()
        object Start : Operator()
        object End : Operator()
        object Less : Operator()
        object More : Operator()

        override fun toString(): String {
            return when (this) {
                End -> "$"
                Equal -> ""
                Include -> "*"
                Less -> "<"
                More -> ">"
                Start -> "^"
            } + "="
        }
    }

    sealed class Attribute {
        object Text : Attribute()
        object ChildCount : Attribute()
        object Id : Attribute()

        override fun toString(): String {
            return when (this) {
                ChildCount -> "childCount"
                Id -> "id"
                Text -> "text"
            }
        }
    }

    companion object {
        private val markCharList = listOf('*', '^', '$', '>', '<')
        fun parse(text: String): AttributeSelector {
            val stack = Stack<Char>()
            val attrSb = StringBuilder()
            val operatorSb = StringBuilder()
            val valueSb = StringBuilder()
            run loop@{
                text.forEach { c ->
                    when (c) {
                        '[' -> {
                            assert(stack.empty())
                            stack.push(c)
                        }
                        '=' -> {
                            assert(stack.peek() == '[' || markCharList.contains(stack.peek()))
                            stack.push(c)
                            operatorSb.append(c)
                        }
                        in markCharList -> {
                            assert(stack.peek() == '[')
                            stack.push(c)
                            operatorSb.append(c)
                        }
                        ']' -> {
                            assert(stack.peek() == '[' || stack.peek() == '=')
                            stack.push(c)
                            return@loop
                        }
                        else -> {
                            when (stack.peek()) {
                                '[' -> {
                                    attrSb.append(c)
                                }
                                '=' -> {
                                    valueSb.append(c)
                                }
                            }
                        }
                    }
                }
            }
            assert(attrSb.isNotEmpty())
            assert(operatorSb.isNotEmpty())
            assert(valueSb.isNotEmpty())
            val attr = when (attrSb.toString()) {
                "text" -> Attribute.Text
                "id" -> Attribute.Id
                "childCount" -> Attribute.ChildCount
                else -> throw Error("invalid attr")
            }
            val operator = when (operatorSb.toString()) {
                "=" -> Operator.Equal
                "*=" -> Operator.Include
                "^=" -> Operator.Start
                "$=" -> Operator.End
                ">=" -> Operator.More
                "<=" -> Operator.Less
                else -> throw Error("invalid operator")
            }
            val value = valueSb.toString()
//            TODO("转义字符处理")
            return AttributeSelector(attr, operator, value)
        }

        fun parseMulti(text: String): List<AttributeSelector> {
            var startIndex = -1
            var endIndex: Int
            val attrRawList = mutableListOf<String>()
            text.forEachIndexed { index, c ->
                when (c) {
                    '[' -> {
                        startIndex = index
                    }
                    ']' -> {
                        endIndex = index
                        assert(startIndex in 0 until endIndex)
                        attrRawList.add(text.substring(startIndex, endIndex + 1))
                        startIndex = -1
                        endIndex = -1
                    }
                }
            }
            return attrRawList.map { parse(it) }
        }

        fun stringify(selector: AttributeSelector): String {
            val attr = when (selector.attr) {
                Attribute.ChildCount -> "childCount"
                Attribute.Id -> "id"
                Attribute.Text -> "text"
            }
            val operator = (when (selector.operator) {
                Operator.End -> "$"
                Operator.Equal -> ""
                Operator.Include -> "*"
                Operator.Less -> "<"
                Operator.More -> ">"
                Operator.Start -> "^"
            })
            return "[$attr$operator=${selector.value}]"
        }

        fun stringifyMulti(selectorList: List<AttributeSelector>): String {
            return selectorList.joinToString("") { stringify(it) }
        }
    }

}
